package group.cyt.common.module.registry.catalog;

import com.fasterxml.jackson.databind.node.ObjectNode;
import group.cyt.common.module.registry.catalog.platform.*;
import group.cyt.common.module.registry.model.OperationDefinition;
import java.util.*;

/** Immutable built-in catalogue. New native platforms register once in Standard below. */
public final class BuiltinCatalog {
  private final Map<String, BuiltinOperation> operations;
  private final Map<String, String> aliases;
  private final ObjectNode contract;
  private final ObjectNode responses;
  private final ObjectNode review;

  public BuiltinCatalog(List<? extends PlatformCatalog> contributors) {
    CatalogBuilder builder = new CatalogBuilder();
    contributors.forEach(contributor -> contributor.register(builder));
    for (var entry : builder.operations.entrySet()) {
      var op = entry.getValue().definition();
      if (!builder.platforms.containsKey(op.platform()))
        throw new IllegalArgumentException("Undeclared platform: " + op.platform());
      for (String recommended : entry.getValue().review().recommendedOperations())
        if (!builder.operations.containsKey(recommended))
          throw new IllegalArgumentException("Unknown recommended operation: " + recommended);
    }
    builder.aliases.forEach(
        (alias, target) -> {
          if (!builder.operations.containsKey(target) || builder.operations.containsKey(alias))
            throw new IllegalArgumentException("Invalid operation alias: " + alias);
        });
    builder.credentials.forEach(
        (platform, credentials) -> {
          if (!builder.platforms.containsKey(platform))
            throw new IllegalArgumentException(
                "Credentials reference undeclared platform: " + platform);
          Set<String> names = new HashSet<>();
          for (var variable : credentials.vars())
            if (!names.add(variable.name()))
              throw new IllegalArgumentException(
                  "Duplicate credential variable: " + variable.name());
        });
    operations = Collections.unmodifiableMap(new LinkedHashMap<>(builder.operations));
    aliases = Collections.unmodifiableMap(new LinkedHashMap<>(builder.aliases));
    contract =
        ContractNodes.object(
            "operations",
            ContractNodes.array(),
            "platforms",
            builder.platforms.values(),
            "credentials",
            builder.credentials.values(),
            "aliases",
            aliases,
            "systems",
            CatalogDefaults.callers(),
            "grants",
            CatalogDefaults.grants());
    responses = ContractNodes.object("$collection", CatalogDocumentation.collectionResponse());
    var contracts = contract.withArray("operations");
    var items = ContractNodes.array();
    var counts = ContractNodes.object();
    for (var value : operations.values()) {
      var op = value.definition();
      contracts.add(value.describe());
      if (value.response() != null) responses.set(op.key(), value.response());
      items.add(value.review().describe(op));
      String depth = value.review().depth().value();
      counts.put(depth, counts.path(depth).asInt() + 1);
    }
    review = CatalogDocumentation.metadata();
    review.set("items", items);
    ObjectNode summary = review.withObject("/depth_summary");
    summary.put("total", operations.size());
    summary.set("counts", counts);
    summary.put(
        "all_deep", !operations.isEmpty() && counts.path("deep").asInt() == operations.size());
  }

  public static BuiltinCatalog standard() {
    return Standard.INSTANCE;
  }

  public List<OperationDefinition> operations() {
    return operations.values().stream().map(BuiltinOperation::definition).toList();
  }

  public Set<String> operationKeys() {
    return operations.keySet();
  }

  public Map<String, String> aliases() {
    return aliases;
  }

  public ObjectNode contract() {
    return contract.deepCopy();
  }

  public ObjectNode responses() {
    return responses.deepCopy();
  }

  public ObjectNode capabilityReview() {
    return review.deepCopy();
  }

  public ObjectNode export() {
    return ContractNodes.object(
        "contract", contract, "responses", responses, "capability_review", review);
  }

  private static final class Standard {
    static final BuiltinCatalog INSTANCE =
        new BuiltinCatalog(
            List.of(
                new DocusignCatalog(),
                new WecomCatalog(),
                new ShopifyCatalog(),
                new GmailCatalog(),
                new GdriveCatalog(),
                new GcsCatalog(),
                new NasCatalog(),
                new QuickcepCatalog(),
                new LingxingCatalog(),
                new WinitCatalog(),
                new MabangCatalog(),
                new JudgemeCatalog(),
                new TrustpilotCatalog(),
                new PaypalCatalog(),
                new StripeCatalog(),
                new RdCatalog(),
                new DiscordCatalog(),
                new TikTokCatalog()));
  }
}
