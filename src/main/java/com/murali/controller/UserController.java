package com.murali.controller;

import com.murali.entity.Employee;
import com.murali.entity.User;
import com.murali.service.EmployeeService;
import com.murali.service.UserService;
import jakarta.annotation.security.PermitAll;
import jdk.jfr.Percentage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final EmployeeService employeeService;
    public UserController(UserService userService, EmployeeService employeeService) { this.userService = userService;
        this.employeeService = employeeService;
    }

    @PutMapping("/add")
//    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or hasRole('ROLE_HR_ADMIN')")
//    @PreAuthorize("permitAll()")
    @PermitAll
    public ResponseEntity<User> addUser(@RequestBody User user) {
        log.info("Came to add user");
        return new ResponseEntity<>(userService.save(user),HttpStatus.CREATED);
    }

    @GetMapping("/get")
//    @PreAuthorize("hasAnyRole('Super Admin', 'HR Admin', 'Auditor')")
    @PermitAll
    public ResponseEntity<List<User>> getAllUsers() {
        return new ResponseEntity<>(userService.findAll(),HttpStatus.OK);
    }

    @PostMapping("/bulk-import")
    @PermitAll
    public ResponseEntity<?> importEmployees(@RequestBody List<Employee> employees) {
        try {
            userService.importEmployeesFromJson(employees);
            return ResponseEntity.ok(Map.of("message", "Successfully imported " + employees.size() + " employees."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Import failed",
                    "details", e.getMessage()
            ));
        }
    }
}

//[
//        {
//        "employeeCode": "SLS-001",
//        "firstName": "Arthur",
//        "department": { "id": 1, "name": "Sales" },
//        "manager": null,
//        "user": {
//        "username": "arthur_sales",
//        "email": "arthur@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 6, "name": "ROLE_DEPT_HEAD" }
//        }
//        },
//        {
//        "employeeCode": "SLS-002",
//        "firstName": "Betty",
//        "department": { "id": 1, "name": "Sales" },
//        "manager": { "employeeCode": "SLS-001" },
//        "user": {
//        "username": "betty_mgr",
//        "email": "betty@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 4, "name": "ROLE_MANAGER" }
//        }
//        },
//        {
//        "employeeCode": "SLS-003",
//        "firstName": "Charles",
//        "department": { "id": 1, "name": "Sales" },
//        "manager": { "employeeCode": "SLS-002" },
//        "user": {
//        "username": "charles_emp",
//        "email": "charles@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//        {
//        "employeeCode": "SLS-004",
//        "firstName": "Diana",
//        "department": { "id": 1, "name": "Sales" },
//        "manager": { "employeeCode": "SLS-002" },
//        "user": {
//        "username": "diana_emp",
//        "email": "diana@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//        {
//        "employeeCode": "SLS-005",
//        "firstName": "Evan",
//        "department": { "id": 1, "name": "Sales" },
//        "manager": { "employeeCode": "SLS-002" },
//        "user": {
//        "username": "evan_emp",
//        "email": "evan@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//        {
//        "employeeCode": "SLS-006",
//        "firstName": "Fiona",
//        "department": { "id": 1, "name": "Sales" },
//        "manager": { "employeeCode": "SLS-002" },
//        "user": {
//        "username": "fiona_emp",
//        "email": "fiona@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//
//        {
//        "employeeCode": "HR-001",
//        "firstName": "Hema",
//        "department": { "id": 2, "name": "Human Resources (HR)" },
//        "manager": null,
//        "user": {
//        "username": "hema_hr",
//        "email": "hema@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 6, "name": "ROLE_DEPT_HEAD" }
//        }
//        },
//        {
//        "employeeCode": "HR-002",
//        "firstName": "George",
//        "department": { "id": 2, "name": "Human Resources (HR)" },
//        "manager": { "employeeCode": "HR-001" },
//        "user": {
//        "username": "george_hr_mgr",
//        "email": "george@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 2, "name": "ROLE_HR_ADMIN" }
//        }
//        },
//        {
//        "employeeCode": "HR-003",
//        "firstName": "Hannah",
//        "department": { "id": 2, "name": "Human Resources (HR)" },
//        "manager": { "employeeCode": "HR-002" },
//        "user": {
//        "username": "hannah_emp",
//        "email": "hannah@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//        {
//        "employeeCode": "HR-004",
//        "firstName": "Ian",
//        "department": { "id": 2, "name": "Human Resources (HR)" },
//        "manager": { "employeeCode": "HR-002" },
//        "user": {
//        "username": "ian_emp",
//        "email": "ian@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//        {
//        "employeeCode": "HR-005",
//        "firstName": "Julia",
//        "department": { "id": 2, "name": "Human Resources (HR)" },
//        "manager": { "employeeCode": "HR-002" },
//        "user": {
//        "username": "julia_emp",
//        "email": "julia@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//        {
//        "employeeCode": "HR-006",
//        "firstName": "Kevin",
//        "department": { "id": 2, "name": "Human Resources (HR)" },
//        "manager": { "employeeCode": "HR-002" },
//        "user": {
//        "username": "kevin_emp",
//        "email": "kevin@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//
//        {
//        "employeeCode": "FIN-001",
//        "firstName": "Laura",
//        "department": { "id": 3, "name": "Finance and Accounting" },
//        "manager": null,
//        "user": {
//        "username": "laura_fin",
//        "email": "laura@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 6, "name": "ROLE_DEPT_HEAD" }
//        }
//        },
//        {
//        "employeeCode": "FIN-002",
//        "firstName": "Michael",
//        "department": { "id": 3, "name": "Finance and Accounting" },
//        "manager": { "employeeCode": "FIN-001" },
//        "user": {
//        "username": "michael_mgr",
//        "email": "michael@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 4, "name": "ROLE_MANAGER" }
//        }
//        },
//        {
//        "employeeCode": "FIN-003",
//        "firstName": "Nina",
//        "department": { "id": 3, "name": "Finance and Accounting" },
//        "manager": { "employeeCode": "FIN-002" },
//        "user": {
//        "username": "nina_emp",
//        "email": "nina@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//        {
//        "employeeCode": "FIN-004",
//        "firstName": "Oscar",
//        "department": { "id": 3, "name": "Finance and Accounting" },
//        "manager": { "employeeCode": "FIN-002" },
//        "user": {
//        "username": "oscar_emp",
//        "email": "oscar@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//        {
//        "employeeCode": "FIN-005",
//        "firstName": "Paula",
//        "department": { "id": 3, "name": "Finance and Accounting" },
//        "manager": { "employeeCode": "FIN-002" },
//        "user": {
//        "username": "paula_emp",
//        "email": "paula@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//        {
//        "employeeCode": "FIN-006",
//        "firstName": "Quinn",
//        "department": { "id": 3, "name": "Finance and Accounting" },
//        "manager": { "employeeCode": "FIN-002" },
//        "user": {
//        "username": "quinn_emp",
//        "email": "quinn@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//
//        {
//        "employeeCode": "MKT-001",
//        "firstName": "Rachel",
//        "department": { "id": 4, "name": "Marketing" },
//        "manager": null,
//        "user": {
//        "username": "rachel_mkt",
//        "email": "rachel@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 6, "name": "ROLE_DEPT_HEAD" }
//        }
//        },
//        {
//        "employeeCode": "MKT-002",
//        "firstName": "Steve",
//        "department": { "id": 4, "name": "Marketing" },
//        "manager": { "employeeCode": "MKT-001" },
//        "user": {
//        "username": "steve_mgr",
//        "email": "steve@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 4, "name": "ROLE_MANAGER" }
//        }
//        },
//        {
//        "employeeCode": "MKT-003",
//        "firstName": "Tina",
//        "department": { "id": 4, "name": "Marketing" },
//        "manager": { "employeeCode": "MKT-002" },
//        "user": {
//        "username": "tina_emp",
//        "email": "tina@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//        {
//        "employeeCode": "MKT-004",
//        "firstName": "Uma",
//        "department": { "id": 4, "name": "Marketing" },
//        "manager": { "employeeCode": "MKT-002" },
//        "user": {
//        "username": "uma_emp",
//        "email": "uma@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//        {
//        "employeeCode": "MKT-005",
//        "firstName": "Victor",
//        "department": { "id": 4, "name": "Marketing" },
//        "manager": { "employeeCode": "MKT-002" },
//        "user": {
//        "username": "victor_emp",
//        "email": "victor@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//        {
//        "employeeCode": "MKT-006",
//        "firstName": "Wendy",
//        "department": { "id": 4, "name": "Marketing" },
//        "manager": { "employeeCode": "MKT-002" },
//        "user": {
//        "username": "wendy_emp",
//        "email": "wendy@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//
//        {
//        "employeeCode": "RND-001",
//        "firstName": "Xavier",
//        "department": { "id": 5, "name": "Research and Development (R&D)" },
//        "manager": null,
//        "user": {
//        "username": "xavier_rnd",
//        "email": "xavier@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 6, "name": "ROLE_DEPT_HEAD" }
//        }
//        },
//        {
//        "employeeCode": "RND-002",
//        "firstName": "Yara",
//        "department": { "id": 5, "name": "Research and Development (R&D)" },
//        "manager": { "employeeCode": "RND-001" },
//        "user": {
//        "username": "yara_mgr",
//        "email": "yara@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 4, "name": "ROLE_MANAGER" }
//        }
//        },
//        {
//        "employeeCode": "RND-003",
//        "firstName": "Zack",
//        "department": { "id": 5, "name": "Research and Development (R&D)" },
//        "manager": { "employeeCode": "RND-002" },
//        "user": {
//        "username": "zack_emp",
//        "email": "zack@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//        {
//        "employeeCode": "RND-004",
//        "firstName": "Alice",
//        "department": { "id": 5, "name": "Research and Development (R&D)" },
//        "manager": { "employeeCode": "RND-002" },
//        "user": {
//        "username": "alice_rnd",
//        "email": "alice@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//        {
//        "employeeCode": "RND-005",
//        "firstName": "Bob",
//        "department": { "id": 5, "name": "Research and Development (R&D)" },
//        "manager": { "employeeCode": "RND-002" },
//        "user": {
//        "username": "bob_rnd",
//        "email": "bob@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        },
//        {
//        "employeeCode": "RND-006",
//        "firstName": "Chloe",
//        "department": { "id": 5, "name": "Research and Development (R&D)" },
//        "manager": { "employeeCode": "RND-002" },
//        "user": {
//        "username": "chloe_rnd",
//        "email": "chloe@company.com",
//        "passwordHash": "1234",
//        "role": { "id": 3, "name": "ROLE_EMPLOYEE" }
//        }
//        }
//        ]
