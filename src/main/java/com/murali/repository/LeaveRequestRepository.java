package com.murali.repository;

import com.murali.entity.LeaveRequest;
import com.murali.entity.LeaveType;
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
            "AND (l.startDate <= :endDate AND l.endDate >= :startDate)")
    List<LeaveRequest> findApprovedLeavesForEmployeesInRange(
            @Param("employeeIds") List<Long> employeeIds,
            @Param("status") String status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );


    @Query("SELECT r FROM LeaveRequest r WHERE r.status = :status ORDER BY r.startDate ASC")
    List<LeaveRequest> findPendingRequests(@Param("status") String status, Pageable pageable);
    long countByStatus(String status);

    @Query("SELECT COUNT(r) FROM LeaveRequest r WHERE r.status LIKE 'PENDING%'")
    long countPendingRequests();

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
            "AND (lr.status = 'APPROVED' OR lr.status LIKE 'PENDING%') " +
            "AND (lr.startDate <= :endDate AND lr.endDate >= :startDate)")
    boolean hasOverlappingLeave(
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
    @Query("SELECT CASE WHEN COUNT(lr) > 0 THEN true ELSE false END FROM LeaveRequest lr " +
            "WHERE lr.employee.id = :employeeId " +
            "AND (lr.status = 'APPROVED' OR lr.status LIKE 'PENDING%') " +
            "AND (lr.startDate <= :endDate AND lr.endDate >= :startDate) " +
            "AND (COALESCE(:ignoredIds, NULL) IS NULL OR lr.id NOT IN :ignoredIds)")
    boolean hasOverlappingLeaveIgnoring(
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("ignoredIds") List<Long> ignoredIds
    );
    @EntityGraph(attributePaths = {"leaveType"})
    @Query("SELECT lr FROM LeaveRequest lr " +
            "WHERE lr.employee.id = :employeeId " +
            "AND (lr.status = 'APPROVED' OR lr.status LIKE 'PENDING%') " +
            "AND ( (lr.startDate <= :endDate AND lr.endDate >= :startDate) " +
            "      OR (lr.endDate = :dayBefore OR lr.startDate = :dayAfter) )")
    List<LeaveRequest> findConflictsForMerge(
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("dayBefore") LocalDate dayBefore,
            @Param("dayAfter") LocalDate dayAfter
    );
    @EntityGraph(attributePaths = {"leaveType"})
    List<LeaveRequest> findByEmployeeIdAndStatusOrderByIdDesc(Long employeeId, String status);
    @Query("SELECT COUNT(lr) FROM LeaveRequest lr WHERE " +
            "(lr.createdAt BETWEEN :startDate AND :endDate) AND " +
            "lr.currentLevel >= 3 AND lr.status LIKE 'PENDING%'")
    long countEscalatedApprovals(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Query("SELECT lr FROM LeaveRequest lr " +
            "WHERE lr.employee.id = :employeeId " +
            "AND lr.status = 'APPROVED' " +
            "AND :targetDate BETWEEN lr.startDate AND lr.endDate")
    Optional<LeaveRequest> findApprovedLeaveForEmployeeOnDate(
            @Param("employeeId") Long employeeId,
            @Param("targetDate") LocalDate targetDate
    );


    @EntityGraph(attributePaths = {"leaveType"})
    @Query("SELECT lr FROM LeaveRequest lr WHERE lr.employee.id = :employeeId " +
            "AND (lr.status = 'APPROVED' OR lr.status LIKE 'PENDING%') " +
            "AND (lr.endDate = :dayBefore OR lr.startDate = :dayAfter)")
    List<LeaveRequest> findAdjacentLeaves(
            @Param("employeeId") Long employeeId,
            @Param("dayBefore") LocalDate dayBefore,
            @Param("dayAfter") LocalDate dayAfter
    );
}
