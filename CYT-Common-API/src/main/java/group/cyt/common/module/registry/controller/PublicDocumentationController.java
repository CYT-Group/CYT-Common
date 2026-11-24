package group.cyt.common.module.registry.controller;

import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.registry.service.OperationRegistry;
import java.io.IOException;
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
public class PublicDocumentationController {
  private static final String README =
      """
      # CYT Common API 接入包

      用浏览器打开 START_HERE.html，可离线搜索接口、查看参数及复制调用示例。
      openapi.json 可直接导入接口工具，Python 与 TypeScript SDK 位于 sdk 目录。

      向管理员领取业务系统 API Key 和网关地址。将密钥存放于业务后端，
      在 Authorization 请求头中使用 Bearer 认证。接口调用权与配额由网关实时校验。

      本包展示网关内置接口。调用方的实际授权范围和自定义接口见管理员导出的专属接入包。
      """;

  private record Documents(Map<String, Object> openapi, String html, byte[] archive) {}

  private final SingletonSupplier<Documents> docs;

  public PublicDocumentationController(OperationRegistry registry, RegistryController exporter) {
    docs = SingletonSupplier.of(() -> generate(registry, exporter));
  }

  @GetMapping(value = {"", "/"}, produces = "text/html;charset=UTF-8")
  public String guide() {
    return docs.obtain().html();
  }

  @GetMapping("/openapi.json")
  public ResponseEntity<Map<String, Object>> openapi() {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cyt-common-openapi.json\"")
        .body(docs.obtain().openapi());
  }

  @GetMapping("/download")
  public ResponseEntity<byte[]> download() {
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
    try {
      return new Documents(
          spec, IntegrationGuide.renderOnline(spec), IntegrationPackage.build(spec, README));
    } catch (IOException e) {
      throw GatewayException.config("在线接入文档生成失败");
    }
  }
}
