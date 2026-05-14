package com.murali.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ShiftConflictDTO {
    private Long employeeId;
    private String employeeName;
    private LocalDate conflictDate;
    private Long shiftId;
    private String shiftName;
    private String conflictType;

    // For Partial Conflicts (Half-Day Leaves)
    private LocalTime standardStartTime;
    private LocalTime standardEndTime;
    private String systemResolution;
    private LocalTime suggestedOverrideStart;
    private LocalTime suggestedOverrideEnd;

    public String getSystemResolution() { return systemResolution; }
    public void setSystemResolution(String systemResolution) { this.systemResolution = systemResolution; }

}
