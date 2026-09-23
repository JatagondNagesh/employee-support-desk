package com.marlabs.desk.api.dto;

/** A stable code and a safe message. Never carries raw dependency output. */
public record ItemError(String code, String message) {
}
