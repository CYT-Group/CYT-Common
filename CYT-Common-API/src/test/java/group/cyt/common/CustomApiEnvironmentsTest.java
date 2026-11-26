package group.cyt.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import group.cyt.common.core.config.GatewaySettings;
import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.http.NetworkPolicy;
import group.cyt.common.core.http.UpstreamHttp;
import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.credential.service.CredentialService;
import group.cyt.common.module.custom.adapter.CustomHttpAdapter;
import group.cyt.common.module.custom.service.ApiPublicationService;
import group.cyt.common.module.custom.service.ConfigurationService;
import group.cyt.common.module.custom.service.ConfigurationService.Kind;
import group.cyt.common.module.grant.service.AuthorizationService;
import group.cyt.common.module.registry.model.OperationDefinition;
import group.cyt.common.module.registry.service.OperationRegistry;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class CustomApiEnvironmentsTest {
  private final GatewayStore store = mock(GatewayStore.class);
  private final OperationRegistry registry = mock(OperationRegistry.class);
  private final NetworkPolicy network = mock(NetworkPolicy.class);
  private final CredentialService credentials = mock(CredentialService.class);

  private static final String TEST_ADDRESS = "https://test.example.org";
  private static final String PRODUCTION_ADDRESS = "https://prod.example.org";
  private static final String LEGACY_ADDRESS = "https://legacy.example.org";
  private static final String OVERRIDE_ADDRESS = "https://override.example.org";
  private static final String KEY = "cs.buyer.push";

  private final Map<String, Object> module =
      JsonUtils.obj(
          "key",
          "cs",
          "service_kind",
          "internal",
          "enabled",
          true,
          "base_url",
          LEGACY_ADDRESS,
          "test_base_url",
          TEST_ADDRESS,
          "production_base_url",
          PRODUCTION_ADDRESS);
  private final Map<String, Object> api =
      JsonUtils.obj(
          "key",
          KEY,
          "module_key",
          "cs",
          "enabled",
          true,
          "base_url",
          OVERRIDE_ADDRESS,
          "method",
          "POST",
          "path",
          "/push");

  private GatewaySettings settings(String env) {
    return new GatewaySettings(new MockEnvironment().withProperty("ENV", env));
  }

  @Test
  void environmentAddressesTakePrecedenceOverLegacyAndApiOverrides() {
    assertEquals(
        TEST_ADDRESS, ConfigurationService.customHttpConfig(api, module, false).get("base_url"));
    assertEquals(
        PRODUCTION_ADDRESS,
        ConfigurationService.customHttpConfig(api, module, true).get("base_url"));
    module.put("production_base_url", null);
    assertEquals("", ConfigurationService.customHttpConfig(api, module, true).get("base_url"));
    module.put("test_base_url", null);
    assertEquals(
        OVERRIDE_ADDRESS, ConfigurationService.customHttpConfig(api, module, true).get("base_url"));
    module.put("service_kind", "third_party");
    module.put("test_base_url", TEST_ADDRESS);
    assertEquals(
        OVERRIDE_ADDRESS,
        ConfigurationService.customHttpConfig(api, module, false).get("base_url"));
  }

  @Test
  void missingProductionAddressBlocksPublicationAndDispatchWhileAllowingDraftEdits() {
    module.put("production_base_url", "");
    GatewaySettings production = settings("production");
    when(store.one("SELECT * FROM gateway_module WHERE key=?", "cs")).thenReturn(module);
    var configuration = new ConfigurationService(store, registry, network, production);
    assertDoesNotThrow(() -> configuration.save(Kind.API, null, api, "fixture"));

    var operation = mock(OperationDefinition.class);
    when(operation.key()).thenReturn(KEY);
    when(registry.custom(api)).thenReturn(operation);
    when(store.one("SELECT * FROM gateway_custom_api WHERE id=?", 123L)).thenReturn(api);
    when(store.one("SELECT * FROM gateway_module WHERE key=? AND service_kind='internal'", "cs"))
        .thenReturn(module);
    var authorization = mock(AuthorizationService.class);
    var publication =
        new ApiPublicationService(store, registry, authorization, credentials, production);
    var check = publication.preflight(123L);
    assertEquals("production", check.get("upstream_environment"));
    assertEquals("", check.get("upstream_base_url"));
    assertEquals(true, check.get("environment_specific"));
    assertEquals(false, check.get("upstream_address_configured"));
    assertEquals(false, check.get("can_publish"));
    assertTrue(JsonUtils.write(check.get("blocking_reasons")).contains("upstream_address_missing"));
    var testCheck =
        new ApiPublicationService(store, registry, authorization, credentials, settings("local"))
            .preflight(123L);
    assertEquals("test", testCheck.get("upstream_environment"));
    assertEquals(TEST_ADDRESS, testCheck.get("upstream_base_url"));

    var upstream = mock(UpstreamHttp.class);
    when(store.one("SELECT * FROM gateway_custom_api WHERE key=? AND enabled=true", KEY))
        .thenReturn(api);
    when(store.one("SELECT * FROM gateway_module WHERE key=? AND enabled=true", "cs"))
        .thenReturn(module);
    var adapter = new CustomHttpAdapter(store, credentials, upstream, network, production);
    var failure =
        assertThrows(GatewayException.class, () -> adapter.invoke(operation, Map.of(), "buyer"));
    assertEquals(503, failure.status);
    verifyNoInteractions(upstream, credentials, network);
  }
}
