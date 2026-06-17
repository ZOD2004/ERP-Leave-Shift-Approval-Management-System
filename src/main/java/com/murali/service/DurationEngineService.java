package com.murali.service;

import com.murali.dto.LeaveDurationResultDTO;
import com.murali.entity.*;
import com.murali.entity.enums.LeaveSession;
import com.murali.entity.enums.RotationSegmentType;
import com.murali.exception.PastDateException;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.HolidayRepository;
import com.murali.repository.ShiftAssignmentRepository;
import com.murali.repository.ShiftRotationPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
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
    private final ShiftRotationService shiftRotationService;
    private final ShiftRotationPolicyRepository shiftRotationPolicyRepository;
    private final EmployeeRepository employeeRepository;

    @Transactional(readOnly = true)
    public LeaveDurationResultDTO calculateLeaveDuration(LocalDate startDate, LocalDate endDate, Employee detachedEmployee, LeaveSession startSession, LeaveSession endSession, boolean applySandwichRulePolicy) {
        Employee employee = employeeRepository.findById(detachedEmployee.getId()).orElseThrow(() -> new IllegalArgumentException("Employee not found"));
        if (endDate.isBefore(startDate)) {
            throw new PastDateException("End date cannot be before Start date");
        }

        List<ShiftAssignment> assignments = shiftAssignmentRepository.findByEmployeeIdInAndDateRange(List.of(employee.getId()), startDate, endDate);

        Map<LocalDate, ShiftAssignment> assignmentMap = new HashMap<>();

        for (ShiftAssignment sa : assignments) {
            LocalDate date = sa.getStartDate();

            while (!date.isAfter(sa.getEndDate())) {
                assignmentMap.put(date, sa);
                date = date.plusDays(1);
            }
        }

        List<LocalDate> holidays = holidayRepository.findHolidayDatesBetween(startDate, endDate);

        ShiftRotationPolicy activePolicy = shiftRotationPolicyRepository.findActivePolicyWithSequencesByEmployeeId(employee.getId()).orElse(null);
        int totalCycleDays = (activePolicy != null) ? shiftRotationService.calculateCycleDays(activePolicy) : 0;
        LocalDate policyGeneratedUntil = activePolicy != null ? activePolicy.getGeneratedUntil() : null;
        LocalDate defaultGeneratedUntil = employee.getDefaultShiftGeneratedUntil();
        BigDecimal baseWorkingDays = BigDecimal.ZERO;
        int offDaysCount = 0;

        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            ShiftAssignment dailyShift = assignmentMap.get(currentDate);

            boolean isHoliday = holidays.contains(currentDate);
            boolean isOffDay;

            if (dailyShift != null) {
                isOffDay = false;
            } else if (activePolicy != null) {
                if (policyGeneratedUntil != null && !currentDate.isAfter(policyGeneratedUntil)) {
                    isOffDay = true;//coz will be there in shiftAssignment
                } else {
                    RotationSequence seq = shiftRotationService.calculateSequenceForDate(activePolicy, currentDate, totalCycleDays);
                    isOffDay = (seq == null || seq.getSegmentType() == RotationSegmentType.OFF);
                }

            } else if (employee.getDefaultShift() != null) {

                if (defaultGeneratedUntil != null && !currentDate.isAfter(defaultGeneratedUntil)) {
                    isOffDay = true;
                } else {
                    isOffDay = !isWorkingDayForShift(currentDate, employee.getDefaultShift());
                }

            } else {
                DayOfWeek day = currentDate.getDayOfWeek();
                isOffDay = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            }

            if (isHoliday) {
                log.info("{} is a Public Holiday", currentDate);
            } else if (isOffDay) {
                log.info("{} is a scheduled Off-Day", currentDate);
                offDaysCount++;
            } else {
                baseWorkingDays = baseWorkingDays.add(BigDecimal.ONE);
            }

            currentDate = currentDate.plusDays(1);
        }

        if (baseWorkingDays.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException("You cannot apply for leave exclusively on your scheduled off-days or a public holiday.");
        }

        boolean sandwichRuleTriggered = false;
        BigDecimal netLeaveDays = baseWorkingDays;
        BigDecimal sandwichPenaltyDays = BigDecimal.ZERO;

        if (applySandwichRulePolicy && offDaysCount > 0) {
            sandwichRuleTriggered = true;
            sandwichPenaltyDays = BigDecimal.valueOf(offDaysCount);
            netLeaveDays = netLeaveDays.add(sandwichPenaltyDays);
            log.info("Sandwich rule applied. Added {} off-days to total.", offDaysCount);
        }

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

    private boolean isWorkingDayForShift(LocalDate date, Shift shift) {
        String dayName = date.getDayOfWeek().name();
        return shift.getWorkingDays().stream().anyMatch(wd -> wd.name().equals(dayName));
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
        ShiftRotationPolicy activePolicy = shiftRotationPolicyRepository.findActivePolicyWithSequencesByEmployeeId(employee.getId()).orElse(null);

        int totalCycleDays = activePolicy != null ? shiftRotationService.calculateCycleDays(activePolicy) : 0;

        LocalDate policyGeneratedUntil = activePolicy != null ? activePolicy.getGeneratedUntil() : null;

        LocalDate defaultGeneratedUntil = employee.getDefaultShiftGeneratedUntil();

        while (safeguard < 30) {
            ShiftAssignment dailyShift = shiftAssignmentRepository.findByEmployeeIdAndAssignmentDate(employee.getId(), current).orElse(null);

            List<LocalDate> holidays = holidayRepository.findHolidayDatesBetween(current, current);
            boolean isHoliday = !holidays.isEmpty();

            boolean isOffDay;
            if (dailyShift != null) {
                isOffDay = false;
            } else if (activePolicy != null) {
                if (policyGeneratedUntil != null && !current.isAfter(policyGeneratedUntil)) {
                    isOffDay = true;
                } else {
                    RotationSequence seq = shiftRotationService.calculateSequenceForDate(activePolicy, current, totalCycleDays);
                    isOffDay = (seq == null || seq.getSegmentType() == RotationSegmentType.OFF);
                }
            } else if (employee.getDefaultShift() != null) {
                if (defaultGeneratedUntil != null && !current.isAfter(defaultGeneratedUntil)) {
                    isOffDay = true;
                } else {
                    isOffDay = !isWorkingDayForShift(current, employee.getDefaultShift());
                }
            } else {
                DayOfWeek day = current.getDayOfWeek();
                isOffDay = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            }
            if (!isHoliday && !isOffDay) {
                return current;
            }

            current = current.plusDays(stepDays);
            safeguard++;
        }
        return current;
    }
}