package com.murali.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class MonthlyPivotRowDTO {
    private String employeeName;
    private java.util.Map<Integer, String> schedule = new java.util.HashMap<>();

    public MonthlyPivotRowDTO(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getEmployeeName() { return employeeName; }

    public void addShift(int dayOfMonth, String shiftName) {
        String shortCode = (shiftName != null && !shiftName.isEmpty())
                ? shiftName.substring(0, 4)
                : "-";
        schedule.put(dayOfMonth, shortCode);
    }

    public String getShiftForDay(int dayOfMonth) {
        return schedule.getOrDefault(dayOfMonth, "-");
    }
}
