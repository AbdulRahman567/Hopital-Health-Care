package com.healthcare.hms.staff;

/**
 * The staff read model (API.md section 6).
 *
 * <p>Deliberately a record of scalars rather than a {@code User}: ARCHITECTURE section 10 keeps
 * persistence entities out of controller signatures, and an entity crossing that boundary would
 * also carry {@code passwordHash}, {@code mfaSecret} and {@code failedAttempts} &mdash; fields no
 * response should ever be one getter away from exposing. Nothing here is masked, because nothing
 * clinical is here; field masking (P6.5) applies to the response types that earn it.
 *
 * @param id the account id, as a string for JSON
 * @param email the work address
 * @param firstName given name
 * @param lastName family name
 * @param status {@code PENDING}/{@code ACTIVE}/{@code SUSPENDED}/{@code DEACTIVATED}, as text
 */
public record StaffResponse(
    String id, String email, String firstName, String lastName, String status) {}
