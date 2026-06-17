package com.murali.service;

import com.murali.entity.*;
import com.murali.entity.enums.AttendanceStatus;
import com.murali.entity.enums.LeaveSession;
import com.murali.repository.*;
import com.murali.dto.TeamAttendanceSummaryDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceProcessService {

    private final AttendanceRepository attendanceRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final TimeLogRepository timeLogRepository;
    private final AuditLogService auditLoggingService;
    private final LeaveRequestRepository leaveRequestRepository;

    @Transactional
    public Attendance processDailyPunch(Long employeeId, LocalDateTime punchTime, boolean isCheckIn) {
        LocalDate today = punchTime.toLocalDate();
        LocalDate yesterday = today.minusDays(1);

        Optional<ShiftAssignment> yesterdayAssignment = shiftAssignmentRepository.findByEmployeeIdAndAssignmentDate(employeeId, yesterday);

        if (yesterdayAssignment.isPresent()) {
            Shift yShift = yesterdayAssignment.get().getShift();
            if (yShift.getCrossesMidnight()) {
                LocalDateTime shiftStart = yesterday.atTime(yShift.getStartTime()).minusHours(2);
                LocalDateTime shiftEnd = today.atTime(yShift.getEndTime()).plusHours(4);

                if (!punchTime.isBefore(shiftStart) && !punchTime.isAfter(shiftEnd)) {
                    log.info("Punch at {} mapped to yesterday's Night Shift for Employee {}", punchTime, employeeId);
                    return recordPunch(employeeId, punchTime, isCheckIn, yesterday, yesterdayAssignment.get());
                }
            }
        }

        ShiftAssignment todayAssignment = shiftAssignmentRepository.findByEmployeeIdAndAssignmentDate(employeeId, today)
                .orElseThrow(() -> new IllegalArgumentException("No shift assigned for employee ID " + employeeId + " on " + today));

        return recordPunch(employeeId, punchTime, isCheckIn, today, todayAssignment);
    }

    private Attendance recordPunch(Long employeeId, LocalDateTime punchTime, boolean isCheckIn, LocalDate targetDate, ShiftAssignment assignment) {
        boolean isNewRecord = false;

        Attendance attendance = attendanceRepository.findByEmployeeIdAndAttendanceDate(employeeId, targetDate)
                .orElse(null);

        if (attendance == null) {
            isNewRecord = true;
            attendance = new Attendance();
            attendance.setEmployee(employeeRepository.getReferenceById(employeeId));
            attendance.setShiftAssignment(assignment);
            attendance.setAttendanceDate(targetDate);
            attendance.setStatus(AttendanceStatus.PENDING);
            attendance = attendanceRepository.save(attendance);
        }

        if (AttendanceStatus.ON_LEAVE.equals(attendance.getStatus())) {
            throw new IllegalStateException("Punch denied: You are marked as ON LEAVE. Cancel your leave request first.");
        }

        TimeLog timeLog = new TimeLog();
        timeLog.setAttendance(attendance);
        timeLog.setPunchTime(punchTime);
        timeLog.setPunchType(isCheckIn ? "IN" : "OUT");
        timeLog.setSource("MANUAL");
        timeLogRepository.save(timeLog);

        LeaveRequest leave = leaveRequestRepository.findApprovedLeaveForEmployeeOnDate(employeeId, targetDate).orElse(null);

        recalculateTimeline(attendance, assignment.getShift(), leave);

        String oldState = isNewRecord ? null : String.format("{ \"logs\": \"Added new punch\" }");
        String newState = String.format("{ \"punchTime\": \"%s\", \"type\": \"%s\" }", punchTime, isCheckIn ? "IN" : "OUT");
        auditLoggingService.saveAuditLog(attendance.getId(), isCheckIn ? "PUNCH_IN" : "PUNCH_OUT", "attendance", oldState, newState);

        return attendance;
    }
    public Optional<Attendance> getTodayAttendance(Long employeeId) {
        return attendanceRepository.findByEmployeeIdAndAttendanceDate(employeeId, LocalDate.now());
    }

    public List<Attendance> getEmployeeAttendanceHistory(Long employeeId, LocalDate startDate, LocalDate endDate) {
        return attendanceRepository.findAttendanceHistoryByEmployee(employeeId, startDate, endDate);
    }

    public TeamAttendanceSummaryDTO getTodayTeamAttendanceSummary(Long managerEmployeeId) {
        LocalDate today = LocalDate.now();
        List<Employee> reportingEmployees = employeeRepository.findReportingEmployees(managerEmployeeId);

        if (reportingEmployees.isEmpty()) {
            return new TeamAttendanceSummaryDTO(0, 0, 0);
        }

        List<Long> teamIds = reportingEmployees.stream().map(Employee::getId).toList();
        List<ShiftAssignment> todayShifts = shiftAssignmentRepository.findTodayAssignmentsForEmployees(teamIds, today);
        List<Long> expectedEmployeeIds = todayShifts.stream().map(sa -> sa.getEmployee().getId()).toList();

        List<Attendance> todayAttendances = attendanceRepository.findByEmployeeIdsAndAttendanceDate(teamIds, today);

        int presentCount = 0;
        int expectedCount = 0;
        int absentOrLeaveCount = 0;

        for (Long expectedId : expectedEmployeeIds) {
            Attendance att = todayAttendances.stream()
                    .filter(a -> a.getEmployee().getId().equals(expectedId))
                    .findFirst()
                    .orElse(null);

            if (att == null) {
                expectedCount++;
            } else {
                if (att.getFirstCheckIn() != null) {
                    presentCount++;
                } else if ("ON_LEAVE".equals(att.getStatus()) || "ABSENT".equals(att.getStatus())) {
                    absentOrLeaveCount++;
                } else {
                    expectedCount++;
                }
            }
        }

        return new TeamAttendanceSummaryDTO(presentCount, expectedCount, absentOrLeaveCount);
    }

    public List<Attendance> getTodayTeamAttendanceDetails(Long managerEmployeeId) {
        LocalDate today = LocalDate.now();
        List<Employee> reportingEmployees = employeeRepository.findReportingEmployees(managerEmployeeId);

        if (reportingEmployees.isEmpty()) {
            return List.of();
        }
        List<Long> teamIds = reportingEmployees.stream().map(Employee::getId).toList();
        return attendanceRepository.findByEmployeeIdsAndAttendanceDate(teamIds, today);
    }

    @Transactional(readOnly = true)
    public boolean isCurrentlyClockedIn(Long employeeId) {
        return timeLogRepository.findFirstByAttendance_Employee_IdOrderByPunchTimeDesc(employeeId)
                .map(timeLog -> "IN".equalsIgnoreCase(timeLog.getPunchType()))
                .orElse(false);
    }

    @Transactional
    public void recalculateTimeline(Attendance attendance, Shift shift, LeaveRequest leaveRequest) {
        List<TimeLog> logs = timeLogRepository.findByAttendanceIdOrderByPunchTimeAsc(attendance.getId());

        int totalMinutes = 0;
        LocalDateTime firstIn = null;
        LocalDateTime lastOut = null;
        boolean currentlyWorking = false;

        for (int i = 0; i < logs.size(); i++) {
            TimeLog log = logs.get(i);

            if ("IN".equalsIgnoreCase(log.getPunchType())) {
                if (firstIn == null) firstIn = log.getPunchTime();
                currentlyWorking = true;

                if (i + 1 < logs.size() && "OUT".equalsIgnoreCase(logs.get(i + 1).getPunchType())) {
                    TimeLog outLog = logs.get(i + 1);
                    totalMinutes += (int) Duration.between(log.getPunchTime(), outLog.getPunchTime()).toMinutes();
                    lastOut = outLog.getPunchTime();
                    currentlyWorking = false;
                    i++;
                }
            }
        }

        attendance.setFirstCheckIn(firstIn);
        attendance.setLastCheckOut(lastOut);
        attendance.setTotalWorkedMinutes(totalMinutes);

        if (currentlyWorking) {
            attendance.setStatus(AttendanceStatus.WORKING);
        } else if (logs.isEmpty()) {
            attendance.setStatus(AttendanceStatus.PENDING);
        } else {
            long expectedMinutes = calculateExpectedMinutes(shift, leaveRequest, attendance.getAttendanceDate());
            long graceMinutes = (shift.getRequiredWorkTime() != null) ? shift.getRequiredWorkTime() : 0;

            if (leaveRequest != null) graceMinutes = graceMinutes / 2;

            if (totalMinutes >= (expectedMinutes - graceMinutes)) {
                attendance.setStatus(leaveRequest != null ? AttendanceStatus.HALF_DAY_LEAVE : AttendanceStatus.PRESENT);
            } else {
                attendance.setStatus(AttendanceStatus.PARTIAL_DAY);
            }
        }

        attendanceRepository.save(attendance);
    }

    private long calculateExpectedMinutes(Shift shift, LeaveRequest leaveRequest, LocalDate targetDate) {
        LeaveSession todaySession = getSessionForDate(leaveRequest, targetDate);
        LocalTime start = shift.getStartTime();
        LocalTime end = shift.getEndTime();

        if (todaySession != null) {
            if (todaySession == LeaveSession.FIRST_HALF) {
                start = shift.getSecondHalfStartTime();
            } else if (todaySession == LeaveSession.SECOND_HALF) {
                end = shift.getFirstHalfEndTime();
            }

            if (start == null || end == null) {
                log.warn("Shift bounds missing for Shift ID {}. Falling back to standard bounds.", shift.getId());
                start = (start == null) ? shift.getStartTime() : start;
                end = (end == null) ? shift.getEndTime() : end;
            }
        }

        if (shift.getCrossesMidnight() && end.isBefore(start)) {
            return Duration.between(start, LocalTime.MAX).toMinutes() +
                    Duration.between(LocalTime.MIDNIGHT, end).toMinutes() + 1;
        }
        return Duration.between(start, end).toMinutes();
    }

    private LeaveSession getSessionForDate(LeaveRequest request, LocalDate targetDate) {
        if (request == null) return null;
        if (targetDate.equals(request.getStartDate())) return request.getStartSession();
        if (targetDate.equals(request.getEndDate())) return request.getEndSession();
        return LeaveSession.FULL_DAY;
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

        recalculateTimeline(attendance, assignment.getShift(), leaveRequest);

        log.info("Successfully recalculated attendance for Employee {} on {}", employeeId, targetDate);
    }


}