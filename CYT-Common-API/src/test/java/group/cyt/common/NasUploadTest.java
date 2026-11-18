package group.cyt.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.sun.net.httpserver.HttpServer;
import group.cyt.common.core.config.GatewaySettings;
import group.cyt.common.core.exception.*;
import group.cyt.common.core.http.*;
import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.adapter.*;
import group.cyt.common.module.adapter.nas.NasAdapter;
import group.cyt.common.module.adapter.support.BusinessWorkflows;
import group.cyt.common.module.adapter.wecom.approval.WecomApprovalService;
import group.cyt.common.module.auth.service.AuthenticationService;
import group.cyt.common.module.credential.service.CredentialService;
import group.cyt.common.module.gateway.controller.IntegrationController;
import group.cyt.common.module.gateway.service.*;
import group.cyt.common.module.grant.service.*;
import group.cyt.common.module.registry.controller.*;
import group.cyt.common.module.registry.service.*;
import jakarta.servlet.ServletInputStream;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.FormContentFilter;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class NasUploadTest {
  static final String PATH = "/integrations/nas/objects/upload";
  static final String TOKEN = "cyt_fixture_test-secret";

  static class Fixture implements AutoCloseable {
    final MockEnvironment env = new MockEnvironment().withProperty("MOCK_ALL", "false");
    final GatewaySettings settings = new GatewaySettings(env);
    final GatewayStore store = mock(GatewayStore.class);
    final OperationRegistry registry = new OperationRegistry(store);
    final AuthorizationService authorization = new AuthorizationService(store, registry);
    final AuthenticationService authentication = new AuthenticationService(settings, store);
    final QuotaService quota = spy(new QuotaService(settings));
    final CallLogService logs = mock(CallLogService.class);
    final CredentialService credentials = new CredentialService(store, settings, registry);
    final NasAdapter nas = new NasAdapter(credentials, new UpstreamHttp());
    final AdapterDispatcher dispatcher =
        new AdapterDispatcher(
            List.of(nas),
            new MockAdapter(),
            settings,
            registry,
            mock(WecomApprovalService.class),
            mock(BusinessWorkflows.class));
    final GatewayExecutor executor =
        new GatewayExecutor(registry, authorization, quota, dispatcher, store, logs);
    final IntegrationController controller =
        new IntegrationController(authentication, executor, registry, settings);
    volatile List<Map<String, Object>> grants = List.of(grant("nas.objects.upload", "allow"));

    Fixture() throws Exception {
      when(store.one("SELECT enabled FROM gateway_system WHERE key=?", "fixture"))
          .thenReturn(Map.of("enabled", true));
      when(store.one(
              "SELECT * FROM gateway_system_apikey WHERE prefix=? AND revoked=false", "fixture"))
          .thenReturn(
              JsonUtils.obj(
                  "id", 1, "system", "fixture", "secret_hash", JsonUtils.sha("test-secret")));
      when(store.rows("SELECT * FROM gateway_grant WHERE system=?", "fixture"))
          .thenAnswer(i -> grants);
    }

    BinaryUpload source(InputStream body, String length) {
      return new BinaryUpload(() -> body, length, null, "application/octet-stream", settings);
    }

    Map<String, Object> upload(BinaryUpload source) {
      return executor.executeUpload(
          "fixture", Map.of("filename", "file.bin", "key", "uploads/file.bin"), source, null);
    }

    void mockMode() {
      env.setProperty("NAS_MOCK", "true");
    }

    void upstream(String endpoint) {
      env.setProperty("NAS_MINIO_ENDPOINT", endpoint);
      env.setProperty("NAS_MINIO_ACCESS_KEY", "fixture-access");
      env.setProperty("NAS_MINIO_SECRET_KEY", "fixture-secret");
    }

    @Override
    public void close() {
      executor.close();
      quota.close();
    }
  }

  static Map<String, Object> grant(String pattern, String effect) {
    return JsonUtils.obj("operation_pattern", pattern, "effect", effect);
  }

  Fixture f;

  @BeforeEach
  void setup() throws Exception {
    f = new Fixture();
  }

  @AfterEach
  void close() {
    if (f != null) f.close();
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 65535, 65536, 65537, 196625})
  void streamsBoundedChunksAndConfirmsEofBeforeLastWrite(int length) throws Exception {
    AtomicBoolean eof = new AtomicBoolean();
    AtomicInteger maxRead = new AtomicInteger();
    var input =
        new ByteArrayInputStream(new byte[length]) {
          @Override
          public synchronized int read(byte[] b, int off, int len) {
            maxRead.accumulateAndGet(len, Math::max);
            int n = super.read(b, off, len);
            if (n < 0) eof.set(true);
            return n;
          }
        };
    var source = f.source(input, Integer.toString(length));
    source.prepare();
    AtomicLong written = new AtomicLong();
    source.writeTo(
        new OutputStream() {
          @Override
          public void write(int b) {
            fail("must use bounded blocks");
          }

          @Override
          public void write(byte[] b, int off, int len) {
            assertTrue(len <= 65536);
            long total = written.addAndGet(len);
            if (total == length) assertTrue(eof.get(), "final block sent before EOF");
            else assertFalse(eof.get(), "whole file buffered before forwarding");
          }
        });
    assertTrue(source.completed());
    assertTrue(eof.get());
    assertEquals(length, written.get());
    assertTrue(maxRead.get() <= 65536);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 1})
  void mismatchNeverSendsTheFinalBlock(int delta) throws Exception {
    var source = f.source(new ByteArrayInputStream(new byte[100000 + delta]), "100000");
    source.prepare();
    var sent = new ByteArrayOutputStream();
    assertEquals(422, assertThrows(GatewayException.class, () -> source.writeTo(sent)).status);
    assertEquals(65536, sent.size());
    assertFalse(source.completed());
  }

  @Test
  void clientReadFailureIs422AndUpstreamWriteFailureIsNotMisreported() throws Exception {
    f.mockMode();
    assertEquals(
        422,
        assertThrows(
                GatewayException.class,
                () ->
                    f.upload(
                        f.source(
                            new InputStream() {
                              public int read() throws IOException {
                                throw new IOException("sensitive client data");
                              }
                            },
                            "20")))
            .status);
    var source = f.source(new ByteArrayInputStream(new byte[80000]), "80000");
    source.prepare();
    assertThrows(
        IOException.class,
        () ->
            source.writeTo(
                new OutputStream() {
                  public void write(int b) throws IOException {
                    throw new IOException("upstream disconnected");
                  }
                }));
    assertNull(source.failure());
  }

  @ParameterizedTest
  @ValueSource(strings = {"*", "nas.*", "nas.objects.*", "nas.objects.presign_put"})
  void wildcardAndPresignGrantsNeverAuthorizeUpload(String pattern) {
    f.grants = List.of(grant(pattern, "allow"));
    var source =
        new BinaryUpload(
            () -> {
              fail("unauthorized source opened");
              return null;
            },
            "1",
            null,
            null,
            f.settings);
    assertEquals(403, assertThrows(GatewayException.class, () -> f.upload(source)).status);
    assertEquals(
        false,
        f.authorization.decision(f.registry.get("nas.objects.upload"), f.grants).get("allowed"));
    verifyNoInteractions(f.quota);
  }

  @ParameterizedTest
  @ValueSource(strings = {"*", "nas.*", "nas.objects.*", "nas.objects.upload"})
  void denyWinsEvenOverExactAllow(String deny) {
    f.grants = List.of(grant("nas.objects.upload", "allow"), grant(deny, "deny"));
    assertEquals(
        403,
        assertThrows(
                GatewayException.class,
                () ->
                    f.upload(
                        new BinaryUpload(
                            () -> {
                              fail("denied source opened");
                              return null;
                            },
                            "1",
                            null,
                            null,
                            f.settings)))
            .status);
  }

  @Test
  void effectiveAndMatrixUseTheSameExactSensitivePolicy() {
    when(f.store.rows("SELECT * FROM gateway_grant"))
        .thenAnswer(
            i ->
                f.grants.stream()
                    .map(
                        g -> {
                          var row = new LinkedHashMap<>(g);
                          row.put("system", "fixture");
                          return row;
                        })
                    .toList());
    when(f.store.rows("SELECT key,display_name FROM gateway_system WHERE enabled=true ORDER BY id"))
        .thenReturn(List.of(JsonUtils.obj("key", "fixture", "display_name", "Fixture")));
    for (String pattern : List.of("nas.*", "nas.objects.upload")) {
      f.grants = List.of(grant(pattern, "allow"));
      boolean expected = pattern.endsWith("upload");
      var effective =
          JsonUtils.list(JsonUtils.map(f.authorization.effective("fixture")).get("operations"))
              .stream()
              .map(JsonUtils::map)
              .filter(o -> o.get("operation").equals("nas.objects.upload"))
              .findFirst()
              .orElseThrow();
      assertEquals(expected, effective.get("allowed"));
      var system =
          JsonUtils.map(
              JsonUtils.list(JsonUtils.map(f.authorization.matrix()).get("systems")).getFirst());
      assertEquals(expected, JsonUtils.list(system.get("allowed")).contains("nas.objects.upload"));
    }
  }

  @Test
  void validatesLengthLimitsAndTransportBeforeOpeningBody() {
    f.mockMode();
    f.env.setProperty("NAS_UPLOAD_MAX_BYTES", "10");
    var calls =
        List.of(
            new Object[] {null, null, "application/octet-stream", 411},
            new Object[] {"1", "chunked", "application/octet-stream", 411},
            new Object[] {"11", null, "application/octet-stream", 413},
            new Object[] {"1", null, "multipart/form-data; boundary=test", 422},
            new Object[] {"1", null, "application/x-www-form-urlencoded", 422});
    for (var item : calls) {
      var source =
          new BinaryUpload(
              () -> {
                fail("invalid source opened");
                return null;
              },
              (String) item[0],
              (String) item[1],
              (String) item[2],
              f.settings);
      assertEquals(item[3], assertThrows(GatewayException.class, () -> f.upload(source)).status);
    }
  }

  @Test
  void jsonBatchAndAdminCannotPretendToUpload() {
    f.mockMode();
    assertEquals(
        422,
        assertThrows(
                GatewayException.class,
                () ->
                    f.executor.execute(
                        "fixture",
                        "nas.objects.upload",
                        Map.of("filename", "file.bin"),
                        "replay",
                        null))
            .status);
    var batch =
        JsonUtils.map(
            f.executor.batch(
                "fixture",
                List.of(
                    JsonUtils.obj(
                        "operation",
                        "nas.objects.upload",
                        "params",
                        Map.of("filename", "file.bin"))),
                false));
    assertEquals(0, JsonUtils.map(batch.get("data")).get("succeeded"));
    assertEquals(
        422,
        assertThrows(
                GatewayException.class,
                () ->
                    f.controller.simulate(
                        JsonUtils.obj(
                            "system",
                            "fixture",
                            "operation",
                            "nas.objects.upload",
                            "params",
                            Map.of("filename", "file.bin"))))
            .status);
    verify(f.store, never()).insert(eq("gateway_idempotency"), anyMap());
  }

  @Test
  void explicitMockConsumesAndChecksBodyOnVirtualThreadWithoutCredentials() {
    f.mockMode();
    AtomicBoolean virtual = new AtomicBoolean();
    var source =
        new BinaryUpload(
            () -> {
              virtual.set(Thread.currentThread().isVirtual());
              return new ByteArrayInputStream(new byte[100000]);
            },
            "100000",
            null,
            null,
            f.settings);
    var result = f.upload(source);
    assertTrue(virtual.get());
    assertEquals(true, JsonUtils.map(result.get("meta")).get("mock"));
    assertEquals(true, JsonUtils.map(result.get("data")).get("_mock"));
    assertEquals(100000L, JsonUtils.map(result.get("data")).get("size_bytes"));
    verify(f.quota).consume("fixture:nas.objects.upload", "60/min");
    verify(f.quota).consume("platform:nas", "600/min");
    assertEquals(
        422,
        assertThrows(
                GatewayException.class,
                () -> f.upload(f.source(new ByteArrayInputStream(new byte[0]), "1")))
            .status);
    f.env.setProperty("ENV", "prod");
    assertEquals(
        503,
        assertThrows(
                GatewayException.class,
                () -> f.upload(f.source(new ByteArrayInputStream(new byte[0]), "0")))
            .status);
  }

  @Test
  void absentCredentialsNeverImplicitlySimulateOrReadBody() {
    f.env.setProperty("MOCK_ALL", "");
    assertFalse(f.dispatcher.mock(f.registry.get("nas.objects.upload")));
    assertEquals(
        503,
        assertThrows(
                GatewayException.class,
                () ->
                    f.upload(
                        new BinaryUpload(
                            () -> {
                              fail("missing credentials read source");
                              return null;
                            },
                            "1",
                            null,
                            null,
                            f.settings)))
            .status);
  }

  @Test
  void existingPresignPutStillReturnsTheDirectUploadContract() {
    f.upstream("https://nas.example.invalid/minio");
    var op = f.registry.get("nas.objects.presign_put");
    var result =
        JsonUtils.map(
            f.nas.invoke(
                op,
                f.registry.validate(op, Map.of("filename", "file.pdf", "folder", "contracts")),
                "fixture"));
    assertEquals("POST", op.httpMethod());
    assertEquals("json", op.transport());
    assertEquals("PUT", result.get("method"));
    assertEquals("cyt-training", result.get("bucket"));
    assertTrue(JsonUtils.str(result, "key").startsWith("contracts/"));
    assertTrue(JsonUtils.str(result, "key").endsWith(".pdf"));
    assertTrue(
        JsonUtils.str(result, "upload_url")
            .startsWith("https://nas.example.invalid/minio/cyt-training/contracts/"));
    assertTrue(JsonUtils.str(result, "upload_url").contains("X-Amz-Signature="));
    assertNotNull(result.get("content_type"));
    assertNotNull(result.get("expires_in"));
  }

  @Test
  void totalTimeoutCancelsSourceWithoutSuccessOrReplay() {
    f.mockMode();
    f.env.setProperty("NAS_UPLOAD_TIMEOUT_S", "0.1");
    var source =
        new BinaryUpload(
            () ->
                new InputStream() {
                  public int read() throws IOException {
                    try {
                      Thread.sleep(5000);
                    } catch (InterruptedException e) {
                      throw new InterruptedIOException();
                    }
                    return -1;
                  }
                },
            "1",
            null,
            null,
            f.settings);
    long start = System.nanoTime();
    assertEquals(504, assertThrows(GatewayException.class, () -> f.upload(source)).status);
    assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2));
    assertFalse(source.completed());
  }

  @Test
  void realLoopbackStreamsSignedPutWithEncodedPathAndScopedCredentials() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    var received = new ConcurrentLinkedQueue<Map<String, String>>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          try {
            long count = exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
            received.add(
                Map.of(
                    "method",
                    exchange.getRequestMethod(),
                    "uri",
                    exchange.getRequestURI().toString(),
                    "auth",
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    "hash",
                    exchange.getRequestHeaders().getFirst("x-amz-content-sha256"),
                    "host",
                    exchange.getRequestHeaders().getFirst("Host"),
                    "size",
                    Long.toString(count)));
            calls.incrementAndGet();
            exchange.getResponseHeaders().set("ETag", "\"fixture-etag\"");
            exchange.sendResponseHeaders(200, -1);
          } finally {
            exchange.close();
          }
        });
    server.start();
    try {
      f.upstream("http://127.0.0.1:" + server.getAddress().getPort() + "/s3");
      f.env.setProperty("NAS_MINIO_FIXTURE_ACCESS_KEY", "caller-a");
      f.env.setProperty("NAS_MINIO_FIXTURE_SECRET_KEY", "secret-a");
      f.env.setProperty("CRED_SCOPE_NAS", "strict");
      var result =
          f.executor.executeUpload(
              "fixture",
              Map.of("filename", "文档.bin", "key", "文件/a b+.bin"),
              f.source(new ByteArrayInputStream(new byte[65539]), "65539"),
              null);
      assertEquals("fixture-etag", JsonUtils.map(result.get("data")).get("etag"));
      assertEquals(false, JsonUtils.map(result.get("meta")).get("mock"));
      f.env.setProperty("NAS_MINIO_OTHER_ACCESS_KEY", "caller-b");
      f.env.setProperty("NAS_MINIO_OTHER_SECRET_KEY", "secret-b");
      when(f.store.one("SELECT enabled FROM gateway_system WHERE key=?", "other"))
          .thenReturn(Map.of("enabled", true));
      when(f.store.rows("SELECT * FROM gateway_grant WHERE system=?", "other"))
          .thenReturn(f.grants);
      f.executor.executeUpload(
          "other",
          Map.of("filename", "empty.bin"),
          f.source(new ByteArrayInputStream(new byte[0]), "0"),
          null);
      assertEquals(2, calls.get());
      var first = received.remove();
      var second = received.remove();
      assertEquals("PUT", first.get("method"));
      assertEquals("65539", first.get("size"));
      assertEquals("UNSIGNED-PAYLOAD", first.get("hash"));
      assertEquals("127.0.0.1:" + server.getAddress().getPort(), first.get("host"));
      assertTrue(first.get("uri").contains("/s3/cyt-training/%E6%96%87%E4%BB%B6/a%20b%2B.bin"));
      assertFalse(first.get("uri").contains("?"));
      assertTrue(first.get("auth").contains("Credential=caller-a/"));
      assertTrue(second.get("auth").contains("Credential=caller-b/"));
      assertTrue(
          first
              .get("auth")
              .contains("content-length;content-type;host;x-amz-content-sha256;x-amz-date"));
      assertEquals("0", second.get("size"));
      verify(f.logs, times(2))
          .record(
              anyString(),
              anyString(),
              eq("nas.objects.upload"),
              eq("nas"),
              anyLong(),
              argThat(
                  v ->
                      !JsonUtils.write(v).contains("caller-a")
                          && !JsonUtils.write(v).contains("InputStream")),
              any(),
              eq(false),
              isNull(),
              isNull());
    } finally {
      server.stop(0);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {301, 403, 500, 503})
  void upstreamFailureNeverRedirectsRetriesOrLeaksS3Response(int status) throws Exception {
    AtomicInteger calls = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          calls.incrementAndGet();
          exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
          exchange.getResponseHeaders().set("Location", "/should-not-follow");
          byte[] body =
              "Credential=fixture-access Signature=secret canonical request"
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      f.upstream("http://127.0.0.1:" + server.getAddress().getPort());
      var error =
          assertThrows(
              GatewayException.class,
              () -> f.upload(f.source(new ByteArrayInputStream(new byte[4]), "4")));
      assertEquals(502, error.status);
      assertFalse(error.retryable);
      assertEquals(1, calls.get());
      String payload = JsonUtils.write(error.payload());
      assertFalse(payload.contains("Credential"));
      assertFalse(payload.contains("Signature"));
      assertFalse(payload.contains("canonical"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void emptyLengthLieCannotStartAnUpstreamPut() {
    f.upstream("http://127.0.0.1:1");
    assertEquals(
        422,
        assertThrows(
                GatewayException.class,
                () -> f.upload(f.source(new ByteArrayInputStream(new byte[1]), "0")))
            .status);
  }

  @Test
  void failedUploadClosesTheRequestSource() throws Exception {
    f.mockMode();
    var closed = new CountDownLatch(1);
    var source =
        f.source(
            new ByteArrayInputStream(new byte[0]) {
              @Override
              public void close() {
                closed.countDown();
              }
            },
            "1");
    assertEquals(422, assertThrows(GatewayException.class, () -> f.upload(source)).status);
    assertTrue(closed.await(1, TimeUnit.SECONDS));
    assertFalse(source.completed());
  }

  @Test
  void upstreamIoTimeoutIs504AndIsNotRetried() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.createContext(
        "/",
        exchange -> {
          calls.incrementAndGet();
          exchange.getRequestBody().transferTo(OutputStream.nullOutputStream());
          try {
            Thread.sleep(500);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          try {
            exchange.sendResponseHeaders(200, -1);
          } catch (IOException ignored) {
          } finally {
            exchange.close();
          }
        });
    server.start();
    try {
      f.upstream("http://127.0.0.1:" + server.getAddress().getPort());
      f.env.setProperty("NAS_UPLOAD_TIMEOUT_S", "0.2");
      var error =
          assertThrows(
              GatewayException.class,
              () -> f.upload(f.source(new ByteArrayInputStream(new byte[1]), "1")));
      assertEquals(504, error.status);
      assertEquals(1, calls.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void missingScopedCredentialsDoNotBorrowAnotherCallersKeys() {
    f.upstream("http://127.0.0.1:1");
    f.env.setProperty("CRED_SCOPE_NAS", "strict");
    f.env.setProperty("NAS_MINIO_OTHER_ACCESS_KEY", "other-access");
    f.env.setProperty("NAS_MINIO_OTHER_SECRET_KEY", "other-secret");
    var source =
        new BinaryUpload(
            () -> {
              fail("credential failure opened body");
              return null;
            },
            "1",
            null,
            null,
            f.settings);
    assertEquals(503, assertThrows(GatewayException.class, () -> f.upload(source)).status);
  }

  @Test
  void publicOpenapiAndConsumerExportDescribePutBinaryAndExactAuthorization() throws Exception {
    var contracts = new DocumentationContracts(f.store);
    var documents = new RegistryController(f.registry, f.store, contracts);
    var consumer =
        new ConsumerCapabilitiesController(
            f.store, f.registry, documents, f.authorization, contracts);
    when(f.store.one("SELECT * FROM gateway_system WHERE key=?", "fixture"))
        .thenReturn(JsonUtils.obj("enabled", true, "display_name", "Fixture"));
    var spec =
        documents.openapiFor(
            List.of(
                f.registry.get("nas.objects.upload"), f.registry.get("nas.objects.presign_put")));
    var path = JsonUtils.map(JsonUtils.map(spec.get("paths")).get(PATH));
    assertFalse(path.containsKey("post"));
    var put = JsonUtils.map(path.get("put"));
    assertEquals("raw_binary", put.get("x-cyt-transport"));
    assertTrue(JsonUtils.write(put.get("requestBody")).contains("binary"));
    assertFalse(JsonUtils.write(put.get("requestBody")).contains("application/json"));
    assertTrue(JsonUtils.write(put.get("parameters")).contains("Content-Length"));
    var old =
        JsonUtils.map(
            JsonUtils.map(spec.get("paths")).get("/integrations/nas/objects/presign-put"));
    assertTrue(old.containsKey("post"));
    assertEquals(
        "PUT", contracts.describe(f.registry.get("nas.objects.upload")).get("http_method"));
    assertTrue(documents.html().getBody().contains("PUT " + PATH));
    f.grants = List.of(grant("nas.*", "allow"));
    assertFalse(
        JsonUtils.map(JsonUtils.map(consumer.export("fixture", "all").getBody()).get("paths"))
            .containsKey(PATH));
    f.grants = List.of(grant("nas.objects.upload", "allow"));
    var exported =
        JsonUtils.map(JsonUtils.map(consumer.export("fixture", "all").getBody()).get("paths"));
    assertTrue(JsonUtils.map(exported.get(PATH)).containsKey("put"));
    assertFalse(JsonUtils.map(exported.get(PATH)).containsKey("post"));
    assertNotNull(
        new DocumentationContracts(f.store).response(f.registry.get("nas.objects.upload")));
    var entries = new HashMap<String, String>();
    try (var zip =
        new java.util.zip.ZipInputStream(
            new ByteArrayInputStream(consumer.integrationPackage("fixture", "all").getBody()),
            StandardCharsets.UTF_8)) {
      java.util.zip.ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null)
        entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
    }
    var packagePaths = JsonUtils.map(JsonUtils.read(entries.get("openapi.json")).get("paths"));
    assertTrue(JsonUtils.map(packagePaths.get(PATH)).containsKey("put"));
    assertFalse(JsonUtils.map(packagePaths.get(PATH)).containsKey("post"));
    assertTrue(entries.get("START_HERE.html").contains("raw_binary"));
    assertTrue(entries.get("sdk/python/cyt_gateway.py").contains("def upload_file("));
    assertTrue(entries.get("sdk/typescript/index.ts").contains("async uploadFile("));
  }

  @Configuration
  @EnableWebMvc
  static class WebConfig {
    @Bean
    Fixture fixture() throws Exception {
      return new Fixture();
    }

    @Bean
    IntegrationController integrationController(Fixture f) {
      return f.controller;
    }

    @Bean
    GlobalExceptionHandler handler() {
      return new GlobalExceptionHandler();
    }

    @Bean(name = "multipartResolver")
    StandardServletMultipartResolver multipartResolver() {
      return new StandardServletMultipartResolver();
    }
  }

  @Configuration
  @Import(WebConfig.class)
  static class LiveConfig {
    @Bean
    org.springframework.boot.web.servlet.server.ServletWebServerFactory serverFactory() {
      var factory =
          new org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory(0);
      factory.setAddress(InetAddress.getLoopbackAddress());
      return factory;
    }

    @Bean
    org.springframework.boot.web.servlet.ServletRegistrationBean<
            org.springframework.web.servlet.DispatcherServlet>
        servlet(org.springframework.web.context.WebApplicationContext context) {
      var registration =
          new org.springframework.boot.web.servlet.ServletRegistrationBean<>(
              new org.springframework.web.servlet.DispatcherServlet(context), "/");
      registration.setLoadOnStartup(1);
      registration.setMultipartConfig(new jakarta.servlet.MultipartConfigElement(""));
      return registration;
    }

    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<BinaryUploadFilter> rawFilter() {
      var registration =
          new org.springframework.boot.web.servlet.FilterRegistrationBean<>(
              new BinaryUploadFilter());
      registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 1);
      return registration;
    }

    @Bean
    org.springframework.boot.web.servlet.FilterRegistrationBean<FormContentFilter> formFilter() {
      return new org.springframework.boot.web.servlet.FilterRegistrationBean<>(
          new FormContentFilter());
    }
  }

  @Test
  void realHttpRequestTraversesServletAuthVirtualThreadAndSignedS3Upload() throws Exception {
    var received = new AtomicReference<byte[]>();
    AtomicInteger calls = new AtomicInteger();
    var s3 = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    s3.createContext(
        "/",
        exchange -> {
          received.set(exchange.getRequestBody().readAllBytes());
          calls.incrementAndGet();
          exchange.getResponseHeaders().set("ETag", "\"e2e-etag\"");
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    s3.start();
    try (var context =
        new org.springframework.boot.web.servlet.context
            .AnnotationConfigServletWebServerApplicationContext(LiveConfig.class)) {
      var fixture = context.getBean(Fixture.class);
      fixture.upstream("http://127.0.0.1:" + s3.getAddress().getPort());
      byte[] bytes = new byte[196625];
      new Random(42).nextBytes(bytes);
      try (var client = java.net.http.HttpClient.newHttpClient()) {
        String base = "http://localhost:" + context.getWebServer().getPort() + PATH;
        var request =
            java.net.http.HttpRequest.newBuilder(
                    URI.create(base + "?filename=file.bin&key=uploads/e2e.bin"))
                .header("Authorization", "Bearer " + TOKEN)
                .header("Content-Type", "application/octet-stream")
                .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        assertArrayEquals(bytes, received.get());
        var envelope = JsonUtils.read(response.body());
        assertEquals(true, envelope.get("ok"));
        assertEquals("e2e-etag", JsonUtils.map(envelope.get("data")).get("etag"));
        assertEquals(false, JsonUtils.map(envelope.get("meta")).get("mock"));
        var empty =
            java.net.http.HttpRequest.newBuilder(URI.create(base + "?filename=empty.bin"))
                .header("Authorization", "Bearer " + TOKEN)
                .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(new byte[0]))
                .build();
        assertEquals(
            200,
            client.send(empty, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
        var chunked =
            java.net.http.HttpRequest.newBuilder(URI.create(base + "?filename=file.bin"))
                .header("Authorization", "Bearer " + TOKEN)
                .PUT(
                    java.net.http.HttpRequest.BodyPublishers.ofInputStream(
                        () -> new ByteArrayInputStream(new byte[1])))
                .build();
        assertEquals(
            411,
            client.send(chunked, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
        for (String type :
            List.of("multipart/form-data; boundary=fixture", "application/x-www-form-urlencoded")) {
          var invalid =
              java.net.http.HttpRequest.newBuilder(URI.create(base + "?filename=file.bin"))
                  .header("Authorization", "Bearer " + TOKEN)
                  .header("Content-Type", type)
                  .PUT(
                      java.net.http.HttpRequest.BodyPublishers.ofString("not a supported raw file"))
                  .build();
          assertEquals(
              422,
              client
                  .send(invalid, java.net.http.HttpResponse.BodyHandlers.ofString())
                  .statusCode());
        }
        clearInvocations(fixture.logs);
        try (var socket = new Socket("localhost", context.getWebServer().getPort())) {
          socket.setSoTimeout(3000);
          String headers =
              "PUT "
                  + PATH
                  + "?filename=file.bin HTTP/1.1\r\nHost: localhost\r\nAuthorization: Bearer "
                  + TOKEN
                  + "\r\nContent-Length: 10\r\nContent-Type: application/octet-stream\r\nConnection: close\r\n\r\nabc";
          socket.getOutputStream().write(headers.getBytes(StandardCharsets.UTF_8));
          socket.getOutputStream().flush();
          socket.shutdownOutput();
          String failed =
              new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
          // Tomcat may take over a malformed HTTP frame before MVC can render its 422.
          assertTrue(
              failed.startsWith("HTTP/1.1 422") || failed.startsWith("HTTP/1.1 400"), failed);
        }
        assertEquals(2, calls.get());
        verify(fixture.logs)
            .record(
                anyString(),
                eq("fixture"),
                eq("nas.objects.upload"),
                eq("nas"),
                anyLong(),
                any(),
                isNull(),
                eq(false),
                isNull(),
                argThat(error -> error.status == 422));
      }
    } finally {
      s3.stop(0);
    }
  }

  static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments>
      uploadRoutesAndTypes() {
    return List.of(
            "application/octet-stream",
            "multipart/form-data; boundary=x",
            "application/x-www-form-urlencoded")
        .stream()
        .flatMap(
            type ->
                List.of(PATH, "/integrations/nas/objects/u%70load", PATH + ";x=1", "/ctx" + PATH)
                    .stream()
                    .map(path -> org.junit.jupiter.params.provider.Arguments.of(type, path)));
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.MethodSource("uploadRoutesAndTypes")
  void realMvcFormFilterAndMultipartResolverDoNotReadUnauthorizedOrInvalidBodies(
      String type, String path) throws Exception {
    try (var context = new AnnotationConfigWebApplicationContext()) {
      context.setServletContext(new MockServletContext());
      context.register(WebConfig.class);
      context.refresh();
      var fixture = context.getBean(Fixture.class);
      fixture.mockMode();
      var mvc =
          MockMvcBuilders.webAppContextSetup(context)
              .addFilters(new BinaryUploadFilter(), new FormContentFilter())
              .build();
      for (int expected : List.of(401, 403, type.startsWith("application/octet") ? 411 : 422)) {
        fixture.grants =
            expected == 403
                ? List.of(grant("nas.*", "allow"))
                : List.of(grant("nas.objects.upload", "allow"));
        mvc.perform(
                servletContext -> {
                  var request =
                      new MockHttpServletRequest(servletContext) {
                        @Override
                        public ServletInputStream getInputStream() {
                          throw new AssertionError("Spring read the body before rejection");
                        }

                        @Override
                        public Collection<jakarta.servlet.http.Part> getParts() {
                          throw new AssertionError("Spring parsed multipart before rejection");
                        }

                        @Override
                        public String getParameter(String name) {
                          throw new AssertionError("must parse only raw query");
                        }
                      };
                  request.setMethod("PUT");
                  request.setRequestURI(path);
                  if (path.startsWith("/ctx/")) request.setContextPath("/ctx");
                  request.setQueryString("filename=file.bin");
                  request.setContentType(type);
                  if (expected != 401) request.addHeader("Authorization", "Bearer " + TOKEN);
                  if (expected != 411) request.addHeader("Content-Length", "1");
                  return request;
                })
            .andExpect(status().is(expected));
      }
    }
  }
}
