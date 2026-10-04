package com.healthcare.hms.authz.masking;

import com.healthcare.hms.authz.PermissionCatalog;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The field-masking table, decision <b>D8</b>: which permission must be held for a field to appear
 * in a response at all.
 *
 * <p>The provisional rule set, all against codes that already exist in the V2 seed (decision D6 —
 * no catalog row and no migration is added here):
 *
 * <ul>
 *   <li>{@link PermissionCatalog#PATIENT_VIEW_DIAGNOSIS} &rarr; {@code diagnosis}
 *   <li>{@link PermissionCatalog#PATIENT_VIEW_NOTES} &rarr; {@code notes}
 *   <li>{@link PermissionCatalog#VISIT_VIEW} &rarr; {@code vitals} (there is deliberately no {@code
 *       VITALS} code: vitals ride on the visit, which is what PRD section 6.4's matrix means by
 *       receptionist ✗ / nurse ✓ / doctor ✓ / billing ✗)
 * </ul>
 *
 * <p><b>Fail closed by construction.</b> A field is withheld unless its permission is present in
 * the authority set, so an empty set, an unknown set and a set of codes the table has never heard
 * of all omit everything the table can name — there is no default grant to fall back on, exactly as
 * {@code PermissionAuthoritiesFilter} treats an account with no roles.
 *
 * <p><b>On "per-type".</b> D8 describes the table as per-type because that is what a production
 * wiring will need: P10.8's patient detail and P12.7's visit note mask different fields. Phase 6
 * ships no clinical response type (they are Phase 10/12's, which is why D8 scopes this to a proof
 * payload), so the provisional rules are held once and applied to whatever payload {@link
 * FieldMaskingService} is handed. The day a type needs its own row, this table gains a key of type
 * {@code Class<?>} — the service's contract does not change.
 *
 * <p>Values are <b>paths</b>, so a rule can name a nested field when the wiring needs one; every
 * Phase 6 rule is a single segment, which {@link FieldMaskingService} matches at any depth so that
 * the same field is removed whether it sits at the root of a DTO or under {@code data} of an
 * envelope.
 */
public final class MaskingRules {

  private static final Map<String, Set<String>> BY_PERMISSION = build();

  private MaskingRules() {}

  /**
   * The whole table: permission code &rarr; the field paths it gates.
   *
   * <p>Exposes the rules so a test can assert every permission it names is a member of {@link
   * PermissionCatalog} — a rule pointing at a code that does not exist would silently never fire.
   */
  public static Map<String, Set<String>> byPermission() {
    return BY_PERMISSION;
  }

  /**
   * The paths this authority set may <b>not</b> see, ready to remove from a payload.
   *
   * @param authorities the caller's permission codes; {@code null} is treated as empty, so an
   *     unauthenticated context withholds every field the table names
   */
  public static Set<String> withheldPaths(Set<String> authorities) {
    Set<String> held = authorities == null ? Set.of() : authorities;
    Set<String> withheld = new LinkedHashSet<>();
    BY_PERMISSION.forEach(
        (permission, paths) -> {
          if (!held.contains(permission)) {
            withheld.addAll(paths);
          }
        });
    return Collections.unmodifiableSet(withheld);
  }

  private static Map<String, Set<String>> build() {
    Map<String, Set<String>> table = new LinkedHashMap<>();
    table.put(PermissionCatalog.PATIENT_VIEW_DIAGNOSIS.code(), Set.of("diagnosis"));
    table.put(PermissionCatalog.PATIENT_VIEW_NOTES.code(), Set.of("notes"));
    table.put(PermissionCatalog.VISIT_VIEW.code(), Set.of("vitals"));
    return Collections.unmodifiableMap(table);
  }
}
