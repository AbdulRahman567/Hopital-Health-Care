package com.healthcare.hms.authz;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The authoritative permission catalog, in code (ROADMAP P6.1, TDD section 8.2).
 *
 * <p><b>This class is the code half of a contract that already exists.</b> The rows were seeded by
 * {@code V2__roles_permissions_and_seed.sql} at P3.3 (decision D3, which pre-declared "P6.1's
 * in-code catalog must reproduce these rows exactly"), and {@code MigrationV2IT} freezes the count
 * at {@value #SIZE}. Phase 6 therefore adds <b>no migration</b> (decision D6): {@code
 * PermissionCatalogTest} compares the two sets in <i>both</i> directions, so a seeded row nobody
 * can reference and a code nobody can grant both fail the build.
 *
 * <p><b>Growth rule.</b> A new permission is a new {@code Vx} migration <b>and</b> a new constant
 * here, in one commit, together with the two sanctioned bumps that forces ({@code MigrationV2IT}'s
 * frozen size and {@code MigrationIT}'s migration count). Nothing else in the codebase may spell a
 * permission as a raw string: annotations carry {@code @RequirePermission} values that this class
 * declares, role bundles reference {@link #code()}, and ArchUnit fails the build on a code that is
 * not a member of this enum.
 *
 * <p>The enum constant <i>is</i> the code ({@code MODULE_ACTION}), so {@link #code()} is {@link
 * #name()} and there is no second place for the string to drift.
 */
public enum PermissionCatalog {

  // TENANT — 4 rows (V2 seed block 1)
  TENANT_VIEW("TENANT", "VIEW", "View tenant details"),
  TENANT_UPDATE("TENANT", "UPDATE", "Update tenant settings"),
  TENANT_SUSPEND("TENANT", "SUSPEND", "Suspend a tenant"),
  TENANT_ACTIVATE("TENANT", "ACTIVATE", "Activate a tenant"),

  // DEPARTMENT — 4 rows (V2 seed block 2)
  DEPARTMENT_VIEW("DEPARTMENT", "VIEW", "List departments"),
  DEPARTMENT_CREATE("DEPARTMENT", "CREATE", "Create a department"),
  DEPARTMENT_UPDATE("DEPARTMENT", "UPDATE", "Update a department"),
  DEPARTMENT_DELETE("DEPARTMENT", "DELETE", "Remove a department"),

  // STAFF — 5 rows (V2 seed block 3)
  STAFF_VIEW("STAFF", "VIEW", "List staff members"),
  STAFF_CREATE("STAFF", "CREATE", "Create a staff account"),
  STAFF_UPDATE("STAFF", "UPDATE", "Update a staff account"),
  STAFF_DEACTIVATE("STAFF", "DEACTIVATE", "Deactivate a staff account"),
  STAFF_INVITE("STAFF", "INVITE", "Send a staff invitation"),

  // ROLE — 4 rows (V2 seed block 4)
  ROLE_VIEW("ROLE", "VIEW", "List roles"),
  ROLE_CREATE("ROLE", "CREATE", "Create a role"),
  ROLE_UPDATE("ROLE", "UPDATE", "Update a role"),
  ROLE_DELETE("ROLE", "DELETE", "Remove a role"),

  // PATIENT — 6 rows (V2 seed block 5)
  PATIENT_VIEW("PATIENT", "VIEW", "List patients"),
  PATIENT_CREATE("PATIENT", "CREATE", "Register a patient"),
  PATIENT_UPDATE("PATIENT", "UPDATE", "Update patient demographics"),
  PATIENT_EXPORT("PATIENT", "EXPORT", "Export patient data"),
  PATIENT_VIEW_DIAGNOSIS("PATIENT", "VIEW_DIAGNOSIS", "Read diagnosis fields"),
  PATIENT_VIEW_NOTES("PATIENT", "VIEW_NOTES", "Read clinical note fields"),

  // APPOINTMENT — 4 rows (V2 seed block 6)
  APPOINTMENT_VIEW("APPOINTMENT", "VIEW", "List appointments"),
  APPOINTMENT_CREATE("APPOINTMENT", "CREATE", "Book an appointment"),
  APPOINTMENT_UPDATE("APPOINTMENT", "UPDATE", "Reschedule an appointment"),
  APPOINTMENT_CANCEL("APPOINTMENT", "CANCEL", "Cancel an appointment"),

  // VISIT — 4 rows (V2 seed block 7)
  VISIT_VIEW("VISIT", "VIEW", "List visits"),
  VISIT_CREATE("VISIT", "CREATE", "Open a visit"),
  VISIT_UPDATE("VISIT", "UPDATE", "Edit an open visit"),
  VISIT_FINALIZE("VISIT", "FINALIZE", "Finalize a visit"),

  // PRESCRIPTION — 4 rows (V2 seed block 8)
  PRESCRIPTION_VIEW("PRESCRIPTION", "VIEW", "List prescriptions"),
  PRESCRIPTION_CREATE("PRESCRIPTION", "CREATE", "Issue a prescription"),
  PRESCRIPTION_UPDATE("PRESCRIPTION", "UPDATE", "Amend a prescription"),
  PRESCRIPTION_EXPORT("PRESCRIPTION", "EXPORT", "Export a prescription"),

  // MEDICINE — 3 rows (V2 seed block 9)
  MEDICINE_VIEW("MEDICINE", "VIEW", "List medicines"),
  MEDICINE_CREATE("MEDICINE", "CREATE", "Add a medicine"),
  MEDICINE_UPDATE("MEDICINE", "UPDATE", "Update a medicine"),

  // LAB — 3 rows (V2 seed block 10)
  LAB_VIEW("LAB", "VIEW", "List lab orders and results"),
  LAB_CREATE("LAB", "CREATE", "Order a lab test"),
  LAB_UPDATE("LAB", "UPDATE", "Record a lab result"),

  // DOCUMENT — 3 rows (V2 seed block 11)
  DOCUMENT_VIEW("DOCUMENT", "VIEW", "List documents"),
  DOCUMENT_UPLOAD("DOCUMENT", "UPLOAD", "Upload a document"),
  DOCUMENT_DOWNLOAD("DOCUMENT", "DOWNLOAD", "Download a document"),

  // BILLING — 3 rows (V2 seed block 12)
  BILLING_VIEW("BILLING", "VIEW", "Read invoices and payments"),
  BILLING_CREATE("BILLING", "CREATE", "Raise an invoice"),
  BILLING_UPDATE("BILLING", "UPDATE", "Adjust an invoice"),

  // AUDIT — 1 row (V2 seed block 13)
  AUDIT_VIEW("AUDIT", "VIEW", "Read audit log entries"),

  // NOTIFICATION — 2 rows (V2 seed block 14)
  NOTIFICATION_VIEW("NOTIFICATION", "VIEW", "Read notifications"),
  NOTIFICATION_MANAGE("NOTIFICATION", "MANAGE", "Configure notification delivery"),

  // SEARCH — 2 rows (V2 seed block 15)
  SEARCH_QUERY("SEARCH", "QUERY", "Run global search"),
  SEARCH_EXPORT("SEARCH", "EXPORT", "Export search results"),

  // DASHBOARD — 1 row (V2 seed block 16)
  DASHBOARD_VIEW("DASHBOARD", "VIEW", "Read dashboards");

  /** Row count frozen by {@code MigrationV2IT.CATALOG_SIZE}; change only with a new {@code Vx}. */
  public static final int SIZE = 53;

  /**
   * The two platform-only codes (decision D5). They carry out a <i>platform</i> operation on a
   * tenant row and are reserved for the platform tenant's own roles at P8.5 (GAP-1); a tenant role
   * may never hold them, so {@code RoleService} rejects them with 422.
   */
  public static final Set<PermissionCatalog> PLATFORM_ONLY =
      Collections.unmodifiableSet(EnumSet.of(TENANT_SUSPEND, TENANT_ACTIVATE));

  private final String module;
  private final String action;
  private final String description;

  PermissionCatalog(String module, String action, String description) {
    this.module = module;
    this.action = action;
    this.description = description;
  }

  /** The catalog code, i.e. {@code MODULE_ACTION}. Equal to the constant's own name. */
  public String code() {
    return name();
  }

  /** First half of the code, also the {@code permissions.module} column. */
  public String module() {
    return module;
  }

  /** Second half of the code, also the {@code permissions.action} column. */
  public String action() {
    return action;
  }

  /** Human-readable text stored in {@code permissions.description}. */
  public String description() {
    return description;
  }

  /** {@code module || '_' || action} — the invariant {@code PermissionCatalogTest} re-checks. */
  public String compound() {
    return module + "_" + action;
  }

  /** {@code true} when this permission may only ever be held by a platform role. */
  public boolean isPlatformOnly() {
    return PLATFORM_ONLY.contains(this);
  }

  /** Every code in the catalog, insertion-ordered. */
  public static Set<String> codes() {
    return Arrays.stream(values())
        .map(PermissionCatalog::code)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /**
   * Resolves a code string.
   *
   * @return the constant, or empty for a code that is not in the catalog
   */
  public static Optional<PermissionCatalog> find(String code) {
    if (code == null) {
      return Optional.empty();
    }
    for (PermissionCatalog permission : values()) {
      if (permission.code().equals(code)) {
        return Optional.of(permission);
      }
    }
    return Optional.empty();
  }

  /** Resolves a {@code (module, action)} pair, mirroring the seed's unique key. */
  public static Optional<PermissionCatalog> of(String module, String action) {
    if (module == null || action == null) {
      return Optional.empty();
    }
    return Arrays.stream(values())
        .filter(p -> p.module.equals(module) && p.action.equals(action))
        .findFirst();
  }

  /** {@code true} when {@code code} is a member of this catalog. */
  public static boolean contains(String code) {
    return find(code).isPresent();
  }
}
