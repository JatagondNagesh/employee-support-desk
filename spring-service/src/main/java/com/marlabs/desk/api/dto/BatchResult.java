package com.marlabs.desk.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * One result per manifest entry. Present even for duplicates and failures.
 *
 * <p>On FAILED, extracted, fieldEvidence and policy are null and error carries a stable code.
 * reviewRequired is always true: this service never approves a claim.
 */
public record BatchResult(String documentId, String processingStatus, ExtractedFields extracted,
                          FieldEvidence fieldEvidence, AnswerResponse policy, boolean reviewRequired,
                          List<Issue> issues, String duplicateOf, ItemError error) {

    public static BatchResult completed(String documentId, DocumentResult document,
                                        List<Issue> issues, String duplicateOf) {
        return new BatchResult(documentId, "COMPLETED", document.extracted(),
                document.fieldEvidence(), document.policy(), true, issues, duplicateOf, null);
    }

    public static BatchResult failed(String documentId, List<Issue> issues, String duplicateOf,
                                     String code, String message) {
        return new BatchResult(documentId, "FAILED", null, null, null, true, issues, duplicateOf,
                new ItemError(code, message));
    }

    /** Summary helper only; not part of the wire contract. */
    @JsonIgnore
    public boolean isCompleted() {
        return "COMPLETED".equals(processingStatus);
    }
}
