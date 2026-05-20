package com.murali.repository;

import com.murali.entity.Attendance;
import com.murali.entity.Employee;
import com.murali.entity.ShiftAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

    Optional<Employee> findByEmployeeCode(String employeeCode);

    @Query("SELECT e.id FROM Employee e JOIN e.user u WHERE u.active = true")
    List<Long> findAllActiveEmployeeIds();
    @Query("""
        SELECT e
        FROM Employee e
        LEFT JOIN FETCH e.user
        LEFT JOIN FETCH e.department
        WHERE e.manager.id = :managerId
    """)
    List<Employee> findReportingEmployees(@Param("managerId") Long managerId);
}