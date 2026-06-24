package com.murali.service;

import com.murali.entity.LeaveApprovalPolicy;
import com.murali.entity.LeaveApprovalRule;
import com.murali.entity.LeaveType;
import com.murali.entity.Role;
import com.murali.repository.LeaveApprovalPolicyRepository;
import com.murali.repository.LeaveApprovalRuleRepository;
import com.murali.repository.LeaveTypeRepository;
import com.murali.repository.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class LeaveApprovalRuleService {

    private final LeaveApprovalRuleRepository ruleRepository;
    private final LeaveApprovalPolicyRepository policyRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final RoleRepository roleRepository;
    private final AuditLogService auditLoggingService;

    public LeaveApprovalRuleService(LeaveApprovalRuleRepository ruleRepository, LeaveApprovalPolicyRepository policyRepository, LeaveTypeRepository leaveTypeRepository, RoleRepository roleRepository, AuditLogService auditLoggingService) {

        this.ruleRepository = ruleRepository;
        this.policyRepository = policyRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.roleRepository = roleRepository;
        this.auditLoggingService = auditLoggingService;
    }

    public List<LeaveApprovalPolicy> getAllPolicies() {
        return policyRepository.findAllWithRules();
    }

    @Transactional
    public LeaveApprovalPolicy savePolicy(LeaveApprovalPolicy policy) {
        boolean isNew = (policy.getId() == null);
        String oldState = null;
        LeaveApprovalPolicy savedPolicy;

        if (!isNew) {
            LeaveApprovalPolicy existing = policyRepository.findById(policy.getId()).orElseThrow();
            oldState = String.format("{ \"id\": %d, \"name\": \"%s\" }", existing.getId(), existing.getName());

            existing.setName(policy.getName());
            existing.getRules().clear();
            policyRepository.saveAndFlush(existing);

            List<LeaveApprovalRule> incomingRules = new ArrayList<>(policy.getRules());
            for (LeaveApprovalRule rule : incomingRules) {
                rule.setPolicy(existing);
                existing.getRules().add(rule);
            }

            savedPolicy = policyRepository.save(existing);
        } else {
            savedPolicy = policyRepository.save(policy);
        }

        String newState = String.format("{ \"id\": %d, \"name\": \"%s\" }", savedPolicy.getId(), savedPolicy.getName());
        String action = isNew ? "CREATED" : "UPDATED";

        log.info("LeaveApprovalPolicy {} successfully. ID: {}", action, savedPolicy.getId());
        auditLoggingService.saveAuditLog(savedPolicy.getId(), action, "leave_approval_policies", oldState, newState);

        return savedPolicy;
    }

    @Transactional
    public void deletePolicy(Long id) {
        String oldState = null;
        Optional<LeaveApprovalPolicy> existingOpt = policyRepository.findById(id);
        if (existingOpt.isPresent()) {
            LeaveApprovalPolicy existing = existingOpt.get();
            oldState = String.format("{ \"id\": %d, \"name\": \"%s\" }", existing.getId(), existing.getName());
        }

        policyRepository.deleteById(id);

        log.info("LeaveApprovalPolicy DELETED successfully. ID: {}", id);
        auditLoggingService.saveAuditLog(id, "DELETED", "leave_approval_policies", oldState, null);
    }

    public List<LeaveApprovalRule> getApplicableRules(Long leaveTypeId, BigDecimal duration) {
        LeaveType leaveType = leaveTypeRepository.findById(leaveTypeId).orElseThrow(() -> new IllegalArgumentException("Leave Type not found"));

        LeaveApprovalPolicy policy = leaveType.getApprovalPolicy();
        if (policy == null) {
            log.warn("No Approval Policy assigned to Leave Type ID: {}", leaveTypeId);
            return new ArrayList<>();
        }

        return ruleRepository.findByPolicyAndDuration(policy.getId(), duration);
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public LeaveApprovalPolicy getPolicyById(Long id) {
        return policyRepository.findByIdWithRules(id)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found with ID: " + id));
    }
}