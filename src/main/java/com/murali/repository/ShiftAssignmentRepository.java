package com.murali.repository;

import com.murali.entity.ShiftAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {

    @Query(
            value = """
            SELECT EXISTS (
                SELECT 1
                FROM shift_assignments sa
                WHERE sa.employee_id = :employeeId
                  AND sa.assignment_date = :assignmentDate
            )
            """,
            nativeQuery = true
    )
    boolean existsByEmployeeIdAndAssignmentDate(@Param("employeeId") Long employeeId,
            @Param("assignmentDate") LocalDate assignmentDate);

    @Query("""
    SELECT sa
    FROM ShiftAssignment sa
    JOIN FETCH sa.employee e
    JOIN FETCH sa.shift s
    WHERE sa.assignmentDate = :filterDate
      AND LOWER(e.firstName)
            LIKE LOWER(CONCAT('%', :employeeName, '%'))
""")
    Page<ShiftAssignment> findFilteredAssignments(@Param("filterDate") LocalDate filterDate,
            @Param("employeeName") String employeeName,Pageable pageable);

    @Query("""
    SELECT sa
    FROM ShiftAssignment sa
    JOIN FETCH sa.employee
    JOIN FETCH sa.shift
    WHERE sa.assignmentDate = :filterDate
""")
    Page<ShiftAssignment> findByDate(@Param("filterDate") LocalDate filterDate,Pageable pageable);


    @Query("""
    SELECT sa
    FROM ShiftAssignment sa
    JOIN FETCH sa.employee
    JOIN FETCH sa.shift
    WHERE sa.assignmentDate BETWEEN :startDate AND :endDate
""")
    List<ShiftAssignment> findByAssignmentDateBetween(@Param("startDate") LocalDate startDate,@Param("endDate") LocalDate endDate);

    @Query("""
    SELECT sa
    FROM ShiftAssignment sa
    JOIN FETCH sa.employee e
    JOIN FETCH sa.shift
    WHERE LOWER(e.firstName)
            LIKE LOWER(CONCAT('%', :employeeName, '%'))
""")
    Page<ShiftAssignment> findByEmployeeName(
            @Param("employeeName") String employeeName,
            Pageable pageable
    );

    @Query("""
    SELECT sa
    FROM ShiftAssignment sa
    JOIN FETCH sa.employee
    JOIN FETCH sa.shift
    WHERE sa.assignmentDate = :date
""")
    List<ShiftAssignment> findByAssignmentDate(@Param("date") LocalDate date);

    @Query("""
    SELECT sa
    FROM ShiftAssignment sa
    JOIN FETCH sa.employee
    JOIN FETCH sa.shift
""")
    Page<ShiftAssignment> findAllAssignments(Pageable pageable);

    @Query("SELECT sa FROM ShiftAssignment sa WHERE sa.employee.id IN :employeeIds " +
            "AND sa.assignmentDate BETWEEN :startDate AND :endDate")
    List<ShiftAssignment> findByEmployeeIdInAndAssignmentDateBetween(
            @Param("employeeIds") List<Long> employeeIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
