package com.smsweb.sms.services.tc;

/**
 * The action is not allowed for the exit in its current state (for example approving an exit that
 * is already approved, or cancelling one after the TC was issued). The message is written for the user.
 */
public class InvalidExitStateException extends TcValidationException {

    public InvalidExitStateException(String message) {
        super(message);
    }
}
