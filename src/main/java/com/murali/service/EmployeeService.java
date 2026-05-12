package com.murali.service;

import com.murali.entity.Employee;
import com.murali.entity.User;
import com.murali.repository.EmployeeRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmployeeService {

    private final UserService userService;
    private final EmployeeRepository employeeRepository;
    public EmployeeService(UserService userService, EmployeeRepository employeeRepository){
        this.userService = userService;
        this.employeeRepository = employeeRepository;
    }

    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_HR_ADMIN')")
    public List<Employee> findAllManagers() {
        return employeeRepository.findAllManagers();
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_HR_ADMIN')")
    public void createOrUpdateEmployeeWithUser(Employee currentEmployee, User currentUser, boolean isExistingUserLinked) {
        User finalUser = currentUser;

        if (isExistingUserLinked && currentUser != null && currentUser.getUsername() != null) {
            finalUser = userService.findByUsername(currentUser.getUsername());
        }
        else if (!isExistingUserLinked && currentUser != null) {
            finalUser = userService.addUser(currentUser);
        }
        currentEmployee.setUser(finalUser);
        employeeRepository.save(currentEmployee);
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
}
