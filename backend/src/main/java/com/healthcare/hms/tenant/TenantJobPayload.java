package com.healthcare.hms.tenant;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The propagation contract for asynchronous work: a job carries the tenant it belongs to (TDD
 * section 14, plan P4.7).
 *
 * <p>Captured under the request context by {@link TenantContext#toJobPayload()} and restored by
 * {@link TenantContext#runWith(TenantJobPayload, Runnable)} on the worker thread. That is the whole
 * mechanism — no executor, no queue, no broker here; the outbox worker that will call it is Phase
 * 18.
 *
 * <p>{@code actorId} is optional: {@link TenantContext} carries only the tenant today, so the
 * capture path leaves it null and the audit layer (Phase 19) supplies an actor when a job needs
 * one. {@code tenantId} and {@code capturedAt} are mandatory — a job without a tenant could not be
 * filtered, which is the exact failure this phase exists to prevent.
 *
 * @param tenantId tenant the job belongs to, from {@link TenantContext} — never from a message
 *     payload or a request (ENGINEERING_RULES section 1.2)
 * @param actorId user the job runs on behalf of, or {@code null} when the job has no actor
 * @param capturedAt when the payload was captured, for staleness checks and audit
 */
public record TenantJobPayload(UUID tenantId, UUID actorId, Instant capturedAt) {

  public TenantJobPayload {
    Objects.requireNonNull(
        tenantId, "tenantId must not be null - a job always belongs to exactly one tenant");
    Objects.requireNonNull(capturedAt, "capturedAt must not be null");
  }
}
