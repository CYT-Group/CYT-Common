package group.cyt.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import group.cyt.common.core.config.GatewaySettings;
import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.http.UpstreamHttp;
import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.adapter.google.*;
import group.cyt.common.module.adapter.lingxing.LingxingAdapter;
import group.cyt.common.module.adapter.support.*;
import group.cyt.common.module.credential.service.CredentialService;
import group.cyt.common.module.registry.service.OperationRegistry;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class NativeCapabilityParityTest {
  private OperationRegistry registry() throws Exception {
    return new OperationRegistry(mock(GatewayStore.class));
  }

  @Test
  void attachmentReadsVerifyIdentityAndOriginalMimeOwnership() throws Exception {
    var http = mock(UpstreamHttp.class);
    var tokens = mock(GoogleTokenService.class);
    when(tokens.user(eq("cs"), anyString())).thenReturn("fixture-token");
    when(http.object(eq("GET"), endsWith("/profile"), anyMap(), isNull()))
        .thenReturn(JsonUtils.obj("emailAddress", "fixture@example.invalid"));
    byte[] bytes = "%PDF-fixture".getBytes(StandardCharsets.US_ASCII);
    var part =
        JsonUtils.obj(
            "filename",
            "测试.pdf",
            "mimeType",
            "application/pdf",
            "body",
            JsonUtils.obj("attachmentId", "a-1", "size", bytes.length));
    when(http.object(eq("GET"), contains("?format=full"), anyMap(), isNull()))
        .thenReturn(JsonUtils.obj("payload", JsonUtils.obj("parts", List.of(part))));
    when(http.object(eq("GET"), endsWith("/attachments/a-1"), anyMap(), isNull()))
        .thenReturn(
            JsonUtils.obj(
                "data",
                Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
                "size",
                bytes.length));
    var adapter =
        new GoogleAdapter(tokens, http, mock(CredentialService.class), mock(GatewaySettings.class));
    var registry = registry();
    var op = registry.get("gmail.attachments.get");
    var params =
        registry.validate(
            op,
            JsonUtils.obj(
                "account",
                "fixture@example.invalid",
                "message_id",
                "mail-1",
                "attachment_id",
                "a-1"));
    var result = JsonUtils.map(adapter.invoke(op, params, "cs"));
    assertEquals("测试.pdf", result.get("filename"));
    params.put("attachment_id", "other-id");
    assertThrows(GatewayException.class, () -> adapter.invoke(op, params, "cs"));
    verify(http, never()).object(eq("GET"), endsWith("/attachments/other-id"), anyMap(), isNull());
    params.put("account", "another@example.invalid");
    assertThrows(GatewayException.class, () -> adapter.invoke(op, params, "cs"));
  }

  @Test
  void outgoingMimePreservesBodiesReferencesAndBinaryAttachments() {
    String encoded =
        Base64.getEncoder().encodeToString("%PDF-fixture".getBytes(StandardCharsets.US_ASCII));
    var params =
        JsonUtils.obj(
            "account",
            "fixture@example.invalid",
            "to",
            List.of("recipient@example.invalid"),
            "subject",
            "附件测试",
            "body_text",
            "plain",
            "body_html",
            "<b>html</b>",
            "in_reply_to",
            "<original@example.invalid>",
            "references",
            List.of("<original@example.invalid>"),
            "attachments",
            List.of(
                JsonUtils.obj(
                    "filename",
                    "测试.pdf",
                    "mime_type",
                    "application/pdf",
                    "content_base64",
                    encoded)));
    String mime = GmailAttachments.mime(params);
    assertTrue(mime.contains("multipart/mixed"));
    assertTrue(mime.contains("multipart/alternative"));
    assertTrue(mime.contains("Content-Type: application/pdf"));
    assertTrue(mime.contains(encoded));
    assertTrue(mime.contains("In-Reply-To: <original@example.invalid>"));
    for (String filename : List.of("../bad.pdf", "bad\r\n.pdf", "wrong.exe"))
      assertThrows(
          GatewayException.class,
          () -> GmailAttachments.validate(filename, "application/pdf", encoded));
    assertThrows(
        GatewayException.class, () -> GmailAttachments.validate("wrong.png", "image/png", encoded));
    params.put("references", List.of("<bad@example.invalid>\r\nBcc: another@example.invalid"));
    assertThrows(GatewayException.class, () -> GmailAttachments.mime(params));
  }

  @Test
  void nativeReviewsPreserveHistoricalWindowAndRejectIncompletePages() throws Exception {
    var c = mock(CredentialService.class);
    Map<String, String> config =
        Map.of(
            "LINGXING_DEFAULT_APP_ID",
            "0123456789abcdef",
            "LINGXING_DEFAULT_APP_SECRET",
            "fixture-secret");
    when(c.resolve(anyString(), anyString()))
        .thenAnswer(a -> config.getOrDefault(a.getArgument(0), ""));
    var http = mock(UpstreamHttp.class);
    Map<String, Object> response =
        JsonUtils.obj(
            "code",
            0,
            "total",
            1,
            "data",
            List.of(
                JsonUtils.obj(
                    "review_id",
                    "review-1",
                    "last_star",
                    2,
                    "last_title",
                    "Title",
                    "last_content",
                    "Body",
                    "buyer_email",
                    List.of("customer@example.invalid"),
                    "local_info",
                    List.of(JsonUtils.obj("local_sku", "SKU-1", "local_name", "Product")))));
    when(http.raw(anyString(), anyString(), anyMap(), any(), anyDouble(), isNull()))
        .thenAnswer(
            a -> {
              String url = a.getArgument(1);
              if (url.endsWith("/access-token"))
                return JsonUtils.write(
                        JsonUtils.obj(
                            "code",
                            "200",
                            "data",
                            JsonUtils.obj("access_token", "fixture-token", "expires_in", 3600)))
                    .getBytes(StandardCharsets.UTF_8);
              var body =
                  JsonUtils.read(new String((byte[]) a.getArgument(3), StandardCharsets.UTF_8));
              assertTrue(url.contains("/service/v3/data/mws/reviews?"));
              assertEquals("2020-01-01", body.get("start_date"));
              assertEquals("2026-11-01", body.get("end_date"));
              assertEquals("1,2,3", body.get("star"));
              return JsonUtils.write(response).getBytes(StandardCharsets.UTF_8);
            });
    var adapter =
        new LingxingAdapter(
            c, http, mock(ErpProxy.class), mock(TestWritePolicy.class), new TokenCache());
    var registry = registry();
    var op = registry.get("lingxing.reviews.list");
    var params =
        registry.validate(op, JsonUtils.obj("start_date", "2020-01-01", "end_date", "2026-11-01"));
    var result = JsonUtils.map(adapter.invoke(op, params, "cs"));
    assertEquals(false, result.get("has_more"));
    var review = JsonUtils.map(JsonUtils.list(result.get("items")).getFirst());
    assertEquals("SKU-1", review.get("sku"));
    assertEquals("customer@example.invalid", review.get("customer_email"));
    response.put("total", 2);
    assertThrows(GatewayException.class, () -> adapter.invoke(op, params, "cs"));
    response.put("total", 1);
    params.put("cursor", JsonUtils.obj("page", 0));
    assertThrows(GatewayException.class, () -> adapter.invoke(op, params, "cs"));
  }

  @Test
  void lingxingWritesRemainReadOnlyEvenWithTestEnvironmentFlags() throws Exception {
    var settings = mock(GatewaySettings.class);
    when(settings.flag(anyString(), anyBoolean())).thenReturn(true);
    when(settings.csv(anyString(), anyString())).thenReturn(List.of("fixture"));
    var http = mock(UpstreamHttp.class);
    var c = mock(CredentialService.class);
    var adapter =
        new LingxingAdapter(
            c, http, mock(ErpProxy.class), new TestWritePolicy(settings), new TokenCache());
    var registry = registry();
    for (String key :
        List.of("lingxing.workorder.add", "lingxing.workorder.audit", "lingxing.refund.create"))
      assertThrows(
          GatewayException.class,
          () -> adapter.invoke(registry.get(key), JsonUtils.obj("account", "fixture"), "cs"));
    verifyNoInteractions(http);
  }
}
