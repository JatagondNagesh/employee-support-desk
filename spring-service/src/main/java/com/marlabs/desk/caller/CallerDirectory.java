package com.marlabs.desk.caller;

import java.util.Map;
import java.util.Optional;

import com.marlabs.desk.error.UnknownCallerException;

import org.springframework.stereotype.Component;

/** The single source of caller tenant and role. */
@Component
public class CallerDirectory {

    private static final Map<String, Caller> CALLERS = Map.of(
            "atlas-employee-01", new Caller("atlas-employee-01", "Atlas", "employee"),
            "atlas-contractor-01", new Caller("atlas-contractor-01", "Atlas", "contractor"),
            "boreal-employee-01", new Caller("boreal-employee-01", "Boreal", "employee"));

    public Optional<Caller> find(String callerId) {
        if (callerId == null || callerId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(CALLERS.get(callerId.trim()));
    }

    public Caller require(String callerId) {
        return find(callerId).orElseThrow(() -> new UnknownCallerException(
                callerId == null || callerId.isBlank()
                        ? "X-Caller-Id header is required."
                        : "Unknown caller."));
    }
}
