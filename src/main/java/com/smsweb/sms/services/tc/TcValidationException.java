package com.smsweb.sms.services.tc;

/**
 * A business-rule or input problem in the TC feature whose message is written for the user and
 * is safe to show on screen. Anything else (bugs, database errors) must NOT use this class.
 */
public class TcValidationException extends RuntimeException {

    public TcValidationException(String message) {
        super(message);
    }
}
