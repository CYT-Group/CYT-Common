package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import group.cyt.common.module.registry.catalog.BuiltinOperation;
import group.cyt.common.module.registry.catalog.CapabilityReview;
import java.util.List;

final class PaymentDisputeCatalog {
  private static final String HANDLER =
      "src/main/java/group/cyt/common/module/callback/service/PaymentDisputeEvents.java";

  private PaymentDisputeCatalog() {}

  static BuiltinOperation feed(String provider, boolean history) {
    var properties = object("account", object("type", "string", "pattern", "^[a-z0-9][a-z0-9_-]{0,63}$"),
        "cursor", object("type", "object", "additionalProperties", false,
            "properties", object("after", history ? object("type", "string", "maxLength", 2048)
                : object("type", "integer", "minimum", 0))),
        "limit", object("type", "integer", "minimum", 1, "maximum", history ? 50 : 100));
    if (history) {
      properties.set("from", object("type", "string", "maxLength", 48));
      properties.set("to", object("type", "string", "maxLength", 48));
    }
    return operation(provider + ".disputes." + (history ? "closure_history" : "closures"))
        .summary(history ? "读取支付平台结案历史" : "读取已验签的支付结案事件")
        .description("按账号返回结案时间和裁决，保留稳定的分页续传游标。")
        .quota("30/min").category("只读归集").tags("read").retries(1)
        .params(object("type", "object", "additionalProperties", false, "properties", properties,
            "required", history ? array("account", "from", "to") : array("account")))
        .review(review(history ? 214 : 186, history ? "public Object history(" : "public Object inbox("))
        .build();
  }

  static BuiltinOperation orderReference() {
    return operation("stripe.disputes.order_reference")
        .summary("读取 Stripe 交易的订单号")
        .description("只返回指定交易的订单号元数据。")
        .quota("30/min").category("只读归集").tags("read").retries(1)
        .params(object("type", "object", "additionalProperties", false,
            "required", array("account", "payment_id"), "properties", object(
                "account", object("type", "string", "pattern", "^[a-z0-9][a-z0-9_-]{0,63}$"),
                "payment_id", object("type", "string", "pattern", "^(ch|pi)_[A-Za-z0-9]{1,100}$"))))
        .review(review(274, "public Object orderReference("))
        .build();
  }

  private static CapabilityReview review(int line, String anchor) {
    return new CapabilityReview(HANDLER, "按账号返回归一后的支付凭据字段。", "用于客服归集和历史回补。",
        "需配置平台授权与对应账号。", "recommended", List.of(), CapabilityReview.Depth.ENHANCED,
        "验证回调或只读平台响应，只输出关联与结案字段。", "历史范围受平台保留数据限制。",
        List.of(new CapabilityReview.Evidence(HANDLER, line, anchor)));
  }
}
