package group.cyt.common.module.registry.controller;

import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.auth.service.AuthenticationService;
import group.cyt.common.module.registry.service.OperationRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.function.SingletonSupplier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api-reference")
public class DocumentationController {
  private static final String README =
      """
      # CYT Common API 接入包

      用浏览器打开 START_HERE.html，可离线搜索接口、查看参数及复制调用示例。
      openapi.json 可直接导入接口工具，Python 与 TypeScript SDK 位于 sdk 目录。

      向管理员领取业务系统 API Key 和网关地址。将密钥存放于业务后端，
      在 Authorization 请求头中使用 Bearer 认证。接口调用权与配额由网关实时校验。

      本包展示网关内置接口。调用方的实际授权范围和自定义接口见管理员导出的专属接入包。
      """;

  private record Documents(Map<String, Object> openapi, byte[] archive) {}

  private final AuthenticationService authentication;
  private final SingletonSupplier<Documents> docs;

  public DocumentationController(
      AuthenticationService authentication, OperationRegistry registry, RegistryController exporter) {
    this.authentication = authentication;
    docs = SingletonSupplier.of(() -> generate(registry, exporter));
  }

  @GetMapping({"", "/"})
  public ResponseEntity<Void> guide() {
    return ResponseEntity.status(302).location(URI.create("/#/docs")).build();
  }

  @GetMapping("/openapi.json")
  public ResponseEntity<Map<String, Object>> openapi(HttpServletRequest request) {
    authentication.admin(request);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cyt-common-openapi.json\"")
        .body(docs.obtain().openapi());
  }

  @GetMapping("/download")
  public ResponseEntity<byte[]> download(HttpServletRequest request) {
    authentication.admin(request);
    return IntegrationPackage.download(docs.obtain().archive(), "cyt-common-integration.zip");
  }

  private static Documents generate(OperationRegistry registry, RegistryController exporter) {
    var spec = exporter.openapiFor(registry.builtinOperations());
    var platforms = new LinkedHashMap<String, String>();
    for (var platform : registry.contract().path("platforms")) {
      platforms.put(platform.path("platform").asText(), platform.path("display_name").asText());
    }
    var info = JsonUtils.map(spec.get("info"));
    info.put("title", "CYT Common API 接入文档");
    spec.put("info", info);
    spec.put("x-cyt-public-reference", true);
    spec.put("x-cyt-platform-labels", platforms);
    return new Documents(spec, IntegrationPackage.build(spec, README));
  }
}
