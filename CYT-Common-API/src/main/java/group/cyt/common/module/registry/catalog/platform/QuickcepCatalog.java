package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import group.cyt.common.module.registry.catalog.*;
import java.util.List;

/** Built-in quickcep contracts. Edit metadata, parameters and review together here. */
public final class QuickcepCatalog implements PlatformCatalog {
  @Override
  public void register(CatalogBuilder catalog) {
    catalog.platform("quickcep", "QuickCEP 客服系统", "600/min");
    catalog.credentials(credentials());
    catalog.operation(analyticsGet());
    catalog.operation(messagesList());
    catalog.operation(messagesSend());
    catalog.operation(sessionsByOperator());
    catalog.operation(sessionsClose());
    catalog.operation(sessionsCreate());
    catalog.operation(sessionsGet());
    catalog.operation(sessionsList());
    catalog.operation(staffList());
    catalog.operation(messagesCollect());
  }

  private static PlatformCredentials credentials() {
    return new PlatformCredentials(
        "quickcep",
        "QuickCEP 客服",
        "",
        "多账号平台：账号是**请求参数**（CYT-CS 有 epomaker / linsoul / sandbox 多个店铺），每个账号一套 Access ID + RSA 公钥。在「账号列表」里填 epomaker,linsoul 就会出现各自的变量行。不传 account 时用「默认账号」",
        "inherit",
        true,
        false,
        "QUICKCEP_ACCOUNTS",
        "QUICKCEP_ACCOUNT",
        "",
        List.of(
            new PlatformCredentials.Variable(
                "QUICKCEP_ACCOUNTS",
                "账号列表",
                false,
                false,
                "逗号分隔。改这一行就会展开出对应账号的变量行",
                "epomaker,linsoul,sandbox",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "QUICKCEP_ACCOUNT",
                "默认账号",
                false,
                false,
                "操作不传 account 时用它；单账号环境只配这个也行",
                "epomaker",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "QUICKCEP_{ACCOUNT}_ACCESS_ID",
                "Access ID",
                true,
                true,
                "QuickCEP 后台 → 开放接口 → Access ID",
                "",
                false,
                "",
                true),
            new PlatformCredentials.Variable(
                "QUICKCEP_{ACCOUNT}_PUBLIC_KEY",
                "RSA 公钥",
                true,
                true,
                "一行 base64 或整段 PEM 都行；每次请求现签，服务端校验 timestamp",
                "",
                false,
                "",
                true),
            new PlatformCredentials.Variable(
                "QUICKCEP_DOMAIN",
                "服务地址",
                false,
                false,
                "默认 https://app.quickcep.com",
                "https://app.quickcep.com",
                false,
                "",
                false)));
  }

  private static BuiltinOperation analyticsGet() {
    return operation("quickcep.analytics.get")
        .summary("会话分析（有效会话/首响时长/流失率/解决率）")
        .description(
            "参数是 startTime/endTime（yyyy-MM-dd），不是 createDate —— 传错会报 Text 'null 00' could not be parsed。")
        .retries(2)
        .since("2026-10")
        .category("分析")
        .order(10)
        .cost("none")
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "QuickCEP 账号名（如 epomaker / linsoul / sandbox）；空=QUICKCEP_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "start",
                    object("description", "开始日期 yyyy-MM-dd", "title", "Start", "type", "string"),
                    "end",
                    object("description", "结束日期 yyyy-MM-dd", "title", "End", "type", "string"),
                    "channels",
                    object(
                        "description",
                        "渠道过滤；空=全部",
                        "items",
                        object("type", "string"),
                        "title",
                        "Channels",
                        "type",
                        "array",
                        "default",
                        array())),
                "required",
                array("start", "end"),
                "title",
                "StatisticsParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/quickcep/QuickcepAdapter.java",
                "按时间和渠道读取统计数据。",
                "统一指标口径、时区与零分母处理，返回数据覆盖范围。",
                "首响、流失等指标的口径需要保留来源解释。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "转换日期/渠道后返回供应商统计主体。",
                "指标口径、时间范围完整性和业务解读未规范。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/quickcep/QuickcepAdapter.java",
                        77,
                        "case \"analytics.get\""))))
        .build();
  }

  private static BuiltinOperation messagesList() {
    return operation("quickcep.messages.list")
        .summary("分页查询会话消息")
        .description("page_size 上限 100。消息体里满意度评价是 contentType=score_notify 的那条。")
        .quota("600/min")
        .retries(3)
        .since("2026-10")
        .category("会话")
        .order(30)
        .cost("none")
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "QuickCEP 账号名（如 epomaker / linsoul / sandbox）；空=QUICKCEP_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "session_id",
                    object("description", "子会话 id", "title", "Session Id", "type", "string"),
                    "page",
                    object("default", 1, "minimum", 1, "title", "Page", "type", "integer"),
                    "page_size",
                    object(
                        "default",
                        50,
                        "maximum",
                        100,
                        "minimum",
                        1,
                        "title",
                        "Page Size",
                        "type",
                        "integer")),
                "required",
                array("session_id"),
                "title",
                "MessageListParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/quickcep/QuickcepAdapter.java",
                "已转换会话 ID、页码，返回 total/total_page。",
                "增加 transcript：自动翻页、按时间排序、发件角色规范化。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "统一消息页码、总量和页数。",
                "未自动汇总会话全文或归一参与者角色。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/quickcep/QuickcepAdapter.java",
                        62,
                        "case \"messages.list\""))))
        .build();
  }

  private static BuiltinOperation messagesSend() {
    return operation("quickcep.messages.send")
        .summary("发消息（坐席回复 / 访客模拟）")
        .description(
            "as_visitor=true 以访客身份写入（造数据用）；false 以坐席身份发给真实客户，**发出去撤不回来**。email 渠道要求该坐席配了发件邮箱，否则报 sender.email.null。")
        .quota("120/min")
        .retries(0)
        .since("2026-10")
        .category("消息")
        .order(10)
        .cost("none")
        .tags("write", "danger")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "QuickCEP 账号名（如 epomaker / linsoul / sandbox）；空=QUICKCEP_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "session_id",
                    object("description", "子会话 id", "title", "Session Id", "type", "string"),
                    "text",
                    object("description", "消息正文", "title", "Text", "type", "string"),
                    "as_visitor",
                    object(
                        "default",
                        false,
                        "description",
                        "true=以访客身份发；false=以坐席身份发",
                        "title",
                        "As Visitor",
                        "type",
                        "boolean"),
                    "owner_id",
                    object(
                        "default",
                        0,
                        "description",
                        "坐席 userId；as_visitor=false 时必填",
                        "title",
                        "Owner Id",
                        "type",
                        "integer"),
                    "channel",
                    object(
                        "default",
                        "web",
                        "description",
                        "需与会话渠道一致",
                        "title",
                        "Channel",
                        "type",
                        "string"),
                    "mid",
                    object(
                        "default",
                        "",
                        "description",
                        "第三方消息 id，用于重试幂等",
                        "title",
                        "Mid",
                        "type",
                        "string"),
                    "from_addr",
                    object(
                        "default",
                        "",
                        "description",
                        "email 渠道的发件邮箱。不传会报 sender.email.null —— QuickCEP 不从坐席档案里取，必须由调用方在 content.from 里带上",
                        "title",
                        "From Addr",
                        "type",
                        "string")),
                "required",
                array("session_id", "text"),
                "title",
                "MessageSendParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/quickcep/QuickcepAdapter.java",
                "已区分坐席回复/访客模拟，检查坐席 owner_id。",
                "增加 reply：从会话读取渠道和坐席，预检可回复状态与稳定 mid。",
                "真实发送有业务影响；访客模拟须明确。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "区分坐席/访客，构建消息并检查 owner_id。",
                "调用方仍要提供会话、渠道和坐席；未查询可回复状态和自动补齐。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/quickcep/QuickcepAdapter.java",
                        112,
                        "case \"messages.send\""))))
        .build();
  }

  private static BuiltinOperation sessionsByOperator() {
    return operation("quickcep.sessions.by_operator")
        .summary("某坐席的会话列表（可按「未接收」筛）")
        .description("chat_type=unreceived 就是客服工作台的「待我接」。比按日期拉全量再本地过滤准。")
        .quota("600/min")
        .retries(3)
        .since("2026-10")
        .category("会话")
        .order(40)
        .cost("none")
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "QuickCEP 账号名（如 epomaker / linsoul / sandbox）；空=QUICKCEP_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "user_id",
                    object(
                        "description",
                        "QuickCEP 坐席 userId（staff.list 里返回的 userId）",
                        "title",
                        "User Id",
                        "type",
                        "string"),
                    "chat_type",
                    object(
                        "default",
                        "all",
                        "description",
                        "all / unreceived（待我接）/ assigned",
                        "title",
                        "Chat Type",
                        "type",
                        "string"),
                    "page",
                    object("default", 1, "minimum", 1, "title", "Page", "type", "integer"),
                    "page_size",
                    object(
                        "default",
                        20,
                        "maximum",
                        100,
                        "minimum",
                        1,
                        "title",
                        "Page Size",
                        "type",
                        "integer")),
                "required",
                array("user_id"),
                "title",
                "OperatorSessionsParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/quickcep/QuickcepAdapter.java",
                "将坐席、会话类型和排序转为供应商参数。",
                "增加坐席名/ID 解析与有界列表，明确未接收筛选。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "转换坐席筛选及分页信息。",
                "没有坐席名称解析、自动翻页和会话详情补齐。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/quickcep/QuickcepAdapter.java",
                        82,
                        "case \"sessions.by_operator\""))))
        .build();
  }

  private static BuiltinOperation sessionsClose() {
    return operation("quickcep.sessions.close")
        .summary("关闭子会话（幂等）")
        .description("已关闭时返回 alreadyClosed=true，不重复关闭。网络抖动可安全重试。")
        .quota("120/min")
        .retries(2)
        .since("2026-10")
        .category("会话")
        .order(50)
        .cost("none")
        .tags("write")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "QuickCEP 账号名（如 epomaker / linsoul / sandbox）；空=QUICKCEP_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "session_id",
                    object("description", "子会话 id", "title", "Session Id", "type", "string")),
                "required",
                array("session_id"),
                "title",
                "SessionCloseParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/quickcep/QuickcepAdapter.java",
                "按会话 ID 关闭，保留幂等语义。",
                "增加当前状态和最终关闭结果，让重复关闭可直接理解。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "调用供应商关闭会话。",
                "未读取原状态或归一重复关闭的最终状态。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/quickcep/QuickcepAdapter.java",
                        97,
                        "case \"sessions.close\""))))
        .build();
  }

  private static BuiltinOperation sessionsCreate() {
    return operation("quickcep.sessions.create")
        .summary("查询或创建会话（mode=2 为测试会话）")
        .description(
            "按邮箱建会话**必须用 channel=email** —— web 渠道要用 visitorId 匹配访客，只给邮箱会报 visitor.not.resolved。mode=2 是测试会话，不会进真实客服队列。")
        .retries(0)
        .since("2026-10")
        .category("会话")
        .order(60)
        .cost("none")
        .tags("write")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "QuickCEP 账号名（如 epomaker / linsoul / sandbox）；空=QUICKCEP_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "email",
                    object(
                        "default",
                        "",
                        "description",
                        "访客邮箱。⚠️ 只有 email 渠道生效，web 渠道传了会被忽略且建不出来",
                        "title",
                        "Email",
                        "type",
                        "string"),
                    "name",
                    object("default", "", "description", "访客名称", "title", "Name", "type", "string"),
                    "channel",
                    object(
                        "default",
                        "email",
                        "description",
                        "渠道。按邮箱建会话必须用 email",
                        "title",
                        "Channel",
                        "type",
                        "string"),
                    "mode",
                    object(
                        "default",
                        2,
                        "description",
                        "1 正式会话 / 2 测试会话（造数据用 2，不进真实客服队列）",
                        "title",
                        "Mode",
                        "type",
                        "integer"),
                    "tags",
                    object(
                        "description",
                        "会话标签名（需先在 QuickCEP 后台建好）",
                        "items",
                        object("type", "string"),
                        "title",
                        "Tags",
                        "type",
                        "array",
                        "default",
                        array()),
                    "third_session_id",
                    object(
                        "default",
                        "",
                        "description",
                        "调用方自己的会话 id，用于幂等定位",
                        "title",
                        "Third Session Id",
                        "type",
                        "string")),
                "title",
                "SessionCreateParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/quickcep/QuickcepAdapter.java",
                "查询或创建会话，支持第三方会话号和测试模式。",
                "按业务会话号提供 ensure_session，并返回 created 与 resolved。",
                "测试 mode=2 与正式会话需要明确区分。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "转换访客与第三方会话号，使用供应商 getOrCreate。",
                "未形成网关自身的业务会话解析和可继续操作视图。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/quickcep/QuickcepAdapter.java",
                        101,
                        "case \"sessions.create\""))))
        .build();
  }

  private static BuiltinOperation sessionsGet() {
    return operation("quickcep.sessions.get")
        .summary("会话详情（访客/坐席/标签/状态/未读数）")
        .quota("600/min")
        .retries(3)
        .since("2026-10")
        .category("会话")
        .order(20)
        .cost("none")
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "QuickCEP 账号名（如 epomaker / linsoul / sandbox）；空=QUICKCEP_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "session_id",
                    object(
                        "description",
                        "子会话 id（chatSubSessionId）",
                        "title",
                        "Session Id",
                        "type",
                        "string")),
                "required",
                array("session_id"),
                "title",
                "SessionGetParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/quickcep/QuickcepAdapter.java",
                "返回会话详情，但主体仍是供应商字段。",
                "整理渠道、参与人、坐席、未读、可回复状态与下一动作。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "读取原会话主体、会话 ID 页或坐席主体。",
                "会话与消息、坐席未关联，供应商角色和状态仍需业务侧理解。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/quickcep/QuickcepAdapter.java",
                        28,
                        "public Object invoke("))))
        .build();
  }

  private static BuiltinOperation sessionsList() {
    return operation("quickcep.sessions.list")
        .summary("按创建/关闭日期拉子会话 id 列表")
        .description("create_date 与 close_date 至少给一个，否则接口返回 500。只返回 id，详情要再调 sessions.get。")
        .quota("600/min")
        .retries(3)
        .since("2026-10")
        .category("会话")
        .order(10)
        .cost("none")
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "QuickCEP 账号名（如 epomaker / linsoul / sandbox）；空=QUICKCEP_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "create_date",
                    object(
                        "description",
                        "会话创建日期 yyyy-MM-dd（东8区）；与 close_date 二选一",
                        "title",
                        "Create Date",
                        "type",
                        "string"),
                    "close_date",
                    object(
                        "default",
                        "",
                        "description",
                        "会话关闭日期 yyyy-MM-dd；与 create_date 二选一",
                        "title",
                        "Close Date",
                        "type",
                        "string"),
                    "state",
                    object(
                        "default",
                        "all",
                        "description",
                        "all / bot / unAssigned / assigned / closed",
                        "title",
                        "State",
                        "type",
                        "string"),
                    "channel",
                    object(
                        "default",
                        "all",
                        "description",
                        "all / web / email / whatsapp / facebook / instagram / amazon / tiktok / line …",
                        "title",
                        "Channel",
                        "type",
                        "string"),
                    "page_no",
                    object(
                        "default",
                        1,
                        "description",
                        "页码，从 1 开始",
                        "minimum",
                        1,
                        "title",
                        "Page No",
                        "type",
                        "integer")),
                "required",
                array("create_date"),
                "title",
                "SessionListParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/quickcep/QuickcepAdapter.java",
                "按创建/关闭日期读取会话 ID，分离有效 ID。",
                "增加日期拆分、分页与详情聚合，返回每项错误。",
                "当前返回的是 ID 列表，不是完整会话。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "读取原会话主体、会话 ID 页或坐席主体。",
                "会话与消息、坐席未关联，供应商角色和状态仍需业务侧理解。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/quickcep/QuickcepAdapter.java",
                        28,
                        "public Object invoke("))))
        .build();
  }

  private static BuiltinOperation staffList() {
    return operation("quickcep.staff.list")
        .summary("坐席列表（staffId / userId / 在线状态）")
        .quota("600/min")
        .retries(3)
        .since("2026-10")
        .category("坐席")
        .order(10)
        .cost("none")
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "QuickCEP 账号名（如 epomaker / linsoul / sandbox）；空=QUICKCEP_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string")),
                "title",
                "StaffListParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/quickcep/QuickcepAdapter.java",
                "读取供应商坐席目录。",
                "归一 staffId/userId、显示名、在线和接待状态，提供唯一解析。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "读取原会话主体、会话 ID 页或坐席主体。",
                "会话与消息、坐席未关联，供应商角色和状态仍需业务侧理解。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/quickcep/QuickcepAdapter.java",
                        28,
                        "public Object invoke("))))
        .build();
  }

  private static BuiltinOperation messagesCollect() {
    return operation("quickcep.messages.collect")
        .summary("自动分页汇总：分页查询会话消息")
        .description("有界自动分页，统一 items/page/errors 与签名续传；保持原筛选，续传时每页大小固定。")
        .timeout(90)
        .category("业务聚合")
        .order(-60)
        .tags("read", "business")
        .params(
            object(
                "type",
                "object",
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "description",
                        "QuickCEP 账号名（如 epomaker / linsoul / sandbox）；空=QUICKCEP_ACCOUNT",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "session_id",
                    object("description", "子会话 id", "title", "Session Id", "type", "string"),
                    "page_size",
                    object("type", "integer", "default", 50, "minimum", 1, "maximum", 1000),
                    "max_items",
                    object("type", "integer", "default", 200, "minimum", 1, "maximum", 1000),
                    "max_pages",
                    object("type", "integer", "default", 10, "minimum", 1, "maximum", 100),
                    "resume_cursor",
                    object(
                        "type", "string", "minLength", 1, "maxLength", 16000, "description", "")),
                "required",
                array("session_id"),
                "additionalProperties",
                false,
                "examples",
                array(object("session_id", "demo-session"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                "有界自动分页，统一 items/page/errors 与签名续传；保持原筛选，续传时每页大小固定。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "自动翻页覆盖根记录；嵌套连接、供应商可见范围和来源快照仍有各自限制。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "固定原生页大小、有界自动汇总、页内续传，并绑定系统与筛选条件。",
                "自动分页不是完整业务事务；根记录范围和上游一致性仍有边界。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                        20,
                        "public static final Map<String, Spec> SPECS"),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/support/BusinessWorkflows.java",
                        223,
                        "case \"total_offset\""))))
        .build();
  }
}
