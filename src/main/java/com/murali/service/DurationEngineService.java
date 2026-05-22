package com.murali.service;

import com.murali.entity.AuditLog;
import com.murali.entity.Employee;
import com.murali.entity.LeaveType;
import com.murali.exception.PastDateException;
import com.murali.repository.AuditLogRepository;
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
    private final AuditLogRepository auditLogRepository;
    private final SecurityService securityService;

    public static final String HALF_DAY_CODE = "HDL-001";

    public DurationEngineService(HolidayRepository holidayRepository,
                                 AuditLogRepository auditLogRepository,
                                 SecurityService securityService) {
        this.holidayRepository = holidayRepository;
        this.auditLogRepository = auditLogRepository;
        this.securityService = securityService;
    }

    public BigDecimal calculateNetLeaveDays(LocalDate startDate, LocalDate endDate,
                                            Employee employee, LeaveType leaveType,
                                            boolean applySandwichRule) {

        log.debug("Starting leave calculation for Employee ID: {} | Dates: {} to {}",
                employee.getId(), startDate, endDate);

        if (leaveType.getId() == 5 || HALF_DAY_CODE.equalsIgnoreCase(leaveType.getCode())) {
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
        saveAuditLog(null, "CALCULATE_DURATION", "none",
                "Calculated " + duration + " days for employee ID: " + employee.getId() +
                        " (Dates: " + startDate + " to " + endDate + ")");

        return duration;
    }

    private boolean isOffDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private void saveAuditLog(Long recordId, String action, String tableAffected, String details) {
        try {
            String username = "SYSTEM";
            String role = "SYSTEM";

            if (securityService.getPrincipal() != null) {
                username = securityService.getPrincipal().getUsername();
                if (securityService.getAuthentication() != null && !securityService.getAuthentication().getAuthorities().isEmpty()) {
                    role = securityService.getAuthentication().getAuthorities().iterator().next().getAuthority();
                }
            }

            AuditLog auditLog = new AuditLog();
            auditLog.setUsername(username);
            auditLog.setRole(role);
            auditLog.setRecordId(recordId);
            auditLog.setAction(action);
            auditLog.setTableAffected(tableAffected);
            auditLog.setDetails(details);

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log for duration calculation: {}", e.getMessage());
        }
    }
}