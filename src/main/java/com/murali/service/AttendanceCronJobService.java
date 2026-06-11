package com.murali.service;

import com.murali.entity.*;
import com.murali.entity.enums.AttendanceStatus;
import com.murali.entity.enums.LeaveSession;
import com.murali.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceCronJobService {

    private final AttendanceRepository attendanceRepository;
    private final TimeLogRepository timeLogRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final AttendanceCorrectionService attendanceCorrectionService;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AttendanceProcessService attendanceProcessService;
    private LocalDateTime lastRunTime;
    private String lastRunStatus = "WAITING";

    @Scheduled(cron = "0 0 12 * * ?")
    @Transactional
    public void dailyMiddaySweeper() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        try {
            this.lastRunStatus = "RUNNING";
            log.info("Starting Midday Sweeper for target date: {}", yesterday);

            List<Attendance> incompleteAttendances = attendanceRepository.findIncompleteAttendancesForDate(yesterday);
            for (Attendance att : incompleteAttendances) {
                if (AttendanceStatus.WORKING.equals(att.getStatus())) {
                    attendanceCorrectionService.evaluateAndRouteAnomaly(att);//missed checkout for emp

                } else if (AttendanceStatus.PARTIAL_DAY.equals(att.getStatus())) {
                    attendanceCorrectionService.evaluateAndRouteAnomaly(att);//not enough in time

                } else if (AttendanceStatus.PENDING.equals(att.getStatus())) {
                    att.setStatus(AttendanceStatus.ABSENT);// no punch
                    deductPenalty(att.getEmployee(), "Emergency Leave", 1.0, yesterday.getYear(), "Absent without notice");
                    attendanceRepository.save(att);
                }
            }
            List<ShiftAssignment> assignments = shiftAssignmentRepository.findOverlappingAssignmentsForDate(yesterday);
            List<Long> empIds = assignments.stream().map(sa -> sa.getEmployee().getId()).toList();

            List<Long> employeesWithRecords = attendanceRepository.findAllByAttendanceDate(yesterday)
                    .stream().map(a -> a.getEmployee().getId()).toList();

            List<LeaveRequest> approvedLeaves = leaveRequestRepository.findApprovedLeavesForEmployeesInRange(
                    empIds, "APPROVED", yesterday, yesterday);

            //dint come
            for (ShiftAssignment assignment : assignments) {
                Employee emp = assignment.getEmployee();

                if (!employeesWithRecords.contains(emp.getId())) {
                    LeaveRequest leave = getActiveLeaveForDate(approvedLeaves, yesterday);
                    LeaveSession session = getSessionForDate(leave, yesterday);

                    String dayOfWeek = yesterday.getDayOfWeek().name();
                    boolean isWorkingDay = assignment.getShift().getWorkingDays().stream().anyMatch(wd -> wd.name().equals(dayOfWeek));

                    if (!isWorkingDay) {
                        saveEmptyAttendance(emp, assignment, yesterday, AttendanceStatus.OFF_DAY);
                    } else if (leave != null && session == LeaveSession.FULL_DAY) {
                        saveEmptyAttendance(emp, assignment, yesterday, AttendanceStatus.ON_LEAVE);
                    } else {
                        handleAbsence(emp, assignment, yesterday, leave, session);
                    }
                }
            }

            this.lastRunStatus = "SUCCESS";
            this.lastRunTime = LocalDateTime.now();
            log.info("Midday Sweeper completed successfully for {}", yesterday);

        } catch (Exception e) {
            this.lastRunStatus = "FAILED";
            this.lastRunTime = LocalDateTime.now();
            log.error("Midday Sweeper failed for target date: {}", yesterday, e);
        }
    }


    private void handleAbsence(Employee employee, ShiftAssignment assignment, LocalDate targetDate, LeaveRequest leaveRequest, LeaveSession todaySession) {
        Attendance attendance = getOrCreateAttendance(employee, assignment, targetDate);

        if (leaveRequest != null && todaySession != LeaveSession.FULL_DAY) {
            attendance.setStatus(AttendanceStatus.HALF_DAY_ABSENT);
            deductPenalty(employee, "Unpaid Leave", 0.5, targetDate.getYear(), "Missed shift on half-day leave");
        } else {
            attendance.setStatus(AttendanceStatus.ABSENT);
            deductPenalty(employee, "Unpaid Leave", 1.0, targetDate.getYear(), "Absent without notice");
        }
        attendanceRepository.save(attendance);
    }

    private Attendance getOrCreateAttendance(Employee employee, ShiftAssignment assignment, LocalDate targetDate) {
        return attendanceRepository.findByEmployeeIdAndAttendanceDate(employee.getId(), targetDate)
                .orElseGet(() -> {
                    Attendance newAtt = new Attendance();
                    newAtt.setEmployee(employee);
                    newAtt.setShiftAssignment(assignment);
                    newAtt.setAttendanceDate(targetDate);
                    return newAtt;
                });
    }

    private void saveEmptyAttendance(Employee employee, ShiftAssignment assignment, LocalDate targetDate, AttendanceStatus status) {
        Attendance attendance = getOrCreateAttendance(employee, assignment, targetDate);
        attendance.setStatus(status);
        attendanceRepository.save(attendance);
    }

    private void deductPenalty(Employee employee, String leaveTypeName, double days, int year, String desc) {
        LeaveType type = leaveTypeRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(leaveTypeName, "UPL-001")
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("Leave type not found!"));
        leaveBalanceService.deductPenalty(employee, type, BigDecimal.valueOf(days), year, desc);
    }

    @Transactional
    public void recalculateAttendanceForDate(Long employeeId, LocalDate targetDate) {
        Attendance attendance = attendanceRepository.findByEmployeeIdAndAttendanceDate(employeeId, targetDate).orElse(null);
        if (attendance == null) {
            log.warn("Cannot recalculate: No attendance record found for Employee {} on {}", employeeId, targetDate);
            return;
        }

        ShiftAssignment assignment = shiftAssignmentRepository.findAssignmentByEmployeeAndDate(employeeId, targetDate).orElse(null);
        if (assignment == null) return;

        LeaveRequest leaveRequest = leaveRequestRepository.findApprovedLeaveForEmployeeOnDate(employeeId, targetDate).orElse(null);

        attendanceProcessService.recalculateTimeline(attendance, assignment.getShift(), leaveRequest);

        log.info("Successfully recalculated attendance for Employee {} on {}", employeeId, targetDate);
    }

    private LeaveSession getSessionForDate(LeaveRequest request, LocalDate targetDate) {
        if (request == null) return null;
        if (targetDate.equals(request.getStartDate()) && targetDate.equals(request.getEndDate())) {
            return request.getStartSession();
        }
        if (targetDate.equals(request.getStartDate())) {
            return request.getStartSession();
        }
        if (targetDate.equals(request.getEndDate())) {
            return request.getEndSession();
        }
        return LeaveSession.FULL_DAY;
    }

    public LocalDateTime getLastRunTime() {
        return lastRunTime;
    }

    public String getLastRunStatus() {
        return lastRunStatus;
    }


    private LeaveRequest getActiveLeaveForDate(List<LeaveRequest> leaves, LocalDate targetDate) {
        if (leaves == null || leaves.isEmpty()) {
            return null;
        }
        for (LeaveRequest leave : leaves) {
            if (!targetDate.isBefore(leave.getStartDate())
                    && !targetDate.isAfter(leave.getEndDate())) {
                return leave;
            }
        }
        return null;
    }
}