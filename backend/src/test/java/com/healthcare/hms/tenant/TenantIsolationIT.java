package com.healthcare.hms.tenant;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * P4.5 — the permanent cross-tenant repository isolation suite (TESTING.md section 4 fixtures, TDD
 * section 6.3 "Repository" layer, plan section 4).
 *
 * <p>Two tenants with deliberately different row counts (3 vs 2), so a leak shows up as the wrong
 * number and not merely the wrong name. Every read goes through {@link UserRepository}, i.e. the
 * exact code path Phase 5+ will call — no raw SQL except the two documented guardrails.
 *
 * <p><b>Limitation to record in the evidence:</b> {@code users} is the only tenant-owned entity in
 * existence today, so this suite proves the <i>mechanism</i>, not full coverage. The class is
 * permanent and grows with patients, departments, appointments, visits, prescriptions, invoices,
 * documents and notifications as they land (TESTING.md section 4), the way {@code
 * IndexConventionIT} grew in P3.5.
 */
@SpringBootTest
class TenantIsolationIT {

  private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

  private static final List<String> TENANT_A_EMAILS =
      List.of(
          "admin@hospital-a.example.com",
          "doctor@hospital-a.example.com",
          "receptionist@hospital-a.example.com");
  private static final List<String> TENANT_B_EMAILS =
      List.of("admin@hospital-b.example.com", "user@hospital-b.example.com");

  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private User adminA;
  private User doctorA;
  private User receptionistA;
  private User adminB;
  private User userB;

  @BeforeEach
  void createFixtures() {
    ensureTenant(TENANT_A, "Hospital A", "hospital-a");
    ensureTenant(TENANT_B, "Hospital B", "hospital-b");
    clearUsers();

    adminA = saveInTenant(TENANT_A, TENANT_A_EMAILS.get(0));
    doctorA = saveInTenant(TENANT_A, TENANT_A_EMAILS.get(1));
    receptionistA = saveInTenant(TENANT_A, TENANT_A_EMAILS.get(2));
    adminB = saveInTenant(TENANT_B, TENANT_B_EMAILS.get(0));
    userB = saveInTenant(TENANT_B, TENANT_B_EMAILS.get(1));
  }

  @AfterEach
  void cleanUp() {
    clearUsers();
    assertThat(TenantContext.find())
        .as("no ThreadLocal leak between suites (TESTING.md section 4)")
        .isEmpty();
    TenantContext.clear();
  }

  @Test
  void listingAsTenantAIsScopedToTenantA() {
    TenantContext.run(
        TENANT_A,
        () -> {
          assertThat(userRepository.findAllByOrderByEmailAsc())
              .extracting(User::getEmail)
              .containsExactlyElementsOf(TENANT_A_EMAILS);
          assertThat(userRepository.findAll()).extracting(User::getTenantId).containsOnly(TENANT_A);
        });
  }

  @Test
  void aTenantBRowIsInvisibleByIdAndByEmailAsTenantA() {
    TenantContext.run(
        TENANT_A,
        () -> {
          assertThat(userRepository.findById(adminB.getId())).isEmpty();
          assertThat(userRepository.findById(userB.getId())).isEmpty();
          assertThat(userRepository.findByEmail("admin@hospital-b.example.com")).isEmpty();
          assertThat(userRepository.findByEmail("user@hospital-b.example.com")).isEmpty();
        });
  }

  @Test
  void listingAsTenantBIsScopedToTenantB() {
    TenantContext.run(
        TENANT_B,
        () -> {
          assertThat(userRepository.findAllByOrderByEmailAsc())
              .extracting(User::getEmail)
              .containsExactlyElementsOf(TENANT_B_EMAILS);
          assertThat(userRepository.findAll()).extracting(User::getTenantId).containsOnly(TENANT_B);
        });
  }

  @Test
  void aTenantARowIsInvisibleByIdAndByEmailAsTenantB() {
    TenantContext.run(
        TENANT_B,
        () -> {
          assertThat(userRepository.findById(adminA.getId())).isEmpty();
          assertThat(userRepository.findById(doctorA.getId())).isEmpty();
          assertThat(userRepository.findById(receptionistA.getId())).isEmpty();
          assertThat(userRepository.findByEmail("admin@hospital-a.example.com")).isEmpty();
        });
  }

  @Test
  void countsAndPaginationTotalsNeverLeakTheOtherTenantsNumbers() {
    TenantContext.run(
        TENANT_A,
        () -> {
          assertThat(userRepository.count()).isEqualTo(3);
          Page<User> page = userRepository.findAll(PageRequest.of(0, 1));
          assertThat(page.getTotalElements()).isEqualTo(3);
          assertThat(page.getTotalPages()).isEqualTo(3);
          assertThat(page.getContent())
              .extracting(User::getEmail)
              .containsExactly(TENANT_A_EMAILS.get(0));
        });

    TenantContext.run(
        TENANT_B,
        () -> {
          assertThat(userRepository.count()).isEqualTo(2);
          Page<User> page = userRepository.findAll(PageRequest.of(0, 1));
          assertThat(page.getTotalElements()).isEqualTo(2);
          assertThat(page.getTotalPages()).isEqualTo(2);
          assertThat(page.getContent())
              .extracting(User::getEmail)
              .containsExactly(TENANT_B_EMAILS.get(0));
        });
  }

  @Test
  void aNativeQueryThatStatesTenantIdIsScopedToThatTenant() {
    TenantContext.run(
        TENANT_A,
        () ->
            assertThat(userRepository.nativeQueryStatingTenantId(TENANT_A.toString()))
                .extracting(User::getEmail)
                .containsExactlyElementsOf(TENANT_A_EMAILS));
  }

  @Test
  void aNativeQueryWithoutTenantIdReturnsBothTenantsAndThatIsWhyTheRuleExists() {
    TenantContext.run(
        TENANT_A,
        () -> {
          List<User> unscoped = userRepository.nativeQueryWithoutTenantId();

          assertThat(unscoped)
              .as(
                  "native SQL is passed to MySQL verbatim: Hibernate does NOT filter it, even with"
                      + " a tenant bound (ENGINEERING_RULES section 5, TDD section 6.3)")
              .extracting(User::getEmail)
              .contains(TENANT_A_EMAILS.toArray(new String[0]))
              .contains(TENANT_B_EMAILS.toArray(new String[0]));

          Integer allRows =
              jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
          assertThat(unscoped).hasSize(allRows);
        });
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
}
