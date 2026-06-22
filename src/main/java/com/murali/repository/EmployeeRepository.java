package com.murali.repository;

import com.murali.entity.Attendance;
import com.murali.entity.Employee;
import com.murali.entity.ShiftAssignment;
import com.murali.entity.User;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {


    @EntityGraph(attributePaths = {"user", "user.role", "department", "manager", "defaultShift"})
    @Query("SELECT e FROM Employee e JOIN e.user u WHERE u.active = true")
    List<Employee> findByActiveTrue();

    @EntityGraph(attributePaths = {"user", "user.role", "department", "manager", "defaultShift"})
    @Query("""
                SELECT DISTINCT e
                FROM Employee e
                JOIN e.user u
                WHERE u.active = true
                AND (
                    LOWER(e.firstName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                    OR LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
                )
            """)
    List<Employee> searchActiveEmployees(@Param("searchTerm") String searchTerm);

    Optional<Employee> findByEmployeeCode(String employeeCode);

    @EntityGraph(attributePaths = {"user", "department"})
    @Query("SELECT e FROM Employee e WHERE e.manager.id = :managerId")
    List<Employee> findReportingEmployees(@Param("managerId") Long managerId);

    @EntityGraph(attributePaths = {"department", "manager"})
    @Query("SELECT e FROM Employee e WHERE e.user.id = :userId")
    Optional<Employee> findByUserId(@Param("userId") Long userId);

    boolean existsByDepartmentId(Long deptId);

    @EntityGraph(attributePaths = {"department", "manager"})
    @Query("SELECT e FROM Employee e WHERE e.id = :id")
    Optional<Employee> findByIdWithDepartmentAndManager(@Param("id") Long id);

    @EntityGraph(attributePaths = {"user", "user.role"})
    List<Employee> findByDepartmentId(Long departmentId);

    boolean existsByManagerId(Long managerId);
    List<Employee> findByManagerId(Long managerId);

    @Modifying
    @Query("UPDATE Employee e SET e.manager.id = :newManagerId WHERE e.manager.id = :oldManagerId")
    void reassignManager(@Param("oldManagerId") Long oldManagerId, @Param("newManagerId") Long newManagerId);

    @Modifying
    @Query("UPDATE Employee e SET e.manager = null WHERE e.manager.id = :oldManagerId")
    void clearManagerReference(@Param("oldManagerId") Long oldManagerId);

}