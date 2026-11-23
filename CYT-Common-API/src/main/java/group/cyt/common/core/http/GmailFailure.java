package group.cyt.common.core.http;

import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.util.JsonUtils;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Preserve Google's operational reasons without retaining email bodies or credentials. */
final class GmailFailure {
  private static final Set<String> QUOTA =
      Set.of("rateLimitExceeded", "userRateLimitExceeded", "dailyLimitExceeded", "quotaExceeded");
  private static final Set<String> PERMISSION =
      Set.of("insufficientPermissions", "forbidden", "domainPolicy", "accessNotConfigured");
  private static final Set<String> AUTH = Set.of("authError", "invalidCredentials");

  private GmailFailure() {}

  static GatewayException classify(int status, byte[] body, String retryAfter) {
    Set<String> reasons = new LinkedHashSet<>();
    boolean unknown = false;
    boolean invalidPage = false;
    try {
      var error = JsonUtils.map(JsonUtils.map(JsonUtils.parse(new String(body, StandardCharsets.UTF_8))).get("error"));
      String message = JsonUtils.str(error, "message").toLowerCase(Locale.ROOT);
      invalidPage = status == 400 && message.contains("invalid")
          && (message.contains("page token") || message.contains("pagetoken"));
      for (Object entry : JsonUtils.list(error.get("errors"))) {
        String reason = JsonUtils.str(JsonUtils.map(entry), "reason");
        if (QUOTA.contains(reason) || PERMISSION.contains(reason) || AUTH.contains(reason)
            || Set.of("backendError", "notFound", "badRequest").contains(reason)) reasons.add(reason);
        else unknown = true;
      }
    } catch (RuntimeException ignored) {
      unknown = true;
    }
    boolean denied = reasons.stream().anyMatch(PERMISSION::contains);
    boolean unauthorized = status == 401 || reasons.stream().anyMatch(AUTH::contains);
    boolean quota = !denied && !unauthorized && (status == 429
        || status == 403 && !unknown && !reasons.isEmpty() && reasons.stream().allMatch(QUOTA::contains));
    String type = unauthorized ? "auth_failed" : denied ? "forbidden" : quota ? "rate_limited" : "upstream_error";
    String message = unauthorized ? "Google 邮箱授权未就绪" : denied ? "Google 明确拒绝邮箱操作权限"
        : quota ? "Google 邮箱请求达到配额限制" : "Google 邮件请求返回 HTTP " + status;
    var result = new GatewayException(unauthorized ? 401 : denied ? 403 : quota ? 429 : 502,
        type, message, quota || !denied && !unauthorized && status >= 500);
    result.code = "UPSTREAM_HTTP_" + status;
    result.detail = JsonUtils.obj("upstream_status", status, "reasons", new ArrayList<>(reasons));
    if (invalidPage) JsonUtils.map(result.detail).put("invalid_page_token", true);
    if (quota) result.retryAfter = Math.max(60, Math.min(86400, JsonUtils.integer(retryAfter, 60)));
    return result;
  }
}
