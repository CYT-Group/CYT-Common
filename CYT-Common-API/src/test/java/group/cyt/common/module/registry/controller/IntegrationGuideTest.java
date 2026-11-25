package group.cyt.common.module.registry.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import group.cyt.common.core.config.GatewaySettings;
import group.cyt.common.core.exception.GlobalExceptionHandler;
import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.auth.service.AuthenticationService;
import group.cyt.common.module.registry.service.DocumentationContracts;
import group.cyt.common.module.registry.service.OperationRegistry;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class IntegrationGuideTest {
  @Test
  void embeddedCatalogCannotCloseTheDataScriptOrInjectMarkup() throws Exception {
    String summary = "</script><script>alert('catalog')</script> & 模板";
    var spec = Map.of("info", Map.of("title", summary), "paths", Map.of());
    String html = IntegrationGuide.render(spec);
    assertFalse(html.contains(summary));
    assertFalse(html.contains("__CYT_OPENAPI__"));
    assertEquals(summary, JsonUtils.MAPPER.readTree(embeddedSpec(html)).path("info").path("title").asText());
    assertTrue(html.contains("textContent"));
    assertFalse(html.contains("innerHTML"));
  }

  @Test
  void redirectsToTheWorkspaceAndRequiresAnAdminSessionForDownloads() throws Exception {
    var store = mock(GatewayStore.class);
    var registry = new OperationRegistry(store);
    var exporter =
        new RegistryController(registry, store, new DocumentationContracts(store, registry));
    var authentication = new AuthenticationService(new GatewaySettings(new MockEnvironment()), store);
    var authorization = "Bearer " + authentication.session("docs-test").get("token");
    var mvc =
        MockMvcBuilders.standaloneSetup(new DocumentationController(authentication, registry, exporter))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    for (String path : new String[] {"/api-reference", "/api-reference/"}) {
      mvc.perform(get(path))
          .andExpect(status().isFound())
          .andExpect(redirectedUrl("/#/docs"))
          .andExpect(content().string(""));
    }
    for (String path : new String[] {"/api-reference/openapi.json", "/api-reference/download"}) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
      mvc.perform(get(path).header("Authorization", "Bearer cyt_invalid_business_api_key"))
          .andExpect(status().isUnauthorized());
    }

    var openapi =
        mvc.perform(get("/api-reference/openapi.json").header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    var spec = JsonUtils.MAPPER.readTree(openapi);
    assertEquals(registry.builtinOperations().size(), spec.path("paths").size());
    assertTrue(spec.path("paths").path("/integrations/nas/objects/upload").has("put"));
    assertTrue(spec.path("paths").path("/integrations/nas/folders/create-batch").has("post"));

    byte[] archive =
        mvc.perform(get("/api-reference/download").header("Authorization", authorization))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/zip"))
            .andExpect(
                header().string(
                    "Content-Disposition", "attachment; filename=\"cyt-common-integration.zip\""))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    var files = new HashMap<String, String>();
    try (var zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
      for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry())
        files.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
    }
    assertEquals(
        Set.of("README.md", "START_HERE.html", "openapi.json", "sdk/python/cyt_gateway.py", "sdk/typescript/index.ts"),
        files.keySet());
    assertEquals(spec, JsonUtils.MAPPER.readTree(files.get("openapi.json")));
    assertEquals(spec, JsonUtils.MAPPER.readTree(embeddedSpec(files.get("START_HERE.html"))));
    assertTrue(files.get("START_HERE.html").contains("href=\"openapi.json\""));
    verifyNoInteractions(store);
  }

  private static String embeddedSpec(String html) {
    String script = "<script id=\"openapi-data\" type=\"application/json\">";
    int start = html.indexOf(script) + script.length();
    return html.substring(start, html.indexOf("</script>", start));
  }
}
