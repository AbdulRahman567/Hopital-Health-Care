package com.healthcare.hms.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/login} body (FR-2.1, API.md section 5).
 *
 * <p>The three fields a tenant-scoped account needs: the hospital picks the tenant (decision D1's
 * slug leg), the email picks the account <i>within</i> that tenant, and the password proves it.
 * Nothing else belongs here — no {@code tenantId}, no roles, no MFA code (D12 reserves the seam,
 * enforcement is a later phase).
 *
 * <p>{@code password} is length-bounded but never policy-checked at login: a stored password may
 * predate a policy change, and rejecting it with a 422 would tell the caller their password is now
 * too weak for an account they may not even own.
 *
 * @param hospitalSlug public slug of the hospital signing in
 * @param email address, unique per tenant ({@code uq_users_tenant_email})
 * @param password raw password; compared by hash, never logged
 */
public record LoginRequest(
    @NotBlank(message = "Hospital slug is required.")
        @Size(max = 63, message = "Hospital slug must be at most 63 characters.")
        String hospitalSlug,
    @NotBlank(message = "Email is required.")
        @Email(message = "Email must be a valid address.")
        @Size(max = 320, message = "Email must be at most 320 characters.")
        String email,
    @NotBlank(message = "Password is required.")
        @Size(max = 128, message = "Password must be at most 128 characters.")
        String password) {}
