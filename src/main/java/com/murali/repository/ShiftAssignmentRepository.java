package com.murali.repository;

import com.murali.entity.ShiftAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

    @EntityGraph(attributePaths = {"employee", "shift"})
    @Query("""
        SELECT sa
        FROM ShiftAssignment sa
        WHERE sa.assignmentDate >= CURRENT_DATE
          AND sa.assignmentDate = :filterDate
          AND LOWER(sa.employee.firstName)
                LIKE LOWER(CONCAT('%', :employeeName, '%'))
        """)
    Page<ShiftAssignment> findFilteredAssignments(
            @Param("filterDate") LocalDate filterDate,
            @Param("employeeName") String employeeName,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"employee", "shift"})
    @Query("""
    SELECT sa
    FROM ShiftAssignment sa
    WHERE sa.assignmentDate >= CURRENT_DATE
      AND sa.assignmentDate = :filterDate
    """)
    Page<ShiftAssignment> findByDate(
            @Param("filterDate") LocalDate filterDate,
            Pageable pageable
    );

    @Query("""
    SELECT sa
    FROM ShiftAssignment sa
    JOIN FETCH sa.employee
    JOIN FETCH sa.shift
    WHERE sa.assignmentDate BETWEEN :startDate AND :endDate
""")
    List<ShiftAssignment> findByAssignmentDateBetween(@Param("startDate") LocalDate startDate,@Param("endDate") LocalDate endDate);

    @EntityGraph(attributePaths = {"employee", "shift"})
    @Query("""
    SELECT sa
    FROM ShiftAssignment sa
    WHERE sa.assignmentDate >= CURRENT_DATE
      AND LOWER(sa.employee.firstName)
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

    @EntityGraph(attributePaths = {"employee", "shift"})
    @Query("""
    SELECT sa
    FROM ShiftAssignment sa
    WHERE sa.assignmentDate >= CURRENT_DATE
    """)
    Page<ShiftAssignment> findAllAssignments(Pageable pageable);

    @Query("SELECT sa FROM ShiftAssignment sa WHERE sa.employee.id IN :employeeIds " +
            "AND sa.assignmentDate BETWEEN :startDate AND :endDate")
    List<ShiftAssignment> findByEmployeeIdInAndAssignmentDateBetween(
            @Param("employeeIds") List<Long> employeeIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(sa) > 0 FROM ShiftAssignment sa " +
            "WHERE sa.employee.id = :employeeId " +
            "AND sa.assignmentDate = :assignmentDate " +
            "AND (:excludeAssignmentId IS NULL OR sa.id <> :excludeAssignmentId)")
    boolean existsConflictExcludingAssignment(
            @Param("employeeId") Long employeeId,
            @Param("assignmentDate") LocalDate assignmentDate,
            @Param("excludeAssignmentId") Long excludeAssignmentId
    );

    @Query("SELECT sa FROM ShiftAssignment sa JOIN FETCH sa.shift WHERE sa.employee.id = :employeeId AND sa.assignmentDate = :date")
    Optional<ShiftAssignment> findByEmployeeIdAndAssignmentDate(@Param("employeeId") Long employeeId, @Param("date") LocalDate date);

    @Query("SELECT sa FROM ShiftAssignment sa JOIN FETCH sa.shift WHERE sa.assignmentDate = :date")
    List<ShiftAssignment> findAllByAssignmentDate(@Param("date") LocalDate date);

    @Query("""
        SELECT sa
        FROM ShiftAssignment sa
        LEFT JOIN FETCH sa.employee e
        LEFT JOIN FETCH e.user
        LEFT JOIN FETCH sa.shift
        WHERE sa.employee.id IN :employeeIds
        AND sa.assignmentDate = :assignmentDate
    """)
    List<ShiftAssignment> findTodayAssignmentsForEmployees(
            @Param("employeeIds") List<Long> employeeIds,
            @Param("assignmentDate") LocalDate assignmentDate
    );
}
