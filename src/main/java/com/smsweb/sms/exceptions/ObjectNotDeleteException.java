package com.smsweb.sms.exceptions;

public class ObjectNotDeleteException extends RuntimeException{
    public ObjectNotDeleteException(String message){
        super(message);
    }
    public ObjectNotDeleteException(String message, Throwable cause) {
        super(message, cause);
    }
}
