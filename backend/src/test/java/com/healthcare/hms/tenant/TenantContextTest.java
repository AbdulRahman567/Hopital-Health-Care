package com.healthcare.hms.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * P4.1 — the request-scoped tenant holder: set/find/require/clear semantics, {@code run()}
 * restoring the outer value in {@code finally}, and isolation between threads (TDD section 6.2.2).
 *
 * <p>Pure JUnit: no Spring context, no HTTP — the holder must be usable from any layer.
 */
class TenantContextTest {

  private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void setThenFindReturnsTheBoundTenant() {
    TenantContext.set(TENANT_A);

    Optional<UUID> found = TenantContext.find();

    assertThat(found).contains(TENANT_A);
    assertThat(TenantContext.require()).isEqualTo(TENANT_A);
  }

  @Test
  void findIsEmptyAndRequireThrowsWhenNothingIsBound() {
    assertThat(TenantContext.find()).isEmpty();
    assertThatThrownBy(TenantContext::require)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No tenant context is bound");
  }

  @Test
  void setRejectsNullSoTheHolderCannotBeClearedSilently() {
    assertThatThrownBy(() -> TenantContext.set(null)).isInstanceOf(NullPointerException.class);
    assertThat(TenantContext.find()).isEmpty();
  }

  @Test
  void clearUnbindsTheTenant() {
    TenantContext.set(TENANT_A);

    TenantContext.clear();

    assertThat(TenantContext.find()).isEmpty();
    assertThatThrownBy(TenantContext::require).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void runRestoresTheOuterValueAfterTheBodyCompletes() {
    TenantContext.set(TENANT_A);

    TenantContext.run(TENANT_B, () -> assertThat(TenantContext.require()).isEqualTo(TENANT_B));

    assertThat(TenantContext.require()).isEqualTo(TENANT_A);
  }

  @Test
  void runLeavesTheContextClearedWhenThereWasNoOuterValue() {
    TenantContext.run(TENANT_A, () -> assertThat(TenantContext.require()).isEqualTo(TENANT_A));

    assertThat(TenantContext.find()).isEmpty();
  }

  @Test
  void runRestoresTheOuterValueWhenTheBodyThrows() {
    TenantContext.set(TENANT_A);

    assertThatThrownBy(
            () ->
                TenantContext.run(
                    TENANT_B,
                    () -> {
                      throw new IllegalArgumentException("boom");
                    }))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(TenantContext.require()).isEqualTo(TENANT_A);
  }

  @Test
  void runClearsTheContextEvenWhenTheBodyThrows() {
    assertThatThrownBy(
            () ->
                TenantContext.run(
                    TENANT_A,
                    () -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(TenantContext.find()).isEmpty();
  }

  @Test
  void nestedRunRestoresEachLevelInTurn() {
    TenantContext.set(TENANT_A);

    TenantContext.run(
        TENANT_B,
        () -> {
          assertThat(TenantContext.require()).isEqualTo(TENANT_B);
          TenantContext.run(
              TENANT_A, () -> assertThat(TenantContext.require()).isEqualTo(TENANT_A));
          assertThat(TenantContext.require()).isEqualTo(TENANT_B);
        });

    assertThat(TenantContext.require()).isEqualTo(TENANT_A);
  }

  @Test
  void aSecondThreadNeverSeesTheFirstThreadsTenant() throws Exception {
    TenantContext.set(TENANT_A);
    CountDownLatch observed = new CountDownLatch(1);
    AtomicReference<Optional<UUID>> seenByOtherThread = new AtomicReference<>();
    AtomicReference<UUID> observedByOtherThread = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();

    Thread other =
        new Thread(
            () -> {
              try {
                seenByOtherThread.set(TenantContext.find());
                TenantContext.set(TENANT_B);
                observedByOtherThread.set(TenantContext.require());
                observed.countDown();
                Thread.sleep(50);
                TenantContext.clear();
              } catch (Throwable t) {
                failure.set(t);
              }
            });
    other.start();

    assertThat(observed.await(5, TimeUnit.SECONDS)).isTrue();
    other.join(5_000);

    assertThat(failure.get()).isNull();
    assertThat(seenByOtherThread.get())
        .as("an InheritableThreadLocal would have leaked TENANT_A here")
        .isEmpty();
    assertThat(observedByOtherThread.get()).isEqualTo(TENANT_B);
    assertThat(TenantContext.require())
        .as("the other thread must not disturb this one")
        .isEqualTo(TENANT_A);
  }
}
