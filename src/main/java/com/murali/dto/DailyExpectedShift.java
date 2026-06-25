package com.murali.dto;

import com.murali.entity.LeaveRequest;
import com.murali.entity.Shift;
import com.murali.entity.enums.LeaveSession;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyExpectedShift {
    private Long assignmentId;
    private LocalDate targetDate;
    private Long employeeId;

    private Shift expectedShift;

    private boolean isWorkingDay;
    private boolean isHoliday;
    private boolean isManualOverride;
    private boolean isIntentionalOffDay;

    private LeaveRequest activeLeave;
    private LeaveSession leaveSession;
}
