package com.healthcare.hms.authz.masking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Decision <b>D8</b> — field-level masking by permission: a response is serialised to a Jackson
 * tree, the fields this caller may not see are <b>removed from the tree</b>, and the tree is
 * converted back to the caller's own type.
 *
 * <p><b>Omit, never blank.</b> API section 6 says "masked fields are omitted" and DESIGN_SYSTEM
 * section 7 says sensitive fields are "omitted, not displayed as blank or 'N/A'". Removing the
 * member from the tree is what makes that true at every layer at once: the JSON has no {@code
 * "diagnosis": ""} for a frontend to render, no {@code null} for a client to distinguish from a
 * genuinely empty value, and no key for a log line to echo.
 *
 * <p><b>Fail closed, twice over.</b> {@link MaskingRules#withheldPaths} decides what to remove from
 * the permission set alone — an empty or unknown authority set withholds everything — and this
 * class only ever removes, so there is no path by which a field it was told to withhold survives.
 *
 * <p><b>Applies to any response object.</b> The conversion back uses {@code response.getClass()},
 * so a record, a DTO, a {@code Map} or a {@code JsonNode} all round-trip as themselves; anything
 * that is not an object (a scalar, an array at the root) is returned untouched, because a field
 * cannot be withheld from a value that has no fields.
 *
 * <p><b>Wiring.</b> First production use is P10.8 (receptionist patient detail), then P12.7 and
 * P17.4 — Phase 6 ships the service and its proof payload, not a clinical endpoint, because those
 * DTOs do not exist yet (decision D8's scope note). The provisional rules it applies are {@link
 * MaskingRules}, whose table deliberately names only codes the V2 seed already contains (D6).
 *
 * <p><b>What "omitted" requires of the response type.</b> The tree is genuinely stripped here — the
 * member is gone before anything is written — but a value handed back to Jackson as a plain record
 * or DTO comes back with {@code null} in the masked slots, and a default serializer would print
 * those {@code null}s. Omission on the wire therefore holds for a payload that is already a JSON
 * shape ({@code Map}, {@code ObjectNode} — removal is final there), or for a type that serialises
 * without nulls: {@link com.healthcare.hms.common.api.ApiResponse} is annotated
 * {@code @JsonInclude(NON_NULL)} for exactly this reason, and the clinical DTOs P10.8 writes must
 * be either. Never rely on the client to hide a {@code null} — DESIGN_SYSTEM section 7 is about
 * what the client is <i>sent</i>.
 */
@Service
public class FieldMaskingService {

  private final ObjectMapper objectMapper;

  public FieldMaskingService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Returns {@code response} without the field paths this caller is not entitled to.
   *
   * @param response the object about to be written to the response body; {@code null} passes
   *     through, because there is nothing to leak
   * @param authorities the caller's permission codes — the same strings {@code
   *     PermissionAuthoritiesFilter} put on the authentication, so a mask and the
   *     {@code @RequirePermission} gate in front of the route read one authority set, not two
   */
  public <T> T mask(T response, Set<String> authorities) {
    if (response == null) {
      return null;
    }
    Set<String> withheld = MaskingRules.withheldPaths(authorities);
    if (withheld.isEmpty()) {
      // Nothing to withhold: hand back the original instance rather than a re-converted copy,
      // so masking a caller who may see everything costs no serialisation at all.
      return response;
    }
    JsonNode tree = copyOf(response);
    if (!(tree instanceof ObjectNode root)) {
      return response;
    }
    removeFieldPaths(root, withheld);
    @SuppressWarnings("unchecked")
    T masked = (T) objectMapper.convertValue(root, response.getClass());
    return masked;
  }

  private JsonNode copyOf(Object response) {
    if (response instanceof JsonNode node) {
      return node.deepCopy();
    }
    return objectMapper.valueToTree(response);
  }

  /**
   * Removes every matching member, at any depth.
   *
   * <p>A rule's path is anchored where the payload happens to carry the field: the same {@code
   * diagnosis} sits at the root of a clinical DTO and under {@code data} inside an envelope, and a
   * mask that only looked at the top level would let the nested copy through. Objects nested in
   * arrays are visited too, so a list of encounters is masked as thoroughly as a single one.
   */
  private static void removeFieldPaths(ObjectNode node, Set<String> paths) {
    List<String> matched = new ArrayList<>();
    Iterator<String> names = node.fieldNames();
    while (names.hasNext()) {
      String name = names.next();
      if (paths.contains(name)) {
        matched.add(name);
      } else {
        removeFieldPaths(node.get(name), paths);
      }
    }
    matched.forEach(node::remove);
  }

  private static void removeFieldPaths(JsonNode value, Set<String> paths) {
    if (value == null) {
      return;
    }
    if (value.isObject()) {
      removeFieldPaths((ObjectNode) value, paths);
    } else if (value.isArray()) {
      value.forEach(child -> removeFieldPaths(child, paths));
    }
  }
}
