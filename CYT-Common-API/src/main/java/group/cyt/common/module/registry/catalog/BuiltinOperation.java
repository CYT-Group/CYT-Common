package group.cyt.common.module.registry.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.registry.model.OperationDefinition;
import java.util.List;
import java.util.Objects;

/** One declaration owns the execution contract, response documentation and capability review. */
public final class BuiltinOperation {
  private final OperationDefinition definition;
  private final ObjectNode metadata;
  private final JsonNode response;
  private final CapabilityReview review;

  private BuiltinOperation(Builder builder) {
    if (builder.summary.isBlank()
        || builder.quota.isBlank()
        || builder.timeoutSeconds <= 0
        || builder.retryMax < 0)
      throw new IllegalArgumentException("Invalid operation metadata: " + builder.key);
    ObjectNode schema = Objects.requireNonNull(builder.schema, "Parameter schema is required");
    review =
        Objects.requireNonNull(builder.review, "Capability review is required: " + builder.key);
    definition =
        new OperationDefinition(
            builder.key,
            builder.key.substring(0, builder.key.indexOf('.')),
            builder.summary,
            builder.description,
            builder.quota,
            builder.sensitive,
            builder.idempotent,
            builder.timeoutSeconds,
            builder.retryMax,
            builder.since,
            builder.deprecated,
            List.copyOf(builder.tags),
            builder.category,
            builder.sortOrder,
            builder.path,
            schema.deepCopy());
    metadata = builder.metadata.deepCopy();
    response = builder.response == null ? null : builder.response.deepCopy();
  }

  public static Builder operation(String key) {
    return new Builder(key);
  }

  public OperationDefinition definition() {
    return new OperationDefinition(
        definition.key(),
        definition.platform(),
        definition.summary(),
        definition.description(),
        definition.quota(),
        definition.sensitive(),
        definition.idempotent(),
        definition.timeoutSeconds(),
        definition.retryMax(),
        definition.since(),
        definition.deprecated(),
        definition.tags(),
        definition.category(),
        definition.sortOrder(),
        definition.path(),
        definition.schema().deepCopy());
  }

  public JsonNode response() {
    return response == null ? null : response.deepCopy();
  }

  public CapabilityReview review() {
    return review;
  }

  ObjectNode describe() {
    ObjectNode node = (ObjectNode) JsonUtils.tree(definition);
    node.setAll(metadata);
    return node;
  }

  public static final class Builder {
    private final String key;
    private String summary = "", description = "", quota = "60/min", since = "2026-11";
    private String category = "", path;
    private boolean sensitive, idempotent, deprecated;
    private double timeoutSeconds = 30;
    private int retryMax = 1, sortOrder;
    private List<String> tags = List.of();
    private ObjectNode schema;
    private final ObjectNode metadata = ContractNodes.object();
    private JsonNode response;
    private CapabilityReview review;

    private Builder(String key) {
      if (!key.matches("[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*){2,}"))
        throw new IllegalArgumentException("Invalid operation key: " + key);
      this.key = key;
      path = "/integrations/" + key.replace('.', '/').replace('_', '-');
    }

    public Builder summary(String value) {
      summary = value;
      return this;
    }

    public Builder description(String value) {
      description = value;
      return this;
    }

    public Builder quota(String value) {
      quota = value;
      return this;
    }

    public Builder sensitive(boolean value) {
      sensitive = value;
      return this;
    }

    public Builder idempotent(boolean value) {
      idempotent = value;
      return this;
    }

    public Builder timeout(double value) {
      timeoutSeconds = value;
      return this;
    }

    public Builder retries(int value) {
      retryMax = value;
      return this;
    }

    public Builder since(String value) {
      since = value;
      return this;
    }

    public Builder deprecated(boolean value) {
      deprecated = value;
      return this;
    }

    public Builder tags(String... values) {
      tags = List.of(values);
      return this;
    }

    public Builder category(String value) {
      category = value;
      return this;
    }

    public Builder order(int value) {
      sortOrder = value;
      return this;
    }

    public Builder path(String value) {
      path = value;
      return this;
    }

    public Builder params(ObjectNode value) {
      schema = value;
      return this;
    }

    public Builder cost(String value) {
      metadata.put("cost", value);
      return this;
    }

    public Builder officialDocs(String value) {
      metadata.put("official_docs", value);
      return this;
    }

    public Builder sourceOperation(String value) {
      metadata.put("source_operation", value);
      return this;
    }

    public Builder response(JsonNode value) {
      response = value;
      return this;
    }

    public Builder review(CapabilityReview value) {
      review = value;
      return this;
    }

    public BuiltinOperation build() {
      return new BuiltinOperation(this);
    }
  }
}
