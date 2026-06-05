package com.murali.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LeaveDurationResultDTO {

    private BigDecimal netLeaveDays;
    private boolean isSandwichLeave;
    private BigDecimal actualWorkingDaysRequested;
}