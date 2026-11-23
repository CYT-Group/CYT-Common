package group.cyt.common.core.http;

import static org.junit.jupiter.api.Assertions.*;

import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.util.JsonUtils;
import java.util.Map;
import okhttp3.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GmailResponseSizeTest {
  @Test
  void rawJsonStringAboveJacksonDefaultLimitParsesWithoutChangingGlobalConstraints() {
    String raw = "A".repeat(21 * 1024 * 1024);
    String json = "{\"id\":\"fixture\",\"raw\":\"" + raw + "\"}";
    var http = new UpstreamHttp();
    var client =
        new OkHttpClient.Builder()
            .addInterceptor(
                chain ->
                    new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(ResponseBody.create(json, MediaType.get("application/json")))
                        .build())
            .build();
    ReflectionTestUtils.setField(http, "client", client);
    var result =
        http.object(
            "GET",
            "https://gmail.googleapis.com/gmail/v1/users/me/messages/fixture?format=raw",
            Map.of(),
            null);
    assertEquals(raw, result.get("raw"));
    assertThrows(GatewayException.class, () -> JsonUtils.parse(json));
  }

  private UpstreamHttp http(int size) {
    var http = new UpstreamHttp();
    var client =
        new OkHttpClient.Builder()
            .addInterceptor(
                chain ->
                    new Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(
                            ResponseBody.create(
                                new byte[size], MediaType.get("application/octet-stream")))
                        .build())
            .build();
    ReflectionTestUtils.setField(http, "client", client);
    return http;
  }

  @Test
  void rawOriginalLargerThanSixteenMiBIsReturnedWhole() {
    int size = 17 * 1024 * 1024;
    assertEquals(
        size,
        http(size)
            .raw(
                "GET",
                "https://gmail.googleapis.com/gmail/v1/users/me/messages/fixture?format=raw",
                Map.of(),
                null,
                30,
                null)
            .length);
  }

  @Test
  void otherProvidersFormatsAndPathsKeepTheExistingLimit() {
    var http = http(16 * 1024 * 1024 + 1);
    for (String url :
        new String[] {
          "https://example.invalid/gmail/v1/users/me/messages/fixture?format=raw",
          "https://gmail.googleapis.com.evil.invalid/gmail/v1/users/me/messages/fixture?format=raw",
          "http://gmail.googleapis.com/gmail/v1/users/me/messages/fixture?format=raw",
          "https://gmail.googleapis.com/gmail/v1/users/me/messages/fixture?format=full",
          "https://gmail.googleapis.com/gmail/v1/users/me/messages?format=raw",
          "https://gmail.googleapis.com/gmail/v1/users/me/messages/fixture/attachments/part?format=raw"
        }) {
      var error =
          assertThrows(
              GatewayException.class, () -> http.raw("GET", url, Map.of(), null, 30, null));
      assertEquals("UPSTREAM_RESPONSE_TOO_LARGE", error.code);
      assertFalse(error.retryable);
    }
  }

  @Test
  void rawOriginalStillHasAFiniteLimit() {
    var error =
        assertThrows(
            GatewayException.class,
            () ->
                http(96 * 1024 * 1024 + 1)
                    .raw(
                        "GET",
                        "https://gmail.googleapis.com/gmail/v1/users/me/messages/fixture?format=raw",
                        Map.of(),
                        null,
                        30,
                        null));
    assertEquals("UPSTREAM_RESPONSE_TOO_LARGE", error.code);
    assertFalse(error.retryable);
  }
}
