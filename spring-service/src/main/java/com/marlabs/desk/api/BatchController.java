package com.marlabs.desk.api;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marlabs.desk.api.dto.BatchMetadata;
import com.marlabs.desk.api.dto.BatchResponse;
import com.marlabs.desk.batch.BatchService;
import com.marlabs.desk.caller.Caller;
import com.marlabs.desk.caller.CallerDirectory;
import com.marlabs.desk.error.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Synchronous batch intake. Multipart plumbing only; the rules live in {@link BatchService}. */
@RestController
public class BatchController {

    private static final Logger log = LoggerFactory.getLogger(BatchController.class);

    private final CallerDirectory callers;
    private final BatchService batchService;
    private final ObjectMapper mapper;

    public BatchController(CallerDirectory callers, BatchService batchService, ObjectMapper mapper) {
        this.callers = callers;
        this.batchService = batchService;
        this.mapper = mapper;
    }

    @PostMapping(value = "/batches", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BatchResponse batch(
            @RequestHeader(value = "X-Caller-Id", required = false) String callerId,
            @RequestPart("metadata") String metadataJson,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {

        Caller caller = callers.require(callerId);
        BatchMetadata metadata;
        try {
            metadata = mapper.readValue(metadataJson, BatchMetadata.class);
        } catch (Exception e) {
            throw new BadRequestException("INVALID_METADATA", "metadata part is not valid JSON.");
        }
        log.info("batch_received caller={} batch_id={} items={}", caller.callerId(),
                metadata == null ? null : metadata.batchId(), files == null ? 0 : files.size());
        return batchService.process(caller, metadata, files);
    }
}
