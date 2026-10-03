package com.healthcare.hms.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/register-hospital} body (FR-1.1, API.md section 5).
 *
 * <p>No {@code tenantId} and no {@code slug}: the tenant id is server-generated and the slug is
 * derived from {@code hospitalName} (decision D1 — the bound id always comes from a database row,
 * never from the client). {@code fail-on-unknown-properties} rejects anything not declared here, so
 * ISO-7's injection surface stays structurally closed.
 *
 * @param hospitalName display name; also the source of the slug and of D9's 409
 * @param email administrator address, unique per tenant ({@code uq_users_tenant_email})
 * @param password checked by {@code PasswordPolicy}, never logged, stored only as a hash
 * @param firstName optional administrator first name
 * @param lastName optional administrator last name
 */
public record RegisterHospitalRequest(
    @NotBlank(message = "Hospital name is required.")
        @Size(max = 100, message = "Hospital name must be at most 100 characters.")
        String hospitalName,
    @NotBlank(message = "Email is required.")
        @Email(message = "Email must be a valid address.")
        @Size(max = 320, message = "Email must be at most 320 characters.")
        String email,
    @NotBlank(message = "Password is required.")
        @Size(max = 128, message = "Password must be at most 128 characters.")
        String password,
    @Size(max = 100, message = "First name must be at most 100 characters.") String firstName,
    @Size(max = 100, message = "Last name must be at most 100 characters.") String lastName) {}
