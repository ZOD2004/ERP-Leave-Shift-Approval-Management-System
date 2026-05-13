package com.murali.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.Map;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PivotRowDTO {
    private String employeeName;
    private java.util.Map<LocalDate, String> schedule = new java.util.HashMap<>();

    public PivotRowDTO(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getEmployeeName() { return employeeName; }

    public void addShift(LocalDate date, String shiftName) {
        schedule.put(date, shiftName);
    }

    public String getShiftForDate(LocalDate date) {
        return schedule.getOrDefault(date, "-");
    }
}