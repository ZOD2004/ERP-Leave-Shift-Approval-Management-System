package com.murali.service;

import com.murali.entity.Role;
import com.murali.repository.RoleRepository;
import jakarta.annotation.security.PermitAll;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
//    @PermitAll
    public Role addRole(Role role){
        return roleRepository.save(role);
    }

    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_AUDITOR','ROLE_HR_ADMIN')")
//    @PermitAll
    public List<Role> getRoles(){
        return roleRepository.findAll();
    }
    @PreAuthorize("hasAnyRole('ROLE_SUPER_ADMIN', 'ROLE_AUDITOR', 'ROLE_HR_ADMIN')")
    public List<Role> findAll() {
        return roleRepository.findAll();
    }
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public Role save(Role role) {
        // You can add logic here to ensure names are always uppercase if desired
        if (role.getName() != null) {
            role.setName(role.getName().toUpperCase().trim());
        }
        return roleRepository.save(role);
    }
    @PreAuthorize("hasRole('ROLE_SUPER_ADMIN')")
    public void delete(Role role) {
        roleRepository.delete(role);
    }
}
