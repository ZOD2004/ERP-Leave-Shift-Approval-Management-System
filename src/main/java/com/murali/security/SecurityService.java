package com.murali.security;



import com.murali.entity.Employee;
import com.murali.entity.User;
import com.murali.exception.EmployeeNotFoundException;
import com.murali.repository.UserRepository;
import com.murali.service.EmployeeService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class SecurityService {

    private final EmployeeService employeeService;
    private final UserRepository userRepository;

    public SecurityService(EmployeeService employeeService,
                           UserRepository userRepository) {
        this.employeeService = employeeService;
        this.userRepository = userRepository;
    }

    public Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public CustomUserDetails getPrincipal() {

        Authentication auth = getAuthentication();

        if (auth == null ||!auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }

        return (CustomUserDetails) auth.getPrincipal();
    }

    public User getAuthenticatedUser() {

        CustomUserDetails principal = getPrincipal();

        if (principal == null) {
            return null;
        }

        return userRepository.findById(principal.getUserId())
                .orElseThrow(() ->
                        new RuntimeException("Authenticated user not found"));
    }

    public Employee getCurrentEmployee() {

        CustomUserDetails principal = getPrincipal();

        if (principal == null || principal.getEmployeeId() == null) {
            throw new EmployeeNotFoundException("No employee linked to current user");
        }

        return employeeService.findById(principal.getEmployeeId())
                .orElseThrow(() ->
                        new EmployeeNotFoundException("Employee with id : "+ principal.getEmployeeId()+ " not found"));
    }

    public Long getCurrentEmployeeId() {

        CustomUserDetails principal = getPrincipal();

        if (principal == null) {
            return null;
        }

        return principal.getEmployeeId();
    }

    public Long getCurrentUserId() {

        CustomUserDetails principal = getPrincipal();

        if (principal == null) {
            return null;
        }

        return principal.getUserId();
    }

    public boolean hasRole(String role) {

        Authentication auth = getAuthentication();

        return auth.getAuthorities()
                .stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }
}
