package org.example.order.exception;

public class CannotModifyOrderException extends RuntimeException {
    public CannotModifyOrderException(String message) {
        super(message);
    }
}
