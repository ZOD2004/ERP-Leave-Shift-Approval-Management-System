package com.murali.repository;

import com.murali.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EmployeeRepository extends JpaRepository<Employee,Long> {
    @Query("""
        SELECT DISTINCT e
        FROM Employee e
        WHERE e.subordinates IS NOT EMPTY
    """)
    List<Employee> findAllManagers();
}
