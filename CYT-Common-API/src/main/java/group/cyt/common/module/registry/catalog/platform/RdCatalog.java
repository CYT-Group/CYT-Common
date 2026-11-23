package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import group.cyt.common.module.registry.catalog.*;
import java.util.List;

/** Built-in rd contracts. Edit metadata, parameters and review together here. */
public final class RdCatalog implements PlatformCatalog {
  @Override
  public void register(CatalogBuilder catalog) {
    catalog.platform("rd", "RD 产品目录", "60/min");
    catalog.credentials(credentials());
    catalog.operation(productsList());
    catalog.operation(productsCollect());
  }

  private static PlatformCredentials credentials() {
    return new PlatformCredentials(
        "rd",
        "RD 产品目录（只读）",
        "",
        "只读归集，不提供退款、回复或其他业务写入。多账号均需独立配置。Trustpilot API_KEY 与 ACCESS_TOKEN 至少配置一个。PayPal ENV 默认 sandbox。",
        "inherit",
        false,
        false,
        "RD_ACCOUNTS",
        "",
        "",
        List.of(
            new PlatformCredentials.Variable(
                "RD_{ACCOUNT}_BASE_URL", "BASE_URL", true, false, "", "", false, "", true),
            new PlatformCredentials.Variable(
                "RD_{ACCOUNT}_ACCESS_TOKEN",
                "ACCESS_TOKEN",
                false,
                true,
                "",
                "",
                false,
                "",
                true)));
  }

  private static BuiltinOperation productsList() {
    return operation("rd.products.list")
        .summary("读取 RD 标准产品目录")
        .description("只读拉取、账号独立路由、标准分页；缺授权明确失败，不返回模拟数据。")
        .quota("30/min")
        .timeout(120)
        .since("2026-10")
        .category("只读归集")
        .cost("none")
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object("default", "", "maxLength", 254, "title", "Account", "type", "string"),
                    "cursor",
                    object(
                        "additionalProperties",
                        true,
                        "title",
                        "Cursor",
                        "type",
                        "object",
                        "default",
                        object()),
                    "limit",
                    object(
                        "default", 100, "maximum", 100, "minimum", 1, "title", "Limit", "type",
                        "integer")),
                "title",
                "Page",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/source/CustomerSourcesAdapter.java",
                "目录适配器将来源产品规范为统一列表。",
                "使用 collect 保持统一读取协议；后续增加 SKU 冲突提示。",
                "来源若不提供分页，collect 的完整性仍取决于来源快照。",
                "recommended",
                List.of("rd.products.collect"),
                CapabilityReview.Depth.ENHANCED,
                "适配来源目录并整理为统一产品列表。",
                "尚无 SKU 冲突处理、产品关联和增量变更模型。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/source/CustomerSourcesAdapter.java",
                        382,
                        "private Map<String, Object> rdProducts("))))
        .build();
  }

  private static BuiltinOperation productsCollect() {
    return operation("rd.products.collect")
        .summary("自动分页汇总：读取 RD 标准产品目录")
        .description(
            "自动分页、统一 items/page/errors；支持 max_items、max_pages 与签名续传游标。仅扫描根记录，嵌套资源遵循原接口的范围。读取能力独立授权，保留原接口兼容。")
        .quota("30/min")
        .timeout(90)
        .category("业务聚合")
        .order(-50)
        .sourceOperation("rd.products.list")
        .tags("read", "business", "collection")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "account",
                    object("default", "", "maxLength", 254, "title", "Account", "type", "string"),
                    "page_size",
                    object("type", "integer", "default", 50, "minimum", 1, "maximum", 1000),
                    "max_items",
                    object("type", "integer", "default", 100, "minimum", 1, "maximum", 1000),
                    "max_pages",
                    object("type", "integer", "default", 10, "minimum", 1, "maximum", 100),
                    "resume_cursor",
                    object(
                        "type",
                        "string",
                        "minLength",
                        0,
                        "maxLength",
                        16000,
                        "description",
                        "回传 page.next_cursor；绑定调用系统和筛选条件")),
                "required",
                array(),
                "examples",
                array(object())))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                "自动分页、统一 items/page/errors；支持 max_items、max_pages 与签名续传游标。仅扫描根记录，嵌套资源遵循原接口的范围。读取能力独立授权，保留原接口兼容。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "自动翻页覆盖根记录；嵌套连接、供应商可见范围和来源快照仍有各自限制。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "在固定只读能力上增加上限、分页、去重、统一返回和签名续传。",
                "统一读取协议不等于 SKU 解析与目录治理。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                        101,
                        "public Object invoke("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                        327,
                        "\"root_records\""))))
        .build();
  }
}
