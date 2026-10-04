package com.healthcare.hms.authz.api;

import com.healthcare.hms.authz.PermissionService;
import com.healthcare.hms.common.api.ApiResponse;
import com.healthcare.hms.common.api.PageParams;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/v1/permissions} — the catalog a custom role is built from (API.md section 11,
 * FR-3.5).
 *
 * <p>Gated by {@code ROLE_VIEW}, not by a {@code PERMISSION_*} code: decision D5 notes the frozen
 * 53-row seed has no such module and that growing it is exactly what decision D6 defers to the
 * phase that first needs a new field permission.
 */
@RestController
@RequestMapping("/api/v1/permissions")
public class PermissionsController {

  private final PermissionService permissionService;

  public PermissionsController(PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<PermissionResponse>>> list(
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "size", required = false) Integer size) {
    Page<PermissionResponse> permissions = permissionService.list(PageParams.of(page, size));
    return ResponseEntity.ok(ApiResponse.paginated(permissions));
  }
}
