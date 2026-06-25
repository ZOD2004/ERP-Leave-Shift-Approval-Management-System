package com.murali.service;

import com.murali.dto.DailyExpectedShift;
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
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceProcessService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final TimeLogRepository timeLogRepository;
    private final AuditLogService auditLoggingService;
    private final ScheduleCalculationService scheduleCalculationService;

    @Transactional
    public Attendance processDailyPunch(Long employeeId, LocalDateTime punchTime, boolean isCheckIn) {
        LocalDate today = punchTime.toLocalDate();
        LocalDate yesterday = today.minusDays(1);
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        // 1. Check if this punch belongs to Yesterday's Night Shift
        DailyExpectedShift yesterdayExpected = scheduleCalculationService.calculateDailyShift(employee, yesterday);
        if (yesterdayExpected.isWorkingDay() && yesterdayExpected.getExpectedShift().getCrossesMidnight()) {
            Shift yShift = yesterdayExpected.getExpectedShift();
            LocalDateTime shiftStart = yesterday.atTime(yShift.getStartTime()).minusHours(2);
            LocalDateTime shiftEnd = today.atTime(yShift.getEndTime()).plusHours(4);

            if (!punchTime.isBefore(shiftStart) && !punchTime.isAfter(shiftEnd)) {
                log.info("Punch at {} mapped to yesterday's Night Shift for Employee {}", punchTime, employeeId);
                return recordPunch(employee, punchTime, isCheckIn, yesterday, yesterdayExpected);
            }
        }

        // 2. Otherwise, map to Today's Shift
        DailyExpectedShift todayExpected = scheduleCalculationService.calculateDailyShift(employee, today);
        if (!todayExpected.isWorkingDay() && !todayExpected.isManualOverride()) {
            // Let them punch in on an Off-Day (generates an anomaly/overtime)
            log.warn("Employee {} is punching in on an unassigned day.", employeeId);
        }

        return recordPunch(employee, punchTime, isCheckIn, today, todayExpected);
    }

    private Attendance recordPunch(Employee employee, LocalDateTime punchTime, boolean isCheckIn, LocalDate targetDate, DailyExpectedShift expectedShift) {
        boolean isNewRecord = false;

        Attendance attendance = attendanceRepository.findByEmployeeIdAndAttendanceDate(employee.getId(), targetDate)
                .orElse(null);

        if (attendance == null) {
            isNewRecord = true;
            attendance = new Attendance();
            attendance.setEmployee(employee);
            attendance.setAttendanceDate(targetDate);
            attendance.setStatus(AttendanceStatus.PENDING);

            // SNAPSHOT THE ENGINE RESULTS! (Immutability)
            if (expectedShift.isWorkingDay() && expectedShift.getExpectedShift() != null) {
                Shift shift = expectedShift.getExpectedShift();
                attendance.setExpectedShiftId(shift.getId());
                attendance.setExpectedShiftName(shift.getName());
                attendance.setExpectedStartTime(shift.getStartTime());
                attendance.setExpectedEndTime(shift.getEndTime());
                attendance.setExpectedWorkMinutes(shift.getRequiredWorkTime());
                attendance.setCrossesMidnight(shift.getCrossesMidnight());
            }
            attendance.setIsManualOverride(expectedShift.isManualOverride());
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

        recalculateTimeline(attendance, expectedShift);

        String oldState = isNewRecord ? null : "{ \"logs\": \"Added new punch\" }";
        String newState = String.format("{ \"punchTime\": \"%s\", \"type\": \"%s\" }", punchTime, isCheckIn ? "IN" : "OUT");
        auditLoggingService.saveAuditLog(attendance.getId(), isCheckIn ? "PUNCH_IN" : "PUNCH_OUT", "attendance", oldState, newState);

        return attendance;
    }

    @Transactional
    public void recalculateTimeline(Attendance attendance, DailyExpectedShift engineResult) {
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
        } else if (attendance.getExpectedStartTime() == null) {
            // They worked on an intentional off day
            attendance.setStatus(AttendanceStatus.PRESENT);
        } else {
            long expectedMinutes = calculateExpectedMinutes(attendance, engineResult);
            long graceMinutes = (attendance.getExpectedWorkMinutes() != null) ? attendance.getExpectedWorkMinutes() : 0;

            if (engineResult != null && engineResult.getActiveLeave() != null) graceMinutes = graceMinutes / 2;

            if (totalMinutes >= (expectedMinutes - graceMinutes)) {
                attendance.setStatus(engineResult != null && engineResult.getActiveLeave() != null ? AttendanceStatus.HALF_DAY_LEAVE : AttendanceStatus.PRESENT);
            } else {
                attendance.setStatus(AttendanceStatus.PARTIAL_DAY);
            }
        }

        attendanceRepository.save(attendance);
    }

    private long calculateExpectedMinutes(Attendance attendance, DailyExpectedShift engineResult) {
        LocalTime start = attendance.getExpectedStartTime();
        LocalTime end = attendance.getExpectedEndTime();

        if (engineResult != null && engineResult.getLeaveSession() != null && engineResult.getLeaveSession() != LeaveSession.FULL_DAY) {
            Shift shift = engineResult.getExpectedShift();
            if (shift != null) {
                if (engineResult.getLeaveSession() == LeaveSession.FIRST_HALF) {
                    start = shift.getSecondHalfStartTime() != null ? shift.getSecondHalfStartTime() : start;
                } else if (engineResult.getLeaveSession() == LeaveSession.SECOND_HALF) {
                    end = shift.getFirstHalfEndTime() != null ? shift.getFirstHalfEndTime() : end;
                }
            }
        }

        if (Boolean.TRUE.equals(attendance.getCrossesMidnight()) && end.isBefore(start)) {
            return Duration.between(start, LocalTime.MAX).toMinutes() + Duration.between(LocalTime.MIDNIGHT, end).toMinutes() + 1;
        }
        return Duration.between(start, end).toMinutes();
    }

    @Transactional
    public void recalculateAttendanceForDate(Long employeeId, LocalDate targetDate) {
        Attendance attendance = attendanceRepository.findByEmployeeIdAndAttendanceDate(employeeId, targetDate).orElse(null);
        if (attendance == null) return;

        Employee employee = employeeRepository.findById(employeeId).orElseThrow();
        DailyExpectedShift engineResult = scheduleCalculationService.calculateDailyShift(employee, targetDate);

        recalculateTimeline(attendance, engineResult);
        log.info("Successfully recalculated attendance for Employee {} on {}", employeeId, targetDate);
    }

    // Keep your getTodayAttendance, getEmployeeAttendanceHistory, getTodayTeamAttendanceSummary methods
    // NOTE: For getTodayTeamAttendanceSummary, replace the shiftAssignmentRepository call with scheduleCalculationService.calculateBatchShifts()
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

        // 1. Ask the Batch Engine for today's schedule for the entire team
        Map<Long, List<DailyExpectedShift>> batchSchedules = scheduleCalculationService.calculateBatchShifts(reportingEmployees, today, today);

        // 2. Extract ONLY the IDs of employees who are expected to work today (not off, not on full-day leave)
        List<Long> expectedEmployeeIds = new ArrayList<>();
        for (Employee emp : reportingEmployees) {
            List<DailyExpectedShift> scheduleList = batchSchedules.getOrDefault(emp.getId(), Collections.emptyList());
            if (!scheduleList.isEmpty() && scheduleList.get(0).isWorkingDay()) {
                expectedEmployeeIds.add(emp.getId());
            }
        }

        // 3. Fetch actual attendance records for the team
        List<Long> teamIds = reportingEmployees.stream().map(Employee::getId).toList();
        List<Attendance> todayAttendances = attendanceRepository.findByEmployeeIdsAndAttendanceDate(teamIds, today);

        int presentCount = 0;
        int expectedCount = 0;
        int absentOrLeaveCount = 0;

        // 4. Calculate metrics based strictly on who the engine says should be here
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
                } else if (AttendanceStatus.ON_LEAVE.equals(att.getStatus()) || AttendanceStatus.ABSENT.equals(att.getStatus())) {
                    absentOrLeaveCount++;
                } else {
                    expectedCount++;
                }
            }
        }

        return new TeamAttendanceSummaryDTO(presentCount, expectedCount, absentOrLeaveCount);
    }
}