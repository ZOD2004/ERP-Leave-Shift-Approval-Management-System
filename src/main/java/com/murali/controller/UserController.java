package com.murali.controller;

import com.murali.entity.User;
import com.murali.service.UserService;
import jakarta.annotation.security.PermitAll;
import jdk.jfr.Percentage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    public UserController(UserService userService) { this.userService = userService; }

    @PutMapping("/add")
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN') or hasRole('ROLE_HR_ADMIN')")
//    @PreAuthorize("permitAll()")
    public ResponseEntity<User> addUser(@RequestBody User user) {
        return new ResponseEntity<>(userService.addUser(user),HttpStatus.CREATED);
    }

    @GetMapping("/get")
//    @PreAuthorize("hasAnyRole('Super Admin', 'HR Admin', 'Auditor')")
    @PermitAll
    public ResponseEntity<List<User>> getAllUsers() {
        return new ResponseEntity<>(userService.getUsers(),HttpStatus.OK);
    }
}
