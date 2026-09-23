package com.marlabs.desk.api.dto;

import java.util.List;

/** Response of the python /documents/process endpoint for a single document. */
public record DocumentResult(ExtractedFields extracted, FieldEvidence fieldEvidence,
                             AnswerResponse policy, List<Issue> issues) {
}
