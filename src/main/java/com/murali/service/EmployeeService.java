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
    public EmployeeService(UserService userService, UserService userService1, EmployeeRepository employeeRepository){
        this.userService = userService1;
        this.employeeRepository = employeeRepository;
    }
    @Transactional
    @PreAuthorize("hasAnyRole('Super Admin', 'HR Admin')")
    public void createEmployeeWithUser(Employee employee, User user) {
        User finalUser = userService.findByUsername(user.getUsername())
                .orElseGet(() -> userService.addUser(user));

        employee.setUser(finalUser);
        employeeRepository.save(employee);
    }

    @PreAuthorize("hasAnyRole('Super Admin', 'HR Admin')")
    public List<Employee> findAllManagers() {
        return employeeRepository.findAllManagers();
    }

}
