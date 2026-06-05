package com.murali.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class MonthlyRowDTO {
    private String employeeName;
    private java.util.Map<Integer, DailyCellDTO> schedule = new java.util.HashMap<>();

    public MonthlyRowDTO(String employeeName) { this.employeeName = employeeName; }
    public void addCell(int dayOfMonth, DailyCellDTO cell) { schedule.put(dayOfMonth, cell); }
    public DailyCellDTO getCellForDay(int dayOfMonth) { return schedule.get(dayOfMonth); }
}
