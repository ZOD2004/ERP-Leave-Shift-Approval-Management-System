package com.murali.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShiftAssignmentDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private Long shiftId;
    private String shiftName;
    private String shiftType;
    private LocalDate assignmentDate;

    private LocalTime startTime;
    private LocalTime endTime;
    private Boolean isOverride;

    private LocalTime overrideStartTime;
    private LocalTime overrideEndTime;
}
