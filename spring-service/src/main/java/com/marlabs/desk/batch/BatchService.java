package com.marlabs.desk.batch;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.marlabs.desk.api.dto.BatchMetadata;
import com.marlabs.desk.api.dto.BatchResponse;
import com.marlabs.desk.api.dto.BatchResult;
import com.marlabs.desk.api.dto.DocumentResult;
import com.marlabs.desk.api.dto.Issue;
import com.marlabs.desk.api.dto.ManifestEntry;
import com.marlabs.desk.caller.Caller;
import com.marlabs.desk.engine.PolicyEngineClient;
import com.marlabs.desk.error.EngineException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Batch orchestration.
 *
 * <p>The manifest is fully validated before any item is processed. Once the loop starts nothing
 * aborts it: each item is isolated, so one failure produces one FAILED result and the remaining
 * items continue. Every manifest entry yields exactly one result, in manifest order.
 */
@Service
public class BatchService {

    private static final Logger log = LoggerFactory.getLogger(BatchService.class);

    private static final Issue REVIEW_REQUIRED = new Issue("HUMAN_REVIEW_REQUIRED",
            "Every submitted request requires human review. This service does not approve claims, "
                    + "determine payable amounts, or contact employees.");

    private final PolicyEngineClient engine;
    private final BatchManifestValidator validator;

    public BatchService(PolicyEngineClient engine, BatchManifestValidator validator) {
        this.engine = engine;
        this.validator = validator;
    }

    public BatchResponse process(Caller caller, BatchMetadata metadata, List<MultipartFile> files) {
        LocalDate asOf = validator.validateMetadata(metadata);
        Map<String, MultipartFile> parts = validator.validateParts(metadata, files);

        List<BatchResult> results = new ArrayList<>();
        Map<String, String> seenDigests = new HashMap<>();

        for (ManifestEntry entry : metadata.documents()) {
            BatchResult result = processOne(caller, metadata, asOf, entry,
                    parts.get(entry.filename()), seenDigests);
            log.info("batch_item batch_id={} document_id={} status={} duplicate_of={} error={}",
                    metadata.batchId(), entry.documentId(), result.processingStatus(),
                    result.duplicateOf(), result.error() == null ? null : result.error().code());
            results.add(result);
        }

        return BatchResponse.of(metadata.batchId(), results);
    }

    private BatchResult processOne(Caller caller, BatchMetadata metadata, LocalDate asOf,
                                   ManifestEntry entry, MultipartFile file,
                                   Map<String, String> seenDigests) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            return failure(entry.documentId(), null, "FILE_READ_ERROR",
                    "Uploaded file could not be read.");
        }

        // An exact file-byte duplicate is still processed; only the link is reported.
        String duplicateOf = seenDigests.putIfAbsent(sha256(content), entry.documentId());

        try {
            DocumentResult document = engine.process(caller.tenant(), caller.role(), asOf,
                    metadata.batchId(), entry.documentId(), entry.filename(), content);
            List<Issue> issues = new ArrayList<>(
                    document.issues() == null ? List.of() : document.issues());
            addDuplicateIssue(issues, duplicateOf);
            return BatchResult.completed(entry.documentId(), document, issues, duplicateOf);
        } catch (EngineException e) {
            return failure(entry.documentId(), duplicateOf, e.code(), e.getMessage());
        }
    }

    private BatchResult failure(String documentId, String duplicateOf, String code, String message) {
        List<Issue> issues = new ArrayList<>();
        issues.add(new Issue(code, message));
        addDuplicateIssue(issues, duplicateOf);
        issues.add(REVIEW_REQUIRED);
        return BatchResult.failed(documentId, issues, duplicateOf, code, message);
    }

    private void addDuplicateIssue(List<Issue> issues, String duplicateOf) {
        if (duplicateOf != null) {
            issues.add(new Issue("EXACT_DUPLICATE",
                    "Byte-identical to earlier document " + duplicateOf + " in this batch."));
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required but unavailable", e);
        }
    }
}
