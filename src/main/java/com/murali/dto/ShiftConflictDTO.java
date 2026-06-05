package com.murali.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShiftConflictDTO {

    private Long employeeId;
    private String employeeName;
    private LocalDate conflictDate;
    private Long shiftId;
    private String shiftName;
    private String conflictType;

}