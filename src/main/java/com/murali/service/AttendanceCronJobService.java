package com.murali.service;

import com.murali.entity.Attendance;
import com.murali.entity.LeaveType;
import com.murali.repository.AttendanceRepository;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.HolidayRepository;
import com.murali.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
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

    private static final int MINIMUM_HOURS_FOR_FULL_DAY = 4;

    @Scheduled(cron = "0 59 23 * * ?")
    @Transactional
    public void reconcileDailyAttendance() {
        LocalDate today = LocalDate.now();
        log.info("Starting Daily Attendance Reconciliation for: {}", today);

        if (isWeekend(today) || holidayRepository.existsByHolidayDate(today)) {
            log.info("Today is a weekend or holiday. Halting automated absence penalties.");
            return;
        }

        List<Long> activeEmployeeIds = employeeRepository.findAllActiveEmployeeIds();

        Map<Long, Attendance> todayAttendanceMap = attendanceRepository.findAllByAttendanceDate(today)
                .stream()
                .collect(Collectors.toMap(a -> a.getEmployee().getId(), a -> a));

        List<LeaveType> casualLeaves = leaveTypeRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase("Casual Leave","CL-001");

        if (casualLeaves.isEmpty()) {
            log.error("Casual Leave type not found! Halting attendance reconciliation to prevent data corruption.");
            return;
        }
        LeaveType casualLeave = casualLeaves.getFirst();

        for (Long employeeId : activeEmployeeIds) {
            Attendance attendance = todayAttendanceMap.getOrDefault(employeeId, initializeEmptyAttendance(employeeId, today));

            String currentStatus = attendance.getStatus();

            if ("ON_LEAVE".equals(currentStatus) || "FULL_LEAVE".equals(currentStatus)) {
                continue;
            }
            if (attendance.getCheckIn() == null) {
                if (!"HALF_DAY_LEAVE".equals(currentStatus)) {
                    attendance.setStatus("ABSENT");

                    leaveBalanceService.deduct(
                            attendance.getEmployee(),
                            casualLeave,
                            BigDecimal.valueOf(1.0),
                            null,
                            today.getYear()
                    );
                    log.info("Employee {} marked ABSENT. 1 day deducted.", employeeId);
                } else {
                    attendance.setStatus("HALF_DAY");
                    leaveBalanceService.deduct(
                            attendance.getEmployee(),
                            casualLeave,
                            BigDecimal.valueOf(0.5),
                            null,
                            today.getYear()
                    );
                    log.info("Employee {} on Half-Day Leave missed shift. 0.5 days deducted.", employeeId);
                }
            }
            else {
                long hoursWorked = calculateHoursWorked(attendance);

                if (hoursWorked < MINIMUM_HOURS_FOR_FULL_DAY) {
                    attendance.setStatus("HALF_DAY_ABSENT");
                    leaveBalanceService.deduct(
                            attendance.getEmployee(),
                            casualLeave,
                            BigDecimal.valueOf(0.5),
                            null,
                            today.getYear()
                    );
                    log.info("Employee {} worked < 4 hours ({} hrs). Marked HALF_DAY_ABSENT. 0.5 days deducted.", employeeId, hoursWorked);
                }
            }
            attendanceRepository.save(attendance);
        }

        log.info("Daily Attendance Reconciliation completed successfully.");
    }

    private boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private Attendance initializeEmptyAttendance(Long employeeId, LocalDate date) {
        Attendance attendance = new Attendance();
        attendance.setEmployee(employeeRepository.getReferenceById(employeeId));
        attendance.setAttendanceDate(date);
        attendance.setStatus("PENDING");
        return attendance;
    }

    private long calculateHoursWorked(Attendance attendance) {
        if (attendance.getCheckIn() == null) return 0;
        var endTime = (attendance.getCheckOut() != null) ? attendance.getCheckOut() : LocalDateTime.now();

        return Duration.between(attendance.getCheckIn(), endTime).toHours();
    }
}