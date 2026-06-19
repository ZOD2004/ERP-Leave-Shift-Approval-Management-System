package com.murali.service;

import com.murali.entity.Role;
import com.murali.repository.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final AuditLogService auditLoggingService;

    public RoleService(RoleRepository roleRepository,
                       AuditLogService auditLoggingService) {
        this.roleRepository = roleRepository;
        this.auditLoggingService = auditLoggingService;
    }

    public Role addRole(Role role){
        boolean isNew = (role.getId() == null);
        String oldState = null;

        if (!isNew) {
            Optional<Role> existingOpt = roleRepository.findById(role.getId());
            if (existingOpt.isPresent()) {
                Role existing = existingOpt.get();
                oldState = String.format("{ \"name\": \"%s\" }", existing.getName());
            }
        }

        Role savedRole = roleRepository.save(role);

        String newState = String.format("{ \"name\": \"%s\" }", savedRole.getName());
        String action = isNew ? "CREATED" : "UPDATED";

        log.info("Role {} successfully. ID: {}", action, savedRole.getId());
        auditLoggingService.saveAuditLog(savedRole.getId(), action, "roles", oldState, newState);

        return savedRole;
    }

    public List<Role> getRoles(){
        return roleRepository.findAll();
    }

    public List<Role> findAll() {
        return roleRepository.findAll();
    }

    @Transactional
    public Role save(Role role) {
        return save(role, false);
    }

    public Role getRoleByWeight(Integer weight) {
        if (weight == null) return null;
        return roleRepository.findByHierarchyWeight(weight);
    }

    @Transactional
    public Role save(Role role, boolean isSwapApproved) {
        boolean isNew = (role.getId() == null);
        String oldState = null;
        Integer oldWeight = null;

        if (!isNew) {
            Optional<Role> existingOpt = roleRepository.findById(role.getId());
            if (existingOpt.isPresent()) {
                Role existing = existingOpt.get();
                oldState = String.format("{ \"name\": \"%s\", \"weight\": %d }", existing.getName(), existing.getHierarchyWeight());
                oldWeight = existing.getHierarchyWeight();
            }
        }

        Integer desiredWeight = role.getHierarchyWeight();
        if (desiredWeight != null && isSwapApproved) {
            Role conflictingRole = roleRepository.findByHierarchyWeight(desiredWeight);

            if (conflictingRole != null && !conflictingRole.getId().equals(role.getId())) {
                int newWeightForConflictingRole = isNew ? (int) roleRepository.count() + 1 : oldWeight;

                conflictingRole.setHierarchyWeight(newWeightForConflictingRole);
                roleRepository.save(conflictingRole);

                log.info("Swapped hierarchy weight. Role {} got weight {}", conflictingRole.getName(), newWeightForConflictingRole);
            }
        }
        if (role.getName() != null) {
            role.setName(role.getName().toUpperCase().trim());
        }

        Role savedRole = roleRepository.save(role);

        String newState = String.format("{ \"name\": \"%s\", \"weight\": %d }", savedRole.getName(), savedRole.getHierarchyWeight());
        String action = isNew ? "CREATED" : "UPDATED";

        log.info("Role {} successfully. ID: {}", action, savedRole.getId());
        auditLoggingService.saveAuditLog(savedRole.getId(), action, "roles", oldState, newState);

        return savedRole;
    }
    public void delete(Role role) {
        Long roleId = role.getId();
        String roleName = role.getName();

        String oldState = String.format("{ \"name\": \"%s\" }", roleName);

        roleRepository.delete(role);

        log.info("Role DELETED successfully. ID: {}", roleId);
        auditLoggingService.saveAuditLog(roleId, "DELETED", "roles", oldState, null);
    }
}