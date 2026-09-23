package com.marlabs.desk.batch;

import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Batch intake limits.
 *
 * <p>{@code maxDocuments} bounds the work behind a single synchronous request. The multipart
 * limits cap bytes, not item count, and each item costs one call to the policy engine.
 */
@Validated
@ConfigurationProperties(prefix = "batch")
public record BatchProperties(@Positive int maxDocuments) {
}
