package com.healthcare.hms.authz.policy;

import com.healthcare.hms.authz.CurrentActor;
import com.healthcare.hms.common.entity.BaseEntity;
import com.healthcare.hms.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The log seam OQ-2/TQ-5 and SECURITY section 4 ask for (ROADMAP P6.4, decision D7).
 *
 * <p>SECURITY section 4 says cross-doctor reads are audited. Auditing as a durable row means
 * writing to {@code audit_logs}, which is Phase 19's job (P14.6 starts it), so Phase 6 ships the
 * event rather than the table: one structured line, emitted by {@link ResourcePolicy#requireRead}
 * whenever a grant was cross-boundary, with {@code event=cross_doctor_read} as its stable name and
 * {@code policy=} identifying which policy spoke.
 *
 * <p>Keeping the name fixed now is what lets P14.6 turn this line into a row without a rename and
 * without a second call site: {@code PatientAccessPolicy} will set {@code
 * policy=PatientAccessPolicy} over the same event, and a reader of either the log or the table sees
 * one vocabulary.
 *
 * <p>Values are logged as key/value pairs rather than concatenated, because the application's
 * logger writes JSON ({@code JsonLoggingTest}): a pair survives as a field, a formatted sentence
 * does not.
 */
public final class PolicyAudit {

  private static final Logger log = LoggerFactory.getLogger(PolicyAudit.class);

  private PolicyAudit() {}

  /**
   * Records a read that was granted by privilege rather than by relationship.
   *
   * @param policy the policy that granted it, by simple class name
   * @param subject the row that was read; a {@link BaseEntity} is logged by id, anything else by
   *     its {@code toString}
   */
  public static void crossBoundaryRead(String policy, Object subject) {
    log.info(
        "event=cross_doctor_read policy={} subjectType={} subjectId={} actorUserId={} tenantId={}",
        policy,
        subject == null ? "null" : subject.getClass().getSimpleName(),
        subjectId(subject),
        actorUserId(),
        tenantId());
  }

  private static String subjectId(Object subject) {
    if (subject instanceof BaseEntity entity) {
      return String.valueOf(entity.getId());
    }
    return String.valueOf(subject);
  }

  /**
   * The account that made the read, or {@code -} when there is none.
   *
   * <p>Never {@link CurrentActor#requireUserId()}: this line is written on the success path of a
   * decision that has already proved a principal exists, and an audit helper that could itself
   * throw would turn a successful read into a 500.
   */
  private static String actorUserId() {
    Optional<UUID> userId = CurrentActor.userId();
    return userId.map(UUID::toString).orElse("-");
  }

  /** The bound tenant, or {@code -}. Same reasoning as {@link #actorUserId()}. */
  private static String tenantId() {
    return TenantContext.find().map(UUID::toString).orElse("-");
  }
}
