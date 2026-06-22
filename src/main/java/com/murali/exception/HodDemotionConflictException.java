package com.murali.exception;

import lombok.Getter;

@Getter
public class HodDemotionConflictException extends RuntimeException {
    private final Long departmentId;
    public HodDemotionConflictException(String message, Long departmentId) {
        super(message);
        this.departmentId = departmentId;
    }
    public Long getDepartmentId() { return departmentId; }
}


