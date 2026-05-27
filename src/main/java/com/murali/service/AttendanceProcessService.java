package com.murali.service;

import com.murali.entity.AttendanceStatus;
import com.murali.dto.TeamAttendanceSummaryDTO;
import com.murali.entity.Attendance;
import com.murali.entity.Employee;
import com.murali.entity.ShiftAssignment;
import com.murali.repository.AttendanceRepository;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.ShiftAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AuditLogService auditLoggingService;

    private static final int GRACE_PERIOD_MINUTES = 15;

    @Transactional
    public Attendance processDailyPunch(Long employeeId, LocalDateTime punchTime, boolean isCheckIn) {
        LocalDate today = punchTime.toLocalDate();

        ShiftAssignment assignment = shiftAssignmentRepository
                .findByEmployeeIdAndAssignmentDate(employeeId, today)
                .orElseThrow(() -> new IllegalArgumentException("No shift assigned for employee ID " + employeeId + " on " + today));

        // Track if this is a brand new record for the audit log
        boolean isNewRecord = false;
        Attendance attendance = attendanceRepository
                .findByEmployee_IdAndAttendanceDate(employeeId, today)
                .orElse(null);

        if (attendance == null) {
            isNewRecord = true;
            attendance = new Attendance();
            attendance.setEmployee(employeeRepository.getReferenceById(employeeId));
            attendance.setShiftAssignment(assignment);
            attendance.setAttendanceDate(today);
            attendance.setIsLate(false);
            attendance.setStatus("PENDING");
        }

        // Capture Old State for Audit Log
        String oldStatus = attendance.getStatus();
        LocalDateTime oldCheckIn = attendance.getCheckIn();
        LocalDateTime oldCheckOut = attendance.getCheckOut();

        // Hard Block: Cannot check in if on a Full Day Leave
        if (isCheckIn && "ON_LEAVE".equals(attendance.getStatus())) {
            throw new IllegalStateException("Check-in denied: You are marked as ON LEAVE for today. If this is a mistake, please cancel your leave request first.");
        }

        if (isCheckIn) {
            if (attendance.getCheckIn() != null) {
                log.warn("Duplicate check-in attempt by Employee {} ignored.", employeeId);
                return attendance; // Abort early, keep original check-in
            }
            attendance.setCheckIn(punchTime);

            LocalTime effectiveStart = assignment.getEffectiveStartTime();
            LocalTime actualStart = punchTime.toLocalTime();

            boolean isLate = actualStart.isAfter(effectiveStart.plusMinutes(GRACE_PERIOD_MINUTES));
            attendance.setIsLate(isLate);

            if (isLate) {
                log.info("Employee {} checked in late at {} against effective start {}", employeeId, actualStart, effectiveStart);
            }

            if (!"HALF_DAY_LEAVE".equals(attendance.getStatus())) {
                attendance.setStatus(isLate ? "LATE" : "PRESENT");
            }

        } else {
            if (attendance.getCheckIn() == null) {
                log.warn("Clock-out anomaly: Employee {} attempted to check out without a prior check-in.", employeeId);
            }
            attendance.setCheckOut(punchTime);
        }

        Attendance savedAttendance = attendanceRepository.save(attendance);

        String oldState = isNewRecord ? null : String.format("{ \"status\": \"%s\", \"checkIn\": \"%s\", \"checkOut\": \"%s\" }",
                oldStatus,
                oldCheckIn != null ? oldCheckIn : "null",
                oldCheckOut != null ? oldCheckOut : "null");

        String newState = String.format("{ \"status\": \"%s\", \"checkIn\": \"%s\", \"checkOut\": \"%s\", \"isLate\": %b }",
                savedAttendance.getStatus(),
                savedAttendance.getCheckIn() != null ? savedAttendance.getCheckIn() : "null",
                savedAttendance.getCheckOut() != null ? savedAttendance.getCheckOut() : "null",
                savedAttendance.getIsLate());

        auditLoggingService.saveAuditLog(savedAttendance.getId(), isCheckIn ? "PUNCH_IN" : "PUNCH_OUT", "attendance", oldState, newState);

        return savedAttendance;
    }
    public Optional<Attendance> getTodayAttendance(Long employeeId) {
        return attendanceRepository.findByEmployee_IdAndAttendanceDate(employeeId, LocalDate.now());
    }

    public List<Attendance> getEmployeeAttendanceHistory(Long employeeId, LocalDate startDate, LocalDate endDate) {
        return attendanceRepository.findAttendanceHistoryByEmployee(employeeId, startDate, endDate);
    }

    public TeamAttendanceSummaryDTO getTodayTeamAttendanceSummary(Long managerEmployeeId) {
        LocalDate today = LocalDate.now();

        // STEP 1: Fetch reporting employees
        List<Employee> reportingEmployees = employeeRepository.findReportingEmployees(managerEmployeeId);

        if (reportingEmployees.isEmpty()) {
            return new TeamAttendanceSummaryDTO(0, 0, 0);
        }

        List<Long> teamIds = reportingEmployees.stream().map(Employee::getId).toList();

        // STEP 2: Fetch today's shift assignments to know who is EXPECTED today
        List<ShiftAssignment> todayShifts = shiftAssignmentRepository.findTodayAssignmentsForEmployees(teamIds, today);
        List<Long> expectedEmployeeIds = todayShifts.stream()
                .map(sa -> sa.getEmployee().getId())
                .toList();

        // STEP 3: Fetch today's actual attendance records
        List<Attendance> todayAttendances = attendanceRepository.findByEmployeeIdsAndAttendanceDate(teamIds, today);
        List<Long> attendedEmployeeIds = todayAttendances.stream()
                .map(a -> a.getEmployee().getId())
                .toList();

        int presentCount = 0;
        int lateCount = 0;
        int absentCount = 0;

        for (Attendance attendance : todayAttendances) {
            String status = attendance.getStatus();
            if (AttendanceStatus.PRESENT.equals(status)) {
                presentCount++;
            } else if (AttendanceStatus.LATE.equals(status)) {
                lateCount++;
            } else if (AttendanceStatus.ABSENT.equals(status)) {
                absentCount++;
            }
        }

        // Add employees who HAD a shift but DID NOT punch in to the Absent count
        for (Long expectedId : expectedEmployeeIds) {
            if (!attendedEmployeeIds.contains(expectedId)) {
                absentCount++;
            }
        }

        return new TeamAttendanceSummaryDTO(presentCount, lateCount, absentCount);
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
}