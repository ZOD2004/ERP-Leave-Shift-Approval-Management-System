package com.murali.dto;

import com.murali.entity.enums.LeaveSession;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyCellDTO {
    private LocalDate date;
    private Long employeeId;
    private String employeeName;

    private boolean isHoliday;
    private boolean isOffDay;

    private boolean isOnLeave;
    private LeaveSession leaveSession; // FULL_DAY, FIRST_HALF, SECOND_HALF

    private ShiftAssignmentDTO assignment; // Holds the shift if they are working
    private boolean isOvertimeOverride; // True if this is a 1-day manual assignment
}
