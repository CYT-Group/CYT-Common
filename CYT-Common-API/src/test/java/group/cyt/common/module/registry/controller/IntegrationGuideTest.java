package group.cyt.common.module.registry.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.registry.service.DocumentationContracts;
import group.cyt.common.module.registry.service.OperationRegistry;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
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
  void servesMatchingPublicDocumentationAndZipWithoutReadingPrivateConfiguration() throws Exception {
    var store = mock(GatewayStore.class);
    var registry = new OperationRegistry(store);
    var exporter =
        new RegistryController(registry, store, new DocumentationContracts(store, registry));
    var mvc =
        MockMvcBuilders.standaloneSetup(new PublicDocumentationController(registry, exporter))
            .build();

    String guide =
        mvc.perform(get("/api-reference"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("text/html;charset=UTF-8"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertTrue(guide.contains("href=\"/api-reference/download\""));
    assertTrue(guide.contains("data-enabled=\"true\""));

    var openapi =
        mvc.perform(get("/api-reference/openapi.json"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    var spec = JsonUtils.MAPPER.readTree(openapi);
    assertEquals(spec, JsonUtils.MAPPER.readTree(embeddedSpec(guide)));
    assertEquals(registry.builtinOperations().size(), spec.path("paths").size());
    assertTrue(spec.path("paths").path("/integrations/nas/objects/upload").has("put"));

    byte[] archive =
        mvc.perform(get("/api-reference/download"))
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
    assertTrue(files.get("START_HERE.html").contains("data-enabled=\"false\""));
    verifyNoInteractions(store);
  }

  private static String embeddedSpec(String html) {
    String script = "<script id=\"openapi-data\" type=\"application/json\">";
    int start = html.indexOf(script) + script.length();
    return html.substring(start, html.indexOf("</script>", start));
  }
}
