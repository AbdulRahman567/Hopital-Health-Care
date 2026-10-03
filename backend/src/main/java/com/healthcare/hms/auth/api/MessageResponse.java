package com.healthcare.hms.auth.api;

/**
 * The {@code data} payload of an auth acknowledgement: one human-readable sentence.
 *
 * <p>Deliberately constant per endpoint. D9 requires the registration and resend responses to be
 * identical whether or not an account exists, so this payload must carry no variable content — no
 * tenant id, no user id, no "we sent it" vs "we did not". The sentence is what the UI shows; the
 * observable difference between a known and an unknown address is only the email itself.
 *
 * @param message text safe to display verbatim
 */
public record MessageResponse(String message) {}
