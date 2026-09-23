package com.marlabs.desk.engine;

import java.time.LocalDate;
import java.util.List;

import com.marlabs.desk.api.dto.AnswerResponse;
import com.marlabs.desk.api.dto.Citation;
import com.marlabs.desk.api.dto.DocumentResult;
import com.marlabs.desk.api.dto.ExtractedFields;
import com.marlabs.desk.api.dto.FieldEvidence;
import com.marlabs.desk.api.dto.Issue;
import com.marlabs.desk.error.EngineException;

/**
 * Controllable test double for the python service. Lets tests exercise timeout,
 * unavailable-provider and malformed-output behaviour without a live dependency.
 */
public class FakePolicyEngineClient implements PolicyEngineClient {

    public EngineException failWith;

    @Override
    public AnswerResponse answer(String tenant, String role, LocalDate asOf, String question) {
        if (failWith != null) {
            throw failWith;
        }
        return new AnswerResponse("ANSWERED",
                "The annual certification reimbursement limit for employees is INR 25000.",
                List.of(new Citation("atlas-cert-current",
                        "The annual certification reimbursement limit for employees is INR 25000.")));
    }

    @Override
    public DocumentResult process(String tenant, String role, LocalDate asOf, String batchId,
                                  String documentId, String filename, byte[] content) {
        if (failWith != null) {
            throw failWith;
        }
        if (content.length == 0) {
            throw new EngineException("EMPTY_DOCUMENT", "Document is empty.", 422);
        }
        return new DocumentResult(
                new ExtractedFields("certification", 18000.0, "INR", "CERT-101"),
                new FieldEvidence("certification reimbursement", "INR 18000", "INR 18000",
                        "Reference: CERT-101"),
                answer(tenant, role, asOf, "certification"),
                List.of(new Issue("ANNUAL_LIMIT_NOT_A_BALANCE",
                        "The annual limit does not establish a payable amount.")));
    }
}
