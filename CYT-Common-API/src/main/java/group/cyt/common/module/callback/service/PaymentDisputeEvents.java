package group.cyt.common.module.callback.service;

import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.http.UpstreamHttp;
import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.credential.service.CredentialService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PaymentDisputeEvents {
  private static final String STRIPE_CLOSED = "charge.dispute.closed";
  private static final String PAYPAL_CLOSED = "CUSTOMER.DISPUTE.RESOLVED";
  private final CredentialService credentials;
  private final UpstreamHttp http;
  private final GatewayStore store;

  public PaymentDisputeEvents(CredentialService credentials, UpstreamHttp http, GatewayStore store) {
    this.credentials = credentials;
    this.http = http;
    this.store = store;
  }

  private static String account(String account) {
    if (account == null || !account.matches("[a-z0-9][a-z0-9_-]{0,63}"))
      throw GatewayException.invalid("支付账号别名无效");
    return account;
  }

  private String credential(String provider, String account, String field, String system) {
    String name = provider.toUpperCase(Locale.ROOT) + "_" + CredentialService.accountKey(account(account)) + "_" + field;
    String value = credentials.resolve(name, system);
    if (value == null || value.isBlank()) throw GatewayException.config("未配置 " + name);
    return value.trim();
  }

  public Object receive(String provider, String alias, byte[] raw, HttpServletRequest request) {
    String account = account(alias);
    if (raw.length == 0 || raw.length > 1024 * 1024) throw GatewayException.invalid("支付回调正文大小无效");
    if (provider.equals("stripe"))
      verifyStripe(raw, request.getHeader("Stripe-Signature"),
          credential(provider, account, "WEBHOOK_SECRET", ""), Instant.now());
    Map<String, Object> body;
    try {
      body = JsonUtils.read(new String(raw, StandardCharsets.UTF_8));
    } catch (RuntimeException error) {
      throw GatewayException.invalid("支付回调不是 JSON 对象");
    }
    if (body == null) throw GatewayException.invalid("支付回调不是 JSON 对象");
    if (provider.equals("paypal")) verifyPaypal(account, raw, request);
    Map<String, Object> event = normalize(provider, body);
    if (event == null) return JsonUtils.obj("ok", true, "accepted", false);
    // Commit in sequence order for this account so a polling cursor never skips a pending insert.
    store.atomic(() -> {
      store.one("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "payment-dispute:" + provider + ":" + account);
      return store.update("""
          INSERT INTO gateway_payment_dispute_closure(provider,account,event_id,dispute_id,resolved_at,status,outcome)
          VALUES(?,?,?,?,CAST(? AS timestamptz),?,?) ON CONFLICT(provider,account,event_id) DO NOTHING
          """, provider, account, event.get("event_id"), event.get("dispute_id"), event.get("resolved_at"),
          event.get("status"), event.get("outcome"));
    });
    return JsonUtils.obj("ok", true, "accepted", true);
  }

  static void verifyStripe(byte[] body, String header, String secret, Instant now) {
    if (header == null || header.length() > 4096) throw GatewayException.unauth("缺少 Stripe 回调签名");
    var timestamp = new ArrayList<String>();
    var signatures = new ArrayList<String>();
    for (String part : header.split(",")) {
      String[] pair = part.trim().split("=", 2);
      if (pair.length != 2) continue;
      if (pair[0].equals("t")) timestamp.add(pair[1]);
      if (pair[0].equals("v1") && pair[1].matches("[0-9a-f]{64}")) signatures.add(pair[1]);
    }
    if (timestamp.size() != 1 || !timestamp.getFirst().matches("[0-9]{1,11}"))
      throw GatewayException.unauth("Stripe 签名时间无效");
    long signedAt = Long.parseLong(timestamp.getFirst());
    if (Math.abs(now.getEpochSecond() - signedAt) > 300) throw GatewayException.unauth("Stripe 签名已过期");
    byte[] prefix = (timestamp.getFirst() + ".").getBytes(StandardCharsets.US_ASCII);
    byte[] signed = Arrays.copyOf(prefix, prefix.length + body.length);
    System.arraycopy(body, 0, signed, prefix.length, body.length);
    String expected = HexFormat.of().formatHex(JsonUtils.hmac(secret.getBytes(StandardCharsets.UTF_8), signed));
    if (signatures.stream().noneMatch(sig -> JsonUtils.equal(expected, sig)))
      throw GatewayException.unauth("Stripe 回调验签失败");
  }

  private record PayPal(String base, Map<String, String> headers) {}

  private PayPal paypal(String account, String system) {
    String scope = credential("paypal", account, "ENV", system);
    String base = switch (scope) {
      case "live" -> "https://api-m.paypal.com";
      case "sandbox" -> "https://api-m.sandbox.paypal.com";
      default -> throw GatewayException.config("PayPal 环境必须为 live 或 sandbox");
    };
    String id = credential("paypal", account, "CLIENT_ID", system);
    String secret = credential("paypal", account, "CLIENT_SECRET", system);
    String basic = Base64.getEncoder().encodeToString((id + ":" + secret).getBytes(StandardCharsets.UTF_8));
    var auth = JsonUtils.map(http.form(base + "/v1/oauth2/token", Map.of("grant_type", "client_credentials"),
        Map.of("Authorization", "Basic " + basic)));
    String token = JsonUtils.str(auth, "access_token");
    if (token.isBlank()) throw GatewayException.config("PayPal 未返回验证令牌");
    return new PayPal(base, Map.of("Authorization", "Bearer " + token));
  }

  private void verifyPaypal(String account, byte[] body, HttpServletRequest request) {
    var params = new LinkedHashMap<String, Object>();
    for (String name : List.of("auth_algo", "cert_url", "transmission_id", "transmission_sig", "transmission_time")) {
      String value = request.getHeader("PayPal-" + name.replace('_', '-'));
      if (value == null || value.isBlank() || value.length() > 2048)
        throw GatewayException.unauth("PayPal 回调缺少签名字段");
      params.put(name, value);
    }
    String cert = JsonUtils.str(params, "cert_url");
    if (!cert.matches("https://api(?:-m)?(?:\\.sandbox)?\\.paypal\\.com/v1/notifications/certs/[A-Za-z0-9_-]+"))
      throw GatewayException.unauth("PayPal 回调证书地址无效");
    params.put("webhook_id", credential("paypal", account, "WEBHOOK_ID", ""));
    PayPal client = paypal(account, "");
    // Preserve the signed event bytes inside the verification request.
    String metadata = JsonUtils.write(params);
    var payload = new ByteArrayOutputStream();
    payload.writeBytes((metadata.substring(0, metadata.length() - 1) + ",\"webhook_event\":")
        .getBytes(StandardCharsets.UTF_8));
    payload.writeBytes(body);
    payload.write('}');
    var headers = new LinkedHashMap<>(client.headers());
    headers.put("Content-Type", "application/json");
    byte[] response = http.raw("POST", client.base() + "/v1/notifications/verify-webhook-signature",
        headers, payload.toByteArray(), 12, null);
    var verdict = JsonUtils.read(new String(response, StandardCharsets.UTF_8));
    if (!"SUCCESS".equals(verdict.get("verification_status")))
      throw GatewayException.unauth("PayPal 回调验签失败");
  }

  static Map<String, Object> normalize(String provider, Map<String, Object> envelope) {
    String eventId = JsonUtils.str(envelope, "id");
    String disputeId, status, outcome = null;
    Instant resolvedAt;
    try {
      if ("stripe".equals(provider)) {
        if (!STRIPE_CLOSED.equals(envelope.get("type"))) return null;
        var dispute = JsonUtils.map(JsonUtils.at(envelope, "data.object"));
        disputeId = JsonUtils.str(dispute, "id");
        status = switch (JsonUtils.str(dispute, "status")) {
          case "won" -> "won";
          case "lost" -> "lost";
          case "warning_closed", "prevented" -> "closed";
          default -> throw GatewayException.invalid("Stripe 结案事件状态无效");
        };
        if (!"closed".equals(status)) outcome = status;
        resolvedAt = Instant.ofEpochSecond(Long.parseLong(JsonUtils.str(envelope, "created")));
        if (!eventId.matches("evt_[A-Za-z0-9]+") || !disputeId.matches("du_[A-Za-z0-9]+"))
          throw GatewayException.invalid("Stripe 结案事件标识无效");
      } else {
        if (!PAYPAL_CLOSED.equals(envelope.get("event_type"))) return null;
        var dispute = JsonUtils.map(envelope.get("resource"));
        disputeId = JsonUtils.str(dispute, "dispute_id");
        status = "resolved";
        outcome = switch (Objects.toString(JsonUtils.at(dispute, "dispute_outcome.outcome_code"), "")) {
          case "RESOLVED_SELLER_FAVOUR" -> "won";
          case "RESOLVED_BUYER_FAVOUR" -> "lost";
          case "CANCELED_BY_BUYER" -> "withdrawn";
          default -> null;
        };
        resolvedAt = OffsetDateTime.parse(JsonUtils.str(envelope, "create_time")).toInstant();
        if (!eventId.matches("[A-Za-z0-9-]{6,100}") || !disputeId.matches("[A-Za-z0-9-]{6,100}"))
          throw GatewayException.invalid("PayPal 结案事件标识无效");
      }
    } catch (java.time.DateTimeException | NumberFormatException error) {
      throw GatewayException.invalid("支付结案事件时间无效");
    }
    if (resolvedAt.isBefore(Instant.EPOCH) || resolvedAt.isAfter(Instant.now().plusSeconds(300)))
      throw GatewayException.invalid("支付结案事件时间超出范围");
    return JsonUtils.obj("event_id", eventId, "dispute_id", disputeId, "status", status,
        "outcome", outcome, "resolved_at", resolvedAt.toString());
  }

  public Object inbox(String provider, String alias, Map<String, Object> params) {
    String account = account(alias);
    int limit = Math.clamp(JsonUtils.integer(params.get("limit"), 100), 1, 100);
    String cursor = Objects.toString(JsonUtils.at(params, "cursor.after"), "0");
    if (!cursor.matches("[0-9]{1,18}")) throw GatewayException.invalid("结案事件游标无效");
    long after = Long.parseLong(cursor);
    var rows = store.rows("""
        SELECT id,event_id,dispute_id,resolved_at,status,outcome FROM gateway_payment_dispute_closure
        WHERE provider=? AND account=? AND id>? ORDER BY id LIMIT ?
        """, provider, account, after, limit + 1);
    boolean more = rows.size() > limit;
    var items = rows.subList(0, Math.min(limit, rows.size()));
    return JsonUtils.obj("items", items, "has_more", more, "account", account,
        "cursor", JsonUtils.obj("after", items.isEmpty() ? after : items.getLast().get("id")));
  }

  private Map<String, String> stripeHeaders(String account, String system) {
    return Map.of("Authorization", "Bearer " + credential("stripe", account, "API_KEY", system));
  }

  private static Instant instant(Map<String, Object> params, String key) {
    try {
      return OffsetDateTime.parse(JsonUtils.str(params, key)).toInstant();
    } catch (java.time.DateTimeException error) {
      throw GatewayException.invalid("历史结案时间范围无效");
    }
  }

  public Object history(String provider, String alias, Map<String, Object> params, String system) {
    String account = account(alias);
    Instant from = instant(params, "from"), to = instant(params, "to");
    if (!from.isBefore(to) || from.isBefore(Instant.now().minus(30, ChronoUnit.DAYS))
        || to.isAfter(Instant.now().plusSeconds(300)))
      throw GatewayException.invalid("仅支持查询最近 30 天的结案事件");
    int limit = Math.clamp(JsonUtils.integer(params.get("limit"), 50), 1, 50);
    String after = Objects.toString(JsonUtils.at(params, "cursor.after"), "");
    Map<String, Object> data;
    String next;
    List<Object> events;
    if ("stripe".equals(provider)) {
      var query = JsonUtils.obj("type", STRIPE_CLOSED, "created[gte]", from.getEpochSecond(),
          "created[lte]", to.getEpochSecond(), "limit", limit);
      if (!after.isEmpty()) {
        if (!after.matches("evt_[A-Za-z0-9]{1,100}")) throw GatewayException.invalid("Stripe 历史游标无效");
        query.put("starting_after", after);
      }
      data = JsonUtils.map(http.json("GET", UpstreamHttp.query("https://api.stripe.com/v1/events", query),
          stripeHeaders(account, system), null, 20));
      events = list(data, "data");
      if (Boolean.TRUE.equals(data.get("has_more")) && events.isEmpty())
        throw GatewayException.config("Stripe 历史事件分页缺少游标");
      next = Boolean.TRUE.equals(data.get("has_more")) ? JsonUtils.str(JsonUtils.map(events.getLast()), "id") : "";
    } else {
      PayPal client = paypal(account, system);
      String url = client.base() + "/v1/notifications/webhooks-events";
      String page = after.isEmpty() ? UpstreamHttp.query(url, JsonUtils.obj("event_type", PAYPAL_CLOSED,
          "start_time", from.toString(), "end_time", to.toString(), "page_size", limit)) : paypalNext(url, after);
      data = JsonUtils.map(http.json("GET", page, client.headers(), null, 20));
      events = list(data, "events");
      next = JsonUtils.list(data.get("links")).stream().map(JsonUtils::map)
          .filter(link -> "next".equals(link.get("rel"))).map(link -> JsonUtils.str(link, "href")).findFirst().orElse("");
      if (!next.isEmpty()) paypalNext(url, next);
    }
    var items = events.stream().map(JsonUtils::map).map(e -> normalize(provider, e)).filter(Objects::nonNull).toList();
    return JsonUtils.obj("items", items, "has_more", !next.isEmpty(), "account", account,
        "cursor", JsonUtils.obj("after", next));
  }

  private static List<Object> list(Map<String, Object> payload, String field) {
    if (!(payload.get(field) instanceof List<?>)) throw GatewayException.config("支付平台历史事件响应不完整");
    return JsonUtils.list(payload.get(field));
  }

  private static String paypalNext(String base, String value) {
    try {
      URI expected = URI.create(base), candidate = URI.create(value);
      if (value.length() > 2048 || !"https".equals(candidate.getScheme())
          || !Set.of(expected.getAuthority(), expected.getAuthority().replace("api-m.", "api."))
              .contains(candidate.getAuthority() == null ? "" : candidate.getAuthority())
          || !Objects.equals(expected.getRawPath(), candidate.getRawPath())
          || candidate.getRawUserInfo() != null || candidate.getRawFragment() != null
          || candidate.getRawQuery() == null) throw new IllegalArgumentException();
      return base + "?" + candidate.getRawQuery();
    } catch (IllegalArgumentException error) {
      throw GatewayException.invalid("PayPal 历史游标地址无效");
    }
  }

  public Object orderReference(String alias, Map<String, Object> params, String system) {
    String account = account(alias), payment = JsonUtils.str(params, "payment_id");
    if (!payment.matches("(?:ch|pi)_[A-Za-z0-9]{1,100}"))
      throw GatewayException.invalid("Stripe 交易标识无效");
    String resource = payment.startsWith("ch_") ? "charges" : "payment_intents";
    var charge = JsonUtils.map(http.json("GET", "https://api.stripe.com/v1/" + resource + "/" + payment,
        stripeHeaders(account, system), null, 15));
    if (!payment.equals(charge.get("id"))) throw GatewayException.config("Stripe 返回交易标识不一致");
    return JsonUtils.obj("account", account, "payment_id", payment,
        "order_number", JsonUtils.at(charge, "metadata.order_number"));
  }
}
