package com.marlabs.desk.error;

/** Caller identity missing or not in the caller directory. Maps to 401. */
public class UnknownCallerException extends RuntimeException {

    public UnknownCallerException(String message) {
        super(message);
    }
}
