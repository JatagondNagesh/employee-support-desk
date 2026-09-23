package com.marlabs.desk.caller;

/**
 * An authenticated caller. For this exercise the X-Caller-Id header simulates authentication;
 * tenant and role always come from {@link CallerDirectory}, never from a request body, a
 * document, or model output.
 */
public record Caller(String callerId, String tenant, String role) {
}
