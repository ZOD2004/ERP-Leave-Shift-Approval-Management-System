package com.murali.service;

import com.murali.entity.*;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.RoleRepository;
import com.murali.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final UserService userService;
    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final AuditLogService auditLoggingService;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public List<Employee> findAvailableManagers(Long departmentId) {
        if (departmentId == null) {
            return Collections.emptyList();
        }
        return employeeRepository.findAvailableManagers(departmentId);
    }

    @Transactional
    public void createOrUpdateEmployeeWithUser(Employee currentEmployee, User currentUser,
                                               boolean isExistingUserLinked, Set<LeaveType> selectedLeaves) {

        boolean isNew = (currentEmployee.getId() == null);
        String oldState = null;

        if (!isNew) {
            Optional<Employee> existingOpt = employeeRepository.findById(currentEmployee.getId());
            if (existingOpt.isPresent()) {
                Employee existing = existingOpt.get();
                oldState = String.format("{ \"firstName\": \"%s\", \"employeeCode\": \"%s\", \"userId\": %d }",
                        existing.getFirstName(),
                        existing.getEmployeeCode(),
                        existing.getUser() != null ? existing.getUser().getId() : null);
            }
        }

        User finalUser = currentUser;
        if (isExistingUserLinked && currentUser != null && currentUser.getUsername() != null) {
            finalUser = userService.findByUsername(currentUser.getUsername());
        } else if (!isExistingUserLinked && currentUser != null) {
            finalUser = userService.save(currentUser);
        }
        currentEmployee.setUser(finalUser);

        Employee savedEmployee = employeeRepository.save(currentEmployee);

        Integer currentYear = LocalDate.now().getYear();
        leaveBalanceService.initializeBalancesForEmployee(savedEmployee, currentYear, selectedLeaves);

        String newState = String.format("{ \"firstName\": \"%s\", \"employeeCode\": \"%s\", \"userId\": %d }",
                savedEmployee.getFirstName(),
                savedEmployee.getEmployeeCode(),
                finalUser != null ? finalUser.getId() : null);

        String action = isNew ? "CREATED" : "UPDATED";
        log.info("Employee {} successfully. Employee ID: {}", action, savedEmployee.getId());
        auditLoggingService.saveAuditLog(savedEmployee.getId(), action, "employees", oldState, newState);
    }

    @Transactional
    public void deactivateEmployee(Employee employee) {
        boolean wasActive = employee.getUser() != null && employee.getUser().getActive();
        String oldState = String.format("{ \"userActive\": %b }", wasActive);

        User currUser = employee.getUser();
        if (currUser != null) {
            currUser.setActive(false);
      }

        employeeRepository.save(employee);

        boolean isNowActive = employee.getUser() != null && employee.getUser().getActive();
        String newState = String.format("{ \"userActive\": %b }", isNowActive);

        log.info("Employee deactivated. Employee ID: {}", employee.getId());
        auditLoggingService.saveAuditLog(employee.getId(), "DEACTIVATED", "employees", oldState, newState);
    }

    public List<Employee> searchActive(String searchTerm) {
        return employeeRepository.searchActiveEmployees(searchTerm);
    }

    public List<Employee> findAllActive() {
        return employeeRepository.findByActiveTrue();
    }

    public Optional<Employee> findById(Long id){
        return employeeRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Employee> findByIdWithDetails(Long id) {
        return employeeRepository.findByIdWithDepartmentAndManager(id);
    }
    // Add this method to EmployeeService
    @Transactional
    public void reassignEmployeesAndDemoteHod(Department oldDept, Department newDept) {
        // 1. Demote the old HOD if one exists
        if (oldDept.getHod() != null) {
            Employee hod = oldDept.getHod();
            User hodUser = hod.getUser();

            if (hodUser != null) {
                // Fetch the standard employee role
                Role empRole = roleRepository.findByName("ROLE_EMPLOYEE");

                // Update and explicitly save the user as requested
                hodUser.setRole(empRole);
                userRepository.save(hodUser);
            }
        }

        // 2. Reassign all employees (This will also include the former HOD)
        List<Employee> employees = employeeRepository.findByDepartmentId(oldDept.getId());
        for (Employee emp : employees) {
            emp.setDepartment(newDept);
        }

        // 3. Save all to trigger JPA lifecycle events and audit logs
        employeeRepository.saveAll(employees);
    }
}