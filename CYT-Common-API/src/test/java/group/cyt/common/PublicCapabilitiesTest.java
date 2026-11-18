package group.cyt.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.registry.service.OperationRegistry;
import java.util.*;
import org.junit.jupiter.api.Test;

/** The same checked-in snapshot is asserted by the independent Python runtime. */
class PublicCapabilitiesTest {
  @Test
  void executablePublicCatalogMatchesTheSharedSnapshot() throws Exception {
    var registry = new OperationRegistry(mock(GatewayStore.class));
    JsonNode expected;
    try (var input = getClass().getResourceAsStream("/public-capabilities.json")) {
      expected = JsonUtils.MAPPER.readTree(input);
    }
    Map<String, JsonNode> actual = new TreeMap<>();
    for (var row : registry.contract().path("operations")) {
      String key = row.path("key").asText();
      assertNull(actual.put(key, row), "duplicate operation");
    }
    removeJavaOnlyOperations(actual);
    assertEquals(expected.size(), actual.size());
    for (var item : expected) {
      String key = item.path("key").asText();
      var row = actual.remove(key);
      assertNotNull(row, key);
      for (String field :
          List.of(
              "key",
              "platform",
              "quota",
              "sensitive",
              "idempotent",
              "retry_max",
              "deprecated",
              "tags",
              "path")) assertEquals(item.path(field), row.path(field), key + ":" + field);
      assertEquals(item.path("timeout_s").asDouble(), row.path("timeout_s").asDouble(), key);
      String schema =
          JsonUtils.write(
              JsonUtils.sorted(
                  JsonUtils.MAPPER.convertValue(
                      normalize(row.path("schema"), row.path("schema")), Object.class)));
      assertEquals(item.path("schema_sha256").asText(), JsonUtils.sha(schema), key + " schema");
    }
    assertTrue(actual.isEmpty());
  }

  private static void removeJavaOnlyOperations(Map<String, JsonNode> actual) {
    for (String key : List.of(
        "shopify.orders.cancel",
        "stripe.disputes.closures",
        "stripe.disputes.closure_history",
        "stripe.disputes.order_reference",
        "paypal.disputes.closures",
        "paypal.disputes.closure_history",
        "nas.objects.upload")) {
      assertNotNull(actual.remove(key), key);
    }
  }

  static JsonNode normalize(JsonNode node, JsonNode root) {
    if (node.isArray()) {
      var result = JsonUtils.MAPPER.createArrayNode();
      node.forEach(item -> result.add(normalize(item, root)));
      return result;
    }
    if (!node.isObject()) return node;
    if (node.has("$ref")) return normalize(root.at(node.path("$ref").asText().substring(1)), root);
    ObjectNode result = JsonUtils.MAPPER.createObjectNode();
    node.fields()
        .forEachRemaining(
            e -> {
              if (!Set.of("title", "description", "examples", "$defs").contains(e.getKey())
                  && !(e.getKey().equals("default") && e.getValue().isNull())) {
                if (Set.of("properties", "patternProperties").contains(e.getKey())) {
                  var properties = JsonUtils.MAPPER.createObjectNode();
                  e.getValue()
                      .fields()
                      .forEachRemaining(
                          p -> properties.set(p.getKey(), normalize(p.getValue(), root)));
                  result.set(e.getKey(), properties);
                } else result.set(e.getKey(), normalize(e.getValue(), root));
              }
            });
    if (result.path("type").isArray()) {
      var types = result.remove("type");
      var branches = JsonUtils.MAPPER.createArrayNode();
      for (var kind : types) {
        ObjectNode branch =
            kind.asText().equals("null") ? JsonUtils.MAPPER.createObjectNode() : result.deepCopy();
        branch.remove("default");
        branch.put("type", kind.asText());
        branches.add(branch);
      }
      var defaultValue = result.get("default");
      result.removeAll();
      result.set("anyOf", branches);
      if (defaultValue != null) result.set("default", defaultValue);
    }
    if (result.path("type").asText().equals("integer")) {
      if (result.has("exclusiveMinimum"))
        result.put("minimum", (long) Math.floor(result.remove("exclusiveMinimum").asDouble()) + 1);
      if (result.has("exclusiveMaximum"))
        result.put("maximum", (long) Math.ceil(result.remove("exclusiveMaximum").asDouble()) - 1);
    }
    if (result.has("required")) {
      List<String> names = new ArrayList<>();
      result.path("required").forEach(n -> names.add(n.asText()));
      Collections.sort(names);
      if (names.isEmpty()) result.remove("required");
      else result.set("required", JsonUtils.tree(names));
    }
    if (result.path("anyOf").isArray()) {
      List<JsonNode> branches = new ArrayList<>();
      result.path("anyOf").forEach(branches::add);
      branches.sort(
          Comparator.comparing(
              n ->
                  JsonUtils.write(
                      JsonUtils.sorted(JsonUtils.MAPPER.convertValue(n, Object.class)))));
      result.set("anyOf", JsonUtils.tree(branches));
    }
    return result;
  }
}
