package com.healthcare.hms.authz.api;

import java.time.Instant;
import java.util.List;

/**
 * A role as the API sees it (API.md section 6: no entities cross the wire, TDD section 5's {@code
 * api &rarr; service} boundary).
 *
 * @param id the role's UUID
 * @param name display name, unique inside the tenant
 * @param system {@code true} for a bundle {@link com.healthcare.hms.authz.SystemRoleProvisioner}
 *     wrote: name immutable, not deletable, permission set read-only
 * @param permissionCodes the catalog codes this role grants, sorted
 * @param createdAt creation instant
 * @param updatedAt last modification instant
 * @param version optimistic-lock version for conditional updates
 */
public record RoleResponse(
    String id,
    String name,
    boolean system,
    List<String> permissionCodes,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
