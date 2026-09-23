package com.marlabs.desk.api.dto;

/** Source quotation backing each supported value in {@link ExtractedFields}. */
public record FieldEvidence(String benefit, String amount, String currency, String reference) {
}
