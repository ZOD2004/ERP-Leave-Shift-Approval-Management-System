package com.murali.repository;

import com.murali.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    @Query("""
        SELECT DISTINCT e
        FROM Employee e
        LEFT JOIN FETCH e.department
        LEFT JOIN FETCH e.user
        LEFT JOIN FETCH e.manager
    """)
    List<Employee> findAllManagers();

    @Query("""
        SELECT e
        FROM Employee e
        JOIN FETCH e.user u
        LEFT JOIN FETCH e.department
        LEFT JOIN FETCH e.manager
        WHERE u.active = true
    """)
    List<Employee> findByActiveTrue();

    @Query("""
        SELECT DISTINCT e
        FROM Employee e
        JOIN FETCH e.user u
        LEFT JOIN FETCH e.department
        LEFT JOIN FETCH e.manager
        WHERE u.active = true
        AND (
            LOWER(e.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
            OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
        )
    """)
    List<Employee> searchActiveEmployees(@Param("searchTerm") String searchTerm);
}