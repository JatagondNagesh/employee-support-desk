package com.marlabs.desk.api.dto;

/** Error response body for every non-2xx public response. */
public record ErrorBody(ItemError error) {

    public static ErrorBody of(String code, String message) {
        return new ErrorBody(new ItemError(code, message));
    }
}
