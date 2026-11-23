package group.cyt.common;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.object;
import static org.junit.jupiter.api.Assertions.*;

import group.cyt.common.module.registry.catalog.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class BuiltinCatalogTest {
  private static BuiltinOperation example() {
    return operation("fixture.items.list")
        .summary("Fixture items")
        .tags("read")
        .params(object("type", "object"))
        .review(
            new CapabilityReview(
                null,
                "Lists items",
                "Use the list operation",
                "Fixture only",
                "retain",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "Single read",
                "No aggregation",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/registry/catalog/BuiltinCatalog.java",
                        1,
                        "class BuiltinCatalog"))))
        .build();
  }

  @Test
  void newPlatformIsDeclaredOnceAndReviewStatisticsFollowItsOperations() {
    var catalog =
        new BuiltinCatalog(
            List.of(
                builder -> {
                  builder.platform("fixture", "Fixture", null);
                  builder.operation(example());
                  builder.alias("fixture.legacy.list", "fixture.items.list");
                }));
    assertEquals(1, catalog.operations().size());
    assertEquals("fixture.items.list", catalog.aliases().get("fixture.legacy.list"));
    assertEquals(1, catalog.capabilityReview().path("depth_summary").path("total").asInt());
    assertEquals(
        1, catalog.capabilityReview().path("depth_summary").path("counts").path("basic").asInt());
  }

  @Test
  void duplicateOperationsAndDanglingAliasesFailWhenCatalogueIsConstructed() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BuiltinCatalog(
                List.of(
                    builder -> {
                      builder.platform("fixture", "Fixture", null);
                      builder.operation(example());
                      builder.operation(example());
                    })));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BuiltinCatalog(
                List.of(
                    builder -> {
                      builder.alias("fixture.old.list", "fixture.missing.list");
                    })));
  }

  @Test
  void anOperationCannotOmitItsParameterSchemaOrCapabilityReview() {
    assertThrows(
        NullPointerException.class,
        () -> operation("fixture.items.list").summary("Fixture").build());
    assertThrows(
        NullPointerException.class,
        () -> operation("fixture.items.list").summary("Fixture").params(object()).build());
  }

  @Test
  void mutatingExportedMetadataCannotChangeTheSharedCatalogue() {
    var catalog = BuiltinCatalog.standard();
    var exported = catalog.contract();
    exported.withArray("operations").removeAll();
    var op = catalog.operations().getFirst();
    ((com.fasterxml.jackson.databind.node.ObjectNode) op.schema()).removeAll();
    assertFalse(catalog.contract().path("operations").isEmpty());
    assertFalse(catalog.operations().getFirst().schema().isEmpty());
  }
}
