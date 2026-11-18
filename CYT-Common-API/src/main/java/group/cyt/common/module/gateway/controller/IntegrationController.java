package group.cyt.common.module.gateway.controller;

import group.cyt.common.core.config.GatewaySettings;
import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.http.BinaryUpload;
import group.cyt.common.core.http.BinaryUploadFilter;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.auth.service.AuthenticationService;
import group.cyt.common.module.gateway.service.GatewayExecutor;
import group.cyt.common.module.registry.service.OperationRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class IntegrationController {
  private final AuthenticationService authentication;
  private final GatewayExecutor executor;
  private final OperationRegistry registry;
  private final GatewaySettings settings;

  public IntegrationController(
      AuthenticationService authentication,
      GatewayExecutor executor,
      OperationRegistry registry,
      GatewaySettings settings) {
    this.authentication = authentication;
    this.executor = executor;
    this.registry = registry;
    this.settings = settings;
  }

  @PutMapping("/integrations/nas/objects/upload")
  public Object upload(HttpServletRequest request) {
    String system = authentication.system(request);
    var query = new LinkedHashMap<String, Object>();
    String raw = request.getQueryString();
    if (raw != null && raw.length() > 8192) throw GatewayException.invalid("上传 query 过长");
    if (raw != null && !raw.isBlank()) {
      try {
        for (String part : raw.split("&")) {
          String[] pair = part.split("=", 2);
          String name =
              java.net.URLDecoder.decode(pair[0], java.nio.charset.StandardCharsets.UTF_8);
          String value =
              pair.length == 1
                  ? ""
                  : java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8);
          if (query.putIfAbsent(name, value) != null)
            throw GatewayException.invalid("上传 query 不允许重复参数");
        }
      } catch (IllegalArgumentException e) {
        throw GatewayException.invalid("上传 query 编码无效");
      }
    }
    String contentType =
        request.getAttribute(BinaryUploadFilter.ORIGINAL_TYPE) instanceof String original
            ? original
            : request.getContentType();
    return executor.executeUpload(
        system,
        query,
        new BinaryUpload(
            request::getInputStream,
            request.getHeader("Content-Length"),
            request.getHeader("Transfer-Encoding"),
            contentType,
            settings),
        request.getHeader("X-Trace-Id"));
  }

  @PostMapping("/integrations/{platform}/{resource}/{action}")
  public Object invoke(
      @PathVariable String platform,
      @PathVariable String resource,
      @PathVariable String action,
      @RequestBody Map<String, Object> params,
      HttpServletRequest request) {
    String key =
        platform.replace('-', '_')
            + "."
            + resource.replace('-', '_')
            + "."
            + action.replace('-', '_');
    return executor.execute(
        authentication.system(request),
        key,
        params,
        request.getHeader("Idempotency-Key"),
        request.getHeader("X-Trace-Id"));
  }

  @PostMapping("/integrations/batch")
  public Object batch(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    if (!body.keySet().stream().allMatch(Set.of("calls", "stop_on_error")::contains))
      throw GatewayException.invalid("批量请求包含未知字段");
    return executor.batch(
        authentication.system(request),
        JsonUtils.list(body.get("calls")).stream().map(JsonUtils::map).toList(),
        JsonUtils.bool(body.get("stop_on_error")));
  }

  @PostMapping("/admin/api/simulate")
  public Object simulate(@RequestBody Map<String, Object> body) {
    return executor.execute(
        JsonUtils.str(body, "system"),
        JsonUtils.str(body, "operation"),
        JsonUtils.map(body.get("params")),
        JsonUtils.str(body, "idempotency_key"),
        null);
  }
}
