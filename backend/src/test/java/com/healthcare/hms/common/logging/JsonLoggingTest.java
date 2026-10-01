package com.healthcare.hms.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import java.util.List;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** P2.4 — structured JSON logging: {@code traceId} present, no PHI/secrets in output. */
@SpringBootTest
@AutoConfigureMockMvc
class JsonLoggingTest {

  @Autowired private MockMvc mockMvc;

  private Logger rootLogger;
  private ListAppenderHolder holder;

  @BeforeEach
  void attachAppender() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    holder = new ListAppenderHolder();
    holder.appender.start();
    rootLogger.addAppender(holder.appender);
  }

  @AfterEach
  void detachAppender() {
    rootLogger.detachAppender(holder.appender);
    org.slf4j.MDC.clear();
  }

  @Test
  void requestLogCarriesTraceIdAsJsonAndQueryIsNeverLogged() throws Exception {
    String traceId = "test-trace-42";

    MvcResult result =
        mockMvc
            .perform(
                get("/actuator/health")
                    .param("secret", "hunter2")
                    .header(TraceIdFilter.HEADER, traceId))
            .andExpect(status().isOk())
            .andReturn();

    assertThat(result.getResponse().getHeader(TraceIdFilter.HEADER)).isEqualTo(traceId);

    List<ILoggingEvent> requestEvents =
        holder.appender.list.stream()
            .filter(event -> event.getLoggerName().startsWith("com.healthcare.hms"))
            .filter(
                event ->
                    event.getFormattedMessage() != null
                        && event.getFormattedMessage().contains("request_completed"))
            .toList();
    assertThat(requestEvents).isNotEmpty();

    ILoggingEvent event = requestEvents.get(0);
    assertThat(event.getMDCPropertyMap()).containsEntry(TraceIds.TRACE_ID_KEY, traceId);

    LogstashEncoder encoder = configuredJsonEncoder();
    assertThat(encoder).isNotNull();
    String json = new String(encoder.encode(event));
    assertThat(json).contains("\"traceId\":\"" + traceId + "\"");
    assertThat(json).contains("\"message\"");
    assertThat(json).doesNotContain("hunter2");
    assertThat(json).doesNotContain("Authorization");
    assertThat(json).doesNotContain("password");
  }

  @Test
  void unsafeIncomingTraceIdIsReplacedByGeneratedOne() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/actuator/health").header(TraceIdFilter.HEADER, "bad trace id with spaces!"))
            .andExpect(status().isOk())
            .andReturn();

    String traceId = result.getResponse().getHeader(TraceIdFilter.HEADER);
    assertThat(traceId).isNotBlank();
    assertThat(traceId).doesNotContain(" ");
    assertThat(traceId).matches("[A-Za-z0-9\\-]+");
  }

  private LogstashEncoder configuredJsonEncoder() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    var appenders = root.iteratorForAppenders();
    while (appenders.hasNext()) {
      Appender<ILoggingEvent> appender = appenders.next();
      if (appender instanceof ConsoleAppender<?> console
          && console.getEncoder() instanceof LogstashEncoder encoder) {
        return encoder;
      }
    }
    return null;
  }

  /** Captures logging events for assertions (attach/detach around each test). */
  static final class ListAppenderHolder {
    final ch.qos.logback.core.read.ListAppender<ILoggingEvent> appender =
        new ch.qos.logback.core.read.ListAppender<>();
  }
}
