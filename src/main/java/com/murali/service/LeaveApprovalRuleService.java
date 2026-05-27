package com.murali.service;

import com.murali.entity.LeaveApprovalRule;
import com.murali.entity.LeaveType;
import com.murali.entity.Role;
import com.murali.repository.LeaveApprovalRuleRepository;
import com.murali.repository.LeaveTypeRepository;
import com.murali.repository.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class LeaveApprovalRuleService {

    private final LeaveApprovalRuleRepository ruleRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final RoleRepository roleRepository;
    private final AuditLogService auditLoggingService;

    public LeaveApprovalRuleService(
            LeaveApprovalRuleRepository ruleRepository,
            LeaveTypeRepository leaveTypeRepository,
            RoleRepository roleRepository,
            AuditLogService auditLoggingService) {

        this.ruleRepository = ruleRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.roleRepository = roleRepository;
        this.auditLoggingService = auditLoggingService;
    }

    public List<LeaveApprovalRule> getApplicableRules(Long leaveTypeId, BigDecimal duration) {
        return ruleRepository.findApplicableRules(leaveTypeId, duration);
    }

    public List<LeaveApprovalRule> getAllRules() {
        return ruleRepository.findAll();
    }

    public LeaveApprovalRule saveRule(LeaveApprovalRule rule) {
        boolean isNew = (rule.getId() == null);
        String oldState = null;

        if (!isNew) {
            Optional<LeaveApprovalRule> existingOpt = ruleRepository.findById(rule.getId());
            if (existingOpt.isPresent()) {
                LeaveApprovalRule existing = existingOpt.get();
                oldState = String.format("{ \"id\": %d }", existing.getId());
            }
        }

        LeaveApprovalRule savedRule = ruleRepository.save(rule);

        String newState = String.format("{ \"id\": %d }", savedRule.getId());
        String action = isNew ? "CREATED" : "UPDATED";

        log.info("LeaveApprovalRule {} successfully. ID: {}", action, savedRule.getId());
        auditLoggingService.saveAuditLog(savedRule.getId(), action, "leave_approval_rules", oldState, newState);

        return savedRule;
    }

    public void deleteRule(Long id) {
        String oldState = null;
        Optional<LeaveApprovalRule> existingOpt = ruleRepository.findById(id);
        if (existingOpt.isPresent()) {
            LeaveApprovalRule existing = existingOpt.get();
            oldState = String.format("{ \"id\": %d }", existing.getId());
        }

        ruleRepository.deleteById(id);

        log.info("LeaveApprovalRule DELETED successfully. ID: {}", id);
        auditLoggingService.saveAuditLog(id, "DELETED", "leave_approval_rules", oldState, null);
    }

    public List<LeaveType> getAllLeaveTypes() {
        return leaveTypeRepository.findAll();
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }
}