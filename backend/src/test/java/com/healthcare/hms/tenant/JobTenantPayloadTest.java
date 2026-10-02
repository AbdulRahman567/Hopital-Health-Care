package com.healthcare.hms.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * P4.7 — tenant propagation for asynchronous job payloads (TDD section 14, plan section 4).
 *
 * <p>A pure unit test: {@link TenantContext} and {@link TenantJobPayload} are plain objects, and
 * the point of the phase is the contract the future outbox worker (Phase 18) will call — capture
 * under the request, restore on the worker, always cleared in {@code finally}.
 */
class JobTenantPayloadTest {

  private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void captureThenClearThenTheWorkerObservesThePayloadsTenant() {
    AtomicReference<TenantJobPayload> captured = new AtomicReference<>();
    TenantContext.run(TENANT_A, () -> captured.set(TenantContext.toJobPayload()));
    TenantContext.clear();

    AtomicReference<UUID> observed = new AtomicReference<>();
    TenantContext.runWith(captured.get(), () -> observed.set(TenantContext.require()));

    assertThat(observed).hasValue(TENANT_A);
    assertThat(TenantContext.find()).as("the worker thread is left unbound").isEmpty();
    assertThat(captured.get().actorId()).isNull();
    assertThat(captured.get().capturedAt()).isNotNull();
  }

  @Test
  void theValueBoundBeforeTheJobIsRestoredAfterwards() {
    TenantContext.run(
        TENANT_B,
        () -> {
          TenantContext.runWith(
              new TenantJobPayload(TENANT_A, null, Instant.now()),
              () -> assertThat(TenantContext.require()).isEqualTo(TENANT_A));

          assertThat(TenantContext.find())
              .as("the enclosing scope keeps its own tenant")
              .hasValue(TENANT_B);
        });
    assertThat(TenantContext.find()).isEmpty();
  }

  @Test
  void theContextIsClearedWhenTheJobThrows() {
    TenantJobPayload payload = new TenantJobPayload(TENANT_A, null, Instant.now());

    Throwable failure =
        catchThrowable(
            () ->
                TenantContext.runWith(
                    payload,
                    () -> {
                      throw new IllegalStateException("job failed");
                    }));

    assertThat(failure).isInstanceOf(IllegalStateException.class);
    assertThat(TenantContext.find())
        .as("a throwing job must not leave its tenant bound to a pooled thread")
        .isEmpty();
  }

  @Test
  void aPayloadWithANullTenantIsRejected() {
    assertThatThrownBy(() -> new TenantJobPayload(null, null, Instant.now()))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("tenantId");
    assertThatThrownBy(() -> new TenantJobPayload(TENANT_A, null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("capturedAt");
  }

  @Test
  void aNullPayloadIsRejected() {
    assertThatThrownBy(() -> TenantContext.runWith(null, () -> {}))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("payload");
  }

  @Test
  void capturingWithoutATenantContextFailsClosed() {
    assertThat(TenantContext.find()).isEmpty();

    assertThatThrownBy(TenantContext::toJobPayload)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No tenant context is bound");
  }

  @Test
  void twoSequentialJobsDoNotBleedIntoEachOther() {
    AtomicReference<UUID> first = new AtomicReference<>();
    AtomicReference<UUID> second = new AtomicReference<>();

    TenantContext.runWith(
        new TenantJobPayload(TENANT_A, null, Instant.now()),
        () -> first.set(TenantContext.require()));
    assertThat(TenantContext.find()).isEmpty();

    TenantContext.runWith(
        new TenantJobPayload(TENANT_B, null, Instant.now()),
        () -> second.set(TenantContext.require()));

    assertThat(first).hasValue(TENANT_A);
    assertThat(second).hasValue(TENANT_B);
    assertThat(TenantContext.find()).isEmpty();
  }
}
