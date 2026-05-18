package com.murali.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(
        name = "leave_approval_rules",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_leave_approval_rules",
                        columnNames = {
                                "leave_type_id",
                                "min_days",
                                "max_days",
                                "approval_level"
                        }
                )
        }
)
public class LeaveApprovalRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id",nullable = false)
    private LeaveType leaveType;

    @Column(name = "min_days", nullable = false, precision = 3, scale = 1)
    private BigDecimal minDays;

    @Column(name = "max_days", nullable = false, precision = 3, scale = 1)
    private BigDecimal maxDays;

    @Column(name = "approval_level", nullable = false)
    private Integer approvalLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "required_role_id",nullable = false)
    private Role requiredRole;

}