package com.murali.service;

import com.murali.entity.*;
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
    private final AuditLogRepository auditLogRepository;
    private final AttendanceProcessService attendanceProcessService;
    private LocalDateTime lastRunTime;
    private String lastRunStatus = "WAITING";


    private void handleAbsence(Employee employee, ShiftAssignment assignment, LocalDate targetDate, LeaveRequest leaveRequest, LeaveSession todaySession) {
        Attendance attendance = getOrCreateAttendance(employee, assignment, targetDate);

        if (leaveRequest != null && todaySession != LeaveSession.FULL_DAY) {
            attendance.setStatus("HALF_DAY_ABSENT");
            deductPenalty(employee, "Emergency Leave", 0.5, targetDate.getYear(), "Missed shift on half-day leave");
        } else {
            attendance.setStatus("ABSENT");
            deductPenalty(employee, "Emergency Leave", 1.0, targetDate.getYear(), "Absent without notice");
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

    private void saveEmptyAttendance(Employee employee, ShiftAssignment assignment, LocalDate targetDate, String status) {
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

        // Fire the centralized Engine!
        attendanceProcessService.recalculateTimeline(attendance, assignment.getShift(), leaveRequest);

        log.info("Successfully recalculated attendance for Employee {} on {}", employeeId, targetDate);
    }

    private LeaveSession getSessionForDate(LeaveRequest request, LocalDate targetDate) {
        if (request == null) return null;

        // If the leave is just one single day, the startSession dictates the leave type
        if (targetDate.equals(request.getStartDate()) && targetDate.equals(request.getEndDate())) {
            return request.getStartSession();
        }

        // If it's the first day of a multi-day leave
        if (targetDate.equals(request.getStartDate())) {
            return request.getStartSession();
        }

        // If it's the last day of a multi-day leave
        if (targetDate.equals(request.getEndDate())) {
            return request.getEndSession();
        }

        // Any day in the middle of a multi-day leave is ALWAYS a full day
        return LeaveSession.FULL_DAY;
    }

    public LocalDateTime getLastRunTime() {
        return lastRunTime;
    }

    public String getLastRunStatus() {
        return lastRunStatus;
    }

    // Runs at 12:00 PM (Noon) every day safely AFTER all night shifts have finished
    @Scheduled(cron = "0 0 12 * * ?")
    @Transactional
    public void dailyMiddaySweeper() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        try {
            this.lastRunStatus = "RUNNING";
            log.info("Starting Midday Sweeper for target date: {}", yesterday);

            // --- PHASE 1: Close Incomplete Records ---
            List<Attendance> incompleteAttendances = attendanceRepository.findIncompleteAttendancesForDate(yesterday);
            for (Attendance att : incompleteAttendances) {
                if (AttendanceStatus.WORKING.equals(att.getStatus())) {
                    // They forgot to check out. Let the anomaly engine evaluate it.
                    attendanceCorrectionService.evaluateAndRouteAnomaly(att);

                } else if (AttendanceStatus.PARTIAL_DAY.equals(att.getStatus())) {
                    // They checked out early. Let the anomaly engine evaluate if grace periods cover it.
                    attendanceCorrectionService.evaluateAndRouteAnomaly(att);

                } else if (AttendanceStatus.PENDING.equals(att.getStatus())) {
                    // Created, but never actually punched
                    att.setStatus(AttendanceStatus.ABSENT);
                    deductPenalty(att.getEmployee(), "Emergency Leave", 1.0, yesterday.getYear(), "Absent without notice");
                    attendanceRepository.save(att);
                }
            }

            // --- PHASE 2: Catch Complete Absences ---
            // Find all people who were supposed to work yesterday but didn't even trigger a PENDING record
            List<ShiftAssignment> assignments = shiftAssignmentRepository.findOverlappingAssignmentsForDate(yesterday);
            List<Long> empIds = assignments.stream().map(sa -> sa.getEmployee().getId()).toList();

            // Get records that DO exist
            List<Long> employeesWithRecords = attendanceRepository.findAllByAttendanceDate(yesterday)
                    .stream().map(a -> a.getEmployee().getId()).toList();

            List<LeaveRequest> approvedLeaves = leaveRequestRepository.findApprovedLeavesForEmployeesInRange(
                    empIds, "APPROVED", yesterday, yesterday);

            for (ShiftAssignment assignment : assignments) {
                Employee emp = assignment.getEmployee();

                // If they have NO record at all
                if (!employeesWithRecords.contains(emp.getId())) {
                    LeaveRequest leave = getActiveLeaveForDate(approvedLeaves, yesterday);
                    LeaveSession session = getSessionForDate(leave, yesterday);

                    String dayOfWeek = yesterday.getDayOfWeek().name();
                    boolean isWorkingDay = assignment.getShift().getWorkingDays().stream().anyMatch(wd -> wd.name().equals(dayOfWeek));

                    if (!isWorkingDay) {
                        saveEmptyAttendance(emp, assignment, yesterday, "OFF_DAY");
                    } else if (leave != null && session == LeaveSession.FULL_DAY) {
                        saveEmptyAttendance(emp, assignment, yesterday, AttendanceStatus.ON_LEAVE);
                    } else {
                        // They completely missed a working day
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
    private LeaveRequest getActiveLeaveForDate(List<LeaveRequest> leaves, LocalDate targetDate) {
        if (leaves == null || leaves.isEmpty()) return null;
        return leaves.stream()
                .filter(l -> !targetDate.isBefore(l.getStartDate()) && !targetDate.isAfter(l.getEndDate()))
                .findFirst().orElse(null);
    }
}