package com.marlabs.desk.support;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import com.marlabs.desk.error.BadRequestException;

/**
 * Parsing of the as_of request date, shared by both endpoints.
 *
 * <p>Lives outside the api package so that batch orchestration does not have to depend on a
 * controller for validation.
 */
public final class AsOfDate {

    private AsOfDate() {
    }

    public static LocalDate parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("INVALID_AS_OF", "as_of is required in YYYY-MM-DD format.");
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException("INVALID_AS_OF", "as_of must be a valid YYYY-MM-DD date.");
        }
    }
}
