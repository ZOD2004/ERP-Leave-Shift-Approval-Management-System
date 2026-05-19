package com.murali.service;

import com.murali.entity.Employee;
import com.murali.entity.LeaveBalance;
import com.murali.entity.LeaveType;
import com.murali.entity.User;
import com.murali.repository.EmployeeRepository;
import com.murali.repository.LeaveTypeRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class EmployeeService {

    private final UserService userService;
    private final EmployeeRepository employeeRepository;
    private final LeaveBalanceService leaveBalanceService;
    private final LeaveTypeRepository leaveTypeRepository;
    public EmployeeService(UserService userService, EmployeeRepository employeeRepository, LeaveBalanceService leaveBalanceService, LeaveTypeRepository leaveTypeRepository){
        this.userService = userService;
        this.employeeRepository = employeeRepository;
        this.leaveBalanceService = leaveBalanceService;
        this.leaveTypeRepository = leaveTypeRepository;
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
    }
    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_HR_ADMIN')")
    public void deactivateEmployee(Employee employee) {
        User currUser = employee.getUser();
        if (currUser != null) {
            currUser.setActive(false);
        }
        employeeRepository.save(employee);
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
    }

    public Optional<Employee> findById(Long id){
        return employeeRepository.findById(id);
    }
}
