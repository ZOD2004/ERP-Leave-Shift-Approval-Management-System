package com.murali.exception;

public class PastDateException extends RuntimeException{
    public PastDateException(String msg){
        super(msg);
    }
}
