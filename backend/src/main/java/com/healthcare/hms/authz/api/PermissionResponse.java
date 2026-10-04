package com.healthcare.hms.authz.api;

/**
 * One row of the permission catalog, served by {@code GET /api/v1/permissions}.
 *
 * <p>The values come from {@link com.healthcare.hms.authz.PermissionCatalog}, not from a table
 * round-trip: {@code PermissionCatalogTest} already proves the two are the same 53 rows in both
 * directions, and serving the code copy means the endpoint cannot answer with a row no role could
 * ever be granted.
 *
 * @param code the {@code MODULE_ACTION} code every {@code @RequirePermission} names
 * @param module first half of the code
 * @param action second half of the code
 * @param description human-readable text
 * @param platformOnly {@code true} for the two codes no tenant role may hold
 */
public record PermissionResponse(
    String code, String module, String action, String description, boolean platformOnly) {}
