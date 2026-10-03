package com.healthcare.hms.auth.api;

/**
 * Successful {@code POST /api/v1/auth/refresh} body: a fresh access token plus the profile decision
 * D10 asks for, and nothing else.
 *
 * <p>The refresh token itself is deliberately <b>not</b> here. It travels only in the {@code
 * hms_refresh} cookie set on this same response, and repeating it in JSON would hand it to any
 * script that can read a response body — undoing the {@code HttpOnly} flag in the same round trip.
 *
 * <p>Carrying the profile is what lets the SPA re-hydrate after a reload: the access token lives in
 * memory only (ADR-003), so after F5 the cookie is the only thing the browser still holds, and this
 * body is enough to render a signed-in session without inventing a {@code GET /auth/me} endpoint
 * that API.md section 11 does not list.
 *
 * @param accessToken compact, bearer-presentable JWT
 * @param tokenType always {@code "Bearer"} (API.md section 7)
 * @param expiresIn lifetime of {@code accessToken} in seconds
 * @param profile who is signed in — see {@link SessionProfile}
 */
public record RefreshResponse(
    String accessToken, String tokenType, long expiresIn, SessionProfile profile) {}
