package com.marlabs.desk.api.dto;

import java.util.List;

/**
 * The answer contract, shared by POST /answer and the policy block of a batch result.
 *
 * <p>status is one of ANSWERED, INSUFFICIENT_EVIDENCE or CONFLICT. answer is null and citations
 * are empty for INSUFFICIENT_EVIDENCE; answer is null and citations show the disagreement for
 * CONFLICT.
 */
public record AnswerResponse(String status, String answer, List<Citation> citations) {
}
