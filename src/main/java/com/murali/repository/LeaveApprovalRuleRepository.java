package com.murali.repository;

import com.murali.entity.LeaveApprovalRule;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface LeaveApprovalRuleRepository extends JpaRepository<LeaveApprovalRule, Long> {

    @EntityGraph(attributePaths = {"policy", "requiredRole"})
    List<LeaveApprovalRule> findAll();

    @EntityGraph(attributePaths = {"policy", "requiredRole"})
    @Query("SELECT r FROM LeaveApprovalRule r WHERE r.policy.id = :policyId " + "AND :duration >= r.minDays AND :duration <= r.maxDays " + "ORDER BY r.approvalLevel ASC")
    List<LeaveApprovalRule> findByPolicyAndDuration(@Param("policyId") Long policyId, @Param("duration") BigDecimal duration);
    @Modifying
    @Query("DELETE FROM LeaveApprovalRule r WHERE r.policy.id = :policyId")
    void deleteByPolicyId(@Param("policyId") Long policyId);
}
