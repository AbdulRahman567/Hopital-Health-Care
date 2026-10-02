package com.healthcare.hms.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.healthcare.hms.auth.User;
import com.healthcare.hms.auth.UserFixtures;
import com.healthcare.hms.auth.repository.UserRepository;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * P4.4 — Hibernate tenant filtering with the TQ-1 default {@code @TenantId} (TDD section 6.3
 * "Repository" layer, plan section 4).
 *
 * <p>Runs against the shared {@code hms_test} schema (Flyway V1-V3, no migration added in this
 * phase) with a fixture tenant row per tenant, because {@code users.tenant_id} carries the foreign
 * key to {@code tenants}.
 *
 * <p>What it proves: reads are filtered, cross-tenant reads come back empty (the repository
 * primitive behind ADR-006's 404), counts are filtered, inserts are stamped with the current
 * tenant, and an empty {@code TenantContext} fails closed instead of degrading into an unfiltered
 * read.
 */
@SpringBootTest
class HibernateTenantFilterTest {

  private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private User userA1;
  private User userA2;
  private User userB1;

  @BeforeEach
  void createFixtures() {
    ensureTenant(TENANT_A, "Hospital A", "hospital-a");
    ensureTenant(TENANT_B, "Hospital B", "hospital-b");
    clearUsers();

    userA1 = saveInTenant(TENANT_A, "admin@hospital-a.example.com");
    userA2 = saveInTenant(TENANT_A, "doctor@hospital-a.example.com");
    userB1 = saveInTenant(TENANT_B, "admin@hospital-b.example.com");
  }

  @AfterEach
  void cleanUp() {
    TenantContext.clear();
    clearUsers();
  }

  @Test
  void findAllUnderTenantASeesOnlyTenantARows() {
    TenantContext.run(
        TENANT_A,
        () -> {
          List<User> found = userRepository.findAll();
          assertThat(found)
              .extracting(User::getEmail)
              .containsExactlyInAnyOrder(
                  "admin@hospital-a.example.com", "doctor@hospital-a.example.com");
          assertThat(found).extracting(User::getTenantId).containsOnly(TENANT_A);
        });
  }

  @Test
  void findAllUnderTenantBSeesOnlyTenantBRows() {
    TenantContext.run(
        TENANT_B,
        () ->
            assertThat(userRepository.findAll())
                .extracting(User::getEmail)
                .containsExactly("admin@hospital-b.example.com"));
  }

  @Test
  void findByIdOfAForeignTenantRowIsEmptyUnderTenantA() {
    TenantContext.run(
        TENANT_A,
        () -> {
          assertThat(userRepository.findById(userB1.getId())).isEmpty();
          assertThat(userRepository.findByEmail("admin@hospital-b.example.com")).isEmpty();
        });
  }

  @Test
  void findByIdOfAForeignTenantRowIsEmptyUnderTenantB() {
    TenantContext.run(
        TENANT_B,
        () -> {
          assertThat(userRepository.findById(userA1.getId())).isEmpty();
          assertThat(userRepository.findById(userA2.getId())).isEmpty();
        });
  }

  @Test
  void countIsFilteredToTheCurrentTenant() {
    TenantContext.run(TENANT_A, () -> assertThat(userRepository.count()).isEqualTo(2));
    TenantContext.run(TENANT_B, () -> assertThat(userRepository.count()).isEqualTo(1));
  }

  @Test
  void saveStampsTheCurrentTenantOnTheRow() {
    User saved = saveInTenant(TENANT_A, "nurse@hospital-a.example.com");

    byte[] tenantOfRow =
        jdbcTemplate.queryForObject(
            "SELECT tenant_id FROM users WHERE id = ?", byte[].class, bytesOf(saved.getId()));

    assertThat(tenantOfRow).as("insert must carry tenant A").isEqualTo(bytesOf(TENANT_A));
  }

  @Test
  void anEmptyTenantContextFailsClosedInsteadOfReadingEverything() {
    assertThat(TenantContext.find()).isEmpty();

    Throwable failure = catchThrowable(userRepository::findAll);

    assertThat(failure).isNotNull();
    assertThat(messagesOf(failure))
        .as("an unfiltered read must be impossible without a tenant context")
        .contains("No tenant context is bound");
  }

  @Test
  void userInheritsTheFilterFromTenantOwnedEntity() {
    TenantContext.run(
        TENANT_A,
        () ->
            assertThat(userRepository.findAllByOrderByEmailAsc())
                .isNotEmpty()
                .allSatisfy(
                    user -> {
                      assertThat(user).isInstanceOf(User.class);
                      assertThat(user.getTenantId()).isEqualTo(TENANT_A);
                    }));
  }

  // -------------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------------

  private User saveInTenant(UUID tenantId, String email) {
    AtomicReference<User> saved = new AtomicReference<>();
    TenantContext.run(
        tenantId, () -> saved.set(userRepository.saveAndFlush(UserFixtures.user(email))));
    return saved.get();
  }

  private void ensureTenant(UUID id, String name, String slug) {
    Integer existing =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM tenants WHERE id = ?", Integer.class, bytesOf(id));
    if (existing != null && existing > 0) {
      return;
    }
    jdbcTemplate.update(
        "INSERT INTO tenants (id, name, slug, status, timezone, created_at, updated_at, version)"
            + " VALUES (?, ?, ?, 'ACTIVE', 'UTC', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)",
        bytesOf(id),
        name,
        slug);
  }

  private void clearUsers() {
    jdbcTemplate.update(
        "DELETE FROM users WHERE tenant_id IN (?, ?)", bytesOf(TENANT_A), bytesOf(TENANT_B));
  }

  private static byte[] bytesOf(UUID id) {
    return ByteBuffer.allocate(16)
        .putLong(id.getMostSignificantBits())
        .putLong(id.getLeastSignificantBits())
        .array();
  }

  private static String messagesOf(Throwable failure) {
    StringBuilder messages = new StringBuilder();
    for (Throwable current = failure; current != null; current = current.getCause()) {
      messages.append(current.getMessage()).append('\n');
    }
    return messages.toString();
  }
}
