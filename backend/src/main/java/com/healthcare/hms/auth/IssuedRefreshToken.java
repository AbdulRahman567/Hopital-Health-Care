package com.healthcare.hms.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * A refresh token as the transport needs it: the value to hand the client, plus enough for the
 * caller to reason about it without reaching into the row.
 *
 * @param rawToken the secret itself — write it to the cookie and forget it; it is never persisted
 * @param familyId the login session this token belongs to
 * @param expiresAt when the token stops being presentable
 */
public record IssuedRefreshToken(String rawToken, UUID familyId, Instant expiresAt) {}
