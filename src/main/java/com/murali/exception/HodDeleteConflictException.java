package com.murali.exception;

import lombok.Getter;

@Getter
public class HodDeleteConflictException extends RuntimeException {
    private final Long employeeId;
    private final Long departmentId;

    public HodDeleteConflictException(String message, Long employeeId, Long departmentId) {
        super(message);
        this.employeeId = employeeId;
        this.departmentId = departmentId;
    }
}
