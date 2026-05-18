package com.murali.repository;

import com.murali.entity.LeaveApprovalRule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LeaveApprovalRuleRepository extends JpaRepository<LeaveApprovalRule, Long> {

    @EntityGraph(attributePaths = {"leaveType", "requiredRole"})
    List<LeaveApprovalRule> findAll();

    @EntityGraph(attributePaths = {"leaveType", "requiredRole"})
    @Query("SELECT r FROM LeaveApprovalRule r WHERE r.leaveType.id = :leaveTypeId " +
            "AND :duration >= r.minDays AND :duration <= r.maxDays " +
            "ORDER BY r.approvalLevel ASC")
    List<LeaveApprovalRule> findApplicableRules(@Param("leaveTypeId") Long leaveTypeId,
                                                @Param("duration") BigDecimal duration);
}
