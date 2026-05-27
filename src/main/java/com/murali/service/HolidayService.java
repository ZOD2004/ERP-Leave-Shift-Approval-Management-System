package com.murali.service;

import com.murali.entity.Holiday;
import com.murali.repository.HolidayRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final AuditLogService auditLoggingService;

    public HolidayService(HolidayRepository holidayRepository,
                          AuditLogService auditLoggingService) {
        this.holidayRepository = holidayRepository;
        this.auditLoggingService = auditLoggingService;
    }

    public List<Holiday> getAllHolidays(){
        return holidayRepository.findAll();
    }

    public void saveHoliday(Holiday holiday){
        boolean isNew = (holiday.getId() == null);
        String oldState = null;

        if (!isNew) {
            Optional<Holiday> existingOpt = holidayRepository.findById(holiday.getId());
            if (existingOpt.isPresent()) {
                Holiday existing = existingOpt.get();
                oldState = String.format("{ \"name\": \"%s\", \"holidayDate\": \"%s\" }",
                        existing.getName(), existing.getHolidayDate());
            }
        }

        Holiday savedHoliday = holidayRepository.save(holiday);

        String newState = String.format("{ \"name\": \"%s\", \"holidayDate\": \"%s\" }",
                savedHoliday.getName(), savedHoliday.getHolidayDate());

        String action = isNew ? "CREATED" : "UPDATED";
        log.info("Holiday {} successfully. ID: {}", action, savedHoliday.getId());
        auditLoggingService.saveAuditLog(savedHoliday.getId(), action, "holidays", oldState, newState);
    }

    public void deleteHoliday(Long id){
        String oldState = null;
        Optional<Holiday> existingOpt = holidayRepository.findById(id);
        if (existingOpt.isPresent()) {
            Holiday existing = existingOpt.get();
            oldState = String.format("{ \"name\": \"%s\", \"holidayDate\": \"%s\" }",
                    existing.getName(), existing.getHolidayDate());
        }

        holidayRepository.deleteById(id);

        log.info("Holiday DELETED successfully. ID: {}", id);
        auditLoggingService.saveAuditLog(id, "DELETED", "holidays", oldState, null);
    }

    public long countUpcomingHolidaysInMonth(LocalDate today, int monthValue, int year) {
        return holidayRepository.countUpcomingHolidaysInMonth(today, monthValue, year);
    }
}