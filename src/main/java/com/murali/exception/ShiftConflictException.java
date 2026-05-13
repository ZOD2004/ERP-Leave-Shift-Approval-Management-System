package com.murali.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ShiftConflictException extends RuntimeException {
    public ShiftConflictException(String message) {
        super(message);
    }
}
