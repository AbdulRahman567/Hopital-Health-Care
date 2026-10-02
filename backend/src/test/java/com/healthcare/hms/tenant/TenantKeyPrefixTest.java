package com.healthcare.hms.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * P4.6 — the key shapes of TDD sections 12 and 13 (plan section 4).
 *
 * <p>Unit test, no Spring and no Redis: decision D7 says {@link TenantKeys} is a pure string
 * builder, so the whole contract is the string it returns.
 */
class TenantKeyPrefixTest {

  private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

  // --- exact formats ------------------------------------------------------

  @Test
  void redisRateLimitKeyMatchesTheTddPattern() {
    assertThat(TenantKeys.redis(TENANT_A, "rl", "login", "user-1"))
        .isEqualTo("t:" + TENANT_A + ":rl:login:user-1");
  }

  @Test
  void redisCacheKeyMatchesTheTddPattern() {
    assertThat(TenantKeys.redis(TENANT_A, "cache", "medicines", "paracetamol-500"))
        .isEqualTo("t:" + TENANT_A + ":cache:medicines:paracetamol-500");
  }

  @Test
  void redisNotificationKeyMatchesTheTddPattern() {
    assertThat(TenantKeys.redis(TENANT_B, "notif", "a1b2c3d4-user"))
        .isEqualTo("t:" + TENANT_B + ":notif:a1b2c3d4-user");
  }

  @Test
  void storageObjectKeyMatchesTheTddObjectKeyPattern() {
    assertThat(
            TenantKeys.storage(TENANT_A, "patients", "p-1", "0f8fad5b-d9cb-469f-a165-70867728950e"))
        .isEqualTo("tenants/" + TENANT_A + "/patients/p-1/0f8fad5b-d9cb-469f-a165-70867728950e");
  }

  // --- isolation between tenants -----------------------------------------

  @Test
  void twoTenantsNeverProduceTheSameKey() {
    String[] parts = {"rl", "login", "user-1"};

    String keyA = TenantKeys.redis(TENANT_A, parts);
    String keyB = TenantKeys.redis(TENANT_B, parts);
    String objectA = TenantKeys.storage(TENANT_A, "patients", "p-1");
    String objectB = TenantKeys.storage(TENANT_B, "patients", "p-1");

    assertThat(keyA).isNotEqualTo(keyB);
    assertThat(objectA).isNotEqualTo(objectB);
    assertThat(keyA).startsWith("t:" + TENANT_A + ":");
    assertThat(keyB).startsWith("t:" + TENANT_B + ":");
    assertThat(objectA).startsWith("tenants/" + TENANT_A + "/");
    assertThat(objectB).startsWith("tenants/" + TENANT_B + "/");
  }

  // --- rejections ---------------------------------------------------------

  @Test
  void aNullTenantIsRejectedForBothShapes() {
    assertThatThrownBy(() -> TenantKeys.redis(null, "rl", "login"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId");
    assertThatThrownBy(() -> TenantKeys.storage(null, "patients", "p-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tenantId");
  }

  @ParameterizedTest
  @MethodSource("missingParts")
  void missingOrBlankPartsAreRejected(String[] parts) {
    assertThatThrownBy(() -> TenantKeys.redis(TENANT_A, parts))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> TenantKeys.storage(TENANT_A, parts))
        .isInstanceOf(IllegalArgumentException.class);
  }

  static Stream<Arguments> missingParts() {
    return Stream.of(
        Arguments.of((Object) null),
        Arguments.of((Object) new String[0]),
        Arguments.of((Object) new String[] {""}),
        Arguments.of((Object) new String[] {"patients", "  "}),
        Arguments.of((Object) new String[] {"patients", null}));
  }

  @ParameterizedTest
  @MethodSource("traversalParts")
  void storageKeysRejectTraversalAndAbsoluteLookingParts(String traversal) {
    assertThatThrownBy(() -> TenantKeys.storage(TENANT_A, "patients", traversal))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'");
  }

  static Stream<Arguments> traversalParts() {
    return Stream.of(
        Arguments.of(".."),
        Arguments.of("../secrets"),
        Arguments.of("a/../../b"),
        Arguments.of("/etc/passwd"),
        Arguments.of("nested\\folder"),
        Arguments.of("..\\..\\windows"));
  }

  // --- the prefix is unomissable -----------------------------------------

  @ParameterizedTest
  @MethodSource("keysThatMustBePrefixed")
  void noKeyCanBeProducedThatOmitsTheTenantPrefix(String key, String expectedPrefix) {
    assertThat(key).startsWith(expectedPrefix);
  }

  static Stream<Arguments> keysThatMustBePrefixed() {
    return Stream.of(
        Arguments.of(TenantKeys.redis(TENANT_A, "rl", "login", "u1"), "t:" + TENANT_A + ":"),
        Arguments.of(TenantKeys.redis(TENANT_A, "cache", "name", "key"), "t:" + TENANT_A + ":"),
        Arguments.of(TenantKeys.redis(TENANT_A, "notif", "u1"), "t:" + TENANT_A + ":"),
        Arguments.of(TenantKeys.storage(TENANT_A, "patients", "p1"), "tenants/" + TENANT_A + "/"),
        Arguments.of(
            TenantKeys.storage(TENANT_A, "patients", "p1", "doc.pdf"),
            "tenants/" + TENANT_A + "/"));
  }
}
