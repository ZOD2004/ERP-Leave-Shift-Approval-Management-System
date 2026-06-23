package com.murali.repository;

import com.murali.entity.LeaveApprovalPolicy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveApprovalPolicyRepository extends JpaRepository<LeaveApprovalPolicy, Long> {
    LeaveApprovalPolicy findByName(String name);

    @EntityGraph(attributePaths = {"rules", "rules.requiredRole"})
    @Query("SELECT p FROM LeaveApprovalPolicy p")
    List<LeaveApprovalPolicy> findAllWithRules();

    @EntityGraph(attributePaths = {"rules", "rules.requiredRole"})
    @Query("SELECT p FROM LeaveApprovalPolicy p WHERE p.id = :id")
    Optional<LeaveApprovalPolicy> findByIdWithRules(@Param("id") Long id);
}
