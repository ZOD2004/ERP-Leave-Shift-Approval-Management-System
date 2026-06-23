package com.murali.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(
        name = "leave_approval_rules",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_policy_days_level",
                        columnNames = { "policy_id", "min_days", "max_days", "approval_level" }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LeaveApprovalRule {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private LeaveApprovalPolicy policy;

    @Column(name = "min_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal minDays;

    @Column(name = "max_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal maxDays;

    @Column(name = "approval_level", nullable = false)
    private Integer approvalLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "required_role_id", nullable = false)
    private Role requiredRole;
}