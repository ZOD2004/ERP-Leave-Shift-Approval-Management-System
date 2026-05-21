package com.murali.service;

import com.murali.entity.AuditLog;
import com.murali.entity.Role;
import com.murali.repository.AuditLogRepository;
import com.murali.repository.RoleRepository;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final AuditLogRepository auditLogRepository;
    private final SecurityService securityService;

    public RoleService(RoleRepository roleRepository,
                       AuditLogRepository auditLogRepository,
                       SecurityService securityService) {
        this.roleRepository = roleRepository;
        this.auditLogRepository = auditLogRepository;
        this.securityService = securityService;
    }

    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
//    @PermitAll
    public Role addRole(Role role){
        boolean isNew = (role.getId() == null);
        Role savedRole = roleRepository.save(role);

        String action = isNew ? "CREATED" : "UPDATED";
        log.info("Role {} successfully. ID: {}", action, savedRole.getId());
        saveAuditLog(savedRole.getId(), action, "roles", "Added Role: " + savedRole.getName());

        return savedRole;
    }

    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_AUDITOR','ROLE_HR_ADMIN')")
//    @PermitAll
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

        if (role.getName() != null) {
            role.setName(role.getName().toUpperCase().trim());
        }
        Role savedRole = roleRepository.save(role);

        String action = isNew ? "CREATED" : "UPDATED";
        log.info("Role {} successfully. ID: {}", action, savedRole.getId());
        saveAuditLog(savedRole.getId(), action, "roles", "Saved Role: " + savedRole.getName());

        return savedRole;
    }

    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public void delete(Role role) {
        Long roleId = role.getId();
        String roleName = role.getName();

        roleRepository.delete(role);

        log.info("Role DELETED successfully. ID: {}", roleId);
        saveAuditLog(roleId, "DELETED", "roles", "Deleted Role: " + roleName);
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
            log.error("Failed to save audit log for role record {}: {}", recordId, e.getMessage());
        }
    }
}