package com.murali.service;

import com.murali.entity.*;
import com.murali.repository.AuditLogRepository;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.LeaveTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
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
    private final AuditLogRepository auditLogRepository;
    private final SecurityService securityService;

    public EmployeeService(UserService userService,
                           EmployeeRepository employeeRepository,
                           LeaveBalanceService leaveBalanceService,
                           LeaveTypeRepository leaveTypeRepository,
                           AuditLogRepository auditLogRepository,
                           @Lazy SecurityService securityService) {
        this.userService = userService;
        this.employeeRepository = employeeRepository;
        this.leaveBalanceService = leaveBalanceService;
        this.leaveTypeRepository = leaveTypeRepository;
        this.auditLogRepository = auditLogRepository;
        this.securityService = securityService;
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

        String action = isNew ? "CREATED" : "UPDATED";
        log.info("Employee {} successfully. Employee ID: {}", action, currentEmployee.getId());
        saveAuditLog(currentEmployee.getId(), action, "employees",
                "Employee details " + action.toLowerCase() + " for user: " + (finalUser != null ? finalUser.getUsername() : "N/A"));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_HR_ADMIN')")
    public void deactivateEmployee(Employee employee) {
        User currUser = employee.getUser();
        if (currUser != null) {
            currUser.setActive(false);
        }
        employeeRepository.save(employee);

        log.info("Employee deactivated. Employee ID: {}", employee.getId());
        saveAuditLog(employee.getId(), "DEACTIVATED", "employees",
                "Employee and linked user account deactivated.");
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
        User finalUser = userService.findByUsername(user.getUsername());
        employee.setUser(finalUser);
        employeeRepository.save(employee);

        log.info("Employee created with existing user. Employee ID: {}", employee.getId());
        saveAuditLog(employee.getId(), "CREATED", "employees",
                "Employee linked to existing user: " + finalUser.getUsername());
    }

    public Optional<Employee> findById(Long id){
        return employeeRepository.findById(id);
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
            log.error("Failed to save audit log for employee record {}: {}", recordId, e.getMessage());
        }
    }
}
