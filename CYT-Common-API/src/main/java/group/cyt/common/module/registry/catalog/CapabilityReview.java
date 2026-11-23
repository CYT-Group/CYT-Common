package group.cyt.common.module.registry.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.node.ObjectNode;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.registry.model.OperationDefinition;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CapabilityReview(
    @JsonInclude(JsonInclude.Include.NON_NULL) String source,
    String current,
    String proposal,
    String caveat,
    String assessment,
    @JsonProperty("recommended_operations") List<String> recommendedOperations,
    Depth depth,
    @JsonProperty("depth_reason") String depthReason,
    @JsonProperty("remaining_gap") String remainingGap,
    List<Evidence> evidence) {
  public CapabilityReview {
    Objects.requireNonNull(depth, "Capability depth is required");
    recommendedOperations = List.copyOf(recommendedOperations);
    evidence = List.copyOf(evidence);
    if (current.isBlank()
        || proposal.isBlank()
        || depthReason.isBlank()
        || remainingGap.isBlank()
        || evidence.isEmpty())
      throw new IllegalArgumentException(
          "Capability review must include behavior, limits and evidence");
  }

  public enum Depth {
    DEEP,
    ENHANCED,
    BASIC,
    SUPPORTING,
    TEST_ONLY;

    @JsonValue
    public String value() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  public record Evidence(String path, int line, String anchor) {
    public Evidence {
      if (!path.startsWith("src/main/java/") || line < 1 || anchor.isBlank())
        throw new IllegalArgumentException("Invalid capability evidence");
    }
  }

  ObjectNode describe(OperationDefinition operation) {
    ObjectNode result = (ObjectNode) JsonUtils.tree(this);
    result.put("key", operation.key());
    result.put("platform", operation.platform());
    result.put("summary", operation.summary());
    return result;
  }
}
