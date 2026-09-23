package com.marlabs.desk.api.dto;

import java.util.List;

/** The metadata part of a POST /batches multipart request. */
public record BatchMetadata(String batchId, String asOf, List<ManifestEntry> documents) {
}
