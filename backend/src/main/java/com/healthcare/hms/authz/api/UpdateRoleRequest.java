package com.healthcare.hms.authz.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of {@code PUT /api/v1/roles/{id}} — a full replace per API.md section 2, so both halves are
 * required rather than merged with whatever the row happens to hold.
 *
 * @param name the new display name
 * @param permissionCodes the complete new permission set
 */
public record UpdateRoleRequest(
    @NotBlank @Size(max = 100) String name, @NotNull List<String> permissionCodes) {}
