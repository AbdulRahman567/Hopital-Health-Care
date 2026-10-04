package com.healthcare.hms.staff;

import com.healthcare.hms.auth.User;
import com.healthcare.hms.auth.repository.UserRepository;
import com.healthcare.hms.authz.policy.ResourcePolicies;
import com.healthcare.hms.common.api.PageParams;
import com.healthcare.hms.common.exception.NotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads of {@code users} that carry a resource policy (ROADMAP P6.4, decision D7) &mdash; the
 * "service that already has a reason to read users", and the place both halves of the framework are
 * actually enforced.
 *
 * <p><b>Record level.</b> {@link #get} loads the row and then asks {@link UserSelfOrStaffPolicy}
 * whether this caller may see it. The order matters: the repository answers "does this row exist
 * <i>in this tenant</i>" (the {@code @TenantId} filter, which is already a 404 for anything
 * foreign), and the policy answers "is this caller allowed to read it". Both denials are {@link
 * NotFoundException}, so an id that is foreign, one that is missing and one that is simply not the
 * caller's are byte-for-byte indistinguishable (ADR-006, API.md section 2).
 *
 * <p><b>List level.</b> {@link #list} hands {@code ResourcePolicies.readPredicate(...)} to the
 * repository together with the page request, so the database removes denied rows <i>before</i> it
 * cuts the page. That is TDD section 8.3's rule, and it is observable: a post-filter over a page of
 * 10 rows that half denies would return a short page with a total that still counts the hidden
 * rows, while this returns a full page and a total of what was visible. {@code ResourcePolicyTest}
 * asserts exactly that difference.
 *
 * <p><b>Module note.</b> {@code ARCHITECTURE} section 3 lets {@code staff} depend on {@code auth},
 * and this class does so through {@code UserRepository} rather than through an {@code auth} service
 * facade: Phase 6 ships a two-route slice of the staff surface, and the facade that {@code staff}
 * will own proper is Phase 9's {@code /staff} + invitations work. The dependency is one-way and is
 * recorded in the module map that {@code EndpointPermissionArchUnitTest} enforces.
 */
@Service
public class StaffService {

  private final UserRepository userRepository;
  private final ResourcePolicies resourcePolicies;

  public StaffService(UserRepository userRepository, ResourcePolicies resourcePolicies) {
    this.userRepository = userRepository;
    this.resourcePolicies = resourcePolicies;
  }

  /**
   * One account, or 404 — for an id that does not exist, belongs to another tenant, or belongs to
   * this tenant but is not the caller's to read.
   */
  @Transactional(readOnly = true)
  public StaffResponse get(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new NotFoundException("Staff member not found."));
    resourcePolicies.requireRead(User.class, user);
    return toResponse(user);
  }

  /**
   * One page of the accounts this caller may list, filtered in SQL by the policy's predicate.
   *
   * <p>Email-ordered so a page is stable across requests and the isolation assertions in {@code
   * ResourcePolicyTest} can compare like with like.
   */
  @Transactional(readOnly = true)
  public Page<StaffResponse> list(PageParams params) {
    Specification<User> visible = resourcePolicies.readPredicate(User.class);
    return userRepository
        .findAll(visible, params.toPageRequest(Sort.by("email").ascending()))
        .map(StaffService::toResponse);
  }

  private static StaffResponse toResponse(User user) {
    return new StaffResponse(
        user.getId().toString(),
        user.getEmail(),
        user.getFirstName(),
        user.getLastName(),
        user.getStatus().name());
  }
}
