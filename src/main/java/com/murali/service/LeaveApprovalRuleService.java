package com.murali.service;


import com.murali.entity.AuditLog;
import com.murali.entity.LeaveApprovalRule;
import com.murali.entity.LeaveType;
import com.murali.entity.Role;
import com.murali.repository.AuditLogRepository;
import com.murali.repository.LeaveApprovalRuleRepository;
import com.murali.repository.LeaveTypeRepository;
import com.murali.repository.RoleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
public class LeaveApprovalRuleService {

    private final LeaveApprovalRuleRepository ruleRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final RoleRepository roleRepository;
    private final AuditLogRepository auditLogRepository;
    private final SecurityService securityService;

    public LeaveApprovalRuleService(
            LeaveApprovalRuleRepository ruleRepository,
            LeaveTypeRepository leaveTypeRepository,
            RoleRepository roleRepository,
            AuditLogRepository auditLogRepository,
            SecurityService securityService) {

        this.ruleRepository = ruleRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.roleRepository = roleRepository;
        this.auditLogRepository = auditLogRepository;
        this.securityService = securityService;
    }

    public List<LeaveApprovalRule> getApplicableRules(Long leaveTypeId, BigDecimal duration) {
        return ruleRepository.findApplicableRules(leaveTypeId, duration);
    }

    public List<LeaveApprovalRule> getAllRules() {
        return ruleRepository.findAll();
    }

    public LeaveApprovalRule saveRule(LeaveApprovalRule rule) {
        boolean isNew = (rule.getId() == null);
        LeaveApprovalRule savedRule = ruleRepository.save(rule);

        String action = isNew ? "CREATED" : "UPDATED";
        log.info("LeaveApprovalRule {} successfully. ID: {}", action, savedRule.getId());
        saveAuditLog(savedRule.getId(), action, "leave_approval_rules", "LeaveApprovalRule " + action.toLowerCase() + " successfully.");

        return savedRule;
    }

    public void deleteRule(Long id) {
        ruleRepository.deleteById(id);

        log.info("LeaveApprovalRule DELETED successfully. ID: {}", id);
        saveAuditLog(id, "DELETED", "leave_approval_rules", "Deleted LeaveApprovalRule with ID: " + id);
    }

    public List<LeaveType> getAllLeaveTypes() {
        return leaveTypeRepository.findAll();
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
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
            log.error("Failed to save audit log for leave approval rule record {}: {}", recordId, e.getMessage());
        }
    }
}
