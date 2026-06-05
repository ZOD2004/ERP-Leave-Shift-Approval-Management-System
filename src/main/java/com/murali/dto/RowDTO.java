package com.murali.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;


@Data
public class RowDTO {
    private String employeeName;
    private java.util.Map<LocalDate, DailyCellDTO> schedule = new java.util.HashMap<>();

    public RowDTO(String employeeName) { this.employeeName = employeeName; }
    public void addCell(LocalDate date, DailyCellDTO cell) { schedule.put(date, cell); }
    public DailyCellDTO getCellForDate(LocalDate date) { return schedule.get(date); }
}