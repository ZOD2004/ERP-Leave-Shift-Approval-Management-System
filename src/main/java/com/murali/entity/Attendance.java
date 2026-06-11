package com.murali.entity;

import com.murali.entity.enums.AttendanceStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "attendance", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"employee_id", "attendance_date"})
})
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_assignment_id")
    private ShiftAssignment shiftAssignment;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    private LocalDateTime firstCheckIn; // changed from LocalDateTime checkIn;

    private LocalDateTime lastCheckOut; // changed from LocalDateTime checkIn; since multiple
    // in and out is possible and maintained via time log

    // newly added to see and compare with gracePeriod in shift
    private Integer totalWorkedMinutes;

    //added newly to cover multi in and out
    @OneToMany(mappedBy = "attendance", fetch = FetchType.LAZY)
    private List<TimeLog> timeLogs = new ArrayList<>();

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private AttendanceStatus status;

    // removed private Boolean isLate = false;
}

