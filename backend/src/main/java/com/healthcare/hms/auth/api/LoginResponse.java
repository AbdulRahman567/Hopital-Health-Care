package com.healthcare.hms.auth.api;

/**
 * Successful {@code POST /api/v1/auth/login} body: the access token and nothing else.
 *
 * <p>Deliberately not a profile (plan D10): the refresh response, which may carry {@code id},
 * {@code email}, names, {@code roles} and {@code tenantName}, is P5.5 and arrives there. Login
 * answers with the token so the SPA can start using it immediately, and the cookie that keeps the
 * session alive is set by a different endpoint entirely.
 *
 * @param accessToken compact, bearer-presentable JWT
 * @param tokenType always {@code "Bearer"} (API.md section 7)
 * @param expiresIn lifetime of {@code accessToken} in seconds — the caller should refresh before it
 *     elapses rather than treat it as a session length
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn) {}
