package com.healthcare.hms.auth;

import com.healthcare.hms.auth.api.RefreshResponse;

/**
 * What a successful refresh produces — the same pairing as {@link LoginSession}, for the same
 * reason.
 *
 * @param response the access token and profile, safe to serialize
 * @param refresh the successor token, for {@code Set-Cookie} and nowhere else
 */
public record RefreshSession(RefreshResponse response, IssuedRefreshToken refresh) {}
