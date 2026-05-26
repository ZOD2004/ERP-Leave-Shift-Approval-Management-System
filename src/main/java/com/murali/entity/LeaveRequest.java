package com.murali.entity;

import com.murali.entity.enums.LeaveSession;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "leave_requests")
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "duration_days", nullable = false, precision = 4, scale = 1)
    private BigDecimal durationDays;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(length = 30)
    private String status;

    @Column(name = "current_level")
    private Integer currentLevel = 1;

    private LocalDate createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_session", length = 20)
    private LeaveSession leaveSession;
}
