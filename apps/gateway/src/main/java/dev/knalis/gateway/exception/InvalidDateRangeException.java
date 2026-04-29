package dev.knalis.gateway.exception;

public class InvalidDateRangeException extends IllegalArgumentException {

    public InvalidDateRangeException(String message) {
        super(message);
    }
}
