package com.smsweb.sms.services.tc;

/**
 * The dues estimate could not be worked out (missing fee date, fee-class map, fine setting, and so
 * on). The message is written for the user. Strike-off is blocked until an admin enters the dues by
 * hand. Extends {@link TcValidationException} so a controller that handles that one handles this too.
 */
public class DuesCalculationException extends TcValidationException {

    public DuesCalculationException(String message) {
        super(message);
    }
}
