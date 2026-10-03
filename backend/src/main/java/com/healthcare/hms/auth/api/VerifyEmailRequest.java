package com.healthcare.hms.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/verify-email} body.
 *
 * <p>The token arrives in a POST body and never as a query parameter (API.md section 5): a URL is
 * logged by proxies and browsers, a body is not. The emailed link therefore points at the web
 * application, which re-presents its token here.
 *
 * @param token the raw token from the link — 43 base64url characters for a 256-bit value, bounded
 *     at 64 so an oversized payload is rejected before it is ever hashed
 */
public record VerifyEmailRequest(
    @NotBlank(message = "Token is required.")
        @Size(max = 64, message = "Token must be at most 64 characters.")
        String token) {}
