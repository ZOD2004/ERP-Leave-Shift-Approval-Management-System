package com.murali.entity;

import com.murali.entity.enums.LeaveSession;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;

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

    @Column(name = "created_at")
    @CreationTimestamp
    private LocalDate createdAt;

    //It was there like private LeaveSession leaveSession; now it can handle half day leave in multiple type
    @Enumerated(EnumType.STRING)
    @Column(name = "start_session", length = 20)
    private LeaveSession startSession = LeaveSession.FULL_DAY;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_session", length = 20)
    private LeaveSession endSession = LeaveSession.FULL_DAY;

    // added new suggested feature
    private Boolean isSandwichLeave;
}
