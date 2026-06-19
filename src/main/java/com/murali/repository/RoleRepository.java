package com.murali.repository;

import com.murali.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleRepository extends JpaRepository<Role,Long> {
    Role findByName(String name);
    Role findByHierarchyWeight(Integer hierarchyWeight);
    @Query("SELECT r FROM Role r ORDER BY r.hierarchyWeight ASC")
    List<Role> findAllOrderedByWeight();
}
