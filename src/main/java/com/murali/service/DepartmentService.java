package com.murali.service;

import com.murali.entity.Department;
import com.murali.repository.DepartmentRepository;
import com.murali.repository.EmployeeRepository;
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
    private final EmployeeRepository employeeRepository; // Injected to check constraints
    private final AuditLogService auditLoggingService;

    public List<Department> findAll() {
        return departmentRepository.findAll();
    }

    public Department findById(Long id) {
        return departmentRepository.findById(id).orElse(null);
    }

    @Transactional
    public Department save(Department department) {
        boolean isNew = (department.getId() == null);
        String oldState = null;

        if (!isNew) {
            Optional<Department> existingOpt = departmentRepository.findById(department.getId());
            if (existingOpt.isPresent()) {
                Department existing = existingOpt.get();
                oldState = formatAuditState(existing); // Extracting to a helper method
            }
        }

        Department savedDepartment = departmentRepository.save(department);
        String newState = formatAuditState(savedDepartment);
        String action = isNew ? "CREATED" : "UPDATED";

        log.info("Department {} successfully. ID: {}", action, savedDepartment.getId());

        auditLoggingService.saveAuditLog(savedDepartment.getId(), action, "departments", oldState, newState);

        return savedDepartment;
    }

    @Transactional
    public void delete(Department department) {
        Long deptId = department.getId();

        if (employeeRepository.existsByDepartmentId(deptId)) {
            throw new IllegalStateException(
                    "Cannot delete department '" + department.getName() + "' because it still has employees assigned to it. Please reassign or remove them first."
            );
        }

        String oldState = formatAuditState(department);

        departmentRepository.delete(department);
        log.info("Department DELETED successfully. ID: {}", deptId);

        auditLoggingService.saveAuditLog(deptId, "DELETED", "departments", oldState, null);
    }

    private String formatAuditState(Department dept) {
        Long hodId = (dept.getHod() != null) ? dept.getHod().getId() : null;
        return String.format("{ \"name\": \"%s\", \"hod_id\": %d }", dept.getName(), hodId);
    }
}