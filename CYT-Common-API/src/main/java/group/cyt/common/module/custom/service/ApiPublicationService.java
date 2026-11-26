package group.cyt.common.module.custom.service;

import group.cyt.common.core.config.GatewaySettings;
import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.credential.service.CredentialService;
import group.cyt.common.module.grant.service.AuthorizationService;
import group.cyt.common.module.registry.service.OperationRegistry;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiPublicationService {
  private final GatewayStore store;
  private final OperationRegistry registry;
  private final AuthorizationService authorization;
  private final CredentialService credentials;
  private final GatewaySettings settings;

  public ApiPublicationService(
      GatewayStore store,
      OperationRegistry registry,
      AuthorizationService authorization,
      CredentialService credentials,
      GatewaySettings settings) {
    this.store = store;
    this.registry = registry;
    this.authorization = authorization;
    this.credentials = credentials;
    this.settings = settings;
  }

  public Map<String, Object> preflight(long id) {
    var api = api(id, false);
    return inspect(api, provider(api, false));
  }

  @Transactional
  public Map<String, Object> publish(long id, boolean enabled, String actor) {
    var api = api(id, true);
    var provider = provider(api, enabled);
    if (enabled) {
      var checks = inspect(api, provider);
      if (!JsonUtils.bool(checks.get("can_publish")))
        throw GatewayException.invalid("请先完成发布检查").detail(checks.get("blocking_reasons"));
    }
    if (JsonUtils.bool(api.get("enabled")) != enabled) {
      store.edit(
          "gateway_custom_api",
          id,
          JsonUtils.obj("enabled", enabled, "updated_at", GatewayStore.now()));
      store.audit(
          actor, "api_publication", JsonUtils.str(api, "key"), JsonUtils.obj("enabled", enabled));
    }
    return JsonUtils.obj("ok", true, "enabled", enabled);
  }

  private Map<String, Object> api(long id, boolean forUpdate) {
    var api =
        store.one(
            "SELECT * FROM gateway_custom_api WHERE id=?" + (forUpdate ? " FOR UPDATE" : ""), id);
    if (api == null) throw GatewayException.missing("配置不存在");
    return api;
  }

  private Map<String, Object> provider(Map<String, Object> api, boolean forShare) {
    var provider =
        store.one(
            "SELECT * FROM gateway_module WHERE key=? AND service_kind='internal'"
                + (forShare ? " FOR SHARE" : ""),
            JsonUtils.str(api, "module_key"));
    if (provider == null) throw GatewayException.invalid("仅内部服务接口支持发布检查");
    return provider;
  }

  private Map<String, Object> inspect(Map<String, Object> api, Map<String, Object> provider) {
    var operation = registry.custom(api);
    boolean production = settings.production();
    boolean scopedAddress = ConfigurationService.hasEnvironmentAddresses(provider);
    var config = ConfigurationService.customHttpConfig(api, provider, production);
    String upstreamAddress = JsonUtils.str(config, "base_url");
    String auth = JsonUtils.str(config, "auth_type", "none");
    var refs =
        switch (auth) {
          case "bearer", "basic", "header", "query" -> List.of(JsonUtils.str(config, "secret_ref"));
          case "oauth2_refresh" ->
              List.of(
                  JsonUtils.str(config, "client_id_ref"),
                  JsonUtils.str(config, "client_secret_ref"),
                  JsonUtils.str(config, "refresh_token_ref"));
          default -> List.<String>of();
        };
    var grants =
        store.rows("SELECT * FROM gateway_grant ORDER BY system,operation_pattern").stream()
            .collect(Collectors.groupingBy(grant -> JsonUtils.str(grant, "system")));
    var callers =
        store.rows("SELECT key,display_name,enabled FROM gateway_system ORDER BY id").stream()
            .map(
                system -> {
                  String key = JsonUtils.str(system, "key");
                  var decision =
                      authorization.decision(operation, grants.getOrDefault(key, List.of()));
                  boolean active = JsonUtils.bool(system.get("enabled"));
                  return JsonUtils.obj(
                      "key",
                      key,
                      "display_name",
                      system.get("display_name"),
                      "enabled",
                      active,
                      "allowed",
                      active && JsonUtils.bool(decision.get("allowed")),
                      "reason",
                      active ? decision.get("reason") : "调用系统已停用",
                      "quota",
                      decision.get("quota"),
                      "rules",
                      decision.get("rules"));
                })
            .toList();
    var authorized =
        callers.stream()
            .filter(caller -> JsonUtils.bool(caller.get("allowed")))
            .map(caller -> JsonUtils.str(caller, "key"))
            .toList();
    boolean apiEnabled = JsonUtils.bool(api.get("enabled"));
    boolean providerEnabled = JsonUtils.bool(provider.get("enabled"));
    boolean addressConfigured = !upstreamAddress.isBlank();
    String credentialStatus = credentialStatus(config, auth, refs, authorized);

    List<Map<String, Object>> blockers = new ArrayList<>();
    if (!providerEnabled) blockers.add(blocker("provider_disabled", "后端服务尚未启用"));
    if (!addressConfigured)
      blockers.add(
          blocker(
              "upstream_address_missing",
              scopedAddress ? "缺少" + (production ? "正式" : "测试") + "环境后端服务地址" : "缺少后端服务地址"));
    switch (credentialStatus) {
      case "missing_reference" ->
          blockers.add(blocker("credential_missing_reference", "上游鉴权配置不完整"));
      case "missing_value" -> blockers.add(blocker("credential_missing_value", "未找到所需上游凭据"));
      case "unverified" -> blockers.add(blocker("credential_unverified", "当前无法核实上游凭据配置"));
      default -> {}
    }
    if (authorized.isEmpty()) blockers.add(blocker("caller_missing", "尚无已授权的启用调用方"));

    return JsonUtils.obj(
        "operation_key",
        operation.key(),
        "gateway_path",
        operation.path(),
        "api_enabled",
        apiEnabled,
        "provider_enabled",
        providerEnabled,
        "enabled",
        apiEnabled && providerEnabled,
        "upstream_address_configured",
        addressConfigured,
        "upstream_environment",
        production ? "production" : "test",
        "upstream_base_url",
        upstreamAddress,
        "environment_specific",
        scopedAddress,
        "auth_type",
        auth,
        "credential_refs",
        refs.stream().filter(ref -> ref.matches("CUSTOM_[A-Z0-9_]{1,57}")).distinct().toList(),
        "credential_status",
        credentialStatus,
        "callers",
        callers,
        "authorized_count",
        authorized.size(),
        "can_publish",
        blockers.isEmpty(),
        "blocking_reasons",
        blockers);
  }

  private String credentialStatus(
      Map<String, Object> config, String auth, List<String> refs, List<String> authorized) {
    if (refs.isEmpty()) return "not_required";
    if (refs.stream().anyMatch(ref -> !ref.matches("CUSTOM_[A-Z0-9_]{1,57}"))
        || (auth.equals("oauth2_refresh") && JsonUtils.str(config, "token_url").isBlank())
        || (Set.of("header", "query").contains(auth)
            && JsonUtils.str(config, "auth_name").isBlank())) return "missing_reference";
    try {
      boolean found =
          authorized.isEmpty()
              ? refs.stream().allMatch(ref -> !credentials.raw(ref).isBlank())
              : authorized.stream()
                  .allMatch(
                      system ->
                          refs.stream()
                              .allMatch(ref -> !credentials.resolve(ref, system).isBlank()));
      return found ? "configured" : "missing_value";
    } catch (GatewayException e) {
      return "unverified";
    }
  }

  private static Map<String, Object> blocker(String code, String message) {
    return JsonUtils.obj("code", code, "message", message);
  }
}
