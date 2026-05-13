package com.murali.exception;

public class ShiftNotFoundException extends RuntimeException{
    public ShiftNotFoundException(String msg){
        super(msg);
    }
}
