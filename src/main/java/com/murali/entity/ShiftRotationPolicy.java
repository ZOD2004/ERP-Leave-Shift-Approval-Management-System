package com.murali.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shift_rotation_policies")
@Getter
@Setter
public class ShiftRotationPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "is_active")
    private Boolean active = true;

    @Column(name = "generated_until")
    private LocalDate generatedUntil;

    @OneToMany(mappedBy = "policy", fetch = FetchType.LAZY,cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceOrder ASC")
    private List<RotationSequence> sequences = new ArrayList<>();
}
