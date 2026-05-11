package com.murali.exception;

public class LeaveTypeNotFoundException extends RuntimeException{
    public LeaveTypeNotFoundException(String msg){
        super(msg);
    }
}
