package com.murali.service;

import com.murali.entity.Employee;
import com.murali.entity.LeaveType;
import com.murali.exception.PastDateException;
import com.murali.repository.HolidayRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
public class DurationEngineService {

    private final HolidayRepository holidayRepository;
    private final AuditLogService auditLoggingService;

    public static final String HALF_DAY_CODE = "HDL-001";

    public DurationEngineService(HolidayRepository holidayRepository,
                                 AuditLogService auditLoggingService) {
        this.holidayRepository = holidayRepository;
        this.auditLoggingService = auditLoggingService;
    }

    public BigDecimal calculateNetLeaveDays(LocalDate startDate, LocalDate endDate,
                                            Employee employee, LeaveType leaveType,
                                            boolean applySandwichRule) {

        log.debug("Starting leave calculation for Employee ID: {} | Dates: {} to {}",
                employee.getId(), startDate, endDate);

        if (HALF_DAY_CODE.equalsIgnoreCase(leaveType.getCode())) {
            log.info("Half-day leave type detected. Returning 0.5 days.");
            return new BigDecimal("0.5");
        }

        if (endDate.isBefore(startDate)) {
            log.error("Calculation failed: End date {} is before Start date {}", endDate, startDate);
            throw new PastDateException("End date cannot be before Start date");
        }

        BigDecimal duration;

        if (applySandwichRule) {
            long totalCalendarDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            duration = BigDecimal.valueOf(totalCalendarDays);
            log.info("Sandwich rule applied. Total days calculated: {}", duration);
        } else {
            List<LocalDate> holidays = holidayRepository.findHolidayDatesBetween(startDate, endDate);

            duration = BigDecimal.ZERO;
            LocalDate currentDate = startDate;

            while (!currentDate.isAfter(endDate)) {
                boolean isHoliday = holidays.contains(currentDate);
                boolean isOffDay = isOffDay(currentDate);

                if (!isHoliday && !isOffDay) {
                    duration = duration.add(BigDecimal.ONE);
                }

                currentDate = currentDate.plusDays(1);
            }
            log.info("Standard calculation applied. Net working days calculated: {}", duration);
        }

        String newState = String.format("{ \"startDate\": \"%s\", \"endDate\": \"%s\", \"calculatedDuration\": %s, \"sandwichRule\": %b }",
                startDate, endDate, duration, applySandwichRule);

        auditLoggingService.saveAuditLog(null, "CALCULATE_DURATION", "none", null, newState);

        return duration;
    }

    private boolean isOffDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}