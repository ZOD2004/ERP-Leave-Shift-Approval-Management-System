package com.murali.exception;

import lombok.Getter;

@Getter
public class HodConflictException extends RuntimeException {
    private final Long departmentId;
    private final Long currentHodId;

    public HodConflictException(String message, Long departmentId, Long currentHodId) {
        super(message);
        this.departmentId = departmentId;
        this.currentHodId = currentHodId;
    }
}
