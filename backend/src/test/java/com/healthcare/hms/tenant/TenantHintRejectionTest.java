package com.healthcare.hms.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * P4.3 — client tenant hints cannot override the authenticated tenant (ROADMAP P4.3, TDD section
 * 6.2.3): the {@code X-Tenant-ID} header may be absent or may match, and is rejected with 404 when
 * it does not — it is never read as a tenant selector.
 *
 * <p>Direct filter tests with a mock chain, so the "chain is never invoked" half of the contract is
 * observable. The path and body legs are documented in the filter: no endpoint accepts a tenant id
 * yet, and P2.3 already rejects a {@code tenantId} body property with 422.
 */
class TenantHintRejectionTest {

  private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final TenantContextFilter filter = new TenantContextFilter(objectMapper);

  @AfterEach
  void reset() {
    TenantContext.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  void absentHintContinuesAndTheContextIsTheJwtTenant() throws Exception {
    authenticateAs(TENANT_A);
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request(null), response, chain);

    assertThat(chain.invoked).isTrue();
    assertThat(chain.observed).containsExactly(TENANT_A);
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(TenantContext.find()).isEmpty();
  }

  @Test
  void matchingHintIsHarmlessAndTheContextDoesNotChange() throws Exception {
    authenticateAs(TENANT_A);
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request(TENANT_A.toString()), response, chain);

    assertThat(chain.invoked).isTrue();
    assertThat(chain.observed).containsExactly(TENANT_A);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void mismatchingHintIsRejectedWith404AndTheChainIsNeverInvoked() throws Exception {
    authenticateAs(TENANT_A);
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request(TENANT_B.toString()), response, chain);

    assertThat(chain.invoked).as("a client hint must never reach the application").isFalse();
    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.getContentType()).startsWith("application/json");
    assertThat(response.getContentAsString())
        .contains("\"code\":\"NOT_FOUND\"")
        .contains("\"success\":false");
    assertThat(TenantContext.find()).isEmpty();
  }

  @Test
  void aHintCarryingAnotherValidTenantNeverBecomesTheContext() throws Exception {
    authenticateAs(TENANT_A);
    RecordingChain matchingHint = new RecordingChain();

    filter.doFilter(request(TENANT_A.toString()), new MockHttpServletResponse(), matchingHint);
    assertThat(matchingHint.observed).containsExactly(TENANT_A);

    RecordingChain foreignHint = new RecordingChain();
    filter.doFilter(request(TENANT_B.toString()), new MockHttpServletResponse(), foreignHint);

    assertThat(foreignHint.observed).isEmpty();
    assertThat(matchingHint.observed).doesNotContain(TENANT_B);
    assertThat(TenantContext.find()).isEmpty();
  }

  @Test
  void anUnparseableHintIsRejectedToo() throws Exception {
    authenticateAs(TENANT_A);
    RecordingChain chain = new RecordingChain();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request("hospital-a"), response, chain);

    assertThat(chain.invoked).isFalse();
    assertThat(response.getStatus()).isEqualTo(404);
  }

  @Test
  void hintWithoutATokenLeavesThePublicPathUnaffected() throws Exception {
    SecurityContextHolder.clearContext();
    RecordingChain chain = new RecordingChain();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request(TENANT_B.toString()), response, chain);

    assertThat(chain.invoked).isTrue();
    assertThat(chain.observed).isEmpty();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(TenantContext.find()).isEmpty();
  }

  // -------------------------------------------------------------------------
  // helpers
  // -------------------------------------------------------------------------

  private MockHttpServletRequest request(String hint) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/patients");
    if (hint != null) {
      request.addHeader(TenantContextFilter.TENANT_HINT_HEADER, hint);
    }
    return request;
  }

  private void authenticateAs(UUID tenantId) {
    Jwt jwt =
        Jwt.withTokenValue("unit-test-token")
            .header("alg", "HS256")
            .claims(claim -> claim.put(TenantContextFilter.TENANT_CLAIM, tenantId.toString()))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
  }

  /** Records whether the chain ran and which tenants it could observe. */
  private static final class RecordingChain implements FilterChain {
    private final List<UUID> observed = new ArrayList<>();
    private boolean invoked;

    @Override
    public void doFilter(
        jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
      invoked = true;
      TenantContext.find().ifPresent(observed::add);
    }
  }
}
