package com.murali.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class MonthlyRowDTO {
    private String employeeName;
    private java.util.Map<Integer, ShiftAssignmentDTO> schedule = new java.util.HashMap<>();

    public MonthlyRowDTO(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getEmployeeName() { return employeeName; }

    public void addShift(int dayOfMonth, ShiftAssignmentDTO assignment) {
        schedule.put(dayOfMonth, assignment);
    }

    public ShiftAssignmentDTO getAssignmentForDay(int dayOfMonth) {
        return schedule.get(dayOfMonth);
    }
}
