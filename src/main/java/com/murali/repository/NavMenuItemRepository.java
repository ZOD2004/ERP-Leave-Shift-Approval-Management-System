package com.murali.repository;

import com.murali.entity.NavMenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface NavMenuItemRepository extends JpaRepository<NavMenuItem, Long> {

    Optional<NavMenuItem> findByPath(String path);
    @Query("SELECT nmr.navMenuItem FROM NavMenuRole nmr WHERE nmr.roleName = :roleName")
    List<NavMenuItem> findByRoleName(@Param("roleName") String roleName);
}
