package com.healthcare.hms.authz.masking;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.hms.authz.PermissionCatalog;
import com.healthcare.hms.authz.SystemRoleBundle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Decision <b>D8</b>'s proof: the three clinical fields are gone for a caller who holds none of the
 * permissions, present for one who holds all three, and split exactly along the rule table for
 * everyone in between — with the envelope around them untouched.
 *
 * <p>The payloads are clinical-<i>shaped</i> rather than real, because no clinical response type
 * exists yet: P10.8, P12.7 and P17.4 own those DTOs and are the first to wire this service in (D8
 * scope note). What is asserted here is the contract those tasks will rely on.
 */
@SpringBootTest
class FieldMaskingTest {

  @Autowired private FieldMaskingService masking;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void aReceptionistPayloadCarriesNoClinicalFieldAnywhereInIt() throws Exception {
    EnvelopePayload masked =
        masking.mask(envelope(), SystemRoleBundle.RECEPTIONIST.permissionCodes());

    JsonNode tree = objectMapper.valueToTree(masked);
    assertThat(tree.findValues("diagnosis"))
        .as("receptionist holds neither PATIENT_VIEW_DIAGNOSIS nor PATIENT_VIEW_NOTES")
        .isEmpty();
    assertThat(tree.findValues("notes")).isEmpty();
    assertThat(tree.findValues("vitals"))
        .as("receptionist holds no VISIT_VIEW, so vitals go too")
        .isEmpty();
    assertThat(tree.path("data").path("patientName").asText())
        .as("the non-clinical half of the same row stays readable")
        .isEqualTo("Ada Lovelace");
    assertThat(objectMapper.writeValueAsString(masked))
        .as(
            "omitted, not blanked: the key itself is absent (API section 6, DESIGN_SYSTEM section 7)")
        .doesNotContain("diagnosis")
        .doesNotContain("\"notes\"")
        .doesNotContain("vitals");
  }

  @Test
  void aDoctorHoldingAllThreePermissionsSeesEveryClinicalField() {
    ClinicalPayload masked = masking.mask(payload(), SystemRoleBundle.DOCTOR.permissionCodes());

    JsonNode tree = objectMapper.valueToTree(masked);
    assertThat(tree.path("diagnosis").asText()).isEqualTo("Type 2 diabetes mellitus");
    assertThat(tree.path("notes").asText()).isEqualTo("Review HbA1c in twelve weeks.");
    assertThat(tree.path("vitals").path("heartRate").asInt()).isEqualTo(72);
    assertThat(masked.patientName()).isEqualTo("Ada Lovelace");
  }

  @Test
  void aPartialGrantSeesExactlyTheFieldItsPermissionGates() {
    ClinicalPayload nurse = masking.mask(payload(), SystemRoleBundle.NURSE.permissionCodes());
    JsonNode nurseTree = objectMapper.valueToTree(nurse);
    assertThat(nurseTree.path("vitals").path("heartRate").asInt())
        .as("vitals ride on VISIT_VIEW, which the nurse holds")
        .isEqualTo(72);
    assertThat(nurseTree.findValues("diagnosis"))
        .as("diagnosis and notes are the section 6.4 Configurable cells, default off")
        .isEmpty();
    assertThat(nurseTree.findValues("notes")).isEmpty();

    ClinicalPayload diagnosisOnly =
        masking.mask(payload(), Set.of(PermissionCatalog.PATIENT_VIEW_DIAGNOSIS.code()));
    JsonNode diagnosisTree = objectMapper.valueToTree(diagnosisOnly);
    assertThat(diagnosisTree.path("diagnosis").asText()).isEqualTo("Type 2 diabetes mellitus");
    assertThat(diagnosisTree.findValues("notes")).isEmpty();
    assertThat(diagnosisTree.findValues("vitals")).isEmpty();
  }

  @Test
  void anUnknownOrEmptyAuthoritySetOmitsAllThreeClinicalFields() {
    List<Set<String>> authoritySets = List.of(Set.of(), Set.of("NOT_A_CATALOG_CODE", "ROLE_ADMIN"));

    for (Set<String> authorities : authoritySets) {
      JsonNode tree = objectMapper.valueToTree(masking.mask(payload(), authorities));
      assertThat(tree.findValues("diagnosis")).isEmpty();
      assertThat(tree.findValues("notes")).isEmpty();
      assertThat(tree.findValues("vitals")).isEmpty();
      assertThat(tree.path("patientName").asText()).isEqualTo("Ada Lovelace");
    }
  }

  @Test
  void aNullAuthoritySetOmitsTheClinicalFieldsToo() {
    JsonNode tree = objectMapper.valueToTree(masking.mask(payload(), null));

    assertThat(tree.findValues("diagnosis")).isEmpty();
    assertThat(tree.findValues("notes")).isEmpty();
    assertThat(tree.findValues("vitals")).isEmpty();
  }

  @Test
  void maskingDoesNotDisturbTheEnvelopePaginationOrTraceId() {
    EnvelopePayload masked =
        masking.mask(envelope(), SystemRoleBundle.RECEPTIONIST.permissionCodes());

    assertThat(masked.status()).isEqualTo("OK");
    assertThat(masked.traceId()).isEqualTo("01J8ZQ4M6K7R2T9V0N3P5B1CDX");
    assertThat(masked.meta().page()).isEqualTo(3);
    assertThat(masked.meta().size()).isEqualTo(25);
    assertThat(masked.meta().totalElements()).isEqualTo(412);
    assertThat(masked.data()).isNotNull();
    assertThat(masked.data().attendingDoctor())
        .as("the envelope's clinical rows lose their fields, nothing else")
        .isEqualTo("Dr. Grady");
  }

  @Test
  void everyPermissionTheRulesTableNamesIsAMemberOfTheCatalog() {
    assertThat(MaskingRules.byPermission())
        .containsOnlyKeys(
            PermissionCatalog.PATIENT_VIEW_DIAGNOSIS.code(),
            PermissionCatalog.PATIENT_VIEW_NOTES.code(),
            PermissionCatalog.VISIT_VIEW.code());

    assertThat(MaskingRules.byPermission().keySet())
        .as("a rule naming a code that does not exist would never fire (D6: no new codes)")
        .allMatch(PermissionCatalog::contains);

    assertThat(MaskingRules.byPermission())
        .containsEntry(PermissionCatalog.PATIENT_VIEW_DIAGNOSIS.code(), Set.of("diagnosis"))
        .containsEntry(PermissionCatalog.PATIENT_VIEW_NOTES.code(), Set.of("notes"))
        .containsEntry(PermissionCatalog.VISIT_VIEW.code(), Set.of("vitals"));
  }

  @Test
  void aPayloadWithNothingToWithholdIsReturnedUntouched() {
    ClinicalPayload original = payload();

    assertThat(masking.mask(original, SystemRoleBundle.DOCTOR.permissionCodes()))
        .isSameAs(original);
    assertThat(masking.mask((Object) null, Set.of())).isNull();
  }

  @Test
  void aJsonShapedPayloadLosesTheKeysFromTheMapItself() {
    Map<String, Object> clinical = new LinkedHashMap<>();
    clinical.put("id", "8f1d5f2c-0a3b-4f6e-9c1a-2b3d4e5f6a7b");
    clinical.put("patientName", "Ada Lovelace");
    clinical.put("diagnosis", "Type 2 diabetes mellitus");
    clinical.put("notes", "Review HbA1c in twelve weeks.");
    clinical.put("vitals", Map.of("heartRate", 72));
    clinical.put("attendingDoctor", "Dr. Grady");
    Map<String, Object> envelope = new LinkedHashMap<>();
    envelope.put("status", "OK");
    envelope.put("traceId", "01J8ZQ4M6K7R2T9V0N3P5B1CDX");
    envelope.put("meta", Map.of("page", 3, "size", 25, "totalElements", 412));
    envelope.put("data", clinical);

    Map<String, Object> masked =
        masking.mask(envelope, SystemRoleBundle.RECEPTIONIST.permissionCodes());

    assertThat(masked)
        .as("the envelope keeps every key it was built with")
        .containsOnlyKeys("status", "traceId", "meta", "data");
    @SuppressWarnings("unchecked")
    Map<String, Object> maskedClinical = (Map<String, Object>) masked.get("data");
    assertThat(maskedClinical)
        .as("here removal is final: the keys are gone from the structure, not nulled in it")
        .containsOnlyKeys("id", "patientName", "attendingDoctor");
  }

  // -------------------------------------------------------------------------
  // Fixtures
  // -------------------------------------------------------------------------

  private ClinicalPayload payload() {
    JsonNode vitals = objectMapper.createObjectNode().put("heartRate", 72).put("oxygen", 98);
    return new ClinicalPayload(
        "8f1d5f2c-0a3b-4f6e-9c1a-2b3d4e5f6a7b",
        "Ada Lovelace",
        "Type 2 diabetes mellitus",
        "Review HbA1c in twelve weeks.",
        vitals,
        "Dr. Grady");
  }

  private EnvelopePayload envelope() {
    return new EnvelopePayload(
        "OK", "01J8ZQ4M6K7R2T9V0N3P5B1CDX", new PageMeta(3, 25, 412), payload());
  }

  /**
   * A clinical-shaped row: three masked fields, two that never are.
   *
   * <p>Nulls are dropped on serialisation, the way {@code ApiResponse} in section 3 of API.md is —
   * which is what turns a withheld field into a key the client never receives rather than a {@code
   * "diagnosis": null} it would have to interpret (DESIGN_SYSTEM section 7).
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record ClinicalPayload(
      String id,
      String patientName,
      String diagnosis,
      String notes,
      JsonNode vitals,
      String attendingDoctor) {}

  record PageMeta(int page, int size, long totalElements) {}

  /** The envelope API section 3 wraps every list and detail response in. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record EnvelopePayload(String status, String traceId, PageMeta meta, ClinicalPayload data) {}
}
