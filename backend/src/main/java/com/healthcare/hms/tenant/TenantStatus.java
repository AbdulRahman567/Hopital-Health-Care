package com.healthcare.hms.tenant;

/** Tenant lifecycle states (TDD section 9.2). Stored as a string + CHECK constraint. */
public enum TenantStatus {
  PENDING,
  ACTIVE,
  SUSPENDED
}
