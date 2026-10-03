package com.healthcare.hms.auth;

/**
 * What an emailed link authorises. Stored as a string with a {@code CHECK} constraint ({@code V4} /
 * {@code chk_verification_tokens_type}).
 *
 * <p>{@link #INVITE} is reserved for P9.3 (invitation acceptance) and is never written before it.
 */
public enum VerificationTokenType {
  VERIFY_EMAIL,
  PASSWORD_RESET,
  INVITE
}
