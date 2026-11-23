package group.cyt.common.core.http;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.util.JsonUtils;
import java.io.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import okhttp3.*;
import org.springframework.stereotype.Component;

@Component
public class UpstreamHttp {
  private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
  private static final int MAX_BYTES = 16 * 1024 * 1024;
  // 原始 MIME 含附件的两层 Base64；普通 JSON 上限不足以容纳真实 Gmail 邮件。
  private static final int GMAIL_RAW_MAX_BYTES = 96 * 1024 * 1024;
  private static final ObjectMapper GMAIL_RAW_JSON = JsonUtils.MAPPER.copy();

  static {
    // 只放宽受信 Gmail 原文响应；业务请求和其他上游仍保留原解析限制。
    GMAIL_RAW_JSON
        .getFactory()
        .setStreamReadConstraints(
            StreamReadConstraints.builder().maxStringLength(GMAIL_RAW_MAX_BYTES).build());
  }

  public record ResponseData(int status, Map<String, String> headers, byte[] body) {}

  private final OkHttpClient client =
      new OkHttpClient.Builder()
          .connectTimeout(Duration.ofSeconds(10))
          .readTimeout(Duration.ofSeconds(30))
          .callTimeout(Duration.ofSeconds(40))
          .followRedirects(false)
          .followSslRedirects(false)
          .retryOnConnectionFailure(false)
          // A cancelled exchange can leave a half-open HTTP/2 connection in the pool, and every
          // later call reusing it stalls until its own timeout. Pings make okhttp evict it.
          .pingInterval(Duration.ofSeconds(15))
          .build();

  public Object json(
      String method, String url, Map<String, String> headers, Object body, double timeout) {
    byte[] bytes =
        raw(
            method,
            url,
            headers,
            body == null
                ? null
                : JsonUtils.write(body).getBytes(java.nio.charset.StandardCharsets.UTF_8),
            timeout,
            null);
    if (gmailRaw(method, HttpUrl.get(url))) {
      try {
        return GMAIL_RAW_JSON.readValue(bytes, Object.class);
      } catch (IOException error) {
        throw new GatewayException(502, "upstream_error", "Gmail 原始邮件响应 JSON 无效", false);
      }
    }
    return JsonUtils.parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
  }

  public Map<String, Object> object(
      String method, String url, Map<String, String> headers, Object body) {
    return JsonUtils.map(json(method, url, headers, body, 30));
  }

  public Object form(String url, Map<String, String> values, Map<String, String> headers) {
    FormBody.Builder form = new FormBody.Builder();
    values.forEach(form::add);
    return execute(
        client,
        new Request.Builder().url(url).headers(Headers.of(headers)).post(form.build()).build(),
        true);
  }

  public byte[] raw(
      String method,
      String url,
      Map<String, String> headers,
      byte[] body,
      double timeout,
      List<InetAddress> pinned) {
    return exchange(method, url, headers, body, timeout, pinned).body();
  }

  /**
   * Same as {@link #exchange} but returns non-2xx responses instead of raising. Callers that must
   * read the error body to classify a failure — a proxy reports its own fault there — use this.
   */
  public ResponseData quietExchange(
      String method, String url, Map<String, String> headers, byte[] body, double timeout) {
    return quietExchange(method, url, headers, body, timeout, null);
  }

  public ResponseData quietExchange(
      String method,
      String url,
      Map<String, String> headers,
      byte[] body,
      double timeout,
      Proxy proxy) {
    var builder =
        client
            .newBuilder()
            .callTimeout(Duration.ofMillis((long) (Math.max(1, Math.min(120, timeout)) * 1000)));
    if (proxy != null) builder.proxy(proxy);
    RequestBody requestBody =
        body == null
            ? null
            : RequestBody.create(
                body, MediaType.get(headers.getOrDefault("Content-Type", JSON.toString())));
    if (Set.of("POST", "PUT", "PATCH").contains(method) && requestBody == null)
      requestBody = RequestBody.create(new byte[0], JSON);
    Request request =
        new Request.Builder()
            .url(url)
            .headers(Headers.of(headers))
            .method(method, requestBody)
            .build();
    try (Response response = builder.build().newCall(request).execute()) {
      Map<String, String> responseHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      for (String key : response.headers().names()) responseHeaders.put(key, response.header(key));
      byte[] bytes =
          response.body() == null
              ? new byte[0]
              : response.body().byteStream().readNBytes(MAX_BYTES + 1);
      return new ResponseData(response.code(), responseHeaders, bytes);
    } catch (java.io.InterruptedIOException e) {
      throw new GatewayException(504, "upstream_timeout", "上游调用超时", true);
    } catch (IOException e) {
      throw new GatewayException(502, "upstream_error", "上游连接失败", true);
    }
  }

  public ResponseData exchange(
      String method,
      String url,
      Map<String, String> headers,
      byte[] body,
      double timeout,
      List<InetAddress> pinned) {
    var builder =
        client
            .newBuilder()
            .callTimeout(Duration.ofMillis((long) (Math.max(1, Math.min(120, timeout)) * 1000)));
    if (pinned != null) {
      String host = URI.create(url).getHost();
      builder.dns(
          name -> {
            if (!name.equals(host)) throw new UnknownHostException("Unexpected DNS host");
            return pinned;
          });
    }
    String contentType =
        headers.entrySet().stream()
            .filter(e -> e.getKey().equalsIgnoreCase("Content-Type"))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(JSON.toString());
    RequestBody requestBody =
        body == null ? null : RequestBody.create(body, MediaType.get(contentType));
    if (Set.of("POST", "PUT", "PATCH").contains(method) && requestBody == null)
      requestBody = RequestBody.create(new byte[0], JSON);
    Request request =
        new Request.Builder()
            .url(url)
            .headers(Headers.of(headers))
            .method(method, requestBody)
            .build();
    return receive(builder.build(), request);
  }

  private Object execute(OkHttpClient http, Request request, boolean json) {
    byte[] bytes = receive(http, request).body();
    return json
        ? JsonUtils.parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8))
        : bytes;
  }

  /** One-shot streaming path: no redirects, replay, byte[] payload or error-body disclosure. */
  public ResponseData upload(String url, Map<String, String> headers, BinaryUpload source) {
    source.verifyEmpty();
    var duration = Duration.ofMillis(Math.max(1, (long) (source.timeoutSeconds() * 1000)));
    var http =
        client
            .newBuilder()
            .callTimeout(duration)
            .readTimeout(duration)
            .writeTimeout(duration)
            .connectTimeout(duration)
            .build();
    RequestBody body =
        new RequestBody() {
          @Override
          public MediaType contentType() {
            return MediaType.get(source.contentType());
          }

          @Override
          public long contentLength() {
            return source.length();
          }

          @Override
          public boolean isOneShot() {
            return true;
          }

          @Override
          public void writeTo(okio.BufferedSink sink) throws IOException {
            if (source.length() > 0) source.writeTo(sink.outputStream());
          }
        };
    var call =
        http.newCall(new Request.Builder().url(url).headers(Headers.of(headers)).put(body).build());
    source.onCancel(call::cancel);
    try (Response response = call.execute()) {
      if (!source.completed()) throw GatewayException.invalid("文件尚未完整接收");
      int status = response.code();
      if (status < 200 || status >= 300) {
        var error =
            new GatewayException(
                status == 429 ? 429 : 502,
                status == 429 ? "rate_limited" : "upstream_error",
                "上游返回 HTTP " + status,
                false);
        error.code = "UPSTREAM_HTTP_" + status;
        throw error;
      }
      Map<String, String> responseHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      for (String key : response.headers().names()) responseHeaders.put(key, response.header(key));
      return new ResponseData(status, responseHeaders, new byte[0]);
    } catch (java.io.InterruptedIOException e) {
      if (source.failure() != null) throw source.failure();
      throw new GatewayException(504, "upstream_timeout", "文件上传超时", false);
    } catch (IOException e) {
      if (source.failure() != null) throw source.failure();
      throw new GatewayException(502, "upstream_error", "文件上传连接失败", false);
    }
  }

  private ResponseData receive(OkHttpClient http, Request request) {
    try (Response response = http.newCall(request).execute()) {
      int status = response.code();
      if (status < 200 || status >= 300) {
        if (request.url().host().equals("gmail.googleapis.com")) {
          byte[] errorBody =
              response.body() == null
                  ? new byte[0]
                  : response.body().byteStream().readNBytes(65537);
          throw GmailFailure.classify(status, errorBody, response.header("Retry-After"));
        }
        var ex =
            new GatewayException(
                status == 429 ? 429 : 502,
                status == 429 ? "rate_limited" : "upstream_error",
                "上游返回 HTTP " + status,
                status == 429 || status >= 500);
        ex.code = "UPSTREAM_HTTP_" + status;
        if (status == 429) ex.retryAfter = JsonUtils.integer(response.header("Retry-After"), 1);
        throw ex;
      }
      Map<String, String> responseHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      for (String key : response.headers().names()) responseHeaders.put(key, response.header(key));
      if (response.body() == null) return new ResponseData(status, responseHeaders, new byte[0]);
      try (InputStream input = response.body().byteStream()) {
        int limit = responseLimit(request);
        byte[] bytes = input.readNBytes(limit + 1);
        if (bytes.length > limit) {
          var error = new GatewayException(502, "upstream_error", "上游响应超过大小限制", false);
          error.code = "UPSTREAM_RESPONSE_TOO_LARGE";
          throw error;
        }
        return new ResponseData(status, responseHeaders, bytes);
      }
    } catch (GatewayException e) {
      throw e;
    } catch (java.io.InterruptedIOException e) {
      throw new GatewayException(504, "upstream_timeout", "上游调用超时", true);
    } catch (IOException e) {
      throw new GatewayException(502, "upstream_error", "上游连接失败", true);
    }
  }

  private static int responseLimit(Request request) {
    return gmailRaw(request.method(), request.url()) ? GMAIL_RAW_MAX_BYTES : MAX_BYTES;
  }

  private static boolean gmailRaw(String method, HttpUrl url) {
    return method.equals("GET")
        && url.isHttps()
        && url.host().equals("gmail.googleapis.com")
        && url.port() == 443
        && url.encodedPath().matches("/gmail/v1/users/me/messages/[^/]+")
        && "raw".equals(url.queryParameter("format"));
  }

  public static String query(String url, Map<String, ?> params) {
    HttpUrl parsed = HttpUrl.get(url);
    var b = parsed.newBuilder();
    params.forEach(
        (k, v) -> {
          if (v != null && !v.toString().isBlank()) b.addQueryParameter(k, v.toString());
        });
    return b.build().toString();
  }

  public static String segment(Object value) {
    return URLEncoder.encode(Objects.toString(value, ""), java.nio.charset.StandardCharsets.UTF_8)
        .replace("+", "%20");
  }
}
