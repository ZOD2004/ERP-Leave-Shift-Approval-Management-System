package com.murali.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class RowDTO {
    private String employeeName;
    private java.util.Map<LocalDate, ShiftAssignmentDTO> schedule = new java.util.HashMap<>();

    public RowDTO(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getEmployeeName() { return employeeName; }

    public void addShift(LocalDate date, ShiftAssignmentDTO assignment) {
        schedule.put(date, assignment);
    }

    public ShiftAssignmentDTO getAssignmentForDate(LocalDate date) {
        return schedule.get(date);
    }
}