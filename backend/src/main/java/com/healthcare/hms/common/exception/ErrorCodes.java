package com.healthcare.hms.common.exception;

/** Standard error codes per API.md section 3. */
public final class ErrorCodes {

  public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
  public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
  public static final String TOKEN_EXPIRED = "TOKEN_EXPIRED";
  public static final String ACCESS_DENIED = "ACCESS_DENIED";
  public static final String NOT_FOUND = "NOT_FOUND";
  public static final String DUPLICATE_RESOURCE = "DUPLICATE_RESOURCE";
  public static final String SLOT_UNAVAILABLE = "SLOT_UNAVAILABLE";
  public static final String VERSION_CONFLICT = "VERSION_CONFLICT";
  public static final String RECORD_FINALIZED = "RECORD_FINALIZED";
  public static final String RATE_LIMITED = "RATE_LIMITED";

  /**
   * 409 for a resource that exists but cannot be removed yet — ROADMAP P6.2 / decision D5: a role
   * still held by one or more accounts. Additive in Phase 6 (the only new code this phase), one
   * line in API.md section 3 alongside it.
   */
  public static final String RESOURCE_IN_USE = "RESOURCE_IN_USE";

  public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
  public static final String FILE_TYPE_NOT_ALLOWED = "FILE_TYPE_NOT_ALLOWED";
  public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

  private ErrorCodes() {}
}
