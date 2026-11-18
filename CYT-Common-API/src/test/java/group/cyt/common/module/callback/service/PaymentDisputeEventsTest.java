package group.cyt.common.module.callback.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.http.UpstreamHttp;
import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.credential.service.CredentialService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class PaymentDisputeEventsTest {
  @Test
  void stripeSignatureBindsTheOriginalBytesAndRejectsOldDelivery() {
    Instant now = Instant.parse("2026-11-18T02:00:00Z");
    byte[] raw = "{ \"type\" : \"charge.dispute.closed\" }".getBytes(StandardCharsets.UTF_8);
    String secret = "fixture-signing-secret";
    long time = now.getEpochSecond();
    byte[] signed = (time + "." + new String(raw, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
    String hex = HexFormat.of().formatHex(JsonUtils.hmac(secret.getBytes(StandardCharsets.UTF_8), signed));
    String header = "t=" + time + ",v1=" + "0".repeat(64) + ",v1=" + hex;
    assertDoesNotThrow(() -> PaymentDisputeEvents.verifyStripe(raw, header, secret, now));
    assertThrows(GatewayException.class, () -> PaymentDisputeEvents.verifyStripe(
        "{}".getBytes(StandardCharsets.UTF_8), header, secret, now));
    assertThrows(GatewayException.class, () -> PaymentDisputeEvents.verifyStripe(raw, header, secret, now.plusSeconds(301)));
  }

  @Test
  void paypalUsesProviderVerificationAndPreservesTheOriginalJson() {
    var credentials = mock(CredentialService.class);
    var flags = Map.of("PAYPAL_FIXTURE_ENV", "sandbox", "PAYPAL_FIXTURE_CLIENT_ID", "fixture-client",
        "PAYPAL_FIXTURE_CLIENT_SECRET", "fixture-secret", "PAYPAL_FIXTURE_WEBHOOK_ID", "fixture-hook");
    when(credentials.resolve(anyString(), eq(""))).thenAnswer(call -> flags.get(call.getArgument(0)));
    var http = mock(UpstreamHttp.class);
    when(http.form(eq("https://api-m.sandbox.paypal.com/v1/oauth2/token"), anyMap(), anyMap()))
        .thenReturn(Map.of("access_token", "fixture-token"));
    var store = mock(GatewayStore.class);
    when(store.atomic(any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
    var events = new PaymentDisputeEvents(credentials, http, store);
    var request = new MockHttpServletRequest();
    request.addHeader("PayPal-Auth-Algo", "SHA256withRSA");
    request.addHeader("PayPal-Cert-Url", "https://api.sandbox.paypal.com/v1/notifications/certs/CERT-Test");
    request.addHeader("PayPal-Transmission-Id", "fixture-transmission");
    request.addHeader("PayPal-Transmission-Sig", "fixture-signature");
    request.addHeader("PayPal-Transmission-Time", "2026-11-18T02:00:00Z");
    String payload = """
        { "id": "WH-Fixture", "event_type": "CUSTOMER.DISPUTE.RESOLVED",
          "create_time": "2026-11-01T01:00:00Z", "resource": {
            "dispute_id": "PP-Fixture", "dispute_outcome": {"outcome_code":"RESOLVED_SELLER_FAVOUR"}
          }
        }
        """;
    var data = payload.getBytes(StandardCharsets.UTF_8);
    when(http.raw(eq("POST"), eq("https://api-m.sandbox.paypal.com/v1/notifications/verify-webhook-signature"),
        anyMap(), any(byte[].class), eq(12d), isNull())).thenAnswer(call -> {
          String forwarded = new String(call.getArgument(3, byte[].class), StandardCharsets.UTF_8);
          assertTrue(forwarded.contains("\"webhook_event\":" + payload));
          assertEquals("fixture-hook", JsonUtils.read(forwarded).get("webhook_id"));
          return "{\"verification_status\":\"FAILURE\"}".getBytes(StandardCharsets.UTF_8);
        });
    assertThrows(GatewayException.class, () -> events.receive("paypal", "fixture", data, request));
    verifyNoInteractions(store);
    when(http.raw(eq("POST"), contains("verify-webhook-signature"), anyMap(), any(byte[].class), eq(12d), isNull()))
        .thenReturn("{\"verification_status\":\"SUCCESS\"}".getBytes(StandardCharsets.UTF_8));
    assertTrue((boolean) JsonUtils.map(events.receive("paypal", "fixture", data, request)).get("accepted"));
    verify(store).update(contains("ON CONFLICT"), eq("paypal"), eq("fixture"), eq("WH-Fixture"),
        eq("PP-Fixture"), eq("2026-11-01T01:00:00Z"), eq("resolved"), eq("won"));
  }

  @Test
  void inboxReturnsOnlyTheRequestedAccountAndAdvancesToLastDeliveredItem() {
    var store = mock(GatewayStore.class);
    when(store.rows(contains("provider=? AND account=?"), eq("stripe"), eq("fixture"), eq(2L), eq(2)))
        .thenReturn(List.of(Map.of("id", 4L, "event_id", "evt_Fixture"), Map.of("id", 7L)));
    var events = new PaymentDisputeEvents(mock(CredentialService.class), mock(UpstreamHttp.class), store);
    var page = JsonUtils.map(events.inbox("stripe", "fixture", Map.of("cursor", Map.of("after", 2), "limit", 1)));
    assertEquals("fixture", page.get("account"));
    assertEquals(true, page.get("has_more"));
    assertEquals(4L, JsonUtils.at(page, "cursor.after"));
    assertEquals(1, JsonUtils.list(page.get("items")).size());
  }

  @Test
  void stripeHistoryReturnsAClosureTimeAndCursorWithoutTheDisputePayload() {
    var credentials = mock(CredentialService.class);
    when(credentials.resolve("STRIPE_FIXTURE_API_KEY", "cs")).thenReturn("fixture-key");
    var http = mock(UpstreamHttp.class);
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    var event = JsonUtils.obj("id", "evt_Fixture", "created", now.minusSeconds(50).getEpochSecond(),
        "type", "charge.dispute.closed", "data", JsonUtils.obj("object",
            JsonUtils.obj("id", "du_Fixture", "status", "lost", "customer", "fixture-private-field")));
    when(http.json(eq("GET"), contains("api.stripe.com/v1/events"), anyMap(), isNull(), eq(20d)))
        .thenReturn(Map.of("data", List.of(event), "has_more", true));
    var service = new PaymentDisputeEvents(credentials, http, mock(GatewayStore.class));
    var inputs = Map.<String, Object>of("from", now.minusSeconds(80).toString(), "to", now.toString());
    var page = JsonUtils.map(service.history("stripe", "fixture", inputs, "cs"));
    assertEquals("evt_Fixture", JsonUtils.at(page, "cursor.after"));
    assertEquals(Map.of("event_id", "evt_Fixture", "dispute_id", "du_Fixture", "status", "lost",
        "outcome", "lost", "resolved_at", now.minusSeconds(50).toString()), JsonUtils.list(page.get("items")).getFirst());
  }

  @Test
  void paypalHistoryNeverFollowsAForeignCursor() {
    var credentials = mock(CredentialService.class);
    var flags = Map.of("PAYPAL_FIXTURE_ENV", "sandbox", "PAYPAL_FIXTURE_CLIENT_ID", "fixture-client",
        "PAYPAL_FIXTURE_CLIENT_SECRET", "fixture-secret");
    when(credentials.resolve(anyString(), eq("cs"))).thenAnswer(call -> flags.get(call.getArgument(0)));
    var http = mock(UpstreamHttp.class);
    when(http.form(anyString(), anyMap(), anyMap())).thenReturn(Map.of("access_token", "fixture-token"));
    var service = new PaymentDisputeEvents(credentials, http, mock(GatewayStore.class));
    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    var inputs = Map.<String, Object>of("from", now.minusSeconds(80).toString(), "to", now.toString(),
        "cursor", Map.of("after", "https://example.invalid/v1/notifications/webhooks-events?limit=1"));
    assertThrows(GatewayException.class, () -> service.history("paypal", "fixture", inputs, "cs"));
    verify(http, never()).json(anyString(), anyString(), anyMap(), any(), anyDouble());
  }
}
