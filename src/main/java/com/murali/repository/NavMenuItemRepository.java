package com.murali.repository;

import com.murali.entity.NavMenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface NavMenuItemRepository extends JpaRepository<NavMenuItem, Long> {

    List<NavMenuItem> findByRoleName(String roleName);
}
