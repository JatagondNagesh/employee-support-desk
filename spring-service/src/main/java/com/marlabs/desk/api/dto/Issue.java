package com.marlabs.desk.api.dto;

/** A reason this item needs human review. */
public record Issue(String code, String detail) {
}
