package com.murali.service;

import com.murali.entity.AuditLog;
import com.murali.entity.Department;
import com.murali.entity.Employee;
import com.murali.entity.Role;
import com.murali.entity.User;
import com.murali.repository.AuditLogRepository;
import com.murali.repository.DepartmentRepository;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.RoleRepository;
import com.murali.repository.UserRepository;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final EmployeeRepository employeeRepository;

    private final AuditLogRepository auditLogRepository;
    private final SecurityService securityService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       DepartmentRepository departmentRepository,
                       RoleRepository roleRepository,
                       EmployeeRepository employeeRepository,
                       AuditLogRepository auditLogRepository,
                       @Lazy SecurityService securityService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogRepository = auditLogRepository;
        this.securityService = securityService;
    }

//    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_HR_ADMIN')")
    @PermitAll
    public User save(User user) {
        boolean isNew = (user.getId() == null);

        if (user.getId() == null || !user.getPasswordHash().startsWith("$2a$")) {
            user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        }

        User savedUser = userRepository.save(user);

        String action = isNew ? "CREATED" : "UPDATED";
        log.info("User {} successfully. ID: {}", action, savedUser.getId());
        saveAuditLog(savedUser.getId(), action, "users", "User " + action.toLowerCase() + ": " + savedUser.getUsername());

        return savedUser;
    }

//    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_HR_ADMIN', 'ROLE_AUDITOR', 'ROLE_MANAGER')")
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional
    public void delete(User user) {
        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + user.getId()));

        Long userId = managedUser.getId();
        String username = managedUser.getUsername();

        employeeRepository.findByUserId(userId).ifPresent(employee -> {
            employee.setUser(null);
            employeeRepository.save(employee);
        });

        // 3. Delete the user safely
        userRepository.delete(managedUser);

        log.info("User DELETED successfully. ID: {}", userId);
        saveAuditLog(userId, "DELETED", "users", "Deleted User: " + username);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public void importEmployeesFromJson(List<Employee> employees) {
        for (Employee empPayload : employees) {

            if (empPayload.getDepartment() != null && empPayload.getDepartment().getId() != null) {
                Department existingDept = departmentRepository.findById(empPayload.getDepartment().getId())
                        .orElseThrow(() -> new RuntimeException("Department not found for ID: " + empPayload.getDepartment().getId()));
                empPayload.setDepartment(existingDept);
            }

            User userPayload = empPayload.getUser();
            if (userPayload != null) {
                if (userPayload.getRole() != null && userPayload.getRole().getId() != null) {
                    Role existingRole = roleRepository.findById(userPayload.getRole().getId())
                            .orElseThrow(() -> new RuntimeException("Role not found for ID: " + userPayload.getRole().getId()));
                    userPayload.setRole(existingRole);
                }
                if (userPayload.getPasswordHash() != null && !userPayload.getPasswordHash().startsWith("$2a$")) {
                    userPayload.setPasswordHash(passwordEncoder.encode(userPayload.getPasswordHash()));
                }
                userPayload.setActive(true);
                User savedUser = userRepository.save(userPayload);
                empPayload.setUser(savedUser);
            }
            if (empPayload.getManager() != null && empPayload.getManager().getEmployeeCode() != null) {
                Employee existingManager = employeeRepository.findByEmployeeCode(empPayload.getManager().getEmployeeCode())
                        .orElseThrow(() -> new RuntimeException("Manager not found for code: " + empPayload.getManager().getEmployeeCode()));
                empPayload.setManager(existingManager);
            } else {
                empPayload.setManager(null);
            }

            // 4. Save the Employee
            Employee savedEmployee = employeeRepository.save(empPayload);

            // 5. (Optional) Generate Leave Balances
            // Based on your previous logs, you generate leave balances upon creation.
            // If you have a separate service for this, call it here:
            // leaveBalanceService.initializeBalancesForNewEmployee(savedEmployee);
        }

        log.info("Successfully imported {} employees/users from JSON.", employees.size());
        saveAuditLog(null, "IMPORTED", "users", "Bulk imported " + employees.size() + " employees/users from JSON.");
    }

    public long getActiveUsersCount() {
        return userRepository.countByActiveTrue();
    }

    @Transactional
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        User user = userRepository.findByUsername(username);

        if (user == null) {
            return false;
        }

        // Verify the old password
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            return false;
        }

        // Encode and save the new password
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Log the action
        saveAuditLog(user.getId(), "PASSWORD_CHANGED", "users", "Password changed for user: " + username);

        return true;
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
            log.error("Failed to save audit log for user record {}: {}", recordId, e.getMessage());
        }
    }
}