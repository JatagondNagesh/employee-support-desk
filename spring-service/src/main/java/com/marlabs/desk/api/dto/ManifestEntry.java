package com.marlabs.desk.api.dto;

/** One manifest entry. Both values must be unique within a batch. */
public record ManifestEntry(String documentId, String filename) {
}
