package com.smsweb.sms.exceptions;

public class UniqueConstraintsException extends RuntimeException{
    public UniqueConstraintsException(String message){
        super(message);
    }
    public UniqueConstraintsException(String message, Throwable cause){
        super(message, cause);
    }
}
