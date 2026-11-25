package group.cyt.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.http.UpstreamHttp;
import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.adapter.MockAdapter;
import group.cyt.common.module.adapter.nas.NasAdapter;
import group.cyt.common.module.credential.service.CredentialService;
import group.cyt.common.module.registry.service.OperationRegistry;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NasFolderBatchTest {
  private static final String OPERATION = "nas.folders.create_batch";
  private static final String BASE = "products/SKU-001/";

  @Test
  void createsTheProductTreeAndPreservesExistingFoldersAndFiles() throws Exception {
    var objects = new ConcurrentHashMap<String, String>();
    String photo = BASE + "产品图片/拍摄白底图/sample.jpg";
    String existingMarker = BASE + "产品视频/横视频/ASMR纯享视频/";
    objects.put(photo, "original-file");
    objects.put(existingMarker, "existing-marker");
    var puts = new CopyOnWriteArrayList<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/proxy/fixture-bucket",
        exchange -> {
          try (exchange) {
            var uri = exchange.getRequestURI();
            if (exchange.getRequestMethod().equals("GET")) {
              String prefix =
                  HttpUrl.get("http://localhost" + uri.toASCIIString()).queryParameter("prefix");
              boolean exists = objects.keySet().stream().anyMatch(key -> key.startsWith(prefix));
              var body =
                  ("<ListBucketResult>" + (exists ? "<Contents/>" : "") + "</ListBucketResult>")
                      .getBytes(StandardCharsets.UTF_8);
              exchange.sendResponseHeaders(200, body.length);
              exchange.getResponseBody().write(body);
              return;
            }
            String key = uri.getPath().substring("/proxy/fixture-bucket/".length());
            puts.add(key);
            var headers = exchange.getRequestHeaders();
            if (!exchange.getRequestMethod().equals("PUT")
                || !"*".equals(headers.getFirst("If-None-Match"))
                || !headers.getFirst("Authorization").contains(";if-none-match;")
                || exchange.getRequestBody().readAllBytes().length != 0) {
              exchange.sendResponseHeaders(400, -1);
              return;
            }
            // Simulate another caller creating this marker between the lookup and the PUT.
            if (key.equals(BASE + "产品设计图/海报设计/")) objects.putIfAbsent(key, "concurrent-marker");
            exchange.sendResponseHeaders(objects.putIfAbsent(key, "") == null ? 200 : 412, -1);
          }
        });
    server.start();
    try {
      var adapter =
          adapter(
              new UpstreamHttp(), "http://127.0.0.1:" + server.getAddress().getPort() + "/proxy");
      var params =
          Map.<String, Object>of(
              "base_prefix",
              BASE,
              "paths",
              List.of(
                  "产品图片/拍摄白底图",
                  "产品图片/拍摄社交图",
                  "产品图片/渲染白底图",
                  "产品图片/渲染社交图",
                  "产品视频/竖视频/ASMR纯享视频",
                  "产品视频/竖视频/开箱视频",
                  "产品视频/竖视频/how to use 视频",
                  "产品视频/横视频/ASMR纯享视频",
                  "产品视频/横视频/开箱视频",
                  "产品视频/横视频/how to use 视频",
                  "产品视频/横视频/品宣视频",
                  "产品设计图/亚马逊平台页面设计",
                  "产品设计图/其他平台页面设计",
                  "产品设计图/独立站页面设计",
                  "产品设计图/国内页面设计",
                  "产品设计图/海报设计",
                  "产品视频/竖视频/"));
      var result = invoke(adapter, params);
      assertEquals(21, result.get("total"));
      assertEquals(15, result.get("created"));
      assertEquals(6, result.get("exists"));
      assertEquals(0, result.get("failed"));
      assertEquals("exists", item(result, BASE + "产品图片/").get("status"));
      assertEquals("exists", item(result, BASE + "产品图片/拍摄白底图/").get("status"));
      assertEquals("exists", item(result, existingMarker).get("status"));
      assertEquals("exists", item(result, BASE + "产品设计图/海报设计/").get("status"));
      assertEquals("created", item(result, BASE + "产品视频/竖视频/how to use 视频/").get("status"));
      assertEquals("original-file", objects.get(photo));
      assertEquals("existing-marker", objects.get(existingMarker));
      assertEquals("concurrent-marker", objects.get(BASE + "产品设计图/海报设计/"));
      assertFalse(puts.contains(existingMarker));
      assertFalse(puts.contains(BASE));
      assertTrue(puts.indexOf(BASE + "产品视频/竖视频/") < puts.indexOf(BASE + "产品视频/竖视频/开箱视频/"));

      puts.clear();
      var replay = invoke(adapter, params);
      assertEquals(21, replay.get("exists"));
      assertEquals(0, replay.get("created"));
      assertEquals(0, replay.get("failed"));
      assertTrue(puts.isEmpty());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void reportsFailedBranchAndStillCreatesIndependentFolders() throws Exception {
    var http = mock(UpstreamHttp.class);
    when(http.exchange(eq("GET"), anyString(), anyMap(), isNull(), anyDouble(), isNull()))
        .thenReturn(
            new UpstreamHttp.ResponseData(
                200, Map.of(), "<ListBucketResult/>".getBytes(StandardCharsets.UTF_8)));
    when(http.exchange(eq("PUT"), anyString(), anyMap(), any(), anyDouble(), isNull()))
        .thenReturn(new UpstreamHttp.ResponseData(200, Map.of(), new byte[0]));
    when(http.exchange(eq("PUT"), endsWith("/blocked/"), anyMap(), any(), anyDouble(), isNull()))
        .thenThrow(
            new GatewayException(502, "upstream_error", "上游返回 HTTP 503", true)
                .code("UPSTREAM_HTTP_503"));

    var result =
        invoke(
            adapter(http, "http://localhost"),
            Map.of("paths", List.of("blocked/sub/leaf", "independent")));

    assertEquals(3, result.get("failed"));
    assertEquals(
        "UPSTREAM_HTTP_503", JsonUtils.map(item(result, "blocked/").get("error")).get("code"));
    var childError = JsonUtils.map(item(result, "blocked/sub/leaf/").get("error"));
    assertEquals("PARENT_FOLDER_FAILED", childError.get("code"));
    assertEquals(true, childError.get("retryable"));
    assertEquals("created", item(result, "independent/").get("status"));
    verify(http, never())
        .exchange(eq("PUT"), contains("/blocked/sub/"), anyMap(), any(), anyDouble(), isNull());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/absolute", "valid//invalid", "trailing//", "a/../b", "a/./b", "a\\b", "a/ \t/b"})
  void rejectsUnsafePathsBeforeContactingStorage(String invalid) throws Exception {
    var http = mock(UpstreamHttp.class);
    var adapter = adapter(http, "http://localhost");
    var error =
        assertThrows(
            GatewayException.class,
            () -> invoke(adapter, Map.of("paths", List.of("valid/path", invalid))));
    assertEquals(422, error.status);
    verifyNoInteractions(http);
  }

  @Test
  void rejectsExpandedDirectoryCountAndUtf8KeyLengthBeforeWriting() throws Exception {
    var http = mock(UpstreamHttp.class);
    var adapter = adapter(http, "http://localhost");
    for (String invalid : List.of("dir/".repeat(101), "字".repeat(167))) {
      assertEquals(
          422,
          assertThrows(
                  GatewayException.class,
                  () -> invoke(adapter, Map.of("paths", List.of("valid", invalid))))
              .status);
    }
    verifyNoInteractions(http);
  }

  @Test
  void mockModeReturnsTheSameExpandedResultShape() throws Exception {
    var result =
        JsonUtils.map(
            new MockAdapter()
                .invoke(
                    registry().get(OPERATION),
                    Map.of("paths", List.of("父目录/子目录", "父目录/")),
                    "fixture"));
    assertEquals(true, result.get("_mock"));
    assertEquals(2, result.get("created"));
    assertEquals("created", item(result, "父目录/子目录/").get("status"));
  }

  private static NasAdapter adapter(UpstreamHttp http, String endpoint) {
    var credentials = mock(CredentialService.class);
    when(credentials.required("NAS_MINIO_ENDPOINT", "fixture")).thenReturn(endpoint);
    when(credentials.required("NAS_MINIO_ACCESS_KEY", "fixture")).thenReturn("fixture-access");
    when(credentials.required("NAS_MINIO_SECRET_KEY", "fixture")).thenReturn("fixture-secret");
    when(credentials.resolve("NAS_MINIO_BUCKET", "fixture")).thenReturn("fixture-bucket");
    when(credentials.resolve("NAS_MINIO_REGION", "fixture")).thenReturn("us-east-1");
    return new NasAdapter(credentials, http);
  }

  private static Map<String, Object> invoke(NasAdapter adapter, Map<String, Object> params)
      throws Exception {
    var registry = registry();
    var operation = registry.get(OPERATION);
    return JsonUtils.map(
        adapter.invoke(operation, registry.validate(operation, params), "fixture"));
  }

  private static OperationRegistry registry() throws Exception {
    return new OperationRegistry(mock(GatewayStore.class));
  }

  private static Map<String, Object> item(Map<String, Object> result, String key) {
    return JsonUtils.list(result.get("items")).stream()
        .map(JsonUtils::map)
        .filter(row -> key.equals(row.get("key")))
        .findFirst()
        .orElseThrow();
  }
}
