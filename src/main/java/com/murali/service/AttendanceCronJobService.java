package com.murali.service;

import com.murali.entity.Attendance;
import com.murali.entity.LeaveType;
import com.murali.entity.ShiftAssignment;
import com.murali.entity.AuditLog;
import com.murali.repository.AttendanceRepository;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.HolidayRepository;
import com.murali.repository.LeaveTypeRepository;
import com.murali.repository.ShiftAssignmentRepository;
import com.murali.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceCronJobService {

    private final AttendanceRepository attendanceRepository;
    private final HolidayRepository holidayRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveTypeRepository leaveTypeRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final AttendanceCorrectionService attendanceCorrectionService;
    private final AuditLogRepository auditLogRepository;

    private static final int MINIMUM_HOURS_FOR_FULL_DAY = 4;

    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void reconcileDailyAttendance() {
        LocalDate targetDate = LocalDate.now().minusDays(1);
        log.info("Starting Daily Attendance Reconciliation for target date: {}", targetDate);

        if (holidayRepository.existsByHolidayDate(targetDate)) {
            log.info("Target date {} was a system-wide holiday. Halting automated absence penalties.", targetDate);
            return;
        }

        List<Long> activeEmployeeIds = employeeRepository.findAllActiveEmployeeIds();

        Map<Long, ShiftAssignment> targetDateAssignmentsMap = shiftAssignmentRepository.findAllByAssignmentDate(targetDate)
                .stream()
                .collect(Collectors.toMap(sa -> sa.getEmployee().getId(), sa -> sa));

        Map<Long, Attendance> targetDateAttendanceMap = attendanceRepository.findAllByAttendanceDate(targetDate)
                .stream()
                .collect(Collectors.toMap(a -> a.getEmployee().getId(), a -> a));

        LeaveType emergencyLeave = leaveTypeRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase("Emergency Leave", "EMG-001")
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("Emergency Leave type not found!"));

        LeaveType halfDayLeave = leaveTypeRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase("Half Day Leave", "HDL-001")
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("Half Day Leave type not found!"));

        for (Long employeeId : activeEmployeeIds) {

            if (!targetDateAssignmentsMap.containsKey(employeeId)) {
                continue;
            }

            boolean isNewRecord = !targetDateAttendanceMap.containsKey(employeeId);
            Attendance attendance = targetDateAttendanceMap.getOrDefault(
                    employeeId,
                    initializeEmptyAttendance(employeeId, targetDate, targetDateAssignmentsMap.get(employeeId))
            );

            String oldStatus = attendance.getStatus();

            if ("ON_LEAVE".equals(oldStatus) || "FULL_LEAVE".equals(oldStatus)) {
                continue;
            }

            // Rule A: No Check-In -> Mark Absent and Deduct EMERGENCY LEAVE
            if (attendance.getCheckIn() == null) {
                if (!"HALF_DAY_LEAVE".equals(oldStatus)) {
                    attendance.setStatus("ABSENT");
                    leaveBalanceService.deductPenalty(attendance.getEmployee(), emergencyLeave, BigDecimal.valueOf(1.0), targetDate.getYear(), "Absent without notice");
                } else {
                    attendance.setStatus("HALF_DAY_ABSENT");
                    leaveBalanceService.deductPenalty(attendance.getEmployee(), emergencyLeave, BigDecimal.valueOf(0.5), targetDate.getYear(), "Missed shift on half-day leave");
                }
            }
            // Rule B: Checked In -> Validate Hours Worked
            else {
                if (attendance.getCheckOut() == null) {
                    attendance.setStatus("MISSING_CHECKOUT");
                    // We must save early here to pass a valid ID to the correction service
                    attendance = attendanceRepository.save(attendance);

                    attendanceCorrectionService.autoCreateCorrection(attendance);
                    log.info("Employee {} missing checkout on {}. Sent to manager.", employeeId, targetDate);
                } else {
                    long hoursWorked = Duration.between(attendance.getCheckIn(), attendance.getCheckOut()).toHours();

                    if (hoursWorked < MINIMUM_HOURS_FOR_FULL_DAY) {
                        attendance.setStatus("HALF_DAY_ABSENT");
                        String desc = "Worked less than " + MINIMUM_HOURS_FOR_FULL_DAY + " hours";
                        leaveBalanceService.deductPenalty(attendance.getEmployee(), halfDayLeave, BigDecimal.valueOf(0.5),
                                targetDate.getYear(), desc);
                    }
                }
            }

            // Save the entity and capture the final state
            Attendance savedAttendance = attendanceRepository.save(attendance);
            String newStatus = savedAttendance.getStatus();

            // Only log if the cron job actually changed the status
            if (!newStatus.equals(oldStatus)) {
                String oldState = isNewRecord ? null : String.format("{ \"status\": \"%s\" }", oldStatus);
                String newState = String.format("{ \"status\": \"%s\", \"automated\": true }", newStatus);

                saveCronAuditLog(savedAttendance.getId(), isNewRecord ? "CREATED" : "UPDATED", "attendance", oldState, newState);
            }
        }

        log.info("Daily Attendance Reconciliation for {} completed successfully.", targetDate);
    }

    private Attendance initializeEmptyAttendance(Long employeeId, LocalDate date, ShiftAssignment assignment) {
        Attendance attendance = new Attendance();
        attendance.setEmployee(employeeRepository.getReferenceById(employeeId));
        attendance.setShiftAssignment(assignment);
        attendance.setAttendanceDate(date);
        attendance.setStatus("PENDING");
        return attendance;
    }

    public String getLastRunStatus() { return "SUCCESS"; }
    public java.time.LocalDateTime getLastRunTime() { return java.time.LocalDateTime.now().minusHours(2); }

    private void saveCronAuditLog(Long recordId, String action, String entityName, String oldState, String newState) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setRecordId(recordId);
            auditLog.setAction(action);
            auditLog.setEntityName(entityName);
            auditLog.setPerformedBy("SYSTEM (CRON)"); // Hardcoded because there is no SecurityContext
            auditLog.setOldState(oldState);
            auditLog.setNewState(newState);

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log for cron job record {}: {}", recordId, e.getMessage());
        }
    }
}