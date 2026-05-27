package com.murali.service;

import com.murali.entity.Department;
import com.murali.entity.Employee;
import com.murali.entity.Role;
import com.murali.entity.User;
import com.murali.repository.DepartmentRepository;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.RoleRepository;
import com.murali.repository.UserRepository;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
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
    private final AuditLogService auditLoggingService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       DepartmentRepository departmentRepository,
                       RoleRepository roleRepository,
                       EmployeeRepository employeeRepository,
                       AuditLogService auditLoggingService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.employeeRepository = employeeRepository;
        this.auditLoggingService = auditLoggingService;
    }

    @PermitAll
    public User save(User user) {
        boolean isNew = (user.getId() == null);
        String oldState = null;

        if (!isNew) {
            Optional<User> existingOpt = userRepository.findById(user.getId());
            if (existingOpt.isPresent()) {
                User existing = existingOpt.get();
                oldState = String.format("{ \"username\": \"%s\", \"email\": \"%s\", \"isActive\": %b, \"roleId\": %d }",
                        existing.getUsername(), existing.getEmail(), existing.getActive(),
                        existing.getRole() != null ? existing.getRole().getId() : null);
            }
        }

        if (user.getId() == null || !user.getPasswordHash().startsWith("$2a$")) {
            user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        }

        User savedUser = userRepository.save(user);

        String newState = String.format("{ \"username\": \"%s\", \"email\": \"%s\", \"isActive\": %b, \"roleId\": %d }",
                savedUser.getUsername(), savedUser.getEmail(), savedUser.getActive(),
                savedUser.getRole() != null ? savedUser.getRole().getId() : null);
        String action = isNew ? "CREATED" : "UPDATED";

        log.info("User {} successfully. ID: {}", action, savedUser.getId());
        auditLoggingService.saveAuditLog(savedUser.getId(), action, "users", oldState, newState);

        return savedUser;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional
    public void delete(User user) {
        User managedUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + user.getId()));

        Long userId = managedUser.getId();
        String username = managedUser.getUsername();
        String oldState = String.format("{ \"username\": \"%s\", \"email\": \"%s\", \"isActive\": %b, \"roleId\": %d }",
                managedUser.getUsername(), managedUser.getEmail(), managedUser.getActive(),
                managedUser.getRole() != null ? managedUser.getRole().getId() : null);

        employeeRepository.findByUserId(userId).ifPresent(employee -> {
            employee.setUser(null);
            employeeRepository.save(employee);
        });

        userRepository.delete(managedUser);

        log.info("User DELETED successfully. ID: {}", userId);
        auditLoggingService.saveAuditLog(userId, "DELETED", "users", oldState, null);
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

            Employee savedEmployee = employeeRepository.save(empPayload);
        }

        String newState = String.format("{ \"importedCount\": %d }", employees.size());
        log.info("Successfully imported {} employees/users from JSON.", employees.size());
        auditLoggingService.saveAuditLog(null, "IMPORTED", "users", null, newState);
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

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            return false;
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        String newState = "{ \"passwordChanged\": true }";
        auditLoggingService.saveAuditLog(user.getId(), "PASSWORD_CHANGED", "users", null, newState);

        return true;
    }
}