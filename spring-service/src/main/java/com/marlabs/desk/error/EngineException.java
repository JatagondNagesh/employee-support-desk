package com.marlabs.desk.error;

/**
 * A dependency failure with a stable code and a safe message. Technical failures are kept
 * separate from policy findings and never degrade to a business outcome.
 */
public class EngineException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public EngineException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    /** HTTP status to use when this failure must fail the whole public call. */
    public int httpStatus() {
        return httpStatus;
    }
}
