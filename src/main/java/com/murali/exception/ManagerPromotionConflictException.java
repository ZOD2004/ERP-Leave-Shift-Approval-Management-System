package com.murali.exception;

import lombok.Getter;

@Getter
public class ManagerPromotionConflictException extends RuntimeException {
    private final Long employeeId;

    public ManagerPromotionConflictException(String message, Long employeeId) {
        super(message);
        this.employeeId = employeeId;
    }
}
