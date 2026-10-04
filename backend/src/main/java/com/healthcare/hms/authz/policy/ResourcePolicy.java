package com.healthcare.hms.authz.policy;

import com.healthcare.hms.common.exception.NotFoundException;
import org.springframework.data.jpa.domain.Specification;

/**
 * Resource-level authorization (ROADMAP P6.4, TDD section 8.3, decision D7) &mdash; the layer
 * {@code @RequirePermission} deliberately does not try to be.
 *
 * <p>The annotation answers <i>may this kind of caller reach this kind of route</i>. This interface
 * answers <i>may this particular caller see this particular row</i>, which no catalog code can
 * express: {@code PATIENT_VIEW} is the capability, "the doctor this patient is assigned to" is the
 * rule. Both are needed, and they are enforced in different places &mdash; the annotation on the
 * controller, this in the service, before any row leaves the process.
 *
 * <p>Three properties the contract fixes, all of them load-bearing:
 *
 * <ol>
 *   <li><b>404, never 403.</b> {@link #requireRead} answers {@link NotFoundException} for a subject
 *       the caller may not see. A 403 would confirm that the row exists and that somebody else's id
 *       is real, which is exactly what ADR-006 forbids; tenancy and privilege both collapse into
 *       one indistinguishable "not found".
 *   <li><b>A predicate, not a post-filter.</b> {@link #readPredicate} returns a {@link
 *       Specification} the service composes into the query itself, so the database discards the
 *       denied rows <i>before</i> the page is cut. Filtering an already-sliced page would quietly
 *       return short pages and a total that counts rows the caller can never see (TDD section 8.3).
 *   <li><b>The cross-boundary read is a log seam.</b> OQ-2/TQ-5's default &mdash; assigned or
 *       previously treated, everything else audited &mdash; is expressed by {@link
 *       #isCrossBoundaryRead}: a grant that came from a privileged relationship rather than from
 *       care involvement. {@link PolicyAudit} writes the structured {@code cross_doctor_read} line
 *       for it. The {@code audit_logs} row that SECURITY section 4 ultimately wants lands at
 *       P14.6/P19; the event exists, is named and is proven now so that the later row is a
 *       translation and not a discovery.
 * </ol>
 *
 * <p><b>Why there is no actor parameter.</b> The caller is not an argument &mdash; it is request
 * state, exactly as {@code RoleService} already reads it: {@link
 * com.healthcare.hms.authz.CurrentActor} for identity, the security context for authorities, and
 * {@link com.healthcare.hms.tenant.TenantContext} for tenancy. Taking it as a parameter would
 * invite a caller to construct one from the request, which is the whole attack ENGINEERING_RULES
 * section 1.2 exists to prevent.
 *
 * <p><b>Phase 6 scope (D7).</b> The framework and its proofs land here; the real {@code
 * PatientAccessPolicy} over {@code patients}/{@code patient_assignments} lands at P10.8, because
 * those tables belong to Phase 10. The real policy that <i>does</i> have an entity today is {@code
 * UserSelfOrStaffPolicy}, enforced by the {@code staff} service.
 *
 * @param <T> the resource type this policy reads; typically an entity, but any subject the service
 *     already loaded works
 */
public interface ResourcePolicy<T> {

  /** The one message every denial carries, so a 404 for "foreign" and "missing" are identical. */
  String NOT_FOUND_MESSAGE = "Not found.";

  /**
   * May the current caller read this subject?
   *
   * <p>Implementations must fail closed: an unbound tenant context, an absent principal or an
   * unrecognised subject all answer {@code false}.
   */
  boolean canRead(T subject);

  /**
   * Enforces {@link #canRead}, answers 404 rather than 403, and emits the cross-boundary log seam.
   *
   * <p>This is the method services are expected to call. It is a default method so that every
   * policy shares one implementation of the three rules above and no future policy can quietly
   * answer 403 or skip the audit line.
   */
  default void requireRead(T subject) {
    if (subject == null || !canRead(subject)) {
      throw new NotFoundException(NOT_FOUND_MESSAGE);
    }
    if (isCrossBoundaryRead(subject)) {
      PolicyAudit.crossBoundaryRead(getClass().getSimpleName(), subject);
    }
  }

  /**
   * Was this grant a cross-boundary read &mdash; permitted by privilege rather than by a
   * relationship to the subject?
   *
   * <p>Defaults to {@code false}, which is the honest answer for a policy whose only grants are
   * "this is yours" and "you hold the permission for this kind of row".
   */
  default boolean isCrossBoundaryRead(T subject) {
    return false;
  }

  /**
   * The same rule expressed for a list query: a {@link Specification} the service composes into its
   * own, so denied rows are removed in SQL rather than after the page is built.
   *
   * <p>A policy with no list surface still has to return something, and it must be fail-closed
   * &mdash; {@code cb.disjunction()} matches nothing, which is the only safe answer for a rule that
   * was never written.
   */
  Specification<T> readPredicate();
}
