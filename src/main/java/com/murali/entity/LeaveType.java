package com.murali.entity;

import jakarta.persistence.*;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "leave_types")
public class LeaveType {

    @Id
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "is_paid")
    private Boolean paid = true;

    @Column(name = "max_days_per_year", nullable = false)
    private Integer maxDaysPerYear;
}
