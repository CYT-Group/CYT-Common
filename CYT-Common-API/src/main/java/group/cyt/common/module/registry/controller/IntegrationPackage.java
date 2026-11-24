package group.cyt.common.module.registry.controller;

import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.util.JsonUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

final class IntegrationPackage {
  private IntegrationPackage() {}

  static byte[] build(Object spec, String readme) {
    try {
      var bytes = new ByteArrayOutputStream();
      try (var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
        entry(zip, "START_HERE.html", IntegrationGuide.render(spec).getBytes(StandardCharsets.UTF_8));
        entry(zip, "README.md", readme.getBytes(StandardCharsets.UTF_8));
        entry(zip, "openapi.json", JsonUtils.write(spec).getBytes(StandardCharsets.UTF_8));
        for (String resource : List.of("sdk/python/cyt_gateway.py", "sdk/typescript/index.ts")) {
          try (var input = IntegrationPackage.class.getResourceAsStream("/" + resource)) {
            if (input == null) throw GatewayException.config("SDK 资源未打包，请重新构建网关");
            entry(zip, resource, input.readAllBytes());
          }
        }
      }
      return bytes.toByteArray();
    } catch (IOException e) {
      throw GatewayException.config("接入包生成失败");
    }
  }

  static ResponseEntity<byte[]> download(byte[] bytes, String filename) {
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/zip"))
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .body(bytes);
  }

  private static void entry(ZipOutputStream zip, String name, byte[] value) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(value);
    zip.closeEntry();
  }
}
