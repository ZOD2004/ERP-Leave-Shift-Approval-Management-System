package com.murali.security;

import com.murali.entity.User;
import com.murali.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        User user = null;
        String roleWithPrefix;
        try {
            user = userRepository.findByUsername(username);
            roleWithPrefix = "ROLE_" + user.getRole().getName();
        } catch (Exception e) {
            throw new UsernameNotFoundException("User not found with name: "+username);
        }


        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(roleWithPrefix);

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPasswordHash(),
                Collections.singletonList(authority)
        );
    }
}