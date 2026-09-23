package com.marlabs.desk.batch;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.marlabs.desk.api.dto.BatchMetadata;
import com.marlabs.desk.api.dto.ManifestEntry;
import com.marlabs.desk.error.BadRequestException;import com.marlabs.desk.support.AsOfDate;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * Manifest and file-part validation.
 *
 * <p>Every check here fails the whole batch with a 400 before any item is processed: an oversized
 * manifest, invalid metadata, duplicate identifiers, and missing or extra file parts are request
 * errors, not item-level outcomes.
 */
@Component
public class BatchManifestValidator {

    private final BatchProperties properties;

    public BatchManifestValidator(BatchProperties properties) {
        this.properties = properties;
    }

    /** Validates the manifest and returns the parsed as_of date. */
    public LocalDate validateMetadata(BatchMetadata metadata) {
        if (metadata == null || metadata.batchId() == null || metadata.batchId().isBlank()) {
            throw new BadRequestException("INVALID_METADATA", "batch_id is required.");
        }
        if (metadata.documents() == null || metadata.documents().isEmpty()) {
            throw new BadRequestException("INVALID_METADATA",
                    "documents must contain at least one entry.");
        }
        // Each entry costs one engine call inside this one synchronous request.
        if (metadata.documents().size() > properties.maxDocuments()) {
            throw new BadRequestException("BATCH_TOO_LARGE",
                    "a batch may contain at most " + properties.maxDocuments() + " documents.");
        }
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (ManifestEntry entry : metadata.documents()) {
            if (entry.documentId() == null || entry.documentId().isBlank()
                    || entry.filename() == null || entry.filename().isBlank()) {
                throw new BadRequestException("INVALID_METADATA",
                        "each manifest entry needs document_id and filename.");
            }
            if (!ids.add(entry.documentId()) || !names.add(entry.filename())) {
                throw new BadRequestException("DUPLICATE_MANIFEST_IDENTIFIER",
                        "document_id and filename must be unique within the manifest.");
            }
        }
        return AsOfDate.parse(metadata.asOf());
    }

    /** Checks that manifest entries and uploaded parts correspond exactly, one to one. */
    public Map<String, MultipartFile> validateParts(BatchMetadata metadata,
                                                    List<MultipartFile> files) {
        Map<String, MultipartFile> byName = new HashMap<>();
        if (files != null) {
            for (MultipartFile file : files) {
                String name = file.getOriginalFilename();
                if (name == null || name.isBlank()) {
                    throw new BadRequestException("INVALID_FILE_PART",
                            "each file part needs a filename.");
                }
                if (byName.put(name, file) != null) {
                    throw new BadRequestException("DUPLICATE_FILE_PART",
                            "file part " + name + " was uploaded more than once.");
                }
            }
        }
        Set<String> manifestNames = new HashSet<>();
        for (ManifestEntry entry : metadata.documents()) {
            manifestNames.add(entry.filename());
            if (!byName.containsKey(entry.filename())) {
                throw new BadRequestException("MISSING_FILE_PART",
                        "no uploaded file matches manifest filename " + entry.filename() + ".");
            }
        }
        for (String uploaded : byName.keySet()) {
            if (!manifestNames.contains(uploaded)) {
                throw new BadRequestException("UNEXPECTED_FILE_PART",
                        "uploaded file " + uploaded + " has no manifest entry.");
            }
        }
        return byName;
    }
}
