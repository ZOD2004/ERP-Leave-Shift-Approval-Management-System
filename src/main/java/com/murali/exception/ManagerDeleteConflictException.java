package com.murali.exception;

import lombok.Getter;

@Getter
public class ManagerDeleteConflictException extends RuntimeException {
    private final Long managerId;

    public ManagerDeleteConflictException(String message, Long managerId) {
        super(message);
        this.managerId = managerId;
    }
}
