package com.healthcare.hms.staff;

import com.healthcare.hms.auth.User;
import com.healthcare.hms.authz.CurrentActor;
import com.healthcare.hms.authz.PermissionAuthorizationManager;
import com.healthcare.hms.authz.PermissionCatalog;
import com.healthcare.hms.authz.policy.ResourcePolicy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The one resource policy that has a real entity to read today (ROADMAP P6.4, decision D7).
 *
 * <p><b>Read your own account, or any account of this hospital if you hold {@code STAFF_VIEW}.</b>
 * That is the whole rule, and it exists to prove the framework rather than to ship a feature: the
 * catalog describes <i>what a role may do</i> ({@code STAFF_VIEW} = "list staff members"), while
 * this decides <i>which rows of {@code users} this particular caller may open</i>. A hospital
 * administrator holding {@code STAFF_VIEW} opens a colleague's account; an accountant holding
 * {@code BILLING_VIEW} opens their own and nothing else.
 *
 * <p>Enforced by {@link StaffService}, behind {@code GET /api/v1/staff/{userId}}, which is itself
 * gated by {@code @RequirePermission("STAFF_VIEW")}. The two layers look redundant and are not: the
 * endpoint gate stops an unqualified caller from reaching the route at all (403), while this rule
 * is what a caller who <i>does</i> hold the code is still subject to &mdash; and it is the rule
 * that a Phase 10 {@code PatientAccessPolicy} will generalise, because "assigned or previously
 * treated" is the same shape as "your own record or a colleague's".
 *
 * <p><b>Why the cross-boundary line fires here.</b> Reading your own account is not an audit event;
 * reading somebody else's is, whichever of the two branches granted it. The event name stays {@code
 * cross_doctor_read} &mdash; SECURITY section 4's standing vocabulary, decided before this policy
 * existed &mdash; and {@code policy=UserSelfOrStaffPolicy} on the line is what says which rule
 * spoke. One event name across policies is what lets P14.6 translate lines into {@code audit_logs}
 * rows without renaming anything.
 */
@Component
public class UserSelfOrStaffPolicy implements ResourcePolicy<User> {

  @Override
  public boolean canRead(User subject) {
    if (subject == null || !sameTenant(subject)) {
      // Fail closed on an unbound tenant context: "we do not know where we are" cannot mean yes.
      return false;
    }
    return isSelf(subject) || holdsStaffView();
  }

  /**
   * Someone else's account &mdash; granted by the permission rather than by being yours.
   *
   * <p>This is the branch OQ-2 wants recorded, and it is the only branch {@link
   * com.healthcare.hms.authz.policy.PolicyAudit} writes a line for.
   */
  @Override
  public boolean isCrossBoundaryRead(User subject) {
    return !isSelf(subject);
  }

  @Override
  public Specification<User> readPredicate() {
    if (holdsStaffView()) {
      // Every account of this tenant: `@TenantId` already put the tenant predicate in the SQL, so
      // the policy adds nothing here — and adding a second tenant predicate by hand would be a
      // second thing to keep correct.
      return (root, query, cb) -> cb.conjunction();
    }
    Optional<UUID> self = CurrentActor.userId();
    if (self.isEmpty()) {
      // No principal — fail closed rather than listing the hospital to an anonymous context.
      return (root, query, cb) -> cb.disjunction();
    }
    UUID userId = self.get();
    return (root, query, cb) -> cb.equal(root.get("id"), userId);
  }

  private static boolean isSelf(User subject) {
    return CurrentActor.userId().map(id -> id.equals(subject.getId())).orElse(false);
  }

  private static boolean sameTenant(User subject) {
    return CurrentActor.findTenantId().map(id -> id.equals(subject.getTenantId())).orElse(false);
  }

  private static boolean holdsStaffView() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return PermissionAuthorizationManager.holds(
        authentication, PermissionCatalog.STAFF_VIEW.code());
  }
}
