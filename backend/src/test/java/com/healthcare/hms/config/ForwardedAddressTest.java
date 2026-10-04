package com.healthcare.hms.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;

/**
 * SEC-1 &mdash; the address {@link com.healthcare.hms.config.RateLimitFilter} budgets is the
 * <i>client's</i> address, not the address of whatever sat in front of it.
 *
 * <p>In deployment an nginx sits between the internet and this application, so without {@code
 * server.forward-headers-strategy: native} every request arrives from nginx's own socket address
 * and every visitor draws from one shared bucket &mdash; twenty logins a minute for the whole
 * planet, and an attacker who never has to work to stay under it.
 *
 * <p>{@code native} hands Tomcat a {@code RemoteIpValve} that rewrites {@code getRemoteAddr()} from
 * {@code X-Forwarded-For} &mdash; but only when the socket that sent the header is itself in {@code
 * server.tomcat.remoteip.internal-proxies}. That allowlist is decision <b>SEC-1</b>: pinned to
 * {@code HMS_TRUSTED_PROXY} rather than the library's default of "any private address", because
 * anyone who can reach port 8080 directly could otherwise forge a header and farm their own
 * bucket-per-request. A forged header from an address outside the allowlist is ignored, and the
 * request is budgeted where it actually came from.
 *
 * <p>Three properties are asserted, each from a real {@code HttpClient} so the header travels as
 * bytes:
 *
 * <ul>
 *   <li>a socket inside the allowlist &rarr; each forwarded address owns its own bucket;
 *   <li>a socket outside it &rarr; the header is worth nothing and one bucket covers everybody;
 *   <li>the pattern the application actually ships with is still a regex that can recognise its own
 *       hop.
 * </ul>
 *
 * <p>The two allowlists below are written without a backslash on purpose: a property inlined with
 * {@code @SpringBootTest(properties = ...)} arrives through {@code java.util.Properties} semantics,
 * which eats {@code \d} and leaves {@code d}. The third test reads {@code application.yml} as
 * Spring resolved it, which is the path production uses.
 */
class ForwardedAddressTest {

  /** TEST-NET-3; documentation addresses no other suite uses. */
  private static final String FIRST_CLIENT = "203.0.113.91";

  private static final String SECOND_CLIENT = "203.0.113.92";

  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @Nested
  @SpringBootTest(
      webEnvironment = RANDOM_PORT,
      properties = {
        "hms.ratelimit.login-ip-limit=1",
        "server.tomcat.remoteip.internal-proxies=127.*"
      })
  class SocketInsideTheAllowlist {

    @LocalServerPort private int port;

    @Test
    void eachForwardedAddressOwnsItsOwnBucket() throws Exception {
      assertThat(login(port, FIRST_CLIENT))
          .as("first request from %s", FIRST_CLIENT)
          .isNotEqualTo(429);
      assertThat(login(port, FIRST_CLIENT))
          .as("the same address again must find its own counter spent")
          .isEqualTo(429);
      assertThat(login(port, SECOND_CLIENT))
          .as(
              "%s is a different visitor: if the filter were still keying on the socket address"
                  + " this would be the third strike against one bucket",
              SECOND_CLIENT)
          .isNotEqualTo(429);
    }
  }

  @Nested
  @SpringBootTest(
      webEnvironment = RANDOM_PORT,
      properties = {
        "hms.ratelimit.login-ip-limit=1",
        "server.tomcat.remoteip.internal-proxies=192.0.2.*"
      })
  class SocketOutsideTheAllowlist {

    @LocalServerPort private int port;

    @Test
    void theHeaderIsWorthNothingFromAnAddressThatIsNotATrustedProxy() throws Exception {
      login(port, FIRST_CLIENT);
      assertThat(login(port, SECOND_CLIENT))
          .as(
              "two different forwarded addresses from an untrusted socket must share one bucket,"
                  + " because the header was never looked at")
          .isEqualTo(429);
    }
  }

  /**
   * The regex that ships in {@code application.yml} &mdash; the one {@code HMS_TRUSTED_PROXY}
   * overrides in production &mdash; must still be a regex once Spring has resolved it. A pattern
   * that lost its backslashes would still be a legal expression and would silently trust nobody:
   * every proxied client would fall back to one shared bucket, which is exactly the bug SEC-1 was
   * raised for.
   */
  @Nested
  @SpringBootTest(webEnvironment = NONE)
  class ThePatternThatShips {

    @Autowired private Environment environment;

    @Test
    void theConfiguredHopIsStillRecognisable() {
      String configured = environment.getProperty("server.tomcat.remoteip.internal-proxies");
      assertThat(configured).as("application.yml must define the hop").isNotBlank();

      Pattern pattern = Pattern.compile(configured);
      assertThat(pattern.matcher("172.16.0.7").matches())
          .as("%s must still recognise the compose-docker hop", configured)
          .isTrue();
      assertThat(pattern.matcher("203.0.113.9").matches())
          .as("%s must not trust the public internet", configured)
          .isFalse();
      assertThat(pattern.matcher("127.0.0.1").matches())
          .as("%s must not trust a direct peer, or the header becomes spoofable", configured)
          .isFalse();
    }
  }

  /**
   * One {@code POST /api/v1/auth/login} carrying {@code X-Forwarded-For}, answered 422 by the
   * controller's own validation &mdash; which happens after the filter has charged &mdash; or 429
   * by the filter itself.
   */
  private static int login(int port, String forwardedFor) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("X-Forwarded-For", forwardedFor)
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();
    HttpResponse<Void> response = HTTP.send(request, HttpResponse.BodyHandlers.discarding());
    return response.statusCode();
  }
}
