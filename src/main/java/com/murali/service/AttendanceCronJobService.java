package com.murali.service;

import com.murali.entity.Attendance;
import com.murali.entity.LeaveType;
import com.murali.entity.ShiftAssignment;
import com.murali.repository.AttendanceRepository;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.HolidayRepository;
import com.murali.repository.LeaveTypeRepository;
import com.murali.repository.ShiftAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private static final int MINIMUM_HOURS_FOR_FULL_DAY = 4;

    // FIX: Changed cron to run daily at 1:00 AM to process the previous day's metrics safely
    @Scheduled(cron = "0 0 1 * * ?")
    @Transactional
    public void reconcileDailyAttendance() {
        // FIX: Look back at yesterday to ensure the entire workday/shift cycle has concluded
        LocalDate targetDate = LocalDate.now().minusDays(1);
        log.info("Starting Daily Attendance Reconciliation for target date: {}", targetDate);

        // FIX: Holiday check now evaluates the targetDate (yesterday), not today
        if (holidayRepository.existsByHolidayDate(targetDate)) {
            log.info("Target date {} was a system-wide holiday. Halting automated absence penalties.", targetDate);
            return;
        }

        List<Long> activeEmployeeIds = employeeRepository.findAllActiveEmployeeIds();

        // FIX: Adjusted queries to target targetDate
        Map<Long, ShiftAssignment> targetDateAssignmentsMap = shiftAssignmentRepository.findAllByAssignmentDate(targetDate)
                .stream()
                .collect(Collectors.toMap(sa -> sa.getEmployee().getId(), sa -> sa));

        Map<Long, Attendance> targetDateAttendanceMap = attendanceRepository.findAllByAttendanceDate(targetDate)
                .stream()
                .collect(Collectors.toMap(a -> a.getEmployee().getId(), a -> a));

        List<LeaveType> casualLeaves = leaveTypeRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase("Casual Leave","CL-001");

        if (casualLeaves.isEmpty()) {
            log.error("Casual Leave type not found! Halting attendance reconciliation to prevent data corruption.");
            return;
        }
        LeaveType casualLeave = casualLeaves.getFirst();

        for (Long employeeId : activeEmployeeIds) {

            // --- THE ROTATIONAL SHIFT BYPASS ---
            if (!targetDateAssignmentsMap.containsKey(employeeId)) {
                continue;
            }

            Attendance attendance = targetDateAttendanceMap.getOrDefault(
                    employeeId,
                    initializeEmptyAttendance(employeeId, targetDate, targetDateAssignmentsMap.get(employeeId))
            );

            String currentStatus = attendance.getStatus();

            if ("ON_LEAVE".equals(currentStatus) || "FULL_LEAVE".equals(currentStatus)) {
                continue;
            }

            // Rule A: No Check-In -> Mark Absent and Deduct
            if (attendance.getCheckIn() == null) {
                if (!"HALF_DAY_LEAVE".equals(currentStatus)) {
                    attendance.setStatus("ABSENT");

                    leaveBalanceService.deduct(
                            attendance.getEmployee(),
                            casualLeave,
                            BigDecimal.valueOf(1.0),
                            null,
                            targetDate.getYear() // FIX: Use targetDate year
                    );
                    log.info("Employee {} marked ABSENT for {}. 1 day deducted.", employeeId, targetDate);
                } else {
                    attendance.setStatus("HALF_DAY_ABSENT");
                    leaveBalanceService.deduct(
                            attendance.getEmployee(),
                            casualLeave,
                            BigDecimal.valueOf(0.5),
                            null,
                            targetDate.getYear() // FIX: Use targetDate year
                    );
                    log.info("Employee {} on Half-Day Leave missed shift on {}. 0.5 days deducted.", employeeId, targetDate);
                }
            }
            // Rule B: Checked In -> Validate Hours Worked
            else {
                if (attendance.getCheckOut() == null) {
                    attendance.setStatus("MISSING_CHECKOUT");

                    leaveBalanceService.deduct(
                            attendance.getEmployee(),
                            casualLeave,
                            BigDecimal.valueOf(0.5),
                            null,
                            targetDate.getYear() // FIX: Use targetDate year
                    );
                    attendance = attendanceRepository.save(attendance);

                    // AUTO-TRIGGER THE MANAGER WORKFLOW
                    attendanceCorrectionService.autoCreateCorrection(attendance);
                    log.info("Employee {} forgot to check out on {}. Marked MISSING_CHECKOUT. 0.5 days deducted.", employeeId, targetDate);
                } else {
                    long hoursWorked = Duration.between(attendance.getCheckIn(), attendance.getCheckOut()).toHours();

                    if (hoursWorked < MINIMUM_HOURS_FOR_FULL_DAY) {
                        attendance.setStatus("HALF_DAY_ABSENT");

                        leaveBalanceService.deduct(
                                attendance.getEmployee(),
                                casualLeave,
                                BigDecimal.valueOf(0.5),
                                null,
                                targetDate.getYear() // FIX: Use targetDate year
                        );
                        log.info("Employee {} worked < 4 hours ({} hrs) on {}. Marked HALF_DAY_ABSENT. 0.5 days deducted.", employeeId, hoursWorked, targetDate);
                    }
                }
            }

            attendanceRepository.save(attendance);
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
}