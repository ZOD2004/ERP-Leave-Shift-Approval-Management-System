package com.murali.service;

import com.murali.entity.Role;
import com.murali.repository.RoleRepository;
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

    @PreAuthorize("hasRole('Super Admin')")
    public Role addRole(Role role){
        return roleRepository.save(role);
    }

    @PreAuthorize("hasAnyRole('Super Admin', 'Auditor','HR Admin')")
    public List<Role> getRoles(){
        return roleRepository.findAll();
    }
}
