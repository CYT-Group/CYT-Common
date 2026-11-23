package group.cyt.common.module.registry.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

final class CatalogDefaults {
  private CatalogDefaults() {}

  record Caller(String key, @JsonProperty("display_name") String displayName, String description) {}

  record Grant(
      String system,
      @JsonProperty("operation_pattern") String operationPattern,
      String quota,
      String note) {}

  static List<Caller> callers() {
    return List.of(
        new Caller("portal", "门户 Portal", "员工门户：SSO、HR、AI 助手"),
        new Caller("marketing", "市场 Marketing", "红人营销、合同、邮件中心"),
        new Caller("sales", "销售 Sales", "销售 CRM、报价、合同"),
        new Caller("cs", "客服 CS", "客服中台、工单、知识库"),
        new Caller("rd", "研发 RD", "产品主数据、研发流程"),
        new Caller("drive", "网盘 Drive", "企业网盘：群晖 NAS 文件浏览、上传与分享"));
  }

  static List<Grant> grants() {
    return List.of(
        new Grant("portal", "wecom.users.get", "300/min", "SSO 换用户信息"),
        new Grant("portal", "wecom.users.resolve_code", "120/min", ""),
        new Grant("portal", "wecom.messages.send_markdown", "60/min", "审批/提醒通知"),
        new Grant("portal", "gdrive.files.list", "60/min", ""),
        new Grant("portal", "gdrive.files.get", "60/min", ""),
        new Grant("portal", "nas.objects.presign_get", "300/min", "培训课时直读"),
        new Grant("portal", "nas.objects.list", "30/min", "培训目录树扫描"),
        new Grant("portal", "nas.objects.stat", "120/min", ""),
        new Grant("portal", "nas.objects.read_head", "120/min", "导入判型探图"),
        new Grant("drive", "nas.objects.presign_get", "300/min", "文件浏览/预览直链"),
        new Grant("drive", "nas.objects.list", "30/min", "目录列表"),
        new Grant("drive", "nas.objects.stat", "300/min", "单文件信息"),
        new Grant("drive", "nas.objects.read_head", "120/min", "真实类型识别"),
        new Grant("drive", "nas.objects.copy", "30/min", "复制/移动/重命名"),
        new Grant("drive", "nas.objects.presign_put", "60/min", "上传（高危，单独授权）"),
        new Grant("drive", "nas.objects.delete", "60/min", "删除文件（高危）"),
        new Grant("drive", "nas.objects.delete_batch", "60/min", "删空目录（高危）"),
        new Grant("drive", "nas.objects.copy_prefix", "20/min", "移动/重命名目录（高危）"),
        new Grant("marketing", "docusign.actions.*", "120/min", "合同主责系统"),
        new Grant("marketing", "gmail.actions.*", "300/min", "邮件中心"),
        new Grant("marketing", "gdrive.actions.*", "120/min", "附件与归档"),
        new Grant("marketing", "shopify.orders.get", "60/min", ""),
        new Grant("marketing", "shopify.products.list", "60/min", ""),
        new Grant("marketing", "shopify.products.get", "60/min", ""),
        new Grant("marketing", "wecom.messages.send_markdown", "60/min", ""),
        new Grant("marketing", "gcs.objects.upload", "60/min", "合同原件归档"),
        new Grant("marketing", "gcs.objects.get_metadata", "120/min", ""),
        new Grant("sales", "docusign.envelopes.create", "30/min", "销售合同"),
        new Grant("sales", "docusign.envelopes.get", "120/min", ""),
        new Grant("sales", "docusign.envelopes.list", "60/min", ""),
        new Grant("sales", "docusign.templates.list", "60/min", ""),
        new Grant("sales", "gmail.messages.list", "120/min", ""),
        new Grant("sales", "gmail.messages.get", "120/min", ""),
        new Grant("sales", "gmail.messages.send", "30/min", "报价单发送"),
        new Grant("sales", "shopify.orders.get", "120/min", "客户订单核对"),
        new Grant("sales", "shopify.products.list", "60/min", ""),
        new Grant("sales", "wecom.messages.send_text", "60/min", ""),
        new Grant("cs", "docusign.envelopes.get", "120/min", "查签署进度"),
        new Grant("cs", "docusign.envelopes.list", "60/min", ""),
        new Grant("cs", "gmail.messages.list", "120/min", "工单关联邮件"),
        new Grant("cs", "gmail.messages.get", "120/min", ""),
        new Grant("cs", "shopify.orders.get", "120/min", "订单查询"),
        new Grant("cs", "shopify.products.get", "120/min", ""),
        new Grant("cs", "wecom.messages.send_markdown", "60/min", "告警通知"),
        new Grant("cs", "wecom.messages.send_text", "60/min", ""),
        new Grant("cs", "mabang.orders.list", "120/min", "物流异常扫描"),
        new Grant("cs", "mabang.orders.track", "120/min", "物流异常轨迹"),
        new Grant("cs", "winit.returns.psc", "30/min", "退货渠道（PSC）查询"),
        new Grant("cs", "winit.returns.label", "60/min", "ReturnLabel 面单拉取"),
        new Grant("cs", "winit.returns.create", "10/min", "中台建退货单（写，受退货仓白名单约束）"),
        new Grant("cs", "winit.returns.void", "10/min", "撤销退货单（写，受退货仓白名单约束）"),
        new Grant("cs", "lingxing.seller.list", "60/min", "亚马逊店铺列表"),
        new Grant("cs", "lingxing.orders.list", "120/min", "亚马逊订单列表"),
        new Grant("cs", "lingxing.orders.detail", "120/min", "订单详情"),
        new Grant("cs", "lingxing.mporders.list", "60/min", "多渠道订单"),
        new Grant("cs", "lingxing.inventory.list", "60/min", "库存明细"),
        new Grant("cs", "lingxing.inventory.fba", "60/min", "FBA 库存"),
        new Grant("cs", "lingxing.listing.list", "60/min", "Listing"),
        new Grant("cs", "lingxing.warehouse.list", "30/min", "仓库列表"),
        new Grant("cs", "lingxing.product.list", "60/min", "产品"),
        new Grant("cs", "lingxing.messages.list", "120/min", "站内信列表"),
        new Grant("cs", "lingxing.messages.detail", "60/min", "站内信正文"),
        new Grant("cs", "lingxing.workorder.list", "60/min", "售后工单列表"),
        new Grant("cs", "lingxing.aftersales.reasons", "30/min", "售后原因"),
        new Grant("cs", "lingxing.return.qty", "30/min", "退货数量"),
        new Grant("cs", "lingxing.refund.create", "10/min", "发起退款（写，受测试写白名单约束）"),
        new Grant("cs", "lingxing.workorder.add", "10/min", "创建售后工单（写，受测试写白名单约束）"),
        new Grant("cs", "lingxing.workorder.audit", "10/min", "审核工单（写，受测试写白名单约束）"),
        new Grant("cs", "judgeme.reviews.list", "60/min", "Judge.me 差评拉取"),
        new Grant("cs", "trustpilot.reviews.list", "60/min", "Trustpilot 评价拉取"),
        new Grant("cs", "paypal.disputes.list", "60/min", "PayPal 争议拉取"),
        new Grant("cs", "stripe.disputes.list", "60/min", "Stripe 争议拉取"),
        new Grant("cs", "rd.products.list", "60/min", "研发产品主数据"),
        new Grant("rd", "shopify.products.list", "60/min", ""),
        new Grant("rd", "shopify.products.get", "60/min", ""),
        new Grant("rd", "gdrive.files.list", "60/min", ""),
        new Grant("rd", "gcs.objects.get_metadata", "120/min", ""),
        new Grant("rd", "gcs.objects.list", "60/min", ""),
        new Grant("rd", "wecom.calendars.*", "30/min", "研发排期日历"),
        new Grant("rd", "wecom.schedules.*", "120/min", "研发排期/评审会议"),
        new Grant("portal", "wecom.schedules.list", "300/min", "门户日程展示"),
        new Grant("portal", "wecom.schedules.get_many", "300/min", ""),
        new Grant("portal", "wecom.calendars.get_many", "120/min", ""),
        new Grant("cs", "judgeme.reviews.reply", "30/min", "Judge.me 公开回复（审批后明确确认，不自动重试）"),
        new Grant("cs", "judgeme.replies.list", "30/min", "Judge.me 公开回复与状态核对"));
  }
}
