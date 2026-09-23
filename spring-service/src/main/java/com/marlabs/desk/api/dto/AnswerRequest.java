package com.marlabs.desk.api.dto;

/** Request body of POST /answer. Caller identity is never read from here. */
public record AnswerRequest(String question, String asOf) {
}
