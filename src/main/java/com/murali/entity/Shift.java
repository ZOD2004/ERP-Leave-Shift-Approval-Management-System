package com.murali.entity;

import com.murali.entity.enums.Shifts;
import com.murali.entity.enums.WorkingDay;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "shifts")
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "shift_type", length = 20)
    @Enumerated(EnumType.STRING)
    private Shifts shiftType;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    // 4 column are new gracePeriod,crossesMidnight,firstHalfEndTime,secondHalfStartTime
    // grace period added so hr can choose the minimum working time for the shift
    // to see its night and help the @schedule annotation not to calculate this
    // to split 2 session for shift was handled in shift assignment which was wrong

    @Column(name = "min_required_work_time")
    private Long requiredWorkTime;

    private Boolean crossesMidnight = false;

    @Column(name = "first_half_end_time")
    private LocalTime firstHalfEndTime;

    @Column(name = "second_half_start_time")
    private LocalTime secondHalfStartTime;


    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "shift_working_days",
            joinColumns = @JoinColumn(name = "shift_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "working_day")
    private Set<WorkingDay> workingDays = new HashSet<>();
}
