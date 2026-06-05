package com.murali.repository;

import com.murali.entity.NavMenuItem;
import com.murali.entity.NavMenuRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NavMenuRoleRepository extends JpaRepository<NavMenuRole, Long> {
    boolean existsByRoleNameAndNavMenuItem(String roleName, NavMenuItem navMenuItem);
}
