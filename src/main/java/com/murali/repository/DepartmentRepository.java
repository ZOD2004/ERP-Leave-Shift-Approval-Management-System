package com.murali.repository;

import com.murali.entity.Department;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DepartmentRepository extends JpaRepository<Department,Long> {

    @EntityGraph(attributePaths = {"hod"})
    List<Department> findAll();

    Department findByName(String name);
}
