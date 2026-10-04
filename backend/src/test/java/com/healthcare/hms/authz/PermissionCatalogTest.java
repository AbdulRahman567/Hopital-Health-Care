package com.healthcare.hms.authz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * P6.1 — the equivalence guard between the in-code catalog and the V2 seed (ROADMAP P6.1, decision
 * D6).
 *
 * <p>Phase 6 ships <b>no migration</b>: the 53 rows landed at P3.3 (decision D3) and this suite is
 * the code half of that contract. The comparison runs <b>both ways</b> — a seeded code that no enum
 * constant can reference and an enum constant that no seeded row can ever be granted both fail —
 * because a one-way count check would let either drift through.
 *
 * <p>Runs against the JVM-scoped {@code hms_test} database after Flyway has applied V1–V4, which is
 * the only place the seed exists.
 */
@SpringBootTest
class PermissionCatalogTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  private static final String SELECT_CODES = "SELECT code FROM permissions ORDER BY code";

  @Test
  void codeCatalogMatchesTheSeededRowsInBothDirections() {
    List<String> dbCodes = jdbcTemplate.queryForList(SELECT_CODES, String.class);
    Set<String> codeCodes = PermissionCatalog.codes();

    assertThat(dbCodes)
        .as(
            "the seed is frozen at %d rows by MigrationV2IT.CATALOG_SIZE (both stay in step"
                + " because D6 allows no Phase 6 migration)",
            PermissionCatalog.SIZE)
        .hasSize(PermissionCatalog.SIZE);
    assertThat(PermissionCatalog.values()).hasSize(PermissionCatalog.SIZE);

    Set<String> onlyInDb = new HashSet<>(dbCodes);
    onlyInDb.removeAll(codeCodes);
    assertThat(onlyInDb)
        .as("a seeded permission no code can reference would be ungrantable and untestable")
        .isEmpty();

    Set<String> onlyInCode = new HashSet<>(codeCodes);
    onlyInCode.removeAll(dbCodes);
    assertThat(onlyInCode)
        .as("a code with no seeded row could never satisfy its own FK, so it must not exist")
        .isEmpty();
  }

  @Test
  void moduleAndActionColumnsMatchEachCodeOwnFields() {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList("SELECT code, `module`, `action`, description FROM permissions");

    assertThat(rows).hasSize(PermissionCatalog.SIZE);
    for (Map<String, Object> row : rows) {
      String code = (String) row.get("code");
      PermissionCatalog permission =
          PermissionCatalog.find(code)
              .orElseThrow(() -> new AssertionError("seeded code missing from catalog: " + code));
      assertThat(row.get("module")).as("module column of %s", code).isEqualTo(permission.module());
      assertThat(row.get("action")).as("action column of %s", code).isEqualTo(permission.action());
      assertThat(row.get("description"))
          .as("description column of %s", code)
          .isEqualTo(permission.description());
    }
  }

  @Test
  void everyCodeIsItsOwnModuleConcatAction() {
    for (PermissionCatalog permission : PermissionCatalog.values()) {
      assertThat(permission.code())
          .as("%s follows MODULE_ACTION", permission)
          .isEqualTo(permission.compound());
      assertThat(permission.module()).doesNotContain("_").isNotBlank();
      assertThat(permission.action()).isNotBlank();
      assertThat(permission.description()).isNotBlank();
    }
  }

  @Test
  void thePlatformOnlySetIsExactlyTheTwoTenantLifecycleCodes() {
    assertThat(PermissionCatalog.PLATFORM_ONLY)
        .containsExactlyInAnyOrder(
            PermissionCatalog.TENANT_SUSPEND, PermissionCatalog.TENANT_ACTIVATE);
    for (PermissionCatalog permission : PermissionCatalog.values()) {
      assertThat(permission.isPlatformOnly())
          .as("%s is platform-only only when it is one of the two", permission)
          .isEqualTo(
              permission == PermissionCatalog.TENANT_SUSPEND
                  || permission == PermissionCatalog.TENANT_ACTIVATE);
    }
    assertThat(PermissionCatalog.codes()).contains("TENANT_SUSPEND", "TENANT_ACTIVATE");
  }

  @Test
  void lookupHelpersRejectUnknownCodesWithoutThrowing() {
    assertThat(PermissionCatalog.contains("NOT_A_PERMISSION")).isFalse();
    assertThat(PermissionCatalog.find(null)).isEmpty();
    assertThat(PermissionCatalog.of("PATIENT", "VIEW")).contains(PermissionCatalog.PATIENT_VIEW);
    assertThat(PermissionCatalog.of("PATIENT", "NOPE")).isEmpty();
    assertThat(PermissionCatalog.find("PATIENT_VIEW_DIAGNOSIS"))
        .contains(PermissionCatalog.PATIENT_VIEW_DIAGNOSIS);
  }
}
