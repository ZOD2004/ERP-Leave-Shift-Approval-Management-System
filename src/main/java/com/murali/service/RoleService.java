package com.murali.service;

import com.murali.entity.Role;
import com.murali.repository.RoleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

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

    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
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

    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_AUDITOR','ROLE_HR_ADMIN')")
    public List<Role> getRoles(){
        return roleRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_AUDITOR', 'ROLE_HR_ADMIN')")
    public List<Role> findAll() {
        return roleRepository.findAll();
    }

    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public Role save(Role role) {
        boolean isNew = (role.getId() == null);
        String oldState = null;

        if (!isNew) {
            Optional<Role> existingOpt = roleRepository.findById(role.getId());
            if (existingOpt.isPresent()) {
                Role existing = existingOpt.get();
                oldState = String.format("{ \"name\": \"%s\" }", existing.getName());
            }
        }

        if (role.getName() != null) {
            role.setName(role.getName().toUpperCase().trim());
        }
        Role savedRole = roleRepository.save(role);

        String newState = String.format("{ \"name\": \"%s\" }", savedRole.getName());
        String action = isNew ? "CREATED" : "UPDATED";

        log.info("Role {} successfully. ID: {}", action, savedRole.getId());
        auditLoggingService.saveAuditLog(savedRole.getId(), action, "roles", oldState, newState);

        return savedRole;
    }

    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public void delete(Role role) {
        Long roleId = role.getId();
        String roleName = role.getName();

        String oldState = String.format("{ \"name\": \"%s\" }", roleName);

        roleRepository.delete(role);

        log.info("Role DELETED successfully. ID: {}", roleId);
        auditLoggingService.saveAuditLog(roleId, "DELETED", "roles", oldState, null);
    }
}