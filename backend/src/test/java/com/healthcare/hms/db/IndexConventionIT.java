package com.healthcare.hms.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * P3.5 — permanent index/convention guard (plan section 5, rules 1-4).
 *
 * <p>The suite reads {@code information_schema} over <b>every</b> table the migrations have created
 * so far. It is meant to stay green for all later migrations: new tables are picked up
 * automatically, so a regression must be fixed in the migration, never by adding an exemption here.
 *
 * <p>Rule 1 is phrased "the FK column appears in some index", matching ENGINEERING_RULES 5.3
 * ("every foreign key column is indexed") and DATABASE section 4. Rule 2 covers the case that
 * actually matters for lookups: every <b>composite</b> index leads with {@code tenant_id}.
 *
 * <p>Naming note: MySQL reports every primary key index as {@code PRIMARY} regardless of what the
 * DDL called the constraint, and it auto-creates an index named after the foreign key whenever no
 * existing index can service that FK (today: {@code fk_audit_logs_users}, {@code
 * fk_user_roles_roles}). Both names still carry a DATABASE section 2 prefix, so {@code pk_} and
 * {@code fk_} are accepted on indexes while {@code PRIMARY} is accepted for primary keys.
 */
class IndexConventionIT {

  /** Tables without a {@code tenant_id} column (DATABASE section 1). */
  private static final Set<String> PLATFORM_TABLES = Set.of("tenants", "permissions");

  /** Flyway owns this table; it is not part of the product schema. */
  private static final String FLYWAY_TABLE = "flyway_schema_history";

  private static MigrationTestSupport db;

  @BeforeAll
  static void migrateFromEmptyDatabase() {
    db = MigrationTestSupport.withEmptyDatabase();
    db.migrate();
  }

  @AfterAll
  static void dropSchema() {
    if (db != null) {
      db.close();
    }
  }

  @Test
  void everyForeignKeyColumnIsIndexed() {
    List<Map<String, Object>> foreignKeys = foreignKeys();

    assertThat(foreignKeys).as("migrations must define at least one FK").isNotEmpty();

    List<String> unindexed = new ArrayList<>();
    for (Map<String, Object> fk : foreignKeys) {
      String table = column(fk, "table_name");
      String column = column(fk, "column_name");
      int indexes =
          db.count(
              "SELECT COUNT(*) FROM information_schema.statistics"
                  + " WHERE table_schema = ? AND table_name = ? AND column_name = ?",
              db.schema(),
              table,
              column);
      if (indexes == 0) {
        unindexed.add(table + "." + column);
      }
    }

    assertThat(unindexed)
        .as("ENGINEERING_RULES 5.3 / DATABASE section 4: every FK column is indexed")
        .isEmpty();
  }

  @Test
  void everyCompositeIndexLeadsWithTenantId() {
    List<Map<String, Object>> statistics =
        query(
            "SELECT table_name, index_name, seq_in_index, column_name"
                + " FROM information_schema.statistics"
                + " WHERE table_schema = ? AND table_name <> ?"
                + " ORDER BY table_name, index_name, seq_in_index",
            db.schema(),
            FLYWAY_TABLE);

    Map<String, List<String>> columnsByIndex = new LinkedHashMap<>();
    Map<String, String> tableByIndex = new LinkedHashMap<>();
    for (Map<String, Object> row : statistics) {
      String index = column(row, "table_name") + "." + column(row, "index_name");
      columnsByIndex
          .computeIfAbsent(index, key -> new ArrayList<>())
          .add(column(row, "column_name"));
      tableByIndex.putIfAbsent(index, column(row, "table_name"));
    }

    assertThat(columnsByIndex).isNotEmpty();

    List<String> violations = new ArrayList<>();
    for (Map.Entry<String, List<String>> index : columnsByIndex.entrySet()) {
      List<String> columns = index.getValue();
      if (columns.size() < 2) {
        continue; // single-column keys are exempt (plan rule 2)
      }
      if (PLATFORM_TABLES.contains(tableByIndex.get(index.getKey()))) {
        continue;
      }
      if (!"tenant_id".equals(columns.get(0))) {
        violations.add(index.getKey() + " -> " + columns);
      }
    }

    assertThat(violations)
        .as("ENGINEERING_RULES 5.2 / DATABASE section 4: composite indexes lead with tenant_id")
        .isEmpty();
  }

  @Test
  void noForeignKeyUsesDeleteCascade() {
    List<Map<String, Object>> constraints =
        query(
            "SELECT table_name, constraint_name, delete_rule"
                + " FROM information_schema.referential_constraints"
                + " WHERE constraint_schema = ? AND table_name <> ?",
            db.schema(),
            FLYWAY_TABLE);

    assertThat(constraints).isNotEmpty();
    assertThat(constraints)
        .as("ENGINEERING_RULES 5.4: no ON DELETE CASCADE")
        .allSatisfy(
            constraint ->
                assertThat(column(constraint, "delete_rule"))
                    .as(
                        "%s.%s",
                        column(constraint, "table_name"), column(constraint, "constraint_name"))
                    .isNotEqualTo("CASCADE"));
  }

  @Test
  void constraintAndIndexNamesFollowTheConvention() {
    List<String> violations = new ArrayList<>();

    Set<String> indexNames =
        new LinkedHashSet<>(
            db.jdbc()
                .queryForList(
                    "SELECT DISTINCT index_name FROM information_schema.statistics"
                        + " WHERE table_schema = ? AND table_name <> ?",
                    String.class,
                    db.schema(),
                    FLYWAY_TABLE));
    assertThat(indexNames).isNotEmpty();
    for (String name : indexNames) {
      // MySQL reports every primary key index as PRIMARY no matter what the DDL called the
      // constraint, and auto-creates an fk_-named index for any FK no existing index can serve.
      if (!name.equals("PRIMARY")
          && !name.startsWith("pk_")
          && !name.startsWith("fk_")
          && !name.startsWith("uq_")
          && !name.startsWith("idx_")) {
        violations.add("index " + name);
      }
    }

    List<Map<String, Object>> constraints =
        query(
            "SELECT table_name, constraint_name, constraint_type"
                + " FROM information_schema.table_constraints"
                + " WHERE constraint_schema = ? AND table_name <> ?",
            db.schema(),
            FLYWAY_TABLE);

    Set<String> seenTypes = new LinkedHashSet<>();
    for (Map<String, Object> constraint : constraints) {
      String type = column(constraint, "constraint_type");
      String name = column(constraint, "constraint_name");
      seenTypes.add(type);
      String expectedPrefix =
          switch (type) {
            case "PRIMARY KEY" -> "pk_";
            case "UNIQUE" -> "uq_";
            case "FOREIGN KEY" -> "fk_";
            case "CHECK" -> "chk_";
            default -> "";
          };
      boolean accepted =
          expectedPrefix.isEmpty()
              || name.startsWith(expectedPrefix)
              || ("PRIMARY KEY".equals(type) && name.equals("PRIMARY"));
      if (!accepted) {
        violations.add(type + " " + column(constraint, "table_name") + "." + name);
      }
    }

    assertThat(violations).as("DATABASE section 2: pk_/fk_/uq_/idx_ naming").isEmpty();
    assertThat(seenTypes)
        .as("the scan must actually see PK, UNIQUE, FOREIGN KEY and CHECK constraints")
        .contains("PRIMARY KEY", "UNIQUE", "FOREIGN KEY", "CHECK");
  }

  // -------------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------------

  private List<Map<String, Object>> foreignKeys() {
    return query(
        "SELECT table_name, column_name FROM information_schema.key_column_usage"
            + " WHERE constraint_schema = ? AND referenced_table_name IS NOT NULL"
            + " AND table_name <> ? ORDER BY table_name, ordinal_position",
        db.schema(),
        FLYWAY_TABLE);
  }

  /** {@code queryForList} with lower-cased column keys so label case never matters. */
  private static List<Map<String, Object>> query(String sql, Object... args) {
    List<Map<String, Object>> rows = db.jdbc().queryForList(sql, args);
    List<Map<String, Object>> normalized = new ArrayList<>(rows.size());
    for (Map<String, Object> row : rows) {
      Map<String, Object> copy = new LinkedHashMap<>();
      row.forEach((key, value) -> copy.put(key.toLowerCase(Locale.ROOT), value));
      normalized.add(copy);
    }
    return normalized;
  }

  private static String column(Map<String, Object> row, String name) {
    Object value = row.get(name);
    return value == null ? null : value.toString();
  }
}
