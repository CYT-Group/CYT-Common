package group.cyt.common.core.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.gateway.service.CallLogService;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GmailFailureTest {
  private byte[] body(String... reasons) {
    return JsonUtils.write(JsonUtils.obj("error", JsonUtils.obj("message", "private@example.invalid secret-token",
        "errors", Arrays.stream(reasons).map(reason -> JsonUtils.obj("reason", reason)).toList())))
        .getBytes(StandardCharsets.UTF_8);
  }

  @Test
  void googleQuota403SchedulesCooldownWithOriginalStatus() {
    for (String reason : List.of("rateLimitExceeded", "userRateLimitExceeded", "dailyLimitExceeded", "quotaExceeded")) {
      var error = GmailFailure.classify(403, body(reason), "120");
      assertEquals("rate_limited", error.type);
      assertEquals(429, error.status);
      assertTrue(error.retryable);
      assertEquals(120, error.retryAfter);
      assertEquals("UPSTREAM_HTTP_403", error.code);
      assertEquals(List.of(reason), JsonUtils.map(error.detail).get("reasons"));
      assertFalse(JsonUtils.write(error.payload()).contains("private"));
    }
  }

  @Test
  void explicitOrMixedDenialCannotBecomeQuotaRetry() {
    var denied = GmailFailure.classify(403, body("userRateLimitExceeded", "insufficientPermissions"), "1");
    assertEquals("forbidden", denied.type);
    assertFalse(denied.retryable);
    assertNull(denied.retryAfter);
    var ambiguous = GmailFailure.classify(403, body("userRateLimitExceeded", "unrecognizedPrivateReason"), null);
    assertEquals("upstream_error", ambiguous.type);
    assertFalse(ambiguous.retryable);
    assertFalse(JsonUtils.write(ambiguous.payload()).contains("unrecognizedPrivateReason"));
    var auth = GmailFailure.classify(401, body("authError"), null);
    assertEquals("auth_failed", auth.type);
    assertFalse(auth.retryable);
  }

  @Test
  void serverFailuresRemainRetryableAndMalformed403DoesNotInventPermissions() {
    var temporary = GmailFailure.classify(503, body("backendError"), null);
    assertEquals("upstream_error", temporary.type);
    assertTrue(temporary.retryable);
    var unknown = GmailFailure.classify(403, "bad payload".getBytes(StandardCharsets.UTF_8), null);
    assertEquals("upstream_error", unknown.type);
    assertFalse(unknown.retryable);
    assertEquals(60, GmailFailure.classify(429, new byte[0], "invalid").retryAfter);
  }

  @Test
  void gmailReceiveClassifiesHttpBodyWhileOtherProvidersKeepExistingContract() {
    var http = new UpstreamHttp();
    var client = new okhttp3.OkHttpClient.Builder().addInterceptor(chain -> new okhttp3.Response.Builder()
        .request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(403).message("Forbidden")
        .body(okhttp3.ResponseBody.create(body("userRateLimitExceeded"), okhttp3.MediaType.get("application/json")))
        .build()).build();
    org.springframework.test.util.ReflectionTestUtils.setField(http, "client", client);
    var gmail = assertThrows(group.cyt.common.core.exception.GatewayException.class,
        () -> http.object("GET", "https://gmail.googleapis.com/gmail/v1/users/me/messages/fixture", Map.of(), null));
    assertEquals("rate_limited", gmail.type);
    assertEquals("UPSTREAM_HTTP_403", gmail.code);
    assertFalse(JsonUtils.write(gmail.payload()).contains("secret-token"));
    var other = assertThrows(group.cyt.common.core.exception.GatewayException.class,
        () -> http.object("GET", "https://example.invalid/fixture", Map.of(), null));
    assertEquals("upstream_error", other.type);
    assertEquals(502, other.status);
  }

  @Test
  void invalidPageTokenKeepsOnlySafeRestartSignal() {
    var response = JsonUtils.write(JsonUtils.obj("error", JsonUtils.obj("message", "Invalid pageToken private-token")))
        .getBytes(StandardCharsets.UTF_8);
    var error = GmailFailure.classify(400, response, null);
    assertEquals(true, JsonUtils.map(error.detail).get("invalid_page_token"));
    assertFalse(JsonUtils.write(error.payload()).contains("private-token"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void callLogKeepsStatusAndSafeReasonWithoutProviderMessage() {
    var store = mock(GatewayStore.class);
    var logs = new CallLogService(store);
    try {
      var error = GmailFailure.classify(403, body("userRateLimitExceeded"), null);
      logs.record("safe-trace", "cidootech", "gmail.messages.get", "gmail", 1, Map.of(), null, false, null, error);
      logs.flush();
      ArgumentCaptor<Map<String, Object>> fields = ArgumentCaptor.forClass(Map.class);
      verify(store).insert(eq("gateway_call_log"), fields.capture());
      assertEquals("rate_limited (UPSTREAM_HTTP_403) userRateLimitExceeded", fields.getValue().get("error_message"));
      assertFalse(JsonUtils.write(fields.getValue()).contains("secret-token"));
    } finally {
      logs.close();
    }
  }
}
