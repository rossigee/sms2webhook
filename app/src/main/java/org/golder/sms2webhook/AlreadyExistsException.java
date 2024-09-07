package org.golder.sms2webhook;

/**
 * Exception thrown when an item already exists.
 */
public class AlreadyExistsException extends Exception {

    /**
     * Constructs an AlreadyExistsException with the specified message.
     * 
     * @param message the detail message (which is saved for later retrieval by the {@link Exception#getMessage()} method).
     */
    public AlreadyExistsException(String message) {
        super(message);
    }
}
