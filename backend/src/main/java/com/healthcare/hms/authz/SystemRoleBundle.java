package com.healthcare.hms.authz;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The six role bundles every tenant is provisioned with (decision <b>D4</b>, ROADMAP P6.2).
 *
 * <p>They are code, not rows, because a Flyway migration cannot know the ids of tenants that do not
 * exist yet: {@link SystemRoleProvisioner} writes these bundles per tenant inside the registration
 * transaction. The bundle <i>contents</i> follow PRD section 3's restriction column and the PRD
 * section 6.4 field matrix, with two deliberate choices recorded here:
 *
 * <ul>
 *   <li>{@link #ADMIN} holds every catalog row except the two platform-only codes. Those two carry
 *       out a <i>platform</i> operation on a tenant and stay reserved for the GAP-1 platform tenant
 *       at P8.5, so even the hospital's own administrator can never hold them.
 *   <li>PRD section 6.4 marks Nurse <i>diagnosis</i> and <i>clinical notes</i> as "Configurable". A
 *       system bundle is not the place to make that choice, so the default is <b>off</b> &mdash;
 *       fail closed &mdash; and a hospital that wants them grants them through a custom role
 *       (FR-3.5), which is exactly what configurability means.
 * </ul>
 *
 * <p>None of the five non-administrative bundles holds a {@code ROLE_*} code: role management is an
 * administrator's job, and P6.6's matrix depends on a non-admin caller being denied it.
 */
public enum SystemRoleBundle {

  /** Hospital administrator (PRD section 3): settings, departments, staff and roles. */
  ADMIN("ADMIN", allExceptPlatformOnly()),

  /**
   * Doctor (PRD section 3): consult, diagnose, prescribe, order, view history — <i>which</i>
   * patients is still governed by the resource policy of TDD section 8.3 (OQ-2, wired at P10.8), so
   * this bundle grants the capability and never the row. PRD section 3 assigns role management to
   * the Hospital Admin alone, so no {@code ROLE_*} code appears below — including the read one.
   */
  DOCTOR(
      "DOCTOR",
      of(
          PermissionCatalog.DASHBOARD_VIEW,
          PermissionCatalog.DEPARTMENT_VIEW,
          PermissionCatalog.STAFF_VIEW,
          PermissionCatalog.PATIENT_VIEW,
          PermissionCatalog.PATIENT_CREATE,
          PermissionCatalog.PATIENT_UPDATE,
          PermissionCatalog.PATIENT_EXPORT,
          PermissionCatalog.PATIENT_VIEW_DIAGNOSIS,
          PermissionCatalog.PATIENT_VIEW_NOTES,
          PermissionCatalog.APPOINTMENT_VIEW,
          PermissionCatalog.APPOINTMENT_CREATE,
          PermissionCatalog.APPOINTMENT_UPDATE,
          PermissionCatalog.APPOINTMENT_CANCEL,
          PermissionCatalog.VISIT_VIEW,
          PermissionCatalog.VISIT_CREATE,
          PermissionCatalog.VISIT_UPDATE,
          PermissionCatalog.VISIT_FINALIZE,
          PermissionCatalog.PRESCRIPTION_VIEW,
          PermissionCatalog.PRESCRIPTION_CREATE,
          PermissionCatalog.PRESCRIPTION_UPDATE,
          PermissionCatalog.PRESCRIPTION_EXPORT,
          PermissionCatalog.MEDICINE_VIEW,
          PermissionCatalog.LAB_VIEW,
          PermissionCatalog.LAB_CREATE,
          PermissionCatalog.LAB_UPDATE,
          PermissionCatalog.DOCUMENT_VIEW,
          PermissionCatalog.DOCUMENT_UPLOAD,
          PermissionCatalog.DOCUMENT_DOWNLOAD,
          PermissionCatalog.NOTIFICATION_VIEW,
          PermissionCatalog.SEARCH_QUERY)),

  /**
   * Nurse (PRD section 3): record vitals, assist visits, manage the queue; <b>no prescribing</b>.
   * Vitals ride on {@code VISIT_VIEW}, which is what the P6.5 masking rule uses. Diagnosis and
   * clinical notes are the section 6.4 "Configurable" cells and default off (see the class
   * javadoc); prescription is <i>view only</i>.
   */
  NURSE(
      "NURSE",
      of(
          PermissionCatalog.DASHBOARD_VIEW,
          PermissionCatalog.STAFF_VIEW,
          PermissionCatalog.PATIENT_VIEW,
          PermissionCatalog.PATIENT_UPDATE,
          PermissionCatalog.APPOINTMENT_VIEW,
          PermissionCatalog.APPOINTMENT_CREATE,
          PermissionCatalog.APPOINTMENT_UPDATE,
          PermissionCatalog.APPOINTMENT_CANCEL,
          PermissionCatalog.VISIT_VIEW,
          PermissionCatalog.VISIT_CREATE,
          PermissionCatalog.VISIT_UPDATE,
          PermissionCatalog.PRESCRIPTION_VIEW,
          PermissionCatalog.MEDICINE_VIEW,
          PermissionCatalog.LAB_VIEW,
          PermissionCatalog.LAB_CREATE,
          PermissionCatalog.DOCUMENT_VIEW,
          PermissionCatalog.NOTIFICATION_VIEW,
          PermissionCatalog.SEARCH_QUERY)),

  /**
   * Receptionist (PRD section 3): register patients, book appointments, manage the queue — and
   * <b>no diagnosis, prescriptions or clinical notes</b>, which PRD section 6.4 states as four
   * consecutive crosses: vitals, diagnosis, prescription and notes are all absent here.
   */
  RECEPTIONIST(
      "RECEPTIONIST",
      of(
          PermissionCatalog.DASHBOARD_VIEW,
          PermissionCatalog.PATIENT_VIEW,
          PermissionCatalog.PATIENT_CREATE,
          PermissionCatalog.PATIENT_UPDATE,
          PermissionCatalog.APPOINTMENT_VIEW,
          PermissionCatalog.APPOINTMENT_CREATE,
          PermissionCatalog.APPOINTMENT_UPDATE,
          PermissionCatalog.APPOINTMENT_CANCEL,
          PermissionCatalog.DOCUMENT_VIEW,
          PermissionCatalog.DOCUMENT_UPLOAD,
          PermissionCatalog.NOTIFICATION_VIEW,
          PermissionCatalog.SEARCH_QUERY)),

  /** Lab / imaging staff (PRD section 3): enter and upload results, order-scoped. */
  LAB_TECHNICIAN(
      "LAB_TECHNICIAN",
      of(
          PermissionCatalog.DASHBOARD_VIEW,
          PermissionCatalog.PATIENT_VIEW,
          PermissionCatalog.VISIT_VIEW,
          PermissionCatalog.LAB_VIEW,
          PermissionCatalog.LAB_CREATE,
          PermissionCatalog.LAB_UPDATE,
          PermissionCatalog.DOCUMENT_VIEW,
          PermissionCatalog.DOCUMENT_UPLOAD,
          PermissionCatalog.DOCUMENT_DOWNLOAD,
          PermissionCatalog.NOTIFICATION_VIEW,
          PermissionCatalog.SEARCH_QUERY)),

  /**
   * Billing staff (PRD section 3): invoices and payments, with no clinical detail beyond the
   * billable items &mdash; section 6.4's four crosses again, though the encounter itself must be
   * readable to be billed.
   */
  BILLING(
      "BILLING",
      of(
          PermissionCatalog.DASHBOARD_VIEW,
          PermissionCatalog.PATIENT_VIEW,
          PermissionCatalog.APPOINTMENT_VIEW,
          PermissionCatalog.VISIT_VIEW,
          PermissionCatalog.PRESCRIPTION_VIEW,
          PermissionCatalog.BILLING_VIEW,
          PermissionCatalog.BILLING_CREATE,
          PermissionCatalog.BILLING_UPDATE,
          PermissionCatalog.DOCUMENT_VIEW,
          PermissionCatalog.NOTIFICATION_VIEW,
          PermissionCatalog.SEARCH_QUERY));

  private final String roleName;
  private final Set<PermissionCatalog> permissions;

  SystemRoleBundle(String roleName, Set<PermissionCatalog> permissions) {
    this.roleName = roleName;
    this.permissions = Collections.unmodifiableSet(permissions);
  }

  /**
   * The role name written into {@code roles.name}; unique per tenant by {@code
   * uq_roles_tenant_name}.
   */
  public String roleName() {
    return roleName;
  }

  /** The permission codes this bundle grants, as catalog constants. */
  public Set<PermissionCatalog> permissions() {
    return permissions;
  }

  /** The same set as code strings, which is what every write path needs. */
  public Set<String> permissionCodes() {
    return permissions.stream()
        .map(PermissionCatalog::code)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  /** Every bundle name, in declaration order — ADMIN plus the five staff bundles. */
  public static List<String> allRoleNames() {
    return Arrays.stream(values()).map(SystemRoleBundle::roleName).toList();
  }

  private static Set<PermissionCatalog> of(PermissionCatalog... permissions) {
    EnumSet<PermissionCatalog> set = EnumSet.noneOf(PermissionCatalog.class);
    Collections.addAll(set, permissions);
    return set;
  }

  private static Set<PermissionCatalog> allExceptPlatformOnly(
      PermissionCatalog... additionallyExcluded) {
    EnumSet<PermissionCatalog> set = EnumSet.allOf(PermissionCatalog.class);
    set.removeAll(PermissionCatalog.PLATFORM_ONLY);
    Collections.addAll(set, additionallyExcluded);
    return set;
  }
}
