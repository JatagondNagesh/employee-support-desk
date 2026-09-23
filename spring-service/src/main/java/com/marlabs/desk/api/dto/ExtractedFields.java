package com.marlabs.desk.api.dto;

/** Values read from a submitted document. Null means missing or unresolved, never guessed. */
public record ExtractedFields(String benefit, Double amount, String currency, String reference) {
}
