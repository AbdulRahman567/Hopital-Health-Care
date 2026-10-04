package com.healthcare.hms.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/reset-password} body (FR-2.3, API.md section 5).
 *
 * <p>No email, no slug, no tenant id: the token <i>is</i> the credential and the D1 secret-leg
 * directory turns it into the tenant to bind. Accepting an address here as well would create a
 * second, weaker way to say which account is being changed.
 *
 * @param token the value from the emailed link, single-use and short-lived
 * @param newPassword replacement password; checked by {@code PasswordPolicy}, never logged
 */
public record ResetPasswordRequest(
    @NotBlank(message = "Token is required.")
        @Size(max = 128, message = "Token must be at most 128 characters.")
        String token,
    @NotBlank(message = "Password is required.")
        @Size(max = 128, message = "Password must be at most 128 characters.")
        String newPassword) {}
