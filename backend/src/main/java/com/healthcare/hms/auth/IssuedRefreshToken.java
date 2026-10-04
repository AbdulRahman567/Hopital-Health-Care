package com.healthcare.hms.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * A refresh token as the transport needs it: the value to hand the client, plus enough for the
 * caller to reason about it without reaching into the row.
 *
 * <p>{@code userId} travels with it because the P5.5 refresh flow has to build the profile D10
 * promises in the response body, and the rotation that produced this record is the only step that
 * has already looked the account up (and refused it if it was gone). Carrying the id here avoids a
 * second decision about which tenant's user table to read.
 *
 * @param userId the account the session belongs to
 * @param rawToken the secret itself — write it to the cookie and forget it; it is never persisted
 * @param familyId the login session this token belongs to
 * @param expiresAt when the token stops being presentable
 */
public record IssuedRefreshToken(UUID userId, String rawToken, UUID familyId, Instant expiresAt) {}
