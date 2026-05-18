package com.murali.service;

import com.murali.entity.NavMenuItem;
import com.murali.repository.NavMenuItemRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NavigationService {

    private final NavMenuItemRepository repository;

    public NavigationService(NavMenuItemRepository repository) {
        this.repository = repository;
    }

    public List<NavMenuItem> getMenuItemsForUser(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return List.of();
        }

        String primaryRole = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(role -> role.startsWith("ROLE_"))
                .findFirst()
                .orElse("");

//        System.out.println("Debug: Primary Role identified: " + primaryRole);

        return repository.findByRoleName(primaryRole);
    }
}
