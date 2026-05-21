package com.murali.service;

import com.murali.entity.AuditLog;
import com.murali.entity.LeaveType;
import com.murali.exception.LeaveTypeNotFoundException;
import com.murali.repository.AuditLogRepository;
import com.murali.repository.LeaveTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final AuditLogRepository auditLogRepository;
    private final SecurityService securityService;

    public LeaveTypeService(LeaveTypeRepository leaveTypeRepository,
                            AuditLogRepository auditLogRepository,
                            SecurityService securityService) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.auditLogRepository = auditLogRepository;
        this.securityService = securityService;
    }

    public List<LeaveType> getAllLeaveTypes() {
        return leaveTypeRepository.findAll();
    }

    public void addLeaveType(LeaveType leaveType) {
        leaveTypeRepository.save(leaveType);

        log.info("LeaveType CREATED successfully. ID: {}", leaveType.getId());
        saveAuditLog(leaveType.getId(), "CREATED", "leave_types", "Created LeaveType: " + leaveType.getName() + " (" + leaveType.getCode() + ")");
    }

    public void deleteLeaveType(Long id) {
        leaveTypeRepository.deleteById(id);

        log.info("LeaveType DELETED successfully. ID: {}", id);
        saveAuditLog(id, "DELETED", "leave_types", "Deleted LeaveType with ID: " + id);
    }

    public LeaveType editLeaveType(Long id, LeaveType leaveType) {
        Optional<LeaveType> optLeaveType = leaveTypeRepository.findById(id);
        LeaveType currLeaveType;

        if (optLeaveType.isPresent()) {
            currLeaveType = optLeaveType.get();
        } else {
            throw new LeaveTypeNotFoundException("LeaveType with id: " + id + " is not found");
        }
        currLeaveType.setName(leaveType.getName());
        currLeaveType.setCode(leaveType.getCode());
        currLeaveType.setPaid(leaveType.getPaid());
        currLeaveType.setMaxDaysPerYear(leaveType.getMaxDaysPerYear());

        LeaveType savedLeaveType = leaveTypeRepository.save(currLeaveType);

        log.info("LeaveType UPDATED successfully. ID: {}", savedLeaveType.getId());
        saveAuditLog(savedLeaveType.getId(), "UPDATED", "leave_types", "Updated LeaveType: " + savedLeaveType.getName() + " (" + savedLeaveType.getCode() + ")");

        return savedLeaveType;
    }

    public List<LeaveType> search(String searchTerm) {
        return leaveTypeRepository.findByNameContainingIgnoreCaseOrCodeContainingIgnoreCase(searchTerm,searchTerm);
    }

    @Transactional(readOnly = true)
    public List<LeaveType> getAvailableLeaveTypes() {
        return leaveTypeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public LeaveType getLeaveTypeByCode(String code) {
        return leaveTypeRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Leave type code not found: " + code));
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
            log.error("Failed to save audit log for leave type record {}: {}", recordId, e.getMessage());
        }
    }
}