package com.murali.service;

import com.murali.entity.AuditLog;
import com.murali.entity.Shift;
import com.murali.exception.UserNotFoundException;
import com.murali.repository.AuditLogRepository;
import com.murali.repository.ShiftRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final AuditLogRepository auditLogRepository;
    private final SecurityService securityService;

    public ShiftService(ShiftRepository shiftRepository,
                        AuditLogRepository auditLogRepository,
                        SecurityService securityService) {
        this.shiftRepository = shiftRepository;
        this.auditLogRepository = auditLogRepository;
        this.securityService = securityService;
    }

    public List<Shift> getShifts(){
        return shiftRepository.findAll();
    }

    public void deleteShift(Long id){
        shiftRepository.deleteById(id);

        // --- ADDED AUDIT LOG ---
        log.info("Shift DELETED successfully. ID: {}", id);
        saveAuditLog(id, "DELETED", "shifts", "Deleted Shift ID: " + id);
    }

    public void addShift(Shift shift) {
        boolean isNew = (shift.getId() == null);

        validateShift(shift);
        shiftRepository.save(shift);

        // --- ADDED AUDIT LOG ---
        String action = isNew ? "CREATED" : "UPDATED";
        log.info("Shift {} successfully. ID: {}", action, shift.getId());
        saveAuditLog(shift.getId(), action, "shifts", "Saved Shift: " + shift.getName());
    }

    private void validateShift(Shift shift) {

        if (shift.getWorkingDays() == null || shift.getWorkingDays().isEmpty()) {
            throw new IllegalArgumentException("At least one working day is required");
        }

        if (shift.getStartTime().equals(shift.getEndTime())) {
            throw new IllegalArgumentException("Start time and end time cannot be same");
        }
    }

    public Optional<Shift> getShiftById(Long id){
        return shiftRepository.findById(id);
    }

    public List<Shift> search(String searchTerm) {
        return shiftRepository.findByNameContainingIgnoreCase(searchTerm);
    }

    // --- ADDED HELPER METHOD ---
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
            log.error("Failed to save audit log for shift record {}: {}", recordId, e.getMessage());
        }
    }
}