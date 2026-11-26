package group.cyt.common.module.custom.controller;

import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.auth.service.AuthenticationService;
import group.cyt.common.module.custom.service.ApiPublicationService;
import group.cyt.common.module.custom.service.ConfigurationService;
import group.cyt.common.module.custom.service.ConfigurationService.Kind;
import group.cyt.common.module.gateway.service.GatewayExecutor;
import group.cyt.common.module.registry.service.OperationRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/api")
public class CustomApiController {
  private final ConfigurationService config;
  private final GatewayStore store;
  private final OperationRegistry registry;
  private final GatewayExecutor executor;
  private final ApiPublicationService publication;

  public CustomApiController(
      ConfigurationService config,
      GatewayStore store,
      OperationRegistry registry,
      GatewayExecutor executor,
      ApiPublicationService publication) {
    this.config = config;
    this.store = store;
    this.registry = registry;
    this.executor = executor;
    this.publication = publication;
  }

  @GetMapping("/modules")
  public Object modules() {
    var rows = config.list(Kind.MODULE);
    rows.forEach(
        m ->
            m.put(
                "operation_count",
                store.count(
                    "SELECT COUNT(*) FROM gateway_custom_api WHERE module_key=?", m.get("key"))));
    return JsonUtils.obj("items", rows);
  }

  @GetMapping("/custom-apis")
  public Object apis() {
    return JsonUtils.obj("items", config.list(Kind.API));
  }

  @PostMapping("/modules")
  public Object createModule(@RequestBody Map<String, Object> body, HttpServletRequest req) {
    return save(Kind.MODULE, null, body, req);
  }

  @PutMapping("/modules/{id}")
  public Object updateModule(
      @PathVariable long id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
    return save(Kind.MODULE, id, body, req);
  }

  @DeleteMapping("/modules/{id}")
  public Object deleteModule(@PathVariable long id, HttpServletRequest req) {
    return delete(Kind.MODULE, id, req);
  }

  @PostMapping("/custom-apis")
  public Object createApi(@RequestBody Map<String, Object> body, HttpServletRequest req) {
    return save(Kind.API, null, body, req);
  }

  @PutMapping("/custom-apis/{id}")
  public Object updateApi(
      @PathVariable long id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
    return save(Kind.API, id, body, req);
  }

  @DeleteMapping("/custom-apis/{id}")
  public Object deleteApi(@PathVariable long id, HttpServletRequest req) {
    return delete(Kind.API, id, req);
  }

  @GetMapping("/custom-apis/{id}/preflight")
  public Object preflight(@PathVariable long id) {
    return publication.preflight(id);
  }

  @PostMapping("/custom-apis/{id}/publication")
  public Object publication(
      @PathVariable long id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
    if (!(body.get("enabled") instanceof Boolean enabled))
      throw GatewayException.invalid("请指定布尔值 enabled");
    return publication.publish(id, enabled, AuthenticationService.actor(req));
  }

  @PostMapping("/custom-apis/{id}/test")
  public Object test(
      @PathVariable long id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
    var row = config.get(Kind.API, id);
    var op = registry.get(JsonUtils.str(row, "key"));
    String system = JsonUtils.str(body, "system");
    if (system.isBlank()) system = "portal";
    return executor.execute(
        system,
        op.key(),
        JsonUtils.map(body.get("params")),
        request.getHeader("Idempotency-Key"),
        request.getHeader("X-Trace-Id"));
  }

  @PostMapping("/modules/{id}/test-auth")
  public Object testAuth(@PathVariable long id) {
    var module = config.get(Kind.MODULE, id);
    return JsonUtils.obj(
        "ok",
        true,
        "verified",
        false,
        "auth_type",
        module.get("auth_type"),
        "note",
        "配置已加载；请使用模块下接口的调试功能验证真实鉴权。");
  }

  @PostMapping("/custom-apis/order")
  public Object order(@RequestBody Map<String, Object> body, HttpServletRequest req) {
    store.atomic(
        () -> {
          int order = 0;
          for (Object id : JsonUtils.list(body.get("items")))
            store.update("UPDATE gateway_custom_api SET sort_order=? WHERE id=?", order++, id);
          store.audit(
              AuthenticationService.actor(req), "custom_api_order", "custom_apis", Map.of());
          return null;
        });
    return JsonUtils.obj("ok", true);
  }

  private Object save(Kind kind, Long id, Map<String, Object> body, HttpServletRequest req) {
    return JsonUtils.obj(
        "ok", true, "id", config.save(kind, id, body, AuthenticationService.actor(req)));
  }

  private Object delete(Kind kind, long id, HttpServletRequest req) {
    config.delete(kind, id, AuthenticationService.actor(req));
    return JsonUtils.obj("ok", true);
  }
}
