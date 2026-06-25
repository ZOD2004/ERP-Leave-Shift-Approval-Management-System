package com.murali.service;

import com.murali.dto.DailyExpectedShift;
import com.murali.dto.LeaveDurationResultDTO;
import com.murali.entity.*;
import com.murali.entity.enums.LeaveSession;
import com.murali.entity.enums.RotationSegmentType;
import com.murali.exception.PastDateException;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.HolidayRepository;
import com.murali.repository.ShiftAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Slf4j
@Service
@RequiredArgsConstructor
public class DurationEngineService {

    private final AuditLogService auditLoggingService;
    private final EmployeeRepository employeeRepository;

    // Inject the new Master Calculation Engine
    private final ScheduleCalculationService scheduleCalculationService;

    @Transactional(readOnly = true)
    public LeaveDurationResultDTO calculateLeaveDuration(LocalDate startDate, LocalDate endDate, Employee detachedEmployee, LeaveSession startSession, LeaveSession endSession, boolean applySandwichRulePolicy) {
        Employee employee = employeeRepository.findById(detachedEmployee.getId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        if (endDate.isBefore(startDate)) {
            throw new PastDateException("End date cannot be before Start date");
        }

        // 1. Ask the Batch Engine for the entire leave window in one go!
        Map<Long, List<DailyExpectedShift>> batchSchedules = scheduleCalculationService.calculateBatchShifts(List.of(employee), startDate, endDate);
        List<DailyExpectedShift> expectations = batchSchedules.getOrDefault(employee.getId(), Collections.emptyList());

        BigDecimal baseWorkingDays = BigDecimal.ZERO;
        int offDaysCount = 0;

        // 2. Evaluate each day based purely on the Engine's output
        for (DailyExpectedShift expected : expectations) {

            // The engine naturally sets isWorkingDay = false for Holidays, Intentional Off Days, and Full-Day Leaves.
            if (expected.isWorkingDay()) {
                baseWorkingDays = baseWorkingDays.add(BigDecimal.ONE);
            } else {
                offDaysCount++;
                log.info("{} is a non-working day (Holiday, Off-Day, or Existing Leave)", expected.getTargetDate());
            }
        }

        if (baseWorkingDays.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException("You cannot apply for leave exclusively on your scheduled off-days or public holidays.");
        }

        boolean sandwichRuleTriggered = false;
        BigDecimal netLeaveDays = baseWorkingDays;
        BigDecimal sandwichPenaltyDays = BigDecimal.ZERO;

        // 3. Apply Sandwich Rule (Now includes Holidays since they are non-working days)
        if (applySandwichRulePolicy && offDaysCount > 0) {
            sandwichRuleTriggered = true;
            sandwichPenaltyDays = BigDecimal.valueOf(offDaysCount);
            netLeaveDays = netLeaveDays.add(sandwichPenaltyDays);
            log.info("Sandwich rule applied. Added {} non-working days to total.", offDaysCount);
        }

        // 4. Handle Half-Day Sessions
        if (startDate.equals(endDate)) {
            if (startSession == LeaveSession.FIRST_HALF || startSession == LeaveSession.SECOND_HALF) {
                netLeaveDays = netLeaveDays.subtract(new BigDecimal("0.5"));
                baseWorkingDays = baseWorkingDays.subtract(new BigDecimal("0.5"));
            }
        } else {
            if (startSession == LeaveSession.SECOND_HALF) {
                netLeaveDays = netLeaveDays.subtract(new BigDecimal("0.5"));
                baseWorkingDays = baseWorkingDays.subtract(new BigDecimal("0.5"));
            }
            if (endSession == LeaveSession.FIRST_HALF) {
                netLeaveDays = netLeaveDays.subtract(new BigDecimal("0.5"));
                baseWorkingDays = baseWorkingDays.subtract(new BigDecimal("0.5"));
            }
        }

        if (netLeaveDays.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Calculated leave duration is invalid (0 days). Check your session selections.");
        }

        log.info("Calculation complete. Net Days: {}, Base Working Days: {}, Sandwich Triggered: {}", netLeaveDays, baseWorkingDays, sandwichRuleTriggered);

        String newState = String.format("{ \"startDate\": \"%s\", \"endDate\": \"%s\", \"netLeaveDays\": %s, \"sandwichRule\": %b }", startDate, endDate, netLeaveDays, sandwichRuleTriggered);
        auditLoggingService.saveAuditLog(null, "CALCULATE_DURATION", "none", null, newState);

        return new LeaveDurationResultDTO(netLeaveDays, sandwichRuleTriggered, baseWorkingDays, sandwichPenaltyDays);
    }

    public LocalDate getPreviousWorkingDay(LocalDate date, Employee employee) {
        return findWorkingDay(date, employee, -1);
    }

    public LocalDate getNextWorkingDay(LocalDate date, Employee employee) {
        return findWorkingDay(date, employee, 1);
    }

    private LocalDate findWorkingDay(LocalDate start, Employee detachedEmployee, int stepDays) {
        Employee employee = employeeRepository.findById(detachedEmployee.getId())
                .orElseThrow(() -> new IllegalArgumentException("Employee not found"));

        LocalDate current = start.plusDays(stepDays);
        int safeguard = 0;

        while (safeguard < 30) {
            // Ask the single-day engine! No manual DB checks needed.
            DailyExpectedShift expected = scheduleCalculationService.calculateDailyShift(employee, current);

            // If the engine says they are supposed to work, we found our adjacent day!
            if (expected.isWorkingDay()) {
                return current;
            }

            current = current.plusDays(stepDays);
            safeguard++;
        }
        return current;
    }
}