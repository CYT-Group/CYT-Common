package group.cyt.common.module.registry.catalog;

import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import com.fasterxml.jackson.databind.node.ObjectNode;

final class CatalogDocumentation {
  private CatalogDocumentation() {}

  static ObjectNode metadata() {
    return object(
        "generated_at",
        "2026-11-12",
        "scope",
        "逐项审阅 Java 内置接口声明；数量和深度统计由同一份目录计算。",
        "references",
        array(
            object(
                "title",
                "企微审批：模板详情",
                "url",
                "https://developer.work.weixin.qq.com/document/path/91982"),
            object(
                "title",
                "企微审批：提交申请",
                "url",
                "https://developer.work.weixin.qq.com/document/path/91853"),
            object(
                "title",
                "企微审批：批量查询",
                "url",
                "https://developer.work.weixin.qq.com/document/path/91816"),
            object("title", "WxJava：按领域组织服务和 DTO", "url", "https://github.com/binarywang/WxJava"),
            object(
                "title",
                "Stripe Java：自动分页迭代器",
                "url",
                "https://github.com/stripe/stripe-java/blob/master/src/main/java/com/stripe/model/StripeCollection.java"),
            object(
                "title",
                "Drive：查询条件与转义",
                "url",
                "https://developers.google.com/workspace/drive/api/guides/search-files"),
            object(
                "title",
                "Gmail：筛选与时间语义",
                "url",
                "https://developers.google.com/workspace/gmail/api/guides/filtering")),
        "depth_summary",
        object(
            "conclusion",
            "当前没有全部完成深度二次封装。推荐调用、business 标签和测试通过均不能替代逐项业务深度判断。",
            "basis",
            "静态代码逐项审阅；只评价实际已实现的语义转换、关联补齐与流程编排，不评价供应商真实账号验收。",
            "scope",
            "覆盖当前全部 Java 内置业务接口；自定义 HTTP 接口和管理端 CRUD 不计入本统计。",
            "levels",
            array(
                object(
                    "key",
                    "deep",
                    "label",
                    "深度业务封装",
                    "description",
                    "已实现业务语义转换或关联查询、预检、提交流程；仍需逐项阅读边界，不等于完整生产验收。"),
                object(
                    "key",
                    "enhanced",
                    "label",
                    "增强封装",
                    "description",
                    "已有字段整理、校验、批处理或分页便利，但业务关联或流程仍需调用方完成。"),
                object("key", "basic", "label", "基础封装", "description", "主要是单项读写、官方参数映射、原始结果或兼容入口。"),
                object(
                    "key",
                    "supporting",
                    "label",
                    "基础管理能力",
                    "description",
                    "凭据、登录换码、桶管理等原子能力，按职责保留，不强行为每项制造业务流程。"),
                object(
                    "key", "test_only", "label", "模拟演练", "description", "只用于模拟，不是已经完成的真实供应商业务能力。")),
            "findings",
            array(
                object(
                    "priority",
                    "P1",
                    "title",
                    "统一网关与推荐标签不能证明深度封装",
                    "operations",
                    array(),
                    "detail",
                    "认证、签名、重试、字段改名、推荐调用和自动翻页不单独计为深度业务封装。"),
                object(
                    "priority",
                    "P1",
                    "title",
                    "领星售后链路仍需业务方编排",
                    "operations",
                    array(
                        "lingxing.mporders.list",
                        "lingxing.workorder.add",
                        "lingxing.workorder.audit",
                        "lingxing.refund.create"),
                    "detail",
                    "订单行号、PID、售后原因、已退数量与审批流 ID 没有形成自动关联预检。"),
                object(
                    "priority",
                    "P1",
                    "title",
                    "万邑通退货创建与取面单尚未串联",
                    "operations",
                    array(
                        "winit.returns.create",
                        "winit.returns.psc",
                        "winit.returns.items",
                        "winit.returns.label"),
                    "detail",
                    "创建接口返回单号/RMA/地址，不直接返回面单；缺少业务订单到退货计划的关联补齐。"),
                object(
                    "priority",
                    "P1",
                    "title",
                    "自动翻根页不代表嵌套数据完整",
                    "operations",
                    array(
                        "shopify.orders.collect",
                        "shopify.products.collect",
                        "shopify.refunds.list",
                        "shopify.returns.list"),
                    "detail",
                    "嵌套行、变体和交易有独立查询上限，退款还需根页和页内偏移共同续传。"),
                object(
                    "priority",
                    "P1边界",
                    "title",
                    "文件头识别不代表完整文件校验",
                    "operations",
                    array("nas.objects.read_head"),
                    "detail",
                    "已识别常见文件签名及图片尺寸，并提示声明 MIME 不一致；仍不执行完整解码、病毒扫描或压缩包内容识别。"),
                object(
                    "priority",
                    "P1",
                    "title",
                    "QuickCEP 缺少一体化会话与回复能力",
                    "operations",
                    array(
                        "quickcep.sessions.get",
                        "quickcep.messages.list",
                        "quickcep.messages.send",
                        "quickcep.staff.list"),
                    "detail",
                    "会话详情、完整正文、坐席/渠道解析和可回复状态仍需分别处理。"),
                object(
                    "priority",
                    "P1",
                    "title",
                    "DocuSign 已补目录分页，业务签署策略仍需显式确定",
                    "operations",
                    array(
                        "docusign.envelopes.create",
                        "docusign.envelopes.list",
                        "docusign.templates.list"),
                    "detail",
                    "已提供模板角色/字段读取、单页与汇总续传；从业务角色到签署策略仍需接入方配置。"),
                object(
                    "priority",
                    "P0接入边界",
                    "title",
                    "审批主流程较深，单项工具与高级控件仍有边界",
                    "operations",
                    array(
                        "wecom.approvals.submit",
                        "wecom.approvals.get",
                        "wecom.approvals.engine_get",
                        "wecom.vacation.set"),
                    "detail",
                    "审批已补业务请求追踪、步骤/字段还原和假期规则预检；高级控件、引擎统一模型和真实账号验收仍有边界。")),
            "auxiliary_interfaces",
            array(
                object(
                    "path",
                    "POST /integrations/batch",
                    "depth",
                    "enhanced",
                    "reason",
                    "统一执行、逐项结果和 stop_on_error；调用依赖、业务回滚和补偿仍由业务层处理。",
                    "evidence",
                    array(
                        object(
                            "path",
                            "src/main/java/group/cyt/common/module/gateway/service/GatewayExecutor.java",
                            "line",
                            252,
                            "anchor",
                            "public Object batch("))),
                object(
                    "path",
                    "GET /integrations/wecom/webhooks/{system}/events",
                    "depth",
                    "enhanced",
                    "reason",
                    "验签、解密并验证企业接收者，处理接入 challenge，不代表审批业务已经发生。",
                    "evidence",
                    array(
                        object(
                            "path",
                            "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalController.java",
                            "line",
                            16,
                            "anchor",
                            "public String verify("))),
                object(
                    "path",
                    "POST /integrations/wecom/webhooks/{system}/events",
                    "depth",
                    "deep",
                    "reason",
                    "验签解密、归一状态、所有者路由、加密去重入库和持久转发重试。未知来源与凭据轮换有明确边界。",
                    "evidence",
                    array(
                        object(
                            "path",
                            "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalEvents.java",
                            "line",
                            55,
                            "anchor",
                            "public void receive("),
                        object(
                            "path",
                            "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalEvents.java",
                            "line",
                            134,
                            "anchor",
                            "public void deliverPending("))),
                object(
                    "path",
                    "POST /integrations/docusign/webhooks/{system}/events（兼容 /integrations/docusign/webhooks/events）",
                    "depth",
                    "enhanced",
                    "reason",
                    "原文验签、事件归一并转发；没有持久去重收件箱和重试作业，失败依赖上游重投。",
                    "evidence",
                    array(
                        object(
                            "path",
                            "src/main/java/group/cyt/common/module/callback/controller/CallbackController.java",
                            "line",
                            82,
                            "anchor",
                            "public Object webhook("))))));
  }

  static ObjectNode collectionResponse() {
    return object(
        "type",
        "object",
        "description",
        "",
        "properties",
        object(
            "items",
            object(
                "type",
                "array",
                "description",
                "已采集的根记录",
                "items",
                object("description", "来源记录，具体字段取决于被汇总的平台接口")),
            "folders",
            object(
                "type",
                "array",
                "description",
                "目录条目；部分来源不提供",
                "items",
                object("description", "目录信息，按来源接口返回")),
            "errors",
            object(
                "type",
                "array",
                "description",
                "部分记录读取失败的信息",
                "items",
                object("description", "单条读取失败的标识和错误信息")),
            "partial",
            object("type", "boolean", "description", "是否存在部分失败或尚未完成的范围"),
            "coverage",
            object("type", "string", "description", "采集范围说明；root_records 不承诺已遍历全部嵌套资源"),
            "page",
            object(
                "type",
                "object",
                "description",
                "",
                "properties",
                object(
                    "has_more",
                    object("type", "boolean", "description", "是否还有待继续采集的数据"),
                    "next_cursor",
                    object(
                        "type",
                        array("string", "null"),
                        "description",
                        "下次续传游标；继续时保持原筛选和 page_size"),
                    "count",
                    object("type", "integer", "description", "本次返回的记录数量"),
                    "pages",
                    object("type", "integer", "description", "本次已读取的上游页数")))));
  }
}
