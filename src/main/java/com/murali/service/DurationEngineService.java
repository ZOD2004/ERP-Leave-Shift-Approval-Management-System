package com.murali.service;

import com.murali.entity.Employee;
import com.murali.entity.LeaveType;
import com.murali.exception.PastDateException;
import com.murali.repository.HolidayRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class DurationEngineService {

    private final HolidayRepository holidayRepository;

    public static final String HALF_DAY_CODE = "HALF_DAY";

    public DurationEngineService(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    public BigDecimal calculateNetLeaveDays(LocalDate startDate, LocalDate endDate,
                                            Employee employee, LeaveType leaveType,
                                            boolean applySandwichRule) {

        if (leaveType.getId() == 5 || HALF_DAY_CODE.equalsIgnoreCase(leaveType.getCode())) {
            return new BigDecimal("0.5");
        }

        if (endDate.isBefore(startDate)) {
            throw new PastDateException("End date cannot be before Start date");
        }

        if (applySandwichRule) {
            long totalCalendarDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            return BigDecimal.valueOf(totalCalendarDays);
        }

        List<LocalDate> holidays = holidayRepository.findHolidayDatesBetween(startDate, endDate);


        BigDecimal duration = BigDecimal.ZERO;
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            boolean isHoliday = holidays.contains(currentDate);
            boolean isOffDay = isOffDay(currentDate, employee);

            if (!isHoliday && !isOffDay) {
                duration = duration.add(BigDecimal.ONE);
            }

            currentDate = currentDate.plusDays(1);
        }

        return duration;
    }

    private boolean isOffDay(LocalDate date, Employee employee) {

         //TODO: Integrate your specific ShiftAssignment logic here dont have days included.

        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
