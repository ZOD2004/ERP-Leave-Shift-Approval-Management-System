package com.murali.controller;

import com.murali.entity.User;
import com.murali.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    public UserController(UserService userService) { this.userService = userService; }

    @PutMapping("/add")
    @PreAuthorize("hasRole('Super Admin') or hasRole('HR Admin')")
//    @PreAuthorize("permitAll()")
    public ResponseEntity<User> addUser(@RequestBody User user) {
        return new ResponseEntity<>(userService.addUser(user),HttpStatus.CREATED);
    }

    @GetMapping("/get")
    @PreAuthorize("hasAnyRole('Super Admin', 'HR Admin', 'Auditor')")
    public ResponseEntity<List<User>> getAllUsers() {
        return new ResponseEntity<>(userService.getUsers(),HttpStatus.OK);
    }
}
