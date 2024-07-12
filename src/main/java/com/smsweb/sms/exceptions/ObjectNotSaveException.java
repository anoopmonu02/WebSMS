package com.smsweb.sms.exceptions;

public class ObjectNotSaveException extends RuntimeException{
    public ObjectNotSaveException(String message){
        super(message);
    }
    public ObjectNotSaveException(String message, Throwable cause){
        super(message, cause);
    }
}
