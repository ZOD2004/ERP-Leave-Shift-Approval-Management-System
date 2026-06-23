package com.murali.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "leave_types")
@Getter
@Setter
public class LeaveType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "is_paid")
    private Boolean paid = true;

    @Column(name = "max_days_per_year", nullable = false)
    private Integer maxDaysPerYear;

    @Column(name = "apply_sandwich_rule", nullable = false)
    private Boolean applySandwichRule = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_policy_id", nullable = false)
    private LeaveApprovalPolicy approvalPolicy;
}
