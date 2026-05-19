package com.murali.repository;

import com.murali.entity.LeaveRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    @Query(
            value = """
            SELECT generate_series(
                       GREATEST(lr.start_date, :startDate),
                       LEAST(lr.end_date, :endDate),
                       INTERVAL '1 day'
                   )::date
            FROM leave_requests lr
            WHERE lr.employee_id = :employeeId
              AND lr.status = :status
              AND lr.start_date <= :endDate
              AND lr.end_date >= :startDate
            ORDER BY 1
            """,
            nativeQuery = true
    )
    List<LocalDate> findApprovedLeaveDatesForEmployee(@Param("employeeId") Long employeeId,
            @Param("status") String status,@Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query(
            value = """
        SELECT COUNT(*) > 0
        FROM leave_requests lr
        WHERE lr.employee_id = :employeeId
          AND lr.status = :status
          AND :date BETWEEN lr.start_date AND lr.end_date
        """,
            nativeQuery = true
    )
    boolean isEmployeeOnApprovedLeave(@Param("employeeId") Long employeeId,
            @Param("status") String status,@Param("date") LocalDate date);

    @Query("SELECT l FROM LeaveRequest l WHERE l.employee.id IN :employeeIds " +
            "AND l.status = :status " +
            "AND l.startDate <= :endDate AND l.endDate >= :startDate")
    List<LeaveRequest> findApprovedLeavesForEmployeesInRange(
            @Param("employeeIds") List<Long> employeeIds,
            @Param("status") String status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);


    @Query("SELECT r FROM LeaveRequest r WHERE r.status = :status ORDER BY r.startDate ASC")
    List<LeaveRequest> findPendingRequests(@Param("status") String status, Pageable pageable);
    long countByStatus(String status);

    @Query("SELECT COUNT(r) FROM LeaveRequest r WHERE r.status = 'APPROVED' " +
            "AND :targetDate >= r.startDate AND :targetDate <= r.endDate")
    long countActiveLeavesForDate(@Param("targetDate") LocalDate targetDate);

    @EntityGraph(attributePaths = {"leaveType"})
    List<LeaveRequest> findByEmployeeIdOrderByStartDateDesc(Long employeeId);

    boolean existsByEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId,
            List<String> statuses,
            LocalDate newEndDate,
            LocalDate newStartDate
    );
    @Query("SELECT CASE WHEN COUNT(lr) > 0 THEN true ELSE false END FROM LeaveRequest lr " +
            "WHERE lr.employee.id = :employeeId " +
            "AND lr.status IN :activeStatuses " +
            "AND lr.startDate <= :endDate AND lr.endDate >= :startDate")
    boolean hasOverlappingLeave(
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("activeStatuses") List<String> activeStatuses
    );
}
