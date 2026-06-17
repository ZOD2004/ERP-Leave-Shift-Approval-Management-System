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

    @Query("""
                SELECT COUNT(sa) > 0 FROM ShiftAssignment sa 
                WHERE sa.employee.id = :employeeId 
                AND sa.startDate <= :endDate AND sa.endDate >= :startDate 
                AND (:excludeAssignmentId IS NULL OR sa.id <> :excludeAssignmentId)
            """)
    boolean existsConflictExcludingAssignment(@Param("employeeId") Long employeeId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate, @Param("excludeAssignmentId") Long excludeAssignmentId);

    @EntityGraph(attributePaths = {"employee", "shift"})
    @Query("""
            SELECT sa FROM ShiftAssignment sa
            WHERE :filterDate BETWEEN sa.startDate AND sa.endDate
            """)
    Page<ShiftAssignment> findByDate(@Param("filterDate") LocalDate filterDate, Pageable pageable);

    @EntityGraph(attributePaths = {"employee", "shift"})
    @Query("""
            SELECT sa FROM ShiftAssignment sa
            WHERE :filterDate BETWEEN sa.startDate AND sa.endDate
              AND LOWER(sa.employee.firstName) LIKE LOWER(CONCAT('%', :employeeName, '%'))
            """)
    Page<ShiftAssignment> findFilteredAssignments(@Param("filterDate") LocalDate filterDate, @Param("employeeName") String employeeName, Pageable pageable);

    @Query("""
                SELECT sa FROM ShiftAssignment sa
                JOIN FETCH sa.employee
                JOIN FETCH sa.shift
                WHERE sa.startDate <= :endDate AND sa.endDate >= :startDate
            """)
    List<ShiftAssignment> findOverlappingAssignmentsInRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @EntityGraph(attributePaths = {"employee", "shift", "shift.workingDays"})
    @Query("""
                SELECT sa FROM ShiftAssignment sa 
                WHERE sa.employee.id IN :employeeIds 
                AND sa.startDate <= :endDate AND sa.endDate >= :startDate
            """)
    List<ShiftAssignment> findByEmployeeIdInAndDateRange(@Param("employeeIds") List<Long> employeeIds, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    boolean existsByShiftId(Long id);

    @Query("SELECT sa.shift.name, COUNT(sa) FROM ShiftAssignment sa WHERE :date BETWEEN sa.startDate AND sa.endDate GROUP BY sa.shift.name")
    List<Object[]> countShiftsByDate(@Param("date") LocalDate date);

    @Query("SELECT COUNT(sa) FROM ShiftAssignment sa WHERE sa.startDate = sa.endDate AND sa.startDate >= :start AND sa.endDate <= :end")
    long countSingleDayHolePunches(@Param("start") LocalDate start, @Param("end") LocalDate end);


    @Query("SELECT sa FROM ShiftAssignment sa WHERE sa.employee.id = :employeeId AND :targetDate BETWEEN sa.startDate AND sa.endDate")
    Optional<ShiftAssignment> findAssignmentByEmployeeAndDate(@Param("employeeId") Long employeeId, @Param("targetDate") LocalDate targetDate);

    @Query("SELECT sa FROM ShiftAssignment sa WHERE sa.employee.id IN :employeeIds AND :targetDate BETWEEN sa.startDate AND sa.endDate")
    List<ShiftAssignment> findTodayAssignmentsForEmployees(@Param("employeeIds") List<Long> employeeIds, @Param("targetDate") LocalDate targetDate);

    @Query("SELECT sa FROM ShiftAssignment sa WHERE sa.employee.id = :employeeId AND :targetDate BETWEEN sa.startDate AND sa.endDate")
    Optional<ShiftAssignment> findByEmployeeIdAndAssignmentDate(@Param("employeeId") Long employeeId, @Param("targetDate") LocalDate targetDate);

    @EntityGraph(attributePaths = {"employee", "shift"})
    @Query("SELECT sa FROM ShiftAssignment sa WHERE LOWER(sa.employee.firstName) LIKE LOWER(CONCAT('%', :employeeName, '%')) AND sa.endDate >= CURRENT_DATE")
    Page<ShiftAssignment> findByEmployeeName(@Param("employeeName") String employeeName, Pageable pageable);

    @EntityGraph(attributePaths = {"employee", "shift"})
    @Query("SELECT sa FROM ShiftAssignment sa WHERE sa.endDate >= CURRENT_DATE")
    Page<ShiftAssignment> findAllAssignments(Pageable pageable);

}