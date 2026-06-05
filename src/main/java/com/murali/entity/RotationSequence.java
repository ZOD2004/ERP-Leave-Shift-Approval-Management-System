package com.murali.entity;

import com.murali.entity.enums.RotationSegmentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "rotation_sequences")
@Getter
@Setter
public class RotationSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private ShiftRotationPolicy policy;

    @Enumerated(EnumType.STRING)
    @Column(name = "segment_type", nullable = false)
    private RotationSegmentType segmentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private Shift shift;

    @Column(name = "sequence_order", nullable = false)
    private Integer sequenceOrder;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;
}