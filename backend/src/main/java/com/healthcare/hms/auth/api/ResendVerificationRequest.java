package com.healthcare.hms.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/resend-verification} body (PRD FR-1.3 — required by the PRD but absent
 * from API.md section 11's indicative map; recorded in the P5.2 evidence).
 *
 * <p>{@code hospitalSlug} identifies <i>which</i> hospital, exactly as {@code login} does (decision
 * D2): email uniqueness is only per tenant, so email alone cannot name an account. It is a lookup
 * key, never a binding — the tenant id still comes from the {@code tenants} row (D1).
 *
 * @param email the address to resend to
 * @param hospitalSlug the hospital's public slug
 */
public record ResendVerificationRequest(
    @NotBlank(message = "Email is required.")
        @Email(message = "Email must be a valid address.")
        @Size(max = 320, message = "Email must be at most 320 characters.")
        String email,
    @NotBlank(message = "Hospital slug is required.")
        @Size(max = 63, message = "Hospital slug must be at most 63 characters.")
        String hospitalSlug) {}
