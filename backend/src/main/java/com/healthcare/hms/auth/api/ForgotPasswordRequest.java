package com.healthcare.hms.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/forgot-password} body (FR-2.3, API.md section 5).
 *
 * <p>Carries the slug rather than a tenant id, exactly as login does (decision D2): the slug is a
 * lookup key the caller can be expected to know, and an id would turn this anonymous endpoint into
 * a tenant-probing oracle.
 *
 * @param email administrator address to send the link to; never echoed back differently
 * @param hospitalSlug public slug of the hospital, so the address can be found within it
 */
public record ForgotPasswordRequest(
    @NotBlank(message = "Email is required.")
        @Email(message = "Email must be a valid address.")
        @Size(max = 320, message = "Email must be at most 320 characters.")
        String email,
    @NotBlank(message = "Hospital slug is required.")
        @Size(max = 63, message = "Hospital slug must be at most 63 characters.")
        String hospitalSlug) {}
