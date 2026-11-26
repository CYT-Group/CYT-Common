package group.cyt.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import group.cyt.common.core.config.GatewaySettings;
import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.module.adapter.AdapterDispatcher;
import group.cyt.common.module.adapter.support.TestWritePolicy;
import group.cyt.common.module.auth.service.AuthenticationService;
import group.cyt.common.module.gateway.service.CallLogService;
import group.cyt.common.module.gateway.service.GatewayExecutor;
import group.cyt.common.module.grant.service.AuthorizationService;
import group.cyt.common.module.grant.service.QuotaService;
import group.cyt.common.module.registry.model.OperationDefinition;
import group.cyt.common.module.registry.service.OperationRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RmaCallerAuthenticationTest {
  @Test
  void rmaReadsRejectDevIdentityBeforeAnyUpstreamDispatch() {
    var settings = mock(GatewaySettings.class);
    when(settings.dev()).thenReturn(true);
    var store = mock(GatewayStore.class);
    when(store.one("SELECT enabled FROM gateway_system WHERE key=?", "cs"))
        .thenReturn(Map.of("enabled", true));
    var authentication = new AuthenticationService(settings, store);
    var request = new MockHttpServletRequest();
    request.addHeader("X-System-Id", "cs");
    var registry = mock(OperationRegistry.class);
    var adapters = mock(AdapterDispatcher.class);
    var executor = new GatewayExecutor(registry, mock(AuthorizationService.class),
        mock(QuotaService.class), adapters, store, mock(CallLogService.class), mock(TestWritePolicy.class));
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    try {
      String system = authentication.system(request);
      for (String operation : List.of("cs.rma.list", "tiktok.returns.list")) {
        var definition = mock(OperationDefinition.class);
        when(definition.key()).thenReturn(operation);
        when(registry.get(operation)).thenReturn(definition);
        var rejection = assertThrows(GatewayException.class,
            () -> executor.execute(system, operation, Map.of(), null, null));
        assertEquals(401, rejection.status);
      }
      verifyNoInteractions(adapters);
    } finally {
      RequestContextHolder.resetRequestAttributes();
      executor.close();
    }
  }
}
