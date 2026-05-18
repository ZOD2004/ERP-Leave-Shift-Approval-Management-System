package com.murali.service;


import com.murali.entity.LeaveApprovalRule;
import com.murali.entity.LeaveType;
import com.murali.entity.Role;
import com.murali.repository.LeaveApprovalRuleRepository;
import com.murali.repository.LeaveTypeRepository;
import com.murali.repository.RoleRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class LeaveApprovalRuleService {

    private final LeaveApprovalRuleRepository ruleRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final RoleRepository roleRepository;

    public LeaveApprovalRuleService(
            LeaveApprovalRuleRepository ruleRepository,
            LeaveTypeRepository leaveTypeRepository,
            RoleRepository roleRepository) {

        this.ruleRepository = ruleRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.roleRepository = roleRepository;
    }

    public List<LeaveApprovalRule> getApplicableRules(Long leaveTypeId, BigDecimal duration) {
        return ruleRepository.findApplicableRules(leaveTypeId, duration);
    }

    public List<LeaveApprovalRule> getAllRules() {
        return ruleRepository.findAll();
    }

    public LeaveApprovalRule saveRule(LeaveApprovalRule rule) {
        return ruleRepository.save(rule);
    }

    public void deleteRule(Long id) {
        ruleRepository.deleteById(id);
    }

    public List<LeaveType> getAllLeaveTypes() {
        return leaveTypeRepository.findAll();
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }
}
