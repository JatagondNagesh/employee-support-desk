package com.marlabs.desk.engine;

import java.time.LocalDate;

import com.marlabs.desk.api.dto.AnswerResponse;
import com.marlabs.desk.api.dto.DocumentResult;

/**
 * Transport to the python service. An interface so tests can substitute a controllable double
 * and exercise timeout, unavailable and malformed-output behaviour without a live dependency.
 */
public interface PolicyEngineClient {

    AnswerResponse answer(String tenant, String role, LocalDate asOf, String question);

    DocumentResult process(String tenant, String role, LocalDate asOf, String batchId,
                           String documentId, String filename, byte[] content);
}
