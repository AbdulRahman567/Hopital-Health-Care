package com.healthcare.hms.authz.api;

import com.healthcare.hms.authz.RequirePermission;
import com.healthcare.hms.authz.RoleService;
import com.healthcare.hms.common.api.ApiResponse;
import com.healthcare.hms.common.api.PageParams;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The first authorized surface of the application (ROADMAP P6.2, API.md section 11): roles CRUD
 * behind {@code /api/v1/roles}, every route {@code .authenticated()} in {@code SecurityConfig} and
 * every method declaring its own code with {@code @RequirePermission} (decision D3, P6.3), so the
 * route being reachable and the caller being allowed are decided in two separate places.
 *
 * <p>Status codes follow API.md section 2 exactly: 201 + {@code Location} on create, 200 on read
 * and update, 204 on delete. Errors are the standard envelope &mdash; 404 for a foreign or unknown
 * id (ADR-006), 409 for a duplicate name, a system role or a role still assigned, 422 for a code
 * that is not in the catalog, is platform-only, or exceeds the caller's own grant.
 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

  private final RoleService roleService;

  public RoleController(RoleService roleService) {
    this.roleService = roleService;
  }

  @GetMapping
  @RequirePermission("ROLE_VIEW")
  public ResponseEntity<ApiResponse<List<RoleResponse>>> list(
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "size", required = false) Integer size) {
    Page<RoleResponse> roles = roleService.list(PageParams.of(page, size));
    return ResponseEntity.ok(ApiResponse.paginated(roles));
  }

  @GetMapping("/{roleId}")
  @RequirePermission("ROLE_VIEW")
  public ResponseEntity<ApiResponse<RoleResponse>> get(@PathVariable("roleId") UUID roleId) {
    return ResponseEntity.ok(ApiResponse.ok(roleService.get(roleId)));
  }

  @PostMapping
  @RequirePermission("ROLE_CREATE")
  public ResponseEntity<ApiResponse<RoleResponse>> create(
      @Valid @RequestBody CreateRoleRequest request) {
    RoleResponse created = roleService.create(request);
    return ResponseEntity.created(URI.create("/api/v1/roles/" + created.id()))
        .body(ApiResponse.ok(created));
  }

  @PutMapping("/{roleId}")
  @RequirePermission("ROLE_UPDATE")
  public ResponseEntity<ApiResponse<RoleResponse>> update(
      @PathVariable("roleId") UUID roleId, @Valid @RequestBody UpdateRoleRequest request) {
    return ResponseEntity.ok(ApiResponse.ok(roleService.update(roleId, request)));
  }

  @DeleteMapping("/{roleId}")
  @RequirePermission("ROLE_DELETE")
  public ResponseEntity<Void> delete(@PathVariable("roleId") UUID roleId) {
    roleService.delete(roleId);
    return ResponseEntity.noContent().build();
  }
}
