package com.murali.service;

import com.murali.entity.AuditLog;
import com.murali.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final SecurityService securityService;

    public AuditLogService(AuditLogRepository auditLogRepository,@Lazy SecurityService securityService) {
        this.auditLogRepository = auditLogRepository;
        this.securityService = securityService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAuditLog(Long recordId, String action, String entityName, String oldState, String newState) {
        try {
            String performedBy = "SYSTEM";

            // Safely fetch current user from SecurityService
            if (securityService.getPrincipal() != null) {
                String username = securityService.getPrincipal().getUsername();
                String role = "USER";

                if (securityService.getAuthentication() != null && !securityService.getAuthentication().getAuthorities().isEmpty()) {
                    role = securityService.getAuthentication().getAuthorities().iterator().next().getAuthority();
                }
                // Combines them to look like "john_doe (ROLE_MANAGER)" for the Vaadin Grid
                performedBy = username + " (" + role + ")";
            }

            saveToDatabase(recordId, action, entityName, performedBy, oldState, newState);

        } catch (Exception e) {
            log.error("Failed to save database audit log for record {}: {}", recordId, e.getMessage());
        }
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveSystemAuditLog(Long recordId, String action, String entityName, String oldState, String newState) {
        try {
            saveToDatabase(recordId, action, entityName, "SYSTEM (CRON)", oldState, newState);
        } catch (Exception e) {
            log.error("Failed to save system audit log for record {}: {}", recordId, e.getMessage());
        }
    }

    private void saveToDatabase(Long recordId, String action, String entityName, String performedBy, String oldState, String newState) {
        AuditLog auditLog = new AuditLog();
        auditLog.setRecordId(recordId);
        auditLog.setAction(action);
        auditLog.setEntityName(entityName);
        auditLog.setPerformedBy(performedBy);
        auditLog.setOldState(oldState);
        auditLog.setNewState(newState);

        auditLogRepository.save(auditLog);
    }
    @Transactional(readOnly = true)
    public List<AuditLog> getAllLogs() {
        return auditLogRepository.findAllByOrderByTimestampDesc();
    }
    @Transactional(readOnly = true)
    public List<AuditLog> getRecentLogs(int limit) {
        return auditLogRepository.findRecentLogs(PageRequest.of(0, limit));
    }
}