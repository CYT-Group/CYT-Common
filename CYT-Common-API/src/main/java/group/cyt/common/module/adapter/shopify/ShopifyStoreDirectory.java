package group.cyt.common.module.adapter.shopify;

import group.cyt.common.core.config.GatewaySettings;
import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.http.UpstreamHttp;
import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.auth.service.AuthenticationService;
import group.cyt.common.module.credential.service.CredentialService;
import group.cyt.common.module.grant.service.AuthorizationService;
import group.cyt.common.module.registry.service.OperationRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.web.bind.annotation.*;

/** Read-only directory for the CS consumer, covered by its existing order-read grant. */
@RestController
public class ShopifyStoreDirectory {
  private static final int MAX_STORES = 250;
  private static final String QUERY = """
      query CSShopifyStore {
        shop { id name myshopifyDomain currencyCode ianaTimezone }
        currentAppInstallation { accessScopes { handle } }
      }
      """;
  private final AuthenticationService authentication;
  private final AuthorizationService authorization;
  private final OperationRegistry operations;
  private final GatewayStore store;
  private final GatewaySettings settings;
  private final CredentialService credentials;
  private final UpstreamHttp http;

  public ShopifyStoreDirectory(AuthenticationService authentication, AuthorizationService authorization,
      OperationRegistry operations, GatewayStore store, GatewaySettings settings,
      CredentialService credentials, UpstreamHttp http) {
    this.authentication = authentication;
    this.authorization = authorization;
    this.operations = operations;
    this.store = store;
    this.settings = settings;
    this.credentials = credentials;
    this.http = http;
  }

  @PostMapping("/integrations/shopify/stores/list")
  public Object list(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    String consumer = authentication.system(request);
    if (!consumer.equals("cs")) throw GatewayException.forbidden("此店铺目录仅供客服系统核对");
    authorization.check(consumer, operations.get("shopify.orders.list"));
    Object domains = body.get("stores");
    if (!Set.of("stores").containsAll(body.keySet()) || !(domains instanceof List<?> requested)
        || requested.size() > MAX_STORES) throw GatewayException.invalid("店铺清单格式无效");
    var candidates = new LinkedHashSet<String>();
    for (Object item : requested) {
      if (!(item instanceof String text) || ShopifyStoreAccess.domain(text).isEmpty())
        throw GatewayException.invalid("店铺必须为有效的 myshopify.com 域名");
      candidates.add(ShopifyStoreAccess.domain(text));
    }
    var names = new TreeSet<>(System.getenv().keySet());
    if (credentials.writable())
      for (var row : store.rows("SELECT name FROM gateway_secret WHERE env=? AND name LIKE ?",
          settings.current(), "SHOPIFY%STORE")) names.add(JsonUtils.str(row, "name"));
    for (String name : names) {
      if (!name.matches("SHOPIFY_CS_[A-Z0-9_]+_STORE")) continue;
      String domain = ShopifyStoreAccess.domain(credentials.raw(name));
      if (!domain.isEmpty()) candidates.add(domain);
    }
    if (candidates.size() > MAX_STORES) throw GatewayException.invalid("单次最多核对 250 家店铺");
    String defaultShop = ShopifyStoreAccess.domain(credentials.resolve("SHOPIFY_STORE", consumer));
    List<Map<String, Object>> items = new ArrayList<>();
    try (var executor = Executors.newFixedThreadPool(8)) {
      var checks = candidates.stream().<Callable<Map<String, Object>>>map(domain ->
          () -> describe(domain, defaultShop, consumer)).toList();
      var results = executor.invokeAll(checks, 50, TimeUnit.SECONDS);
      int index = 0;
      for (String domain : candidates) {
        var result = results.get(index++);
        if (result.isCancelled()) items.add(status(domain, "unavailable"));
        else items.add(result.get());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new GatewayException(503, "interrupted", "店铺核对被中断", true);
    } catch (ExecutionException e) {
      throw new GatewayException(502, "directory_unavailable", "店铺核对暂不可用", true);
    }
    return JsonUtils.obj("ok", true, "meta", JsonUtils.obj("mock", false), "data",
        JsonUtils.obj("stores", items, "mock", false));
  }

  private Map<String, Object> describe(String domain, String defaultShop, String consumer) {
    try {
      boolean own = domain.equals(defaultShop);
      String scope = own ? consumer : credentials.systemForStore("SHOPIFY_STORE", domain);
      if (scope == null) return status(domain, "pending");
      String version = credentials.resolve("SHOPIFY_API_VERSION", scope);
      if (version.isBlank()) version = "2026-07";
      if (!version.matches("[0-9]{4}-[0-9]{2}")) return status(domain, "unavailable");
      String token = ShopifyStoreAccess.token(credentials, http, scope, domain, !own);
      var response = http.object("POST", "https://" + domain + "/admin/api/" + version + "/graphql.json",
          Map.of("X-Shopify-Access-Token", token), JsonUtils.obj("query", QUERY, "variables", Map.of()));
      if (!JsonUtils.list(response.get("errors")).isEmpty()) return status(domain, "unavailable");
      var data = JsonUtils.map(response.get("data"));
      var shop = JsonUtils.map(data.get("shop"));
      String shopId = JsonUtils.str(shop, "id");
      if (!domain.equals(ShopifyStoreAccess.domain(JsonUtils.str(shop, "myshopifyDomain")))
          || !shopId.matches("gid://shopify/Shop/[0-9]+")) return status(domain, "unavailable");
      var installation = JsonUtils.map(data.get("currentAppInstallation"));
      if (!(installation.get("accessScopes") instanceof List<?> scopes))
        return status(domain, "unavailable");
      var handles = scopes.stream().map(scopeItem -> JsonUtils.str(JsonUtils.map(scopeItem), "handle")).toList();
      var result = status(domain, handles.contains("read_orders") || handles.contains("write_orders")
          ? "authorized" : "pending");
      result.put("shop_id", shopId);
      result.put("name", JsonUtils.str(shop, "name"));
      result.put("currency", JsonUtils.str(shop, "currencyCode"));
      result.put("timezone", JsonUtils.str(shop, "ianaTimezone"));
      result.put("historical_orders", handles.contains("read_all_orders"));
      return result;
    } catch (GatewayException e) {
      boolean missing = e.status == 401 || e.status == 403 || e.type.equals("not_configured")
          || "UPSTREAM_HTTP_401".equals(e.code) || "UPSTREAM_HTTP_403".equals(e.code);
      return status(domain, missing ? "pending" : "unavailable");
    }
  }

  private static Map<String, Object> status(String domain, String status) {
    return JsonUtils.obj("domain", domain, "status", status);
  }
}
