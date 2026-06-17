package com.murali.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Entity
@Table(name = "shift_assignments")
public class ShiftAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    // instead of  LocalDate assignmentDate;
    // if emp has 7 days of work this new one result in one row the old resulted in 7 row
    // son converted n rows to one row
    private LocalDate startDate;
    private LocalDate endDate;

    //removed overrideStartTime,overrideEndTime,overrideApplied this was handled in shift itself
    // there was no need to have this since shift is divided into 2 session and leave can be based on that
}
