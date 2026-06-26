package com.murali.repository;

import com.murali.entity.LeaveBalance;
import com.murali.entity.LeaveType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {
    @EntityGraph(attributePaths = {"leaveType"})
    List<LeaveBalance> findByEmployeeIdAndYear(Long employeeId, Integer year);
    @EntityGraph(attributePaths = {"leaveType"})
    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(Long employeeId, Long leaveTypeId, Integer year);

    @Query("SELECT COALESCE(SUM(lb.totalEntitled), 0), COALESCE(SUM(lb.used), 0) FROM LeaveBalance lb WHERE lb.year = :year")
    List<Object[]> getGlobalLeaveUtilization(@Param("year") Integer year);

    @Query("SELECT COUNT(lb) FROM LeaveBalance lb WHERE lb.leaveType.code = 'UPL-001' AND lb.used > 0 AND lb.year = :year")
    long countUsersWithUnpaidLeave(@Param("year") int year);

    @Query("SELECT SUM(lb.totalEntitled), SUM(lb.used) FROM LeaveBalance lb WHERE lb.year = :year")
    List<Object[]> getGlobalLeaveUtilizationStats(@Param("year") Integer year);
}

