package com.marlabs.desk.api.dto;

import java.util.List;

/** Response of POST /batches. Results are in manifest order. */
public record BatchResponse(String batchId, BatchSummary summary, List<BatchResult> results) {

    public static BatchResponse of(String batchId, List<BatchResult> results) {
        int completed = (int) results.stream().filter(BatchResult::isCompleted).count();
        return new BatchResponse(batchId,
                new BatchSummary(results.size(), completed, results.size() - completed), results);
    }
}
