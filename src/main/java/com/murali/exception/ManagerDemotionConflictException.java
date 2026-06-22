package com.murali.exception;

import lombok.Getter;

@Getter
public class ManagerDemotionConflictException extends RuntimeException {
    private final Long departmentId;
    public ManagerDemotionConflictException(String message, Long departmentId) {
        super(message);
        this.departmentId = departmentId;
    }
    public Long getDepartmentId() { return departmentId; }
}
