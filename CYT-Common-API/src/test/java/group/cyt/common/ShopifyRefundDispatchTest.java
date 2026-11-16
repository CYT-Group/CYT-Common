package group.cyt.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.adapter.AdapterDispatcher;
import group.cyt.common.module.gateway.service.CallLogService;
import group.cyt.common.module.gateway.service.GatewayExecutor;
import group.cyt.common.module.grant.service.AuthorizationService;
import group.cyt.common.module.grant.service.QuotaService;
import group.cyt.common.module.registry.service.OperationRegistry;
import java.util.*;
import java.util.concurrent.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** A real isolated JDBC journal, with mocked upstreams: never issues financial writes. */
class ShopifyRefundDispatchTest {
  JdbcTemplate jdbc;
  GatewayStore store;
  OperationRegistry registry;
  AdapterDispatcher adapters;
  AuthorizationService authorization;
  GatewayExecutor gateway;

  @BeforeEach
  void setup() throws Exception {
    var ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:refund_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
    jdbc = new JdbcTemplate(ds);
    jdbc.execute("CREATE TABLE gateway_system (`key` VARCHAR(32) PRIMARY KEY,enabled BOOLEAN)");
    jdbc.execute("INSERT INTO gateway_system VALUES('cs',true)");
    jdbc.execute(
        "CREATE TABLE gateway_idempotency(id BIGINT AUTO_INCREMENT PRIMARY KEY,`system` VARCHAR(32),`key` VARCHAR(128),operation VARCHAR(128),response_json LONGTEXT,params_hash VARCHAR(64),state VARCHAR(16),UNIQUE(`system`,`key`))");
    store = new GatewayStore(jdbc, new DataSourceTransactionManager(ds));
    registry = new OperationRegistry(store);
    adapters = mock(AdapterDispatcher.class);
    authorization = mock(AuthorizationService.class);
    when(authorization.check(eq("cs"), any())).thenReturn("30/min");
    when(adapters.invoke(any(), anyMap(), eq("cs")))
        .thenReturn(
            JsonUtils.obj(
                "mock",
                false,
                "storeCreditAccountTransaction",
                Map.of("id", "gid://shopify/StoreCreditAccountCreditTransaction/9")));
    gateway = newGateway();
  }

  GatewayExecutor newGateway() {
    return new GatewayExecutor(
        registry,
        authorization,
        mock(QuotaService.class),
        adapters,
        store,
        mock(CallLogService.class));
  }

  @AfterEach
  void close() {
    gateway.close();
    jdbc.execute("DROP ALL OBJECTS");
  }

  Map<String, Object> params() {
    return JsonUtils.obj(
        "store",
        "fixture.myshopify.com",
        "order_id",
        "gid://shopify/Order/1",
        "customer_id",
        "gid://shopify/Customer/2",
        "currency",
        "EUR",
        "amount",
        "20.00",
        "confirm",
        true);
  }

  Map<String, Object> call(String key, Map<String, Object> p) {
    return gateway.execute("cs", "shopify.store_credit.credit", p, key, null);
  }

  @Test
  void successfulCreditReplaysAcrossExecutorRestartWithoutIssuingAgain() {
    var first = call("fixture-bonus", params());
    gateway.close();
    gateway = newGateway();
    var second = call("fixture-bonus", params());
    assertEquals(JsonUtils.write(first.get("data")), JsonUtils.write(second.get("data")));
    assertEquals(true, JsonUtils.at(second, "meta.idempotent_replay"));
    verify(adapters, times(1)).invoke(any(), anyMap(), eq("cs"));
    assertEquals("complete", store.one("SELECT state FROM gateway_idempotency").get("state"));
  }

  @Test
  void unknownCreditOutcomeStaysBlockedAfterExecutorRestart() {
    when(adapters.invoke(any(), anyMap(), eq("cs")))
        .thenThrow(new GatewayException(504, "upstream_timeout", "lost response", false));
    assertEquals(
        504, assertThrows(GatewayException.class, () -> call("fixture-lost", params())).status);
    assertEquals("uncertain", store.one("SELECT state FROM gateway_idempotency").get("state"));
    gateway.close();
    gateway = newGateway();
    assertEquals(
        409, assertThrows(GatewayException.class, () -> call("fixture-lost", params())).status);
    verify(adapters, times(1)).invoke(any(), anyMap(), eq("cs"));
  }

  @Test
  void missingKeyAndChangedAmountCannotDispatchASecondCredit() {
    assertEquals(422, assertThrows(GatewayException.class, () -> call(null, params())).status);
    verify(adapters, never()).invoke(any(), anyMap(), anyString());
    call("fixture-same", params());
    var changed = params();
    changed.put("amount", "21.00");
    assertEquals(
        409, assertThrows(GatewayException.class, () -> call("fixture-same", changed)).status);
    verify(adapters, times(1)).invoke(any(), anyMap(), eq("cs"));
  }

  @Test
  void aConcurrentRequestSeesTheCommittedClaimBeforeUpstreamCompletes() throws Exception {
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(adapters.invoke(any(), anyMap(), eq("cs")))
        .thenAnswer(
            call -> {
              started.countDown();
              assertTrue(release.await(5, TimeUnit.SECONDS));
              return JsonUtils.obj(
                  "mock",
                  false,
                  "storeCreditAccountTransaction",
                  Map.of("id", "gid://shopify/StoreCreditAccountCreditTransaction/9"));
            });
    try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = threads.submit(() -> call("fixture-concurrent", params()));
      try {
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertEquals(
            409,
            assertThrows(GatewayException.class, () -> call("fixture-concurrent", params()))
                .status);
      } finally {
        release.countDown();
      }
      assertEquals(true, first.get(5, TimeUnit.SECONDS).get("ok"));
    }
    verify(adapters, times(1)).invoke(any(), anyMap(), eq("cs"));
  }

  @Test
  void rejectedPermissionCannotReserveOrDispatchCredit() {
    when(authorization.check(eq("cs"), any()))
        .thenThrow(GatewayException.forbidden("precise financial grant required"));
    assertEquals(
        403, assertThrows(GatewayException.class, () -> call("fixture-denied", params())).status);
    assertEquals(0, store.count("SELECT COUNT(*) FROM gateway_idempotency"));
    verify(adapters, never()).invoke(any(), anyMap(), eq("cs"));
  }
}
