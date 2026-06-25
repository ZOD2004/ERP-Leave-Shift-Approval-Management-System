package com.murali.entity;

import com.murali.entity.enums.RotationSegmentType;
import com.murali.entity.enums.Shifts;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "rotation_sequences")
@Getter
@Setter
public class RotationSequence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_shift_id", nullable = false)
    private Shift parentShift;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "segment_type", nullable = false)
    private RotationSegmentType segmentType;

    @Column(name = "shift_type", length = 20)
    @Enumerated(EnumType.STRING)
    private Shifts shiftType;

    @Column(name = "sequence_order", nullable = false)
    private Integer sequenceOrder;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "min_required_work_time")
    private Long requiredWorkTime;

    @Column(name = "crosses_midnight")
    private Boolean crossesMidnight = false;

    @Column(name = "first_half_end_time")
    private LocalTime firstHalfEndTime;

    @Column(name = "second_half_start_time")
    private LocalTime secondHalfStartTime;
}