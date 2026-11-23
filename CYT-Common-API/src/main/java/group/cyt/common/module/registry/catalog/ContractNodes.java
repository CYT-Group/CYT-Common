package group.cyt.common.module.registry.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import group.cyt.common.core.util.JsonUtils;

/** Java declarations for JSON Schema and documentation; no resource parsing or embedded JSON. */
public final class ContractNodes {
  private ContractNodes() {}

  public static ObjectNode object(Object... fields) {
    if (fields.length % 2 != 0) throw new IllegalArgumentException("Expected name/value pairs");
    ObjectNode result = JsonUtils.MAPPER.createObjectNode();
    for (int i = 0; i < fields.length; i += 2) {
      String name = (String) fields[i];
      if (result.has(name)) throw new IllegalArgumentException("Duplicate schema field: " + name);
      result.set(name, node(fields[i + 1]));
    }
    return result;
  }

  public static ArrayNode array(Object... values) {
    ArrayNode result = JsonUtils.MAPPER.createArrayNode();
    for (Object value : values) result.add(node(value));
    return result;
  }

  private static JsonNode node(Object value) {
    return value instanceof JsonNode json ? json.deepCopy() : JsonUtils.tree(value);
  }
}
