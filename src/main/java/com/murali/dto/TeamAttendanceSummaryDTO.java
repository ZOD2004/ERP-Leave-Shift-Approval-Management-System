package com.murali.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamAttendanceSummaryDTO {
    private int presentCount;
    private int lateCount;
    private int absentCount;
}
