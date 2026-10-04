package com.healthcare.hms.staff;

import com.healthcare.hms.authz.RequirePermission;
import com.healthcare.hms.common.api.ApiResponse;
import com.healthcare.hms.common.api.PageParams;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The staff read surface (API.md section 11), reached only by a caller holding {@code STAFF_VIEW}.
 *
 * <p>P6.4 ships it as the proof for the resource-policy framework rather than as the staff feature
 * &mdash; the full {@code /staff} surface, including create and invitations, is Phase 9's. Two
 * routes, both declared the same way every authorized route is (decision D3, P6.3): {@code
 * .authenticated()} in {@code SecurityConfig} says the route exists for a signed-in caller,
 * {@code @RequirePermission} on each handler says which code is needed, and {@link StaffService}
 * then decides which <i>rows* that caller may open.
 *
 * <p>The second layer is why a route can be reachable and still return 404: holding {@code
 * STAFF_VIEW} is the capability to use this route, not a blanket right to every row behind it. Both
 * denials carry the same "not found" answer (ADR-006).
 */
@RestController
@RequestMapping("/api/v1/staff")
public class StaffController {

  private final StaffService staffService;

  public StaffController(StaffService staffService) {
    this.staffService = staffService;
  }

  @GetMapping
  @RequirePermission("STAFF_VIEW")
  public ResponseEntity<ApiResponse<List<StaffResponse>>> list(
      @RequestParam(name = "page", required = false) Integer page,
      @RequestParam(name = "size", required = false) Integer size) {
    Page<StaffResponse> staff = staffService.list(PageParams.of(page, size));
    return ResponseEntity.ok(ApiResponse.paginated(staff));
  }

  @GetMapping("/{userId}")
  @RequirePermission("STAFF_VIEW")
  public ResponseEntity<ApiResponse<StaffResponse>> get(@PathVariable("userId") UUID userId) {
    return ResponseEntity.ok(ApiResponse.ok(staffService.get(userId)));
  }
}
