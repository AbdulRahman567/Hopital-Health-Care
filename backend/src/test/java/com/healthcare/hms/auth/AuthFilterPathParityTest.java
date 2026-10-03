package com.healthcare.hms.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.healthcare.hms.config.CustomHeaderCsrfFilter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * SEC-2 &mdash; the rate-limit filter and the custom-header CSRF guard recognise exactly the routes
 * Spring Security and Spring MVC recognise.
 *
 * <p>Both filters used to decide "is this the login route?" by string-comparing {@code
 * getRequestURI()} with a constant. That comparison sees the raw, undecoded, still-parameterised
 * path a container hands over, so {@code /api/v1/auth/%6Cogin} — which Spring Security permits and
 * Spring MVC dispatches to {@code POST /login} — was neither charged nor guarded.
 *
 * <p>The filters now match with {@link
 * org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher}, the very type
 * {@code SecurityConfig.requestMatchers(...)} uses, so the three layers cannot drift apart again.
 *
 * <p><b>Two properties are asserted, and only two.</b> Let <i>handled</i> mean "the request reached
 * a {@code @PostMapping} handler" and <i>charged</i> mean "the address's {@code rl:ip:} counter
 * went up":
 *
 * <ul>
 *   <li>{@code handled ⇒ charged} — nothing that is served slips past the budget;
 *   <li>{@code reachedTheService ⇒ guarded} — nothing that is served slips past the CSRF header
 *       check.
 * </ul>
 *
 * <p>The converse is deliberately <b>not</b> asserted. {@code PathPattern} ignores path parameters,
 * so {@code /login;x=1} would be charged even though {@code StrictHttpFirewall} rejects it with a
 * 400 before the filter runs — an over-charge, which costs an attacker a unit of their own budget
 * and never costs an honest caller one, because such a request can never succeed.
 *
 * <p><b>Why raw request URIs.</b> {@code MockMvcRequestBuilders.post(uri)} runs the template
 * through {@code UriComponentsBuilder.encode()}, which turns an already-encoded {@code %6C} into
 * {@code %256C} — a URL no real container would ever produce, and one {@code StrictHttpFirewall}
 * rejects for its {@code %25}. The probes below therefore build the {@link MockHttpServletRequest}
 * directly, so the bytes on the wire are the bytes the filters see.
 */
@SpringBootTest(properties = {"hms.ratelimit.email-ip-limit=1", "hms.ratelimit.login-ip-limit=1"})
@AutoConfigureMockMvc
class AuthFilterPathParityTest {

  /** A response the handler produced, as opposed to one the security chain produced. */
  private static final Set<Integer> HANDLED = Set.of(202, 204, 208, 409, 422);

  /** What {@link InvalidRefreshTokenException} says; the entry point says something else. */
  private static final String SERVICE_MESSAGE = InvalidRefreshTokenException.MESSAGE;

  /** The entry point's own words — the proof that the handler was <i>not</i> reached. */
  private static final String ENTRY_POINT_MESSAGE = "Authentication required.";

  /**
   * Static because JUnit builds one test instance per method: an instance field would hand the same
   * address to two methods, and the second would find the first one's counter already spent.
   */
  private static final AtomicInteger ADDRESSES = new AtomicInteger(50);

  @Autowired private MockMvc mvc;

  // -------------------------------------------------------------------------
  // rate-limit scope
  // -------------------------------------------------------------------------

  @Test
  void everyHandledRouteCostsOneUnitOfTheAddressBudget() throws Exception {
    for (String uri : ALL_RATE_LIMIT_ROUTES) {
      Probe result = probe(uri);
      if (HANDLED.contains(result.status())) {
        assertThat(result.charged())
            .as("%s was served (%d) but did not charge the address", uri, result.status())
            .isTrue();
      }
    }
  }

  /**
   * The routes the old string comparison missed, asserted one by one rather than through the
   * implication above, so a future change which simply stops dispatching them fails here instead of
   * passing vacuously.
   */
  @Test
  void theEncodedAliasesAreServedAndCharged() throws Exception {
    assertThat(probe("/api/v1/auth/login")).as("the plain route").isEqualTo(new Probe(422, true));
    assertThat(probe("/api/v1/auth/%6Cogin")).as("encoded login").isEqualTo(new Probe(422, true));
    assertThat(probe("/api/v1/auth/register-hospital"))
        .as("the plain route")
        .isEqualTo(new Probe(422, true));
    assertThat(probe("/api/v1/auth/%72egister-hospital"))
        .as("encoded register")
        .isEqualTo(new Probe(422, true));
    assertThat(probe("/api/v1/auth/register%2Dhospital"))
        .as("encoded hyphen")
        .isEqualTo(new Probe(422, true));
  }

  @Test
  void routesThatNoHandlerOwnsCostNothing() throws Exception {
    assertThat(probe("/api/v1/auth/login/")).as("trailing slash").isEqualTo(new Probe(401, false));
    assertThat(probe("/api/v1/auth/register-hospital/"))
        .as("trailing slash")
        .isEqualTo(new Probe(401, false));
    assertThat(probe("/api/v1/auth/definitely-not-a-route"))
        .as("unknown route")
        .isEqualTo(new Probe(401, false));
    // No IP scope in SECURITY section 12: refresh is protected by the header guard instead, and
    // inventing a budget for it here would be a policy change, not a bug fix.
    assertThat(probe("/api/v1/auth/refresh")).as("no IP scope").isEqualTo(new Probe(403, false));
  }

  /**
   * {@code StrictHttpFirewall} rejects these before {@link com.healthcare.hms.config
   * .RateLimitFilter} ever runs, so no budget is spent on them.
   */
  @Test
  void pathsTheFirewallRejectsNeverReachTheFilter() throws Exception {
    assertThat(probe("/api/v1/auth/login;x=1")).isEqualTo(new Probe(400, false));
    assertThat(probe("/api/v1/auth/login%3bx=1")).isEqualTo(new Probe(400, false));
    assertThat(probe("/api/v1/auth/register-hospital;x=1")).isEqualTo(new Probe(400, false));
  }

  // -------------------------------------------------------------------------
  // custom-header CSRF guard
  // -------------------------------------------------------------------------

  @Test
  void anythingTheServiceIsReachedBehindIsRequiredToCarryTheCustomHeader() throws Exception {
    for (String uri : ALL_CSRF_ROUTES) {
      GuardResult result = guard(uri);
      if (result.reachedTheService()) {
        assertThat(result.bare())
            .as("%s reached the handler without the custom header", uri)
            .isEqualTo(403);
      }
    }
  }

  /**
   * The same routes the rate-limit half missed, from the guard's side: the old comparison let
   * {@code %72efresh} straight through to {@code RefreshService}.
   */
  @Test
  void theCustomHeaderGuardCoversEveryEncodingOfRefreshAndLogout() throws Exception {
    assertThat(guard("/api/v1/auth/refresh")).as("plain refresh").isEqualTo(GuardResult.guarded());
    assertThat(guard("/api/v1/auth/%72efresh"))
        .as("encoded refresh")
        .isEqualTo(GuardResult.guarded());
    assertThat(guard("/api/v1/auth/logout")).as("plain logout").isEqualTo(GuardResult.guarded());
    assertThat(guard("/api/v1/auth/%6Cogout"))
        .as("encoded logout")
        .isEqualTo(GuardResult.guarded());
  }

  /** A trailing slash is not this route — neither for the guard nor for the handler. */
  @Test
  void aTrailingSlashIsNotTheRouteAtAll() throws Exception {
    assertThat(guard("/api/v1/auth/refresh/")).isEqualTo(GuardResult.notTheRoute());
    assertThat(guard("/api/v1/auth/logout/")).isEqualTo(GuardResult.notTheRoute());
  }

  // -------------------------------------------------------------------------
  // fixtures
  // -------------------------------------------------------------------------

  private static final List<String> ALL_RATE_LIMIT_ROUTES =
      List.of(
          "/api/v1/auth/login",
          "/api/v1/auth/%6Cogin",
          "/api/v1/auth/login/",
          "/api/v1/auth/login;x=1",
          "/api/v1/auth/login%3bx=1",
          "/api/v1/auth/register-hospital",
          "/api/v1/auth/%72egister-hospital",
          "/api/v1/auth/register%2Dhospital",
          "/api/v1/auth/register-hospital/",
          "/api/v1/auth/register-hospital;x=1",
          "/api/v1/auth/register-hospital%3bx=1",
          "/api/v1/auth/refresh",
          "/api/v1/auth/definitely-not-a-route");

  private static final List<String> ALL_CSRF_ROUTES =
      List.of(
          "/api/v1/auth/refresh",
          "/api/v1/auth/%72efresh",
          "/api/v1/auth/refresh/",
          "/api/v1/auth/refresh;x=1",
          "/api/v1/auth/refresh%3bx=1",
          "/api/v1/auth/logout",
          "/api/v1/auth/%6Cogout",
          "/api/v1/auth/logout/",
          "/api/v1/auth/logout;x=1");

  /**
   * Fires {@code uri} from a brand-new address, then reads that address's counter back by making
   * one more {@code POST /login} from it: with {@code login-ip-limit=1} a counter the request did
   * not touch is still at 0/1 and answers 422, one it did touch answers 429. Register, resend and
   * verify share the same {@code rl:ip:} counter, so one read-back covers all of them.
   */
  private Probe probe(String uri) throws Exception {
    String address = newAddress();
    int status = mvc.perform(rawPost(uri, address)).andReturn().getResponse().getStatus();
    int readBack =
        mvc.perform(
                post("/api/v1/auth/login")
                    .with(setRemoteAddr(address))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
            .andReturn()
            .getResponse()
            .getStatus();
    return new Probe(status, readBack == 429);
  }

  /**
   * Fires {@code uri} twice: once with no custom header (what a browser on another origin can
   * compose) and once with it (what our own client composes).
   */
  private GuardResult guard(String uri) throws Exception {
    MvcResult bare = mvc.perform(rawPost(uri, newAddress())).andReturn();
    MvcResult withHeader = mvc.perform(rawPost(uri, newAddress(), true)).andReturn();
    String body =
        new String(withHeader.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    return new GuardResult(
        bare.getResponse().getStatus(),
        withHeader.getResponse().getStatus(),
        body.contains(SERVICE_MESSAGE),
        body.contains(ENTRY_POINT_MESSAGE));
  }

  private static String newAddress() {
    return "198.51.100." + ADDRESSES.getAndIncrement();
  }

  private static RequestBuilder rawPost(String uri, String address) {
    return rawPost(uri, address, false);
  }

  private static RequestBuilder rawPost(String uri, String address, boolean withHeader) {
    return request -> {
      MockHttpServletRequest httpRequest = new MockHttpServletRequest("POST", uri);
      httpRequest.setRemoteAddr(address);
      httpRequest.setContextPath("");
      httpRequest.setContentType(MediaType.APPLICATION_JSON_VALUE);
      httpRequest.setContent("{}".getBytes(StandardCharsets.UTF_8));
      if (withHeader) {
        httpRequest.addHeader(CustomHeaderCsrfFilter.HEADER, "XMLHttpRequest");
      }
      return httpRequest;
    };
  }

  private static RequestPostProcessor setRemoteAddr(String address) {
    return request -> {
      request.setRemoteAddr(address);
      return request;
    };
  }

  /** The status a route answered with, and whether it spent its address's budget. */
  private record Probe(int status, boolean charged) {}

  /**
   * @param bare the status with no custom header — 403 means the guard stopped it
   * @param withHeader the status with the header present
   * @param reachedTheService whether that body is the refresh service's own words (handler ran)
   * @param entryPoint whether that body is Spring Security's entry point (handler did not run)
   */
  private record GuardResult(
      int bare, int withHeader, boolean reachedTheService, boolean entryPoint) {
    /** Served, and the header check is what stands between the two requests. */
    static GuardResult guarded() {
      return new GuardResult(403, 401, true, false);
    }

    /** Not this route at all: the entry point answers both times, so nothing reached a handler. */
    static GuardResult notTheRoute() {
      return new GuardResult(401, 401, false, true);
    }
  }
}
