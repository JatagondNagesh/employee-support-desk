package com.marlabs.desk.engine;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Connection settings for the python policy engine.
 *
 * <p>Validated at startup so a missing or mistyped timeout fails the boot instead of silently
 * binding to {@code 0}, which the request factory treats as "wait forever".
 */
@Validated
@ConfigurationProperties(prefix = "policy-engine")
public record PolicyEngineProperties(
        @NotBlank String baseUrl,
        @Positive int connectTimeoutMs,
        @Positive int readTimeoutMs) {
}
