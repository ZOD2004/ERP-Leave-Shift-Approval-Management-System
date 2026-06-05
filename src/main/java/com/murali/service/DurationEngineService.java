package com.murali.service;

import com.murali.dto.LeaveDurationResultDTO;
import com.murali.entity.Employee;
import com.murali.entity.enums.LeaveSession;
import com.murali.entity.Shift;
import com.murali.entity.ShiftAssignment;
import com.murali.exception.PastDateException;
import com.murali.repository.HolidayRepository;
import com.murali.repository.ShiftAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DurationEngineService {

    private final HolidayRepository holidayRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final AuditLogService auditLoggingService;

    public LeaveDurationResultDTO calculateLeaveDuration(
            LocalDate startDate, LocalDate endDate,
            Employee employee,
            LeaveSession startSession, LeaveSession endSession,
            boolean applySandwichRulePolicy) {

        log.debug("Starting leave calculation for Employee ID: {} | Dates: {} to {}", employee.getId(), startDate, endDate);

        if (endDate.isBefore(startDate)) {
            log.error("Calculation failed: End date {} is before Start date {}", endDate, startDate);
            throw new PastDateException("End date cannot be before Start date");
        }

        List<ShiftAssignment> assignments = shiftAssignmentRepository
                .findByEmployeeIdInAndDateRange(List.of(employee.getId()), startDate, endDate);

        Map<LocalDate, ShiftAssignment> assignmentMap = assignments.stream()
                .collect(Collectors.toMap(ShiftAssignment::getStartDate, sa -> sa));

        // 1. Fetch Public Holidays
        List<LocalDate> holidays = holidayRepository.findHolidayDatesBetween(startDate, endDate);

        BigDecimal baseWorkingDays = BigDecimal.ZERO;
        int offDaysCount = 0;

        LocalDate currentDate = startDate;

        // 2. Iterate through every day to classify it
        while (!currentDate.isAfter(endDate)) {
            ShiftAssignment dailyShift = assignmentMap.get(currentDate);

            boolean isHoliday = holidays.contains(currentDate);
            boolean isOffDay;

            // --- THE FIX: Fallback Logic instead of throwing an Exception ---
            if (dailyShift == null) {
                log.debug("No shift assigned for {}. Applying standard weekend fallback.", currentDate);
                java.time.DayOfWeek day = currentDate.getDayOfWeek();
                isOffDay = (day == java.time.DayOfWeek.SATURDAY || day == java.time.DayOfWeek.SUNDAY);
            } else {
                isOffDay = !isWorkingDayForShift(currentDate, dailyShift.getShift());
            }

            if (isHoliday) {
                log.debug("{} is a Public Holiday (Free)", currentDate);
            } else if (isOffDay) {
                log.debug("{} is a scheduled Off-Day / Weekend", currentDate);
                offDaysCount++;
            } else {
                baseWorkingDays = baseWorkingDays.add(BigDecimal.ONE);
            }

            currentDate = currentDate.plusDays(1);
        }

        // 3. Validation: Block applying solely on Off-Days/Holidays
        if (baseWorkingDays.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException("You cannot apply for leave exclusively on your scheduled off-days or a public holiday.");
        }

        // 4. Apply the Sandwich Rule
        boolean sandwichRuleTriggered = false;
        BigDecimal netLeaveDays = baseWorkingDays;

        if (applySandwichRulePolicy && offDaysCount > 0) {
            sandwichRuleTriggered = true;
            // Add the off-days to the penalty. (Holidays remain free)
            netLeaveDays = netLeaveDays.add(BigDecimal.valueOf(offDaysCount));
            log.info("Sandwich rule applied. Added {} off-days to total.", offDaysCount);
        }

        // 5. Handle Sessions (Subtracting 0.5 days where applicable)
        if (startDate.equals(endDate)) {
            // Single day leave logic
            if (startSession == LeaveSession.FIRST_HALF || startSession == LeaveSession.SECOND_HALF) {
                netLeaveDays = netLeaveDays.subtract(new BigDecimal("0.5"));
                baseWorkingDays = baseWorkingDays.subtract(new BigDecimal("0.5"));
            }
        } else {
            // Multi-day leave logic
            if (startSession == LeaveSession.SECOND_HALF) {
                netLeaveDays = netLeaveDays.subtract(new BigDecimal("0.5"));
                baseWorkingDays = baseWorkingDays.subtract(new BigDecimal("0.5"));
            }
            if (endSession == LeaveSession.FIRST_HALF) {
                netLeaveDays = netLeaveDays.subtract(new BigDecimal("0.5"));
                baseWorkingDays = baseWorkingDays.subtract(new BigDecimal("0.5"));
            }
        }

        // 6. Final Validation
        if (netLeaveDays.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Calculated leave duration is invalid (0 days). Check your session selections.");
        }

        log.info("Calculation complete. Net Days: {}, Base Working Days: {}, Sandwich Triggered: {}",
                netLeaveDays, baseWorkingDays, sandwichRuleTriggered);

        // 7. Audit Logging
        String newState = String.format("{ \"startDate\": \"%s\", \"endDate\": \"%s\", \"netLeaveDays\": %s, \"sandwichRule\": %b }",
                startDate, endDate, netLeaveDays, sandwichRuleTriggered);
        auditLoggingService.saveAuditLog(null, "CALCULATE_DURATION", "none", null, newState);

        return new LeaveDurationResultDTO(netLeaveDays, sandwichRuleTriggered, baseWorkingDays);
    }

    private boolean isWorkingDayForShift(LocalDate date, Shift shift) {
        String dayName = date.getDayOfWeek().name();
        return shift.getWorkingDays().stream()
                .anyMatch(wd -> wd.name().equals(dayName));
    }
}