package com.murali.service;

import com.murali.entity.Employee;
import com.murali.entity.LeaveType;
import com.murali.entity.User;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.LeaveTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class EmployeeService {

    private final UserService userService;
    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveTypeRepository leaveTypeRepository;

    // Replaced AuditLogRepository and SecurityService with the centralized logger
    private final AuditLogService auditLoggingService;

    public EmployeeService(UserService userService,
                           EmployeeRepository employeeRepository,
                           LeaveBalanceService leaveBalanceService,
                           LeaveTypeRepository leaveTypeRepository,
                           AuditLogService auditLoggingService) {
        this.userService = userService;
        this.employeeRepository = employeeRepository;
        this.leaveBalanceService = leaveBalanceService;
        this.leaveTypeRepository = leaveTypeRepository;
        this.auditLoggingService = auditLoggingService;
    }

    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_HR_ADMIN')")
    public List<Employee> findAvailableManagers(Long departmentId) {
        if (departmentId == null) {
            return Collections.emptyList();
        }
        return employeeRepository.findAvailableManagers(departmentId);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_HR_ADMIN')")
    public void createOrUpdateEmployeeWithUser(Employee currentEmployee, User currentUser,
                                               boolean isExistingUserLinked, java.util.Set<LeaveType> selectedLeaves) {

        boolean isNew = (currentEmployee.getId() == null);

        // 1. Capture Old State for updates
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

        // --- ORIGINAL BUSINESS LOGIC (UNTOUCHED) ---
        User finalUser = currentUser;

        if (isExistingUserLinked && currentUser != null && currentUser.getUsername() != null) {
            finalUser = userService.findByUsername(currentUser.getUsername());
        }
        else if (!isExistingUserLinked && currentUser != null) {
            finalUser = userService.save(currentUser);
        }
        currentEmployee.setUser(finalUser);

        if (selectedLeaves == null || selectedLeaves.isEmpty()) {
            java.util.List<LeaveType> allLeaves = leaveTypeRepository.findAll();
            currentEmployee.setApplicableLeaveTypes(new java.util.HashSet<>(allLeaves));
        } else {
            currentEmployee.setApplicableLeaveTypes(selectedLeaves);
        }

        Integer currentYear = LocalDate.now().getYear();
        employeeRepository.save(currentEmployee);
        leaveBalanceService.initializeBalancesForEmployee(currentEmployee, currentYear);
        // -------------------------------------------

        // 2. Capture New State and Log
        String newState = String.format("{ \"firstName\": \"%s\", \"employeeCode\": \"%s\", \"userId\": %d }",
                currentEmployee.getFirstName(),
                currentEmployee.getEmployeeCode(),
                finalUser != null ? finalUser.getId() : null);

        String action = isNew ? "CREATED" : "UPDATED";
        log.info("Employee {} successfully. Employee ID: {}", action, currentEmployee.getId());
        auditLoggingService.saveAuditLog(currentEmployee.getId(), action, "employees", oldState, newState);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_HR_ADMIN')")
    public void deactivateEmployee(Employee employee) {

        // 1. Capture Old State (Targeting the User's active flag)
        boolean wasActive = employee.getUser() != null && employee.getUser().getActive();
        String oldState = String.format("{ \"userActive\": %b }", wasActive);

        // --- ORIGINAL BUSINESS LOGIC (UNTOUCHED) ---
        User currUser = employee.getUser();
        if (currUser != null) {
            currUser.setActive(false);
        }
        employeeRepository.save(employee);
        // -------------------------------------------

        // 2. Capture New State and Log
        boolean isNowActive = employee.getUser() != null && employee.getUser().getActive();
        String newState = String.format("{ \"userActive\": %b }", isNowActive);

        log.info("Employee deactivated. Employee ID: {}", employee.getId());
        auditLoggingService.saveAuditLog(employee.getId(), "DEACTIVATED", "employees", oldState, newState);
    }

    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_HR_ADMIN')")
    public List<Employee> searchActive(String searchTerm) {
        return employeeRepository.searchActiveEmployees(searchTerm);
    }

    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_HR_ADMIN')")
    public List<Employee> findAllActive() {
        return employeeRepository.findByActiveTrue();
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_HR_ADMIN')")
    public void createEmployeeWithUser(Employee employee, User user) {
        // --- ORIGINAL BUSINESS LOGIC (UNTOUCHED) ---
        User finalUser = userService.findByUsername(user.getUsername());
        employee.setUser(finalUser);
        employeeRepository.save(employee);
        // -------------------------------------------

        // Capture New State and Log (Creation has no oldState)
        String newState = String.format("{ \"firstName\": \"%s\", \"employeeCode\": \"%s\", \"userId\": %d }",
                employee.getFirstName(),
                employee.getEmployeeCode(),
                finalUser.getId());

        log.info("Employee created with existing user. Employee ID: {}", employee.getId());
        auditLoggingService.saveAuditLog(employee.getId(), "CREATED", "employees", null, newState);
    }

    public Optional<Employee> findById(Long id){
        return employeeRepository.findById(id);
    }
}