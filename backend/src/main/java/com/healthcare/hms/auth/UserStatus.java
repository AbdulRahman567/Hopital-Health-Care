package com.healthcare.hms.auth;

/**
 * Account lifecycle states for {@code users}. Values are fixed by {@code
 * V1__tenants_and_users.sql}: PENDING (invited, not yet usable), ACTIVE, INACTIVE (deactivated via
 * STAFF_DEACTIVATE). Account lockout is a separate concern driven by {@code locked_until}.
 */
public enum UserStatus {
  PENDING,
  ACTIVE,
  INACTIVE
}
