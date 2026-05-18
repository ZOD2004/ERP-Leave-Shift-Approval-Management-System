package com.murali.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(
        name = "leave_balances",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_leave_balances",
                        columnNames = {
                                "employee_id",
                                "leave_type_id",
                                "year"
                        }
                )
        }
)
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false)
    private Integer year;

    @Column(name = "used_days", precision = 5, scale = 1)
    private BigDecimal used = BigDecimal.ZERO;

    @Column(name = "total_entitled", nullable = false, precision = 5, scale = 1)
    private BigDecimal totalEntitled;

    @Column(name = "pending_days", precision = 5, scale = 1)
    private BigDecimal pendingDays = BigDecimal.ZERO;
}
