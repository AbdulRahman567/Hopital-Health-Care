package com.healthcare.hms.common.api;

/**
 * A single field-level validation violation naming the exact offending field per ENGINEERING_RULES
 * section 6.
 *
 * @param field dotted field path, e.g. {@code phone} or {@code address.city}
 * @param message human-readable validation message
 */
public record FieldViolation(String field, String message) {}
