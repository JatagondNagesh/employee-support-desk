package com.marlabs.desk.api.dto;

/** Counts across a batch. completed + failed always equals total. */
public record BatchSummary(int total, int completed, int failed) {
}
