package com.marlabs.desk.api;

import com.marlabs.desk.TestEngineConfig;
import com.marlabs.desk.engine.FakePolicyEngineClient;
import com.marlabs.desk.error.EngineException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestEngineConfig.class)
class AnswerApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    FakePolicyEngineClient engine;

    private static final String BODY =
            "{\"question\":\"What is my annual certification reimbursement limit?\",\"as_of\":\"2026-09-21\"}";

    @BeforeEach
    void reset() {
        engine.failWith = null;
    }

    @Test
    void answersForAKnownCaller() throws Exception {
        mvc.perform(post("/answer").header("X-Caller-Id", "atlas-employee-01")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ANSWERED"))
                .andExpect(jsonPath("$.citations[0].chunk_id").value("atlas-cert-current"))
                .andExpect(jsonPath("$.citations[0].quote").exists());
    }

    @Test
    void rejectsMissingCaller() throws Exception {
        mvc.perform(post("/answer").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNKNOWN_CALLER"));
    }

    @Test
    void rejectsUnknownCaller() throws Exception {
        mvc.perform(post("/answer").header("X-Caller-Id", "nobody-01")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsEmptyQuestion() throws Exception {
        mvc.perform(post("/answer").header("X-Caller-Id", "atlas-employee-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"  \",\"as_of\":\"2026-09-21\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_QUESTION"));
    }

    @Test
    void rejectsInvalidAsOf() throws Exception {
        mvc.perform(post("/answer").header("X-Caller-Id", "atlas-employee-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"limit?\",\"as_of\":\"21-09-2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_AS_OF"));
    }

    @Test
    void dependencyTimeoutIsNon2xxAndNotInsufficientEvidence() throws Exception {
        engine.failWith = new EngineException("MODEL_TIMEOUT", "Model provider timed out.", 504);
        mvc.perform(post("/answer").header("X-Caller-Id", "atlas-employee-01")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error.code").value("MODEL_TIMEOUT"));
    }

    @Test
    void unavailableProviderIsNon2xx() throws Exception {
        engine.failWith = new EngineException("MODEL_UNAVAILABLE", "Model provider is unavailable.", 503);
        mvc.perform(post("/answer").header("X-Caller-Id", "atlas-employee-01")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void malformedModelOutputIsNon2xx() throws Exception {
        engine.failWith = new EngineException("MODEL_MALFORMED_OUTPUT", "Model returned unusable output.", 502);
        mvc.perform(post("/answer").header("X-Caller-Id", "atlas-employee-01")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("MODEL_MALFORMED_OUTPUT"));
    }
}
