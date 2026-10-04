package com.healthcare.hms.authz.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of {@code POST /api/v1/roles} (API.md section 5: DTO validation at the edge, unknown
 * properties rejected).
 *
 * <p>{@code tenantId} and {@code systemFlag} are deliberately absent from this record: the tenant
 * comes from the token and a system role cannot be created through the API at all, so neither is
 * something a client could send even if the mapper let it ({@code fail-on-unknown-properties} turns
 * the attempt into a 422).
 *
 * @param name display name, unique inside the tenant
 * @param permissionCodes catalog codes to grant; may be empty, never {@code null}
 */
public record CreateRoleRequest(
    @NotBlank @Size(max = 100) String name, @NotNull List<String> permissionCodes) {}
