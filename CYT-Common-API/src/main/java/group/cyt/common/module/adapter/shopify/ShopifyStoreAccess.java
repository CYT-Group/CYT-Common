package group.cyt.common.module.adapter.shopify;

import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.http.UpstreamHttp;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.credential.service.CredentialService;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Managed Shopify app credentials stay in Common; CS receives only verified shop metadata. */
final class ShopifyStoreAccess {
  private static final String DOMAIN = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.myshopify\\.com";
  private static final Map<String, AppToken> TOKENS = new ConcurrentHashMap<>();

  private ShopifyStoreAccess() {}

  static String domain(String value) {
    String domain = value.strip().toLowerCase(Locale.ROOT);
    return domain.matches(DOMAIN) ? domain : "";
  }

  static boolean hasOrderWriteScope(Map<String, Object> installation) {
    return JsonUtils.list(installation.get("accessScopes")).stream()
        .anyMatch(scope -> "write_orders".equals(JsonUtils.str(JsonUtils.map(scope), "handle")));
  }

  static String token(CredentialService credentials, UpstreamHttp http, String scope, String store,
      boolean exactScope) {
    if (scope.startsWith("cs_")) {
      String prefix = "SHOPIFY_" + scope.toUpperCase(Locale.ROOT) + "_";
      String id = credentials.raw(prefix + "CLIENT_ID"), secret = credentials.raw(prefix + "CLIENT_SECRET");
      if (!id.isBlank() || !secret.isBlank()) {
        if (id.isBlank() || secret.isBlank() || domain(store).isEmpty())
          throw GatewayException.config("Shopify 应用凭据未配齐");
        return TOKENS.compute(store, (key, current) -> {
          String fingerprint = JsonUtils.sha(id + ":" + secret);
          if (current != null && current.fingerprint().equals(fingerprint)
              && current.expiresAt().isAfter(Instant.now().plusSeconds(300))) return current;
          var response = JsonUtils.map(http.form("https://" + store + "/admin/oauth/access_token",
              Map.of("grant_type", "client_credentials", "client_id", id, "client_secret", secret), Map.of()));
          String token = JsonUtils.str(response, "access_token");
          if (token.isBlank() || !(response.get("expires_in") instanceof Number lifetime)
              || lifetime.longValue() <= 0)
            throw GatewayException.config("Shopify 未返回可用的应用访问令牌");
          return new AppToken(token, fingerprint, Instant.now().plusSeconds(lifetime.longValue()));
        }).value();
      }
    }
    if (exactScope || scope.startsWith("cs_")) {
      String token = credentials.raw("SHOPIFY_" + scope.toUpperCase(Locale.ROOT) + "_ACCESS_TOKEN");
      if (token.isBlank()) throw GatewayException.config("店铺尚未登记独立 Shopify 授权");
      return token;
    }
    return credentials.required("SHOPIFY_ACCESS_TOKEN", scope);
  }

  private record AppToken(String value, String fingerprint, Instant expiresAt) {}
}
