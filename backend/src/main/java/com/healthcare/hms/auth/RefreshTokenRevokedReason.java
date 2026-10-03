package com.healthcare.hms.auth;

/**
 * Why a refresh token stopped working ({@code V4 refresh_tokens.revoked_reason}).
 *
 * <p>The set is a database CHECK constraint, not a free choice: a revocation is evidence, and
 * evidence is only useful if the reason is one of a fixed, queryable few (SECURITY section 10).
 * Deleting the row instead would lose the story of <i>why</i> a session ended, which is exactly
 * what reuse detection needs to tell apart from an ordinary logout.
 */
public enum RefreshTokenRevokedReason {

  /** Superseded by a rotation — the normal end of a token's life. */
  ROTATED,

  /** Presented after it had already been revoked: the family is poisoned and dies with it. */
  REUSED,

  /** The session was ended deliberately — logout, deactivation, or any other "stop now". */
  LOGOUT,

  /** Every session of the user ended because the password changed (P5.7). */
  PASSWORD_RESET
}
