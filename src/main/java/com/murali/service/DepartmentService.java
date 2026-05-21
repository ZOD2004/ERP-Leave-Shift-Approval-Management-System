package com.murali.service;

import com.murali.entity.AuditLog;
import com.murali.entity.Department;
import com.murali.repository.AuditLogRepository;
import com.murali.repository.DepartmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class DepartmentService {
    private final DepartmentRepository departmentRepository;
    private final AuditLogRepository auditLogRepository;
    private final SecurityService securityService;

    public DepartmentService(DepartmentRepository departmentRepository, AuditLogRepository auditLogRepository, SecurityService securityService) {
        this.departmentRepository = departmentRepository;
        this.auditLogRepository = auditLogRepository;
        this.securityService = securityService;
    }

    public List<Department> findAll() {
        return departmentRepository.findAll();
    }

    public Department save(Department department) {
        boolean isNew = (department.getId() == null);
        Department savedDepartment = departmentRepository.save(department);
        String action = isNew ? "CREATED" : "UPDATED";
        log.info("Department {} successfully. ID: {}", action, savedDepartment.getId());
        saveAuditLog(savedDepartment.getId(), action, "departments", "Department Name: " + savedDepartment.getName());
        return departmentRepository.save(department);
    }

    public void delete(Department department){
        Long deptId = department.getId();
        String deptName = department.getName();
        log.info("Department DELETED successfully. ID: {}", deptId);
        saveAuditLog(deptId, "DELETED", "departments", "Deleted Department Name: " + deptName);
        departmentRepository.delete(department);
    }

    public Department findById(Long id) {
        return departmentRepository.findById(id).get();
    }
    // --- ADD THIS HELPER METHOD ---
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
            auditLog.setTableAffected("departments"); // Hardcoded for this service
            auditLog.setDetails(details);

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log for department record {}: {}", recordId, e.getMessage());
        }
    }
}
