package com.murali.service;

import com.murali.entity.Department;
import com.murali.repository.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final AuditLogService auditLoggingService;

    public List<Department> findAll() {
        return departmentRepository.findAll();
    }

    @Transactional
    public Department save(Department department) {
        boolean isNew = (department.getId() == null);
        String oldState = null;

        if (!isNew) {
            Optional<Department> existingOpt = departmentRepository.findById(department.getId());
            if (existingOpt.isPresent()) {
                Department existing = existingOpt.get();
                oldState = String.format("{ \"name\": \"%s\" }", existing.getName());
            }
        }
        Department savedDepartment = departmentRepository.save(department);
        String newState = String.format("{ \"name\": \"%s\" }", savedDepartment.getName());
        String action = isNew ? "CREATED" : "UPDATED";

        log.info("Department {} successfully. ID: {}", action, savedDepartment.getId());

        auditLoggingService.saveAuditLog(savedDepartment.getId(), action, "departments", oldState, newState);

        return savedDepartment;
    }

    @Transactional
    public void delete(Department department){
        Long deptId = department.getId();
        String deptName = department.getName();

        String oldState = String.format("{ \"name\": \"%s\" }", deptName);

        departmentRepository.delete(department);
        log.info("Department DELETED successfully. ID: {}", deptId);
        auditLoggingService.saveAuditLog(deptId, "DELETED", "departments", oldState, null);
    }

    public Department findById(Long id) {
        return departmentRepository.findById(id).orElse(null);
    }
}