package com.murali.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;

@ControllerAdvice
public class GlobalExceptionHandler extends Exception{

    @ExceptionHandler(UserAlreadyExistException.class)
    public ResponseEntity<ErrorDetails> UserAlreadyExistException(UserAlreadyExistException e, WebRequest request){
        ErrorDetails errorDetails = new ErrorDetails(e.getMessage(), request.getDescription(true), LocalDateTime.now());
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorDetails> UserNotFoundException(UserNotFoundException e, WebRequest request){
        ErrorDetails errorDetails = new ErrorDetails(e.getMessage(), request.getDescription(true), LocalDateTime.now());
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(LeaveTypeNotFoundException.class)
    public ResponseEntity<ErrorDetails> LeaveTypeNotFoundException(LeaveTypeNotFoundException e, WebRequest request){
        ErrorDetails errorDetails = new ErrorDetails(e.getMessage(), request.getDescription(true), LocalDateTime.now());
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<ErrorDetails> EmployeeNotFoundException(EmployeeNotFoundException e, WebRequest request){
        ErrorDetails errorDetails = new ErrorDetails(e.getMessage(), request.getDescription(true), LocalDateTime.now());
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ShiftNotFoundException.class)
    public ResponseEntity<ErrorDetails> ShiftNotFoundException(ShiftNotFoundException e, WebRequest request){
        ErrorDetails errorDetails = new ErrorDetails(e.getMessage(), request.getDescription(true), LocalDateTime.now());
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ShiftConflictException.class)
    public ResponseEntity<ErrorDetails> ShiftConflictException(ShiftConflictException e, WebRequest request){
        ErrorDetails errorDetails = new ErrorDetails(e.getMessage(), request.getDescription(true), LocalDateTime.now());
        return new ResponseEntity<>(errorDetails, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(PastDateException.class)
    public ResponseEntity<ErrorDetails> PastDateException(PastDateException e, WebRequest request){
        ErrorDetails errorDetails = new ErrorDetails(e.getMessage(), request.getDescription(true), LocalDateTime.now());
        return new ResponseEntity<>(errorDetails, HttpStatus.CONFLICT);
    }

}
