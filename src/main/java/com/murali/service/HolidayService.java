package com.murali.service;

import com.murali.entity.AuditLog;
import com.murali.entity.Holiday;
import com.murali.repository.AuditLogRepository;
import com.murali.repository.HolidayRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
public class HolidayService {

    private final HolidayRepository holidayRepository;
    private final AuditLogRepository auditLogRepository;
    private final SecurityService securityService;

    public HolidayService(HolidayRepository holidayRepository,
                          AuditLogRepository auditLogRepository,
                          SecurityService securityService) {
        this.holidayRepository = holidayRepository;
        this.auditLogRepository = auditLogRepository;
        this.securityService = securityService;
    }

    public List<Holiday> getAllHolidays(){
        return holidayRepository.findAll();
    }

    public void saveHoliday(Holiday holiday){
        boolean isNew = (holiday.getId() == null);
        holidayRepository.save(holiday);

        String action = isNew ? "CREATED" : "UPDATED";
        log.info("Holiday {} successfully. ID: {}", action, holiday.getId());
        saveAuditLog(holiday.getId(), action, "holidays",
                "Holiday record " + action.toLowerCase() + " successfully.");
    }

    public void deleteHoliday(Long id){
        holidayRepository.deleteById(id);

        log.info("Holiday DELETED successfully. ID: {}", id);
        saveAuditLog(id, "DELETED", "holidays", "Deleted Holiday with ID: " + id);
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
            log.error("Failed to save audit log for holiday record {}: {}", recordId, e.getMessage());
        }
    }

    public long countUpcomingHolidaysInMonth(LocalDate today, int monthValue, int year) {
        return holidayRepository.countUpcomingHolidaysInMonth(today,monthValue,year);
    }
}
