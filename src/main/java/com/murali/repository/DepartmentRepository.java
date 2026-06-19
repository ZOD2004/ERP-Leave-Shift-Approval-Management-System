package com.murali.repository;

import com.murali.entity.Department;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department,Long> {

    @EntityGraph(attributePaths = {"hod"})
    List<Department> findAll();

    Department findByName(String name);

    boolean existsByHodId(Long hodId);

    Optional<Department> findByHodId(Long hodId);

   @EntityGraph(attributePaths = {"hod"})
    Optional<Department> findById(Long id);
}
