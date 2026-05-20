package com.murali.service;

import com.murali.entity.Department;
import com.murali.entity.Employee;
import com.murali.entity.Role;
import com.murali.entity.User;
import com.murali.repository.DepartmentRepository;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.RoleRepository;
import com.murali.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;


@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final EmployeeRepository employeeRepository;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, DepartmentRepository departmentRepository, RoleRepository roleRepository, EmployeeRepository employeeRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.employeeRepository = employeeRepository;
    }

    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_HR_ADMIN')")
    public User save(User user) {
        // Only encode if it's a new user or password was actually changed in the UI
        // In a real app, you'd check if the raw password field is dirty
        if (user.getId() == null || !user.getPasswordHash().startsWith("$2a$")) {
            user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        }
        return userRepository.save(user);
    }

    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_HR_ADMIN', 'ROLE_AUDITOR', 'ROLE_MANAGER')")
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public void delete(User user) {
        userRepository.delete(user);
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

            // 2. Resolve and Save User & Role
            User userPayload = empPayload.getUser();
            if (userPayload != null) {
                // Attach existing Role from DB
                if (userPayload.getRole() != null && userPayload.getRole().getId() != null) {
                    Role existingRole = roleRepository.findById(userPayload.getRole().getId())
                            .orElseThrow(() -> new RuntimeException("Role not found for ID: " + userPayload.getRole().getId()));
                    userPayload.setRole(existingRole);
                }

                // Encode the dummy "1234" password
                if (userPayload.getPasswordHash() != null && !userPayload.getPasswordHash().startsWith("$2a$")) {
                    userPayload.setPasswordHash(passwordEncoder.encode(userPayload.getPasswordHash()));
                }

                // Ensure user is active
                userPayload.setActive(true);

                // Save User first (because Employee has a foreign key to User)
                User savedUser = userRepository.save(userPayload);
                empPayload.setUser(savedUser);
            }

            // 3. Resolve and Attach Manager
            // The JSON only provides the manager's employeeCode, so we look them up.
            if (empPayload.getManager() != null && empPayload.getManager().getEmployeeCode() != null) {
                Employee existingManager = employeeRepository.findByEmployeeCode(empPayload.getManager().getEmployeeCode())
                        .orElseThrow(() -> new RuntimeException("Manager not found for code: " + empPayload.getManager().getEmployeeCode()));
                empPayload.setManager(existingManager);
            } else {
                // If no manager is provided, ensure it's null
                empPayload.setManager(null);
            }

            // 4. Save the Employee
            Employee savedEmployee = employeeRepository.save(empPayload);

            // 5. (Optional) Generate Leave Balances
            // Based on your previous logs, you generate leave balances upon creation.
            // If you have a separate service for this, call it here:
            // leaveBalanceService.initializeBalancesForNewEmployee(savedEmployee);
        }
    }
    public long getActiveUsersCount() {
        return userRepository.countByActiveTrue();
    }
}
