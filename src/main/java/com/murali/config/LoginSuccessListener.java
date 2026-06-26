package com.murali.config;

import com.murali.entity.User;
import com.murali.repository.UserRepository;
import com.murali.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginSuccessListener implements ApplicationListener<AuthenticationSuccessEvent> {

    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        Object principal = event.getAuthentication().getPrincipal();

        if (principal instanceof UserDetails userDetails) {
            String username = userDetails.getUsername();

            // Optional: Get the user ID if you want to attach it to the record_id
            User user = userRepository.findByUsername(username);
            Long userId = (user != null) ? user.getId() : 0L;

            // Log the action
            auditLogService.saveAuditLog(
                    userId,
                    "LOGIN",
                    "USER_SESSION",
                    null,
                    "{ \"status\": \"SUCCESS\", \"username\": \"" + username + "\" }"
            );

            log.info("User logged in and recorded in audit log: {}", username);
        }
    }
}
