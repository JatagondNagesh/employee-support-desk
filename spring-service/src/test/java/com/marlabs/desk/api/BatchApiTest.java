package com.marlabs.desk.api;

import java.nio.charset.StandardCharsets;

import com.marlabs.desk.TestEngineConfig;
import com.marlabs.desk.engine.FakePolicyEngineClient;
import com.marlabs.desk.error.EngineException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestEngineConfig.class)
class BatchApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    FakePolicyEngineClient engine;

    private static final byte[] REQUEST_01 =
            "Reference: CERT-101\nI request certification reimbursement of INR 18000.\n"
                    .getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void reset() {
        engine.failWith = null;
    }

    private static MockMultipartFile metadata(String json) {
        return new MockMultipartFile("metadata", "metadata.json", "application/json",
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("files", name, "text/plain", content);
    }

    private static MockMultipartHttpServletRequestBuilder batch() {
        return multipart("/batches");
    }

    @Test
    void rejectsAManifestLargerThanTheConfiguredCap() throws Exception {
        StringBuilder entries = new StringBuilder();
        MockMultipartHttpServletRequestBuilder request = batch();
        for (int i = 0; i < 26; i++) {
            String name = "doc-" + i + ".txt";
            entries.append(i == 0 ? "" : ",")
                    .append("{\"document_id\":\"doc-").append(i)
                    .append("\",\"filename\":\"").append(name).append("\"}");
            request = (MockMultipartHttpServletRequestBuilder) request.file(file(name, REQUEST_01));
        }
        String json = "{\"batch_id\":\"big-01\",\"as_of\":\"2026-09-21\",\"documents\":["
                + entries + "]}";

        request.file(metadata(json)).header("X-Caller-Id", "atlas-employee-01");
        mvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BATCH_TOO_LARGE"));
    }

    @Test
    void mixedBatchKeepsAResultForEveryItemInManifestOrder() throws Exception {
        String json = """
                {"batch_id":"demo-01","as_of":"2026-09-21","documents":[
                  {"document_id":"request-01","filename":"request-01.txt"},
                  {"document_id":"request-06","filename":"request-06.txt"},
                  {"document_id":"request-08","filename":"request-08.txt"}]}
                """;
        mvc.perform(batch().file(metadata(json))
                        .file(file("request-01.txt", REQUEST_01))
                        .file(file("request-06.txt", REQUEST_01))
                        .file(file("request-08.txt", new byte[0]))
                        .header("X-Caller-Id", "atlas-employee-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batch_id").value("demo-01"))
                .andExpect(jsonPath("$.summary.total").value(3))
                .andExpect(jsonPath("$.summary.completed").value(2))
                .andExpect(jsonPath("$.summary.failed").value(1))
                .andExpect(jsonPath("$.results[0].document_id").value("request-01"))
                .andExpect(jsonPath("$.results[0].processing_status").value("COMPLETED"))
                .andExpect(jsonPath("$.results[0].completed").doesNotExist())
                .andExpect(jsonPath("$.results[0].duplicate_of").doesNotExist())
                .andExpect(jsonPath("$.results[0].policy.status").value("ANSWERED"))
                .andExpect(jsonPath("$.results[0].review_required").value(true))
                .andExpect(jsonPath("$.results[0].error").doesNotExist())
                .andExpect(jsonPath("$.results[1].document_id").value("request-06"))
                .andExpect(jsonPath("$.results[1].processing_status").value("COMPLETED"))
                .andExpect(jsonPath("$.results[1].duplicate_of").value("request-01"))
                .andExpect(jsonPath("$.results[2].document_id").value("request-08"))
                .andExpect(jsonPath("$.results[2].processing_status").value("FAILED"))
                .andExpect(jsonPath("$.results[2].error.code").value("EMPTY_DOCUMENT"))
                .andExpect(jsonPath("$.results[2].review_required").value(true))
                .andExpect(jsonPath("$.results[2].policy").doesNotExist());
    }

    @Test
    void rejectsDuplicateManifestIdentifiers() throws Exception {
        String json = """
                {"batch_id":"demo-01","as_of":"2026-09-21","documents":[
                  {"document_id":"request-01","filename":"request-01.txt"},
                  {"document_id":"request-01","filename":"request-02.txt"}]}
                """;
        mvc.perform(batch().file(metadata(json))
                        .file(file("request-01.txt", REQUEST_01))
                        .file(file("request-02.txt", REQUEST_01))
                        .header("X-Caller-Id", "atlas-employee-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE_MANIFEST_IDENTIFIER"));
    }

    @Test
    void rejectsMissingFilePart() throws Exception {
        String json = """
                {"batch_id":"demo-01","as_of":"2026-09-21","documents":[
                  {"document_id":"request-01","filename":"request-01.txt"},
                  {"document_id":"request-03","filename":"request-03.txt"}]}
                """;
        mvc.perform(batch().file(metadata(json))
                        .file(file("request-01.txt", REQUEST_01))
                        .header("X-Caller-Id", "atlas-employee-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MISSING_FILE_PART"));
    }

    @Test
    void rejectsExtraFilePart() throws Exception {
        String json = """
                {"batch_id":"demo-01","as_of":"2026-09-21","documents":[
                  {"document_id":"request-01","filename":"request-01.txt"}]}
                """;
        mvc.perform(batch().file(metadata(json))
                        .file(file("request-01.txt", REQUEST_01))
                        .file(file("stray.txt", REQUEST_01))
                        .header("X-Caller-Id", "atlas-employee-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("UNEXPECTED_FILE_PART"));
    }

    @Test
    void rejectsInvalidMetadata() throws Exception {
        mvc.perform(batch().file(metadata("{not json"))
                        .file(file("request-01.txt", REQUEST_01))
                        .header("X-Caller-Id", "atlas-employee-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_METADATA"));
    }

    @Test
    void rejectsUnknownCallerBeforeProcessing() throws Exception {
        String json = """
                {"batch_id":"demo-01","as_of":"2026-09-21","documents":[
                  {"document_id":"request-01","filename":"request-01.txt"}]}
                """;
        mvc.perform(batch().file(metadata(json))
                        .file(file("request-01.txt", REQUEST_01))
                        .header("X-Caller-Id", "nobody-01"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void dependencyFailureFailsItemsButStillReturnsAResultForEach() throws Exception {
        engine.failWith = new EngineException("MODEL_TIMEOUT", "Model provider timed out.", 504);
        String json = """
                {"batch_id":"demo-01","as_of":"2026-09-21","documents":[
                  {"document_id":"request-01","filename":"request-01.txt"}]}
                """;
        mvc.perform(batch().file(metadata(json))
                        .file(file("request-01.txt", REQUEST_01))
                        .header("X-Caller-Id", "atlas-employee-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.failed").value(1))
                .andExpect(jsonPath("$.results[0].error.code").value("MODEL_TIMEOUT"));
    }
}
