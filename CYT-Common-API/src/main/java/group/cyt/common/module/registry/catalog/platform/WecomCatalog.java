package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import group.cyt.common.module.registry.catalog.*;
import java.util.List;

/** Built-in wecom contracts. Edit metadata, parameters and review together here. */
public final class WecomCatalog implements PlatformCatalog {
  @Override
  public void register(CatalogBuilder catalog) {
    catalog.platform("wecom", "企业微信", "600/min");
    catalog.credentials(credentials());
    catalog.alias("wecom.message.send_text", "wecom.messages.send_text");
    catalog.alias("wecom.message.send_markdown", "wecom.messages.send_markdown");
    catalog.alias("wecom.contact.get_user", "wecom.users.get");
    catalog.alias("wecom.calendars.get", "wecom.calendars.get_many");
    catalog.alias("wecom.schedules.get", "wecom.schedules.get_many");
    catalog.alias("wecom.schedules.add_attendees", "wecom.attendees.add");
    catalog.alias("wecom.schedules.del_attendees", "wecom.attendees.remove");
    catalog.alias("wecom.oauth.exchange_code", "wecom.users.resolve_code");
    catalog.operation(attendeesAdd());
    catalog.operation(attendeesRemove());
    catalog.operation(authAccessToken());
    catalog.operation(calendarsCreate());
    catalog.operation(calendarsDelete());
    catalog.operation(calendarsGetMany());
    catalog.operation(calendarsUpdate());
    catalog.operation(directoryDepartments());
    catalog.operation(directoryList());
    catalog.operation(directoryUsers());
    catalog.operation(messagesSendMarkdown());
    catalog.operation(messagesSendText());
    catalog.operation(schedulesBatchCreate());
    catalog.operation(schedulesCopy());
    catalog.operation(schedulesCreate());
    catalog.operation(schedulesDelete());
    catalog.operation(schedulesGetMany());
    catalog.operation(schedulesList());
    catalog.operation(schedulesListAll());
    catalog.operation(schedulesUpdate());
    catalog.operation(usersGet());
    catalog.operation(usersResolveCode());
    catalog.operation(approvalTemplatesList());
    catalog.operation(approvalsTrack());
    catalog.operation(approvalTemplatesGet());
    catalog.operation(approvalTemplatesBind());
    catalog.operation(approvalTemplatesUnbind());
    catalog.operation(approvalTemplatesCreate());
    catalog.operation(approvalTemplatesUpdate());
    catalog.operation(approvalTemplatesClone());
    catalog.operation(approvalsPrepare());
    catalog.operation(approvalsSubmit());
    catalog.operation(approvalsGet());
    catalog.operation(approvalsGetMany());
    catalog.operation(approvalsList());
    catalog.operation(approvalsCollect());
    catalog.operation(approvalsEngineGet());
    catalog.operation(approvalsEngineLaunch());
    catalog.operation(approvalsLegacyGet());
    catalog.operation(approvalFilesUpload());
    catalog.operation(approvalFilesDownload());
    catalog.operation(approvalEventsList());
    catalog.operation(approvalEventsRetry());
    catalog.operation(vacationConfig());
    catalog.operation(vacationGet());
    catalog.operation(vacationSet());
    catalog.operation(hrGetFields());
    catalog.operation(hrGetStaffInfo());
  }

  private static PlatformCredentials credentials() {
    return new PlatformCredentials(
        "wecom",
        "企业微信",
        "https://developer.work.weixin.qq.com/document/path/90664",
        "默认应用使用 WEWORK_*；命名应用统一使用 WEWORK_APP_<APP_KEY>_{CORP_ID,SECRET,AGENT_ID}，新增应用不用改代码。企微的「按系统分凭据」由 adapter 自己解析（_resolve_app），不走本清单的 scoped 机制",
        "inherit",
        false,
        true,
        "",
        "",
        "",
        List.of(
            new PlatformCredentials.Variable(
                "WEWORK_CORP_ID", "企业 ID", true, false, "我的企业 → 企业 ID", "", false, "", false),
            new PlatformCredentials.Variable(
                "WEWORK_SECRET", "应用 Secret", true, true, "自建应用的 Secret", "", false, "", false),
            new PlatformCredentials.Variable(
                "WEWORK_AGENT_ID", "AgentId", true, false, "自建应用的 AgentId", "", false, "", false),
            new PlatformCredentials.Variable(
                "WEWORK_CONTACT_SECRET",
                "通讯录 Secret",
                false,
                true,
                "拉全员通讯录（wecom.directory.list）用；不配则退回应用 Secret + simplelist 逐人补全（慢）",
                "",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "WEWORK_PROXY_BASE",
                "反代地址",
                false,
                false,
                "erp-proxy 地址，直连可留空",
                "",
                false,
                "",
                false),
            new PlatformCredentials.Variable(
                "WEWORK_PROXY_TOKEN", "反代 Token", false, true, "反代鉴权，直连可留空", "", false, "", false),
            new PlatformCredentials.Variable(
                "WEWORK_APPROVAL_SECRET",
                "审批应用 Secret",
                false,
                true,
                "优先用于审批；未设置时使用当前调用系统的企微自建应用凭据，需要审批数据权限",
                null,
                true,
                "WEWORK_{SYSTEM}_APPROVAL_SECRET",
                null),
            new PlatformCredentials.Variable(
                "WEWORK_CALLBACK_TOKEN",
                "企微回调 Token",
                false,
                true,
                "与企微后台 API 接收配置一致，用于 SHA-1 验签",
                null,
                true,
                "WEWORK_{SYSTEM}_CALLBACK_TOKEN",
                null),
            new PlatformCredentials.Variable(
                "WEWORK_CALLBACK_AES_KEY",
                "企微回调 EncodingAESKey",
                false,
                true,
                "与企微后台 API 接收配置一致，固定 43 位",
                null,
                true,
                "WEWORK_{SYSTEM}_CALLBACK_AES_KEY",
                null),
            new PlatformCredentials.Variable(
                "WEWORK_HR_SECRET",
                "花名册应用 Secret",
                false,
                true,
                "优先使用具备企微人事助手权限的应用 Secret，未设置时使用当前系统应用",
                null,
                true,
                "WEWORK_{SYSTEM}_HR_SECRET",
                null)));
  }

  private static BuiltinOperation attendeesAdd() {
    return operation("wecom.attendees.add")
        .summary("新增日程参与者")
        .quota("120/min")
        .since("2026-10")
        .category("日程管理")
        .order(30)
        .cost("none")
        .tags("write")
        .params(
            object(
                "$defs",
                object(
                    "Attendee",
                    object(
                        "additionalProperties",
                        false,
                        "properties",
                        object(
                            "userid",
                            object(
                                "description",
                                "成员 userid，区分大小写",
                                "maxLength",
                                64,
                                "minLength",
                                1,
                                "title",
                                "Userid",
                                "type",
                                "string")),
                        "required",
                        array("userid"),
                        "title",
                        "Attendee",
                        "type",
                        "object")),
                "additionalProperties",
                false,
                "properties",
                object(
                    "schedule_id",
                    object(
                        "description",
                        "日程 ID",
                        "minLength",
                        1,
                        "title",
                        "Schedule Id",
                        "type",
                        "string"),
                    "attendees",
                    object(
                        "description",
                        "要增/删的参与者，可简写 [\"zhangsan\"]",
                        "items",
                        object("$ref", "#/$defs/Attendee"),
                        "maxItems",
                        1000,
                        "minItems",
                        1,
                        "title",
                        "Attendees",
                        "type",
                        "array")),
                "required",
                array("schedule_id", "attendees"),
                "title",
                "ScheduleAttendeesParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "按日程 ID 转换参与人列表。",
                "增加参与人差异预检，避免重复通知和无效成员。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "按日程 ID 转交参与者增删。",
                "未读取原参与者、计算差异或预检可操作角色。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        222,
                        "if (key.equals(\"attendees.add\")"))))
        .build();
  }

  private static BuiltinOperation attendeesRemove() {
    return operation("wecom.attendees.remove")
        .summary("删除日程参与者")
        .quota("120/min")
        .since("2026-10")
        .category("日程管理")
        .order(40)
        .cost("none")
        .tags("write")
        .params(
            object(
                "$defs",
                object(
                    "Attendee",
                    object(
                        "additionalProperties",
                        false,
                        "properties",
                        object(
                            "userid",
                            object(
                                "description",
                                "成员 userid，区分大小写",
                                "maxLength",
                                64,
                                "minLength",
                                1,
                                "title",
                                "Userid",
                                "type",
                                "string")),
                        "required",
                        array("userid"),
                        "title",
                        "Attendee",
                        "type",
                        "object")),
                "additionalProperties",
                false,
                "properties",
                object(
                    "schedule_id",
                    object(
                        "description",
                        "日程 ID",
                        "minLength",
                        1,
                        "title",
                        "Schedule Id",
                        "type",
                        "string"),
                    "attendees",
                    object(
                        "description",
                        "要增/删的参与者，可简写 [\"zhangsan\"]",
                        "items",
                        object("$ref", "#/$defs/Attendee"),
                        "maxItems",
                        1000,
                        "minItems",
                        1,
                        "title",
                        "Attendees",
                        "type",
                        "array")),
                "required",
                array("schedule_id", "attendees"),
                "title",
                "ScheduleAttendeesParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "按日程 ID 转换移除成员。",
                "显示当前参与人及移除后结果，保护组织者等受限角色。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "按日程 ID 转交参与者增删。",
                "未读取原参与者、计算差异或预检可操作角色。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        222,
                        "if (key.equals(\"attendees.add\")"))))
        .build();
  }

  private static BuiltinOperation authAccessToken() {
    return operation("wecom.auth.access_token")
        .summary("获取应用 access_token")
        .description(
            "返回当前应用的平台 access_token（有效期 7200 秒）。⚠️ 这个操作会把平台凭据原样返回给调用方，破坏网关的凭据隔离，因此默认敏感：需要在「授权管理」里显式勾选，且每次调用都会审计。只在手工调试 / 少数必须持有 token 的场景使用，业务调用请让网关自己管 token。")
        .quota("120/min")
        .sensitive(true)
        .retries(2)
        .since("2026-10")
        .category("接入鉴权")
        .order(10)
        .cost("none")
        .tags("read", "danger")
        .params(
            object(
                "additionalProperties",
                false,
                "description",
                "获取应用 access_token。\n\n安全取舍：这个操作会把平台 token 原样返回给调用方，等于把凭据隔离掀掉一角。\n因此它默认敏感（sensitive），授权管理里要显式勾选 + 全程审计；\n并且只返回给 WEWORK_PROXY_TOKEN 已配置好的自建应用，不给第三方。",
                "properties",
                object(
                    "refresh",
                    object(
                        "default",
                        0,
                        "description",
                        "1 = 强制重新获取（默认命中缓存，直到过期前 120 秒）",
                        "enum",
                        array(0, 1),
                        "title",
                        "Refresh",
                        "type",
                        "integer")),
                "title",
                "AccessTokenParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "缓存企微应用 token，按凭据选择应用。",
                "业务方优先调用领域接口；token 仅保留必要的可信兼容场景。",
                "access_token 属于上游凭据，不能用作普通页面数据。",
                "low_level",
                List.of(),
                CapabilityReview.Depth.SUPPORTING,
                "提供应用令牌或网页授权换码的基础兼容入口。",
                "不作为业务深封完成项；业务侧优先使用领域接口和自己的登录流程。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        50,
                        "if (key.equals(\"auth.access_token\"))"),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        65,
                        "if (key.equals(\"users.resolve_code\"))"))))
        .build();
  }

  private static BuiltinOperation calendarsCreate() {
    return operation("wecom.calendars.create")
        .summary("创建日历")
        .description(
            "企微已取消「日历列表」接口，日历 ID 只能由本接口创建后返回，调用方必须自行保存。只有本应用创建的日历下的日程才读得到。set_as_default=1 可让后续建日程省略 cal_id。agentid 是旧第三方多应用套件字段；本网关是自建应用，已从凭据自动确定，故不暴露给调用方。")
        .quota("30/min")
        .since("2026-10")
        .category("日历管理")
        .order(10)
        .cost("none")
        .tags("write")
        .params(
            object(
                "$defs",
                object(
                    "CalendarShare",
                    object(
                        "additionalProperties",
                        false,
                        "description",
                        "日历通知范围成员。",
                        "properties",
                        object(
                            "userid",
                            object(
                                "maxLength",
                                64,
                                "minLength",
                                1,
                                "title",
                                "Userid",
                                "type",
                                "string"),
                            "permission",
                            object(
                                "default",
                                1,
                                "description",
                                "1 = 可查看；3 = 仅查看闲忙状态",
                                "enum",
                                array(1, 3),
                                "title",
                                "Permission",
                                "type",
                                "integer")),
                        "required",
                        array("userid"),
                        "title",
                        "CalendarShare",
                        "type",
                        "object"),
                    "PublicRange",
                    object(
                        "additionalProperties",
                        false,
                        "description",
                        "公共日历的公开范围（仅 is_public=1 时有效）。",
                        "properties",
                        object(
                            "userids",
                            object(
                                "description",
                                "公开的成员列表",
                                "items",
                                object("type", "string"),
                                "maxItems",
                                1000,
                                "title",
                                "Userids",
                                "type",
                                "array",
                                "default",
                                array()),
                            "partyids",
                            object(
                                "description",
                                "公开的部门 ID 列表",
                                "items",
                                object("type", "integer"),
                                "maxItems",
                                100,
                                "title",
                                "Partyids",
                                "type",
                                "array",
                                "default",
                                array())),
                        "title",
                        "PublicRange",
                        "type",
                        "object")),
                "additionalProperties",
                false,
                "description",
                "创建日历 —— 字段与企微官方「创建日历」一一对应。",
                "properties",
                object(
                    "summary",
                    object(
                        "description",
                        "日历标题",
                        "maxLength",
                        128,
                        "minLength",
                        1,
                        "title",
                        "Summary",
                        "type",
                        "string"),
                    "color",
                    object(
                        "default",
                        "#1A73E8",
                        "description",
                        "日历颜色，默认 #1A73E8（官方必填，网关给默认值省得调用方查色值）；全员日历不支持指定颜色",
                        "pattern",
                        "^#[0-9A-Fa-f]{6}$",
                        "title",
                        "Color",
                        "type",
                        "string"),
                    "description",
                    object(
                        "default",
                        "",
                        "description",
                        "日历描述",
                        "maxLength",
                        512,
                        "title",
                        "Description",
                        "type",
                        "string"),
                    "admins",
                    object(
                        "description",
                        "日历管理员 userid 列表，最多 3 人；必须在 shares 里",
                        "items",
                        object("type", "string"),
                        "maxItems",
                        3,
                        "title",
                        "Admins",
                        "type",
                        "array",
                        "default",
                        array()),
                    "set_as_default",
                    object(
                        "default",
                        0,
                        "description",
                        "设为该应用的默认日历；设了之后创建日程可以不传 cal_id。全员日历不支持",
                        "enum",
                        array(0, 1),
                        "title",
                        "Set As Default",
                        "type",
                        "integer"),
                    "is_public",
                    object(
                        "default",
                        0,
                        "description",
                        "是否公共日历（每人最多创建/订阅 100 个）",
                        "enum",
                        array(0, 1),
                        "title",
                        "Is Public",
                        "type",
                        "integer"),
                    "public_range",
                    object(
                        "anyOf",
                        array(object("$ref", "#/$defs/PublicRange"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "公开范围；仅 is_public=1 时有效，公共日历必须给"),
                    "is_corp_calendar",
                    object(
                        "default",
                        0,
                        "description",
                        "是否全员日历（每企业最多 20 个）；它也是公共日历，必须给 public_range",
                        "enum",
                        array(0, 1),
                        "title",
                        "Is Corp Calendar",
                        "type",
                        "integer"),
                    "shares",
                    object(
                        "description",
                        "日历通知范围成员，最多 2000 人",
                        "items",
                        object("$ref", "#/$defs/CalendarShare"),
                        "maxItems",
                        2000,
                        "title",
                        "Shares",
                        "type",
                        "array",
                        "default",
                        array()),
                    "agentid",
                    object(
                        "anyOf",
                        array(object("minimum", 0, "type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "授权方安装的应用 agentid；仅旧的第三方多应用套件需要，自建应用留空即可（网关按凭据自动带）",
                        "title",
                        "Agentid")),
                "required",
                array("summary"),
                "title",
                "CreateCalendarParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "已将所有者、权限和日历属性转换为官方请求。",
                "增加业务日历别名与 prepare，检查所有者及共享范围。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "清理日历参数并转换官方 calendar 请求。",
                "无业务日历别名、所有者解析、共享权限预检和差异更新。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        139,
                        "if (key.startsWith(\"calendars.\"))"))))
        .build();
  }

  private static BuiltinOperation calendarsDelete() {
    return operation("wecom.calendars.delete")
        .summary("删除日历")
        .description("日历下的日程需先删掉，否则上游会拒绝。")
        .quota("30/min")
        .since("2026-10")
        .category("日历管理")
        .order(40)
        .cost("none")
        .tags("write")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "cal_id",
                    object("maxLength", 64, "minLength", 1, "title", "Cal Id", "type", "string")),
                "required",
                array("cal_id"),
                "title",
                "DeleteCalendarParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "按 cal_id 删除日历。",
                "增加关联日程预检，显式展示影响范围。",
                "不能默认联动删除业务记录。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "按 cal_id 删除。",
                "没有关联日程影响预检。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        162,
                        "if (action.equals(\"delete\"))"))))
        .build();
  }

  private static BuiltinOperation calendarsGetMany() {
    return operation("wecom.calendars.get_many")
        .summary("查询日历详情")
        .description("按 cal_id_list 查详情（不是列表接口，企微没有枚举能力），一次最多 1000 个。")
        .quota("300/min")
        .retries(3)
        .since("2026-10")
        .category("日历管理")
        .order(20)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "cal_id_list",
                    object(
                        "description",
                        "日历 ID 列表，只能查本应用创建的日历",
                        "items",
                        object("type", "string"),
                        "maxItems",
                        1000,
                        "minItems",
                        1,
                        "title",
                        "Cal Id List",
                        "type",
                        "array")),
                "required",
                array("cal_id_list"),
                "title",
                "GetCalendarsParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "支持批量读取日历详情。",
                "归一 owner、共享范围及逐个失败信息。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "批量读取并增加 id 别名，主体保留官方字段。",
                "无逐个错误隔离和所有者/共享范围的业务解释。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        141,
                        "if (action.equals(\"get_many\"))"))))
        .build();
  }

  private static BuiltinOperation calendarsUpdate() {
    return operation("wecom.calendars.update")
        .summary("更新日历")
        .description("企微更新日历是覆盖式，不是增量式；summary / color 必须一起传。skip_public_range=1 可保留原公开范围。")
        .quota("30/min")
        .since("2026-10")
        .category("日历管理")
        .order(30)
        .cost("none")
        .tags("write")
        .params(
            object(
                "$defs",
                object(
                    "CalendarShare",
                    object(
                        "additionalProperties",
                        false,
                        "description",
                        "日历通知范围成员。",
                        "properties",
                        object(
                            "userid",
                            object(
                                "maxLength",
                                64,
                                "minLength",
                                1,
                                "title",
                                "Userid",
                                "type",
                                "string"),
                            "permission",
                            object(
                                "default",
                                1,
                                "description",
                                "1 = 可查看；3 = 仅查看闲忙状态",
                                "enum",
                                array(1, 3),
                                "title",
                                "Permission",
                                "type",
                                "integer")),
                        "required",
                        array("userid"),
                        "title",
                        "CalendarShare",
                        "type",
                        "object"),
                    "PublicRange",
                    object(
                        "additionalProperties",
                        false,
                        "description",
                        "公共日历的公开范围（仅 is_public=1 时有效）。",
                        "properties",
                        object(
                            "userids",
                            object(
                                "description",
                                "公开的成员列表",
                                "items",
                                object("type", "string"),
                                "maxItems",
                                1000,
                                "title",
                                "Userids",
                                "type",
                                "array",
                                "default",
                                array()),
                            "partyids",
                            object(
                                "description",
                                "公开的部门 ID 列表",
                                "items",
                                object("type", "integer"),
                                "maxItems",
                                100,
                                "title",
                                "Partyids",
                                "type",
                                "array",
                                "default",
                                array())),
                        "title",
                        "PublicRange",
                        "type",
                        "object")),
                "additionalProperties",
                false,
                "description",
                "更新日历。注意官方接口是覆盖式，必填的日历标题/颜色要一起带上。",
                "properties",
                object(
                    "cal_id",
                    object("maxLength", 64, "minLength", 1, "title", "Cal Id", "type", "string"),
                    "summary",
                    object("maxLength", 128, "minLength", 1, "title", "Summary", "type", "string"),
                    "color",
                    object("pattern", "^#[0-9A-Fa-f]{6}$", "title", "Color", "type", "string"),
                    "description",
                    object(
                        "default", "", "maxLength", 512, "title", "Description", "type", "string"),
                    "admins",
                    object(
                        "items",
                        object("type", "string"),
                        "maxItems",
                        3,
                        "title",
                        "Admins",
                        "type",
                        "array",
                        "default",
                        array()),
                    "public_range",
                    object(
                        "anyOf",
                        array(object("$ref", "#/$defs/PublicRange"), object("type", "null")),
                        "default",
                        null),
                    "shares",
                    object(
                        "items",
                        object("$ref", "#/$defs/CalendarShare"),
                        "maxItems",
                        2000,
                        "title",
                        "Shares",
                        "type",
                        "array",
                        "default",
                        array()),
                    "skip_public_range",
                    object(
                        "default",
                        0,
                        "description",
                        "1 = 不更新公开范围；默认 0，会覆盖更新公开范围",
                        "enum",
                        array(0, 1),
                        "title",
                        "Skip Public Range",
                        "type",
                        "integer")),
                "required",
                array("cal_id", "summary", "color"),
                "title",
                "UpdateCalendarParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "把日历属性映射为更新请求。",
                "增加差异预览，区分不修改与清空字段。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "清理日历参数并转换官方 calendar 请求。",
                "无业务日历别名、所有者解析、共享权限预检和差异更新。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        139,
                        "if (key.startsWith(\"calendars.\"))"))))
        .build();
  }

  private static BuiltinOperation directoryDepartments() {
    return operation("wecom.directory.departments")
        .summary("查询部门列表（按 ID / 路径 / 名称，单个/多个）")
        .description(
            "查询企业部门。不传任何条件返回整个部门树。\n三种查法（可叠加）：\n- department_ids：按 ID 查（知道 ID 的老调用方），不存在的在 missing_departments 里列出；\n- paths：不知道 ID 时用这个——含 / 按完整路径精确匹配（如「畅韵科技/采购部」），不含 / 按部门名称精确匹配，同名部门全部命中（resolved_paths 里能看到命中了哪些），查不到的在 missing_paths 里列出。\n每个部门带完整层级路径 path，调用方不用自己按 parentid 拼树。")
        .retries(3)
        .since("2026-10")
        .category("通讯录")
        .order(15)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "description",
                "查询部门（不传任何条件返回整个部门树）。\n\n两种查法可叠加：department_ids（知道 ID 的老调用方）+ paths（不知道 ID 的调用方，\n完整路径或部门名称都认）。同名部门会全部命中，resolved_paths 里能看到命中了哪些。",
                "properties",
                object(
                    "department_ids",
                    object(
                        "anyOf",
                        array(
                            object(
                                "items",
                                object("minimum", 1, "type", "integer"),
                                "maxItems",
                                50,
                                "type",
                                "array"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "要查的部门 ID 列表；不存在的 ID 在 missing_departments 里列出",
                        "title",
                        "Department Ids"),
                    "paths",
                    object(
                        "anyOf",
                        array(
                            object(
                                "items",
                                object("maxLength", 128, "minLength", 1, "type", "string"),
                                "maxItems",
                                50,
                                "type",
                                "array"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "按路径/名称查，不知道部门 ID 时用这个。含 / 按完整路径精确匹配（如「畅韵科技/采购部」）；不含 / 按部门名称精确匹配，同名部门全部命中（见 resolved_paths）。查不到的在 missing_paths 里列出",
                        "title",
                        "Paths"),
                    "app",
                    object(
                        "anyOf",
                        array(
                            object("pattern", "^[a-z][a-z0-9_-]{0,31}$", "type", "string"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "企业微信应用编码；通常省略，由调用系统身份自动选择",
                        "title",
                        "App")),
                "title",
                "DepartmentsParams",
                "type",
                "object"))
        .response(
            object(
                "type",
                "object",
                "description",
                "",
                "properties",
                object(
                    "resolved_paths",
                    object(
                        "type",
                        "array",
                        "description",
                        "已成功解析的部门路径",
                        "items",
                        object(
                            "type",
                            "object",
                            "description",
                            "",
                            "properties",
                            object(
                                "path",
                                object("type", "string", "description", "输入部门路径"),
                                "id",
                                object("type", "integer", "description", "唯一匹配的部门 ID")))),
                    "missing_paths",
                    object(
                        "type",
                        "array",
                        "description",
                        "不存在或有歧义的部门路径",
                        "items",
                        object("type", "string", "description", "无法唯一匹配的路径")),
                    "app",
                    object("type", "string", "description", "实际使用的企微应用配置标识"),
                    "counts",
                    object(
                        "type",
                        "object",
                        "description",
                        "",
                        "properties",
                        object(
                            "departments",
                            object("type", "integer", "description", "部门数量"),
                            "users",
                            object("type", "integer", "description", "返回成员数量"),
                            "missing",
                            object("type", "integer", "description", "未找到目标数量"),
                            "missing_paths",
                            object("type", "integer", "description", "无法匹配的部门路径数量"))),
                    "departments",
                    object(
                        "type",
                        "array",
                        "description",
                        "匹配的部门列表",
                        "items",
                        object(
                            "type",
                            "object",
                            "description",
                            "",
                            "properties",
                            object(
                                "id",
                                object("type", "integer", "description", "部门 ID"),
                                "name",
                                object("type", "string", "description", "部门名称"),
                                "parent_id",
                                object("type", "integer", "description", "上级部门 ID"),
                                "path",
                                object("type", "string", "description", "从根部门到当前部门的名称路径"),
                                "order",
                                object("type", "integer", "description", "上游部门排序值")))),
                    "missing_departments",
                    object(
                        "type",
                        "array",
                        "description",
                        "指定部门中未找到的 ID",
                        "items",
                        object("type", "integer", "description", "未找到的部门 ID")))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "已有按 ID/名称/完整路径查询与去重，支持单个/多个。",
                "将未找到与歧义路径的原因分开，供表单部门选择使用。",
                "当前二者都记录为 missing_paths；同名部门宜通过路径或 ID 区分。",
                "ready",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "组合部门树、路径/名称解析、去重与成员补齐，并按凭据能力选择读取方式。",
                "未找到和歧义路径都进 missing_paths；大企业边界和部分失败还可细化。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        385,
                        "private Object directory("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        538,
                        "private String departmentPath("))))
        .build();
  }

  private static BuiltinOperation directoryList() {
    return operation("wecom.directory.list")
        .summary("获取企业通讯录（部门树 + 全员）")
        .description(
            "一次拉全企业通讯录：部门树 + 成员列表，字段已规范化（见下）。配了通讯录 Secret（WEWORK_CONTACT_SECRET）走 user/list 快路径，一次拿全量；没配则用应用 Secret：先 simplelist 再逐人补详情，组织大了会明显变慢（与 Portal 现网相同）。部门树始终返回全量——成员的 department_names / path 依赖完整树解析；department_id + fetch_child 只圈定成员范围。响应里的 mode 标注本次实际走了哪条路径，排查慢/权限问题时先看它。已做连接池复用 + 补详情并发（20），同样的参数 60 秒内直接返回缓存，不会重复打上游。配额只有 10/min：应用 Secret 路径一次调用会放大成 1+N 次上游请求，别当热接口用。\n规范化说明（三个通讯录操作一致）：\n- 成员：userid / name / alias / position / department_ids（主部门排第一）/ main_department / main_department_name / department_names / mobile / telephone / email / avatar / status（上游原值）/ active（1=已激活 的布尔）\n- 部门：id / name / parent_id / path（完整层级路径，如「畅韵科技/运营部/亚马逊运营部」）/ order\n- 上游噪音字段（errcode/extattr/external_profile/hide_mobile 等）不透传；需要时再申请加，避免 schema 被上游接口形状牵着走")
        .quota("10/min")
        .timeout(180)
        .since("2026-10")
        .category("通讯录")
        .order(20)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "description",
                "获取企业通讯录：部门树 + 成员列表，一次拉全。\n\n上游是 department/list + user/list/simplelist 等多个接口的拼接，\n网关合并成一次调用并做字段规范化，调用方拿到的就是「能直接用的通讯录」。",
                "properties",
                object(
                    "department_id",
                    object(
                        "default",
                        1,
                        "description",
                        "从哪个部门开始拉成员，1 = 根部门（整个企业）",
                        "minimum",
                        1,
                        "title",
                        "Department Id",
                        "type",
                        "integer"),
                    "fetch_child",
                    object(
                        "default",
                        1,
                        "description",
                        "1 = 递归包含子部门成员；0 = 只拉当前部门",
                        "enum",
                        array(0, 1),
                        "title",
                        "Fetch Child",
                        "type",
                        "integer"),
                    "app",
                    object(
                        "anyOf",
                        array(
                            object("pattern", "^[a-z][a-z0-9_-]{0,31}$", "type", "string"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "企业微信应用编码；通常省略，由调用系统身份自动选择",
                        "title",
                        "App")),
                "title",
                "DirectoryParams",
                "type",
                "object"))
        .response(
            object(
                "type",
                "object",
                "description",
                "",
                "properties",
                object(
                    "resolved_paths",
                    object(
                        "type",
                        "array",
                        "description",
                        "已成功解析的部门路径",
                        "items",
                        object(
                            "type",
                            "object",
                            "description",
                            "",
                            "properties",
                            object(
                                "path",
                                object("type", "string", "description", "输入部门路径"),
                                "id",
                                object("type", "integer", "description", "唯一匹配的部门 ID")))),
                    "missing_paths",
                    object(
                        "type",
                        "array",
                        "description",
                        "不存在或有歧义的部门路径",
                        "items",
                        object("type", "string", "description", "无法唯一匹配的路径")),
                    "app",
                    object("type", "string", "description", "实际使用的企微应用配置标识"),
                    "counts",
                    object(
                        "type",
                        "object",
                        "description",
                        "",
                        "properties",
                        object(
                            "departments",
                            object("type", "integer", "description", "部门数量"),
                            "users",
                            object("type", "integer", "description", "返回成员数量"),
                            "missing",
                            object("type", "integer", "description", "未找到目标数量"),
                            "missing_paths",
                            object("type", "integer", "description", "无法匹配的部门路径数量"))),
                    "users",
                    object(
                        "type",
                        "array",
                        "description",
                        "去重后的成员资料，不包含直属领导推断",
                        "items",
                        object(
                            "type",
                            "object",
                            "description",
                            "",
                            "properties",
                            object(
                                "userid",
                                object("type", "string", "description", "企微成员唯一标识"),
                                "name",
                                object("type", "string", "description", "成员显示姓名"),
                                "alias",
                                object("type", "string", "description", "成员别名"),
                                "position",
                                object("type", "string", "description", "职位名称"),
                                "main_department",
                                object("type", "integer", "description", "主部门 ID；来源可能为空"),
                                "main_department_name",
                                object("type", "string", "description", "主部门名称"),
                                "mobile",
                                object("type", "string", "description", "手机号；是否返回取决于上游权限"),
                                "telephone",
                                object("type", "string", "description", "座机号码"),
                                "email",
                                object("type", "string", "description", "电子邮箱"),
                                "avatar",
                                object("type", "string", "description", "头像地址"),
                                "status",
                                object("type", "integer", "description", "企微成员状态，1 为已激活"),
                                "active",
                                object("type", "boolean", "description", "status 是否为 1"),
                                "department_ids",
                                object(
                                    "type",
                                    "array",
                                    "description",
                                    "所属部门 ID，主部门优先",
                                    "items",
                                    object("type", "integer", "description", "部门 ID")),
                                "department_names",
                                object(
                                    "type",
                                    "array",
                                    "description",
                                    "与 department_ids 对应的部门名称",
                                    "items",
                                    object("type", "string", "description", "部门名称"))))),
                    "missing",
                    object(
                        "type",
                        "array",
                        "description",
                        "指定成员中未找到的 ID",
                        "items",
                        object("type", "string", "description", "未找到的成员 ID")),
                    "mode",
                    object("type", "string", "description", "实际读取通讯录的授权方式"),
                    "departments",
                    object(
                        "type",
                        "array",
                        "description",
                        "组织部门树节点，不包含经理关系",
                        "items",
                        object(
                            "type",
                            "object",
                            "description",
                            "",
                            "properties",
                            object(
                                "id",
                                object("type", "integer", "description", "部门 ID"),
                                "name",
                                object("type", "string", "description", "部门名称"),
                                "parent_id",
                                object("type", "integer", "description", "上级部门 ID"),
                                "path",
                                object("type", "string", "description", "从根部门到当前部门的名称路径"),
                                "order",
                                object("type", "integer", "description", "上游部门排序值")))))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "合并部门树和成员，规范用户显示字段。",
                "大企业增加有界输出、按部门选择与可见范围说明。",
                "结果受通讯录权限限制，不代表企业全部成员。",
                "ready",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "组合部门树、路径/名称解析、去重与成员补齐，并按凭据能力选择读取方式。",
                "未找到和歧义路径都进 missing_paths；大企业边界和部分失败还可细化。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        385,
                        "private Object directory("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        538,
                        "private String departmentPath("))))
        .build();
  }

  private static BuiltinOperation directoryUsers() {
    return operation("wecom.directory.users")
        .summary("查询成员（按账号或按部门，均支持单个/多个）")
        .description(
            "查询企业成员，返回规范化字段（见 wecom.directory.list 的字段说明）。\n两种查法二选一：\n① userids 按账号查（单个/多个，一次最多 100 个，不存在的账号在 missing 里列出）；\n② 按部门查（单个/多个）：department_ids 按 ID、paths 按路径/名称（不知道 ID 时用，写法同 wecom.directory.departments），两者可叠加；fetch_child=1 递归子部门。\n多个部门/账号会并发查上游，结果按 userid 去重。同样的参数 60 秒内直接返回缓存，不会重复打上游。\n配额只有 10/min：按账号查是逐人调上游，别当热接口用。\nmode 标注本次走的路径（contact_secret 快 / app_secret_simplelist / user_get 逐人）。")
        .quota("10/min")
        .timeout(180)
        .since("2026-10")
        .category("通讯录")
        .order(25)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "description",
                "查询成员：按账号（userids）或按部门（department_ids / paths）查，两种二选一，都支持单个/多个。\n\n按部门查时两种写法可叠加：department_ids（知道 ID）+ paths（不知道 ID，\n完整路径或部门名称都认）。",
                "properties",
                object(
                    "userids",
                    object(
                        "anyOf",
                        array(
                            object(
                                "items",
                                object("maxLength", 64, "minLength", 1, "type", "string"),
                                "maxItems",
                                100,
                                "type",
                                "array"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "按账号查（单个或多个）。不存在的账号在 missing 里列出。与部门查法二选一",
                        "title",
                        "Userids"),
                    "department_ids",
                    object(
                        "anyOf",
                        array(
                            object(
                                "items",
                                object("minimum", 1, "type", "integer"),
                                "maxItems",
                                50,
                                "type",
                                "array"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "按部门 ID 查（单个或多个）。与 userids 二选一",
                        "title",
                        "Department Ids"),
                    "paths",
                    object(
                        "anyOf",
                        array(
                            object(
                                "items",
                                object("maxLength", 128, "minLength", 1, "type", "string"),
                                "maxItems",
                                50,
                                "type",
                                "array"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "按部门路径/名称查（单个或多个），不知道部门 ID 时用这个。含 / 按完整路径精确匹配（如「畅韵科技/采购部」）；不含 / 按部门名称精确匹配，同名部门全部命中（见 resolved_paths）。与 userids 二选一",
                        "title",
                        "Paths"),
                    "fetch_child",
                    object(
                        "default",
                        0,
                        "description",
                        "按部门查时：1 = 递归子部门成员；0 = 只查部门直属成员",
                        "enum",
                        array(0, 1),
                        "title",
                        "Fetch Child",
                        "type",
                        "integer"),
                    "app",
                    object(
                        "anyOf",
                        array(
                            object("pattern", "^[a-z][a-z0-9_-]{0,31}$", "type", "string"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "企业微信应用编码；通常省略，由调用系统身份自动选择",
                        "title",
                        "App")),
                "title",
                "UsersParams",
                "type",
                "object"))
        .response(
            object(
                "type",
                "object",
                "description",
                "",
                "properties",
                object(
                    "resolved_paths",
                    object(
                        "type",
                        "array",
                        "description",
                        "已成功解析的部门路径",
                        "items",
                        object(
                            "type",
                            "object",
                            "description",
                            "",
                            "properties",
                            object(
                                "path",
                                object("type", "string", "description", "输入部门路径"),
                                "id",
                                object("type", "integer", "description", "唯一匹配的部门 ID")))),
                    "missing_paths",
                    object(
                        "type",
                        "array",
                        "description",
                        "不存在或有歧义的部门路径",
                        "items",
                        object("type", "string", "description", "无法唯一匹配的路径")),
                    "app",
                    object("type", "string", "description", "实际使用的企微应用配置标识"),
                    "counts",
                    object(
                        "type",
                        "object",
                        "description",
                        "",
                        "properties",
                        object(
                            "departments",
                            object("type", "integer", "description", "部门数量"),
                            "users",
                            object("type", "integer", "description", "返回成员数量"),
                            "missing",
                            object("type", "integer", "description", "未找到目标数量"),
                            "missing_paths",
                            object("type", "integer", "description", "无法匹配的部门路径数量"))),
                    "users",
                    object(
                        "type",
                        "array",
                        "description",
                        "去重后的成员资料，不包含直属领导推断",
                        "items",
                        object(
                            "type",
                            "object",
                            "description",
                            "",
                            "properties",
                            object(
                                "userid",
                                object("type", "string", "description", "企微成员唯一标识"),
                                "name",
                                object("type", "string", "description", "成员显示姓名"),
                                "alias",
                                object("type", "string", "description", "成员别名"),
                                "position",
                                object("type", "string", "description", "职位名称"),
                                "main_department",
                                object("type", "integer", "description", "主部门 ID；来源可能为空"),
                                "main_department_name",
                                object("type", "string", "description", "主部门名称"),
                                "mobile",
                                object("type", "string", "description", "手机号；是否返回取决于上游权限"),
                                "telephone",
                                object("type", "string", "description", "座机号码"),
                                "email",
                                object("type", "string", "description", "电子邮箱"),
                                "avatar",
                                object("type", "string", "description", "头像地址"),
                                "status",
                                object("type", "integer", "description", "企微成员状态，1 为已激活"),
                                "active",
                                object("type", "boolean", "description", "status 是否为 1"),
                                "department_ids",
                                object(
                                    "type",
                                    "array",
                                    "description",
                                    "所属部门 ID，主部门优先",
                                    "items",
                                    object("type", "integer", "description", "部门 ID")),
                                "department_names",
                                object(
                                    "type",
                                    "array",
                                    "description",
                                    "与 department_ids 对应的部门名称",
                                    "items",
                                    object("type", "string", "description", "部门名称"))))),
                    "missing",
                    object(
                        "type",
                        "array",
                        "description",
                        "指定成员中未找到的 ID",
                        "items",
                        object("type", "string", "description", "未找到的成员 ID")),
                    "mode",
                    object("type", "string", "description", "实际读取通讯录的授权方式"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "支持成员账号或部门列表，补齐部门名和路径。",
                "作为审批申请人与流程节点的选择能力，保留唯一性检查。",
                "调用方仍需单独拥有通讯录权限。",
                "ready",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "组合部门树、路径/名称解析、去重与成员补齐，并按凭据能力选择读取方式。",
                "未找到和歧义路径都进 missing_paths；大企业边界和部分失败还可细化。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        385,
                        "private Object directory("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        538,
                        "private String departmentPath("))))
        .build();
  }

  private static BuiltinOperation messagesSendMarkdown() {
    return operation("wecom.messages.send_markdown")
        .summary("发送应用 markdown 消息")
        .quota("120/min")
        .idempotent(true)
        .retries(2)
        .since("2026-10")
        .category("消息")
        .order(20)
        .cost("none")
        .tags("write")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "safe",
                    object(
                        "default",
                        0,
                        "description",
                        "是否是保密消息，0 否 1 是；保密消息不允许分享",
                        "enum",
                        array(0, 1),
                        "title",
                        "Safe",
                        "type",
                        "integer"),
                    "enable_id_trans",
                    object(
                        "default",
                        0,
                        "description",
                        "是否开启 id 转译（第三方平台才需要）",
                        "enum",
                        array(0, 1),
                        "title",
                        "Enable Id Trans",
                        "type",
                        "integer"),
                    "enable_duplicate_check",
                    object(
                        "default",
                        0,
                        "description",
                        "是否开启重复消息检查，0 否 1 是",
                        "enum",
                        array(0, 1),
                        "title",
                        "Enable Duplicate Check",
                        "type",
                        "integer"),
                    "duplicate_check_interval",
                    object(
                        "default",
                        1800,
                        "description",
                        "重复消息检查的时间间隔（秒），仅上面为 1 时有效",
                        "maximum",
                        14400,
                        "minimum",
                        0,
                        "title",
                        "Duplicate Check Interval",
                        "type",
                        "integer"),
                    "content",
                    object(
                        "description",
                        "markdown 正文，最多 4096 字节",
                        "maxLength",
                        4096,
                        "minLength",
                        1,
                        "title",
                        "Content",
                        "type",
                        "string"),
                    "to_users",
                    object(
                        "description",
                        "成员账号；只发一个人时也可以直接写 \"zhangsan\"",
                        "items",
                        object("type", "string"),
                        "title",
                        "To Users",
                        "type",
                        "array",
                        "default",
                        array()),
                    "app",
                    object(
                        "anyOf",
                        array(
                            object("pattern", "^[a-z][a-z0-9_-]{0,31}$", "type", "string"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "企业微信应用编码；通常省略，由调用系统身份自动选择",
                        "title",
                        "App")),
                "required",
                array("content"),
                "title",
                "SendMarkdownParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "按应用、接收者构建 markdown 消息。",
                "增加模板变量预检、分批收件人与 invaliduser 等部分失败归一。",
                "不能因网关 200 就认为所有收件人均成功。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "构建应用消息并拼接接收成员/部门/标签。",
                "返回仍有官方 invaliduser 等字段；未做业务模板、收件人解析和投递结果统一。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        67,
                        "if (key.startsWith(\"messages.\"))"))))
        .build();
  }

  private static BuiltinOperation messagesSendText() {
    return operation("wecom.messages.send_text")
        .summary("发送应用文本消息")
        .description("发给成员 / 部门 / 标签。内容上限 2048 字节。应用默认按已认证系统选择；可传 app 明确指定同名应用。")
        .quota("120/min")
        .idempotent(true)
        .retries(2)
        .since("2026-10")
        .category("消息")
        .order(10)
        .cost("none")
        .tags("write")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "safe",
                    object(
                        "default",
                        0,
                        "description",
                        "是否是保密消息，0 否 1 是；保密消息不允许分享",
                        "enum",
                        array(0, 1),
                        "title",
                        "Safe",
                        "type",
                        "integer"),
                    "enable_id_trans",
                    object(
                        "default",
                        0,
                        "description",
                        "是否开启 id 转译（第三方平台才需要）",
                        "enum",
                        array(0, 1),
                        "title",
                        "Enable Id Trans",
                        "type",
                        "integer"),
                    "enable_duplicate_check",
                    object(
                        "default",
                        0,
                        "description",
                        "是否开启重复消息检查，0 否 1 是",
                        "enum",
                        array(0, 1),
                        "title",
                        "Enable Duplicate Check",
                        "type",
                        "integer"),
                    "duplicate_check_interval",
                    object(
                        "default",
                        1800,
                        "description",
                        "重复消息检查的时间间隔（秒），仅上面为 1 时有效",
                        "maximum",
                        14400,
                        "minimum",
                        0,
                        "title",
                        "Duplicate Check Interval",
                        "type",
                        "integer"),
                    "content",
                    object(
                        "description",
                        "消息正文，最多 2048 字节",
                        "maxLength",
                        2048,
                        "minLength",
                        1,
                        "title",
                        "Content",
                        "type",
                        "string"),
                    "to_users",
                    object(
                        "description",
                        "成员账号，如 [\"zhangsan\"]；只发一个人时也可以直接写 \"zhangsan\"",
                        "items",
                        object("type", "string"),
                        "title",
                        "To Users",
                        "type",
                        "array",
                        "default",
                        array()),
                    "to_party",
                    object(
                        "description",
                        "部门 ID",
                        "items",
                        object("type", "string"),
                        "title",
                        "To Party",
                        "type",
                        "array",
                        "default",
                        array()),
                    "to_tags",
                    object(
                        "description",
                        "标签",
                        "items",
                        object("type", "string"),
                        "title",
                        "To Tags",
                        "type",
                        "array",
                        "default",
                        array()),
                    "app",
                    object(
                        "anyOf",
                        array(
                            object("pattern", "^[a-z][a-z0-9_-]{0,31}$", "type", "string"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "企业微信应用编码；通常省略，由调用系统身份自动选择",
                        "title",
                        "App")),
                "required",
                array("content"),
                "title",
                "SendTextParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "按应用、接收者构建文本消息。",
                "与 markdown 统一为通知业务层，增加模板和每类无效接收人结果。",
                "发送超时需核对，不自动更换业务编号重发。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "构建应用消息并拼接接收成员/部门/标签。",
                "返回仍有官方 invaliduser 等字段；未做业务模板、收件人解析和投递结果统一。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        67,
                        "if (key.startsWith(\"messages.\"))"))))
        .build();
  }

  private static BuiltinOperation schedulesBatchCreate() {
    return operation("wecom.schedules.batch_create")
        .summary("批量创建日程（部分成功不回滚）")
        .description(
            "一次最多 100 条，默认并发 3。返回 created / failed / summary，失败的不会影响成功的（企微没有事务），按 index 对应回你的入参顺序。")
        .quota("120/min")
        .since("2026-10")
        .category("日程管理")
        .order(16)
        .cost("none")
        .tags("write")
        .params(
            object(
                "additionalProperties",
                false,
                "description",
                "一次建多个日程：部分成功不回滚（企微没有事务）。\n\n每条单独校验：某一条参数不对只会在 ``failed`` 里出现，不影响其他条。",
                "properties",
                object(
                    "items",
                    object(
                        "description",
                        "要创建的日程列表，最多 100 条。每条字段与 wecom.schedules.create 完全一致，例如 {\"summary\":\"周会\",\"start_at\":\"2026-10-22 14:00\",\"end_at\":\"2026-10-22 15:00\"}",
                        "items",
                        object("additionalProperties", true, "type", "object"),
                        "maxItems",
                        100,
                        "minItems",
                        1,
                        "title",
                        "Items",
                        "type",
                        "array"),
                    "concurrency",
                    object(
                        "default",
                        3,
                        "description",
                        "并发数，默认 3；企微对写接口有并发限制，别调太高",
                        "maximum",
                        10,
                        "minimum",
                        1,
                        "title",
                        "Concurrency",
                        "type",
                        "integer")),
                "required",
                array("items"),
                "title",
                "BatchCreateSchedulesParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "已有批量创建、逐项结果，部分成功不回滚。",
                "增加每项稳定外部编号和预检，支持只重试失败项。",
                "批次不是事务，调用方必须处理部分成功。",
                "ready",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "逐项规范化和创建，按索引返回成功、失败及批次摘要。",
                "部分成功不回滚，每项业务编号和只重试失败项的持久编排仍需补充。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        91,
                        "if (key.equals(\"schedules.batch_create\"))"))))
        .build();
  }

  private static BuiltinOperation schedulesCopy() {
    return operation("wecom.schedules.copy")
        .summary("复制日程（可换日历 / 换时间）")
        .description("保留标题、描述、地点、参与人；不填 cal_id 就复制到原日历，不填 start_at/end_at 就沿用原时间（常用于「每周例会」快速建下一次）。")
        .since("2026-10")
        .category("日程管理")
        .order(46)
        .cost("none")
        .tags("write")
        .params(
            object(
                "additionalProperties",
                false,
                "description",
                "复制日程：保留标题/描述/地点/参与人，时间与日历可覆盖。",
                "properties",
                object(
                    "schedule_id",
                    object(
                        "description",
                        "源日程 ID",
                        "minLength",
                        1,
                        "title",
                        "Schedule Id",
                        "type",
                        "string"),
                    "cal_id",
                    object(
                        "anyOf",
                        array(object("maxLength", 64, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "目标日历；不填就复制到原日历",
                        "title",
                        "Cal Id"),
                    "summary",
                    object(
                        "anyOf",
                        array(object("maxLength", 128, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "新标题；不填就是「原标题（副本）」",
                        "title",
                        "Summary"),
                    "start_at",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "新的开始时间，如 `2026-10-22 14:00`；不填沿用原时间",
                        "title",
                        "Start At"),
                    "end_at",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "新的结束时间",
                        "title",
                        "End At")),
                "required",
                array("schedule_id"),
                "title",
                "CopyScheduleParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "读取原日程，可替换日历和时间后创建。",
                "增加时间偏移表达和复制内容预览，明确通知参与人。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "读取源日程，覆盖目标日历/时间并自动创建副本。",
                "源返回字段复制到创建请求，仍宜采用可写字段白名单和复制预览。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        114,
                        "if (key.equals(\"schedules.copy\"))"))))
        .build();
  }

  private static BuiltinOperation schedulesCreate() {
    return operation("wecom.schedules.create")
        .summary("创建日程")
        .description(
            "在指定日历下建日程。admins 必须同时出现在 attendees 中（网关已做 422 前置校验）。cal_id 必须是本应用创建的日历；省略则落到应用默认日历。agentid 是旧第三方多应用套件字段；自建应用场景由网关凭据自动确定，故不暴露。")
        .quota("120/min")
        .since("2026-10")
        .category("日程管理")
        .order(10)
        .cost("none")
        .tags("write")
        .params(
            object(
                "$defs",
                object(
                    "Attendee",
                    object(
                        "additionalProperties",
                        false,
                        "properties",
                        object(
                            "userid",
                            object(
                                "description",
                                "成员 userid，区分大小写",
                                "maxLength",
                                64,
                                "minLength",
                                1,
                                "title",
                                "Userid",
                                "type",
                                "string")),
                        "required",
                        array("userid"),
                        "title",
                        "Attendee",
                        "type",
                        "object"),
                    "Reminders",
                    object(
                        "additionalProperties",
                        false,
                        "description",
                        "提醒与重复规则。字段含义见企微「创建日程」文档，这里只做取值收口。",
                        "properties",
                        object(
                            "is_remind",
                            object(
                                "default",
                                0,
                                "description",
                                "是否需要提醒",
                                "enum",
                                array(0, 1),
                                "title",
                                "Is Remind",
                                "type",
                                "integer"),
                            "is_repeat",
                            object(
                                "default",
                                0,
                                "description",
                                "是否重复日程",
                                "enum",
                                array(0, 1),
                                "title",
                                "Is Repeat",
                                "type",
                                "integer"),
                            "remind_before_event_secs",
                            object(
                                "anyOf",
                                array(
                                    object(
                                        "enum", array(0, 300, 900, 3600, 86400), "type", "integer"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "开始前多少秒提醒；与 remind_time_diffs 只生效一个",
                                "title",
                                "Remind Before Event Secs"),
                            "remind_time_diffs",
                            object(
                                "anyOf",
                                array(
                                    object("items", object("type", "integer"), "type", "array"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "相对开始时间的秒差，如 [0,-3600]；有值且非空时优先于 remind_before_event_secs",
                                "title",
                                "Remind Time Diffs"),
                            "repeat_type",
                            object(
                                "anyOf",
                                array(
                                    object("enum", array(0, 1, 2, 5, 7), "type", "integer"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "0 每日 / 1 每周 / 2 每月 / 5 每年 / 7 工作日",
                                "title",
                                "Repeat Type"),
                            "repeat_until",
                            object(
                                "anyOf",
                                array(
                                    object("minimum", 0, "type", "integer"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "重复结束时间戳，0 表示一直重复",
                                "title",
                                "Repeat Until"),
                            "is_custom_repeat",
                            object(
                                "anyOf",
                                array(
                                    object("enum", array(0, 1), "type", "integer"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "是否自定义重复",
                                "title",
                                "Is Custom Repeat"),
                            "repeat_interval",
                            object(
                                "anyOf",
                                array(
                                    object("minimum", 1, "type", "integer"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "重复间隔，仅自定义重复有效",
                                "title",
                                "Repeat Interval"),
                            "repeat_day_of_week",
                            object(
                                "anyOf",
                                array(
                                    object("items", object("type", "integer"), "type", "array"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "周几重复，1~7 = 周一~周日",
                                "title",
                                "Repeat Day Of Week"),
                            "repeat_day_of_month",
                            object(
                                "anyOf",
                                array(
                                    object("items", object("type", "integer"), "type", "array"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "每月哪几天重复，1~31",
                                "title",
                                "Repeat Day Of Month"),
                            "timezone",
                            object(
                                "default",
                                8,
                                "description",
                                "时区偏移小时数，北京时间为 8",
                                "maximum",
                                12,
                                "minimum",
                                -12,
                                "title",
                                "Timezone",
                                "type",
                                "integer")),
                        "title",
                        "Reminders",
                        "type",
                        "object")),
                "additionalProperties",
                false,
                "properties",
                object(
                    "cal_id",
                    object(
                        "anyOf",
                        array(object("maxLength", 64, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "必须是本应用创建的日历 ID；不传则落到应用默认日历（需先设过 set_as_default）",
                        "title",
                        "Cal Id"),
                    "summary",
                    object(
                        "default",
                        "新建事件",
                        "description",
                        "日程标题",
                        "maxLength",
                        128,
                        "title",
                        "Summary",
                        "type",
                        "string"),
                    "description",
                    object(
                        "default",
                        "",
                        "description",
                        "日程描述",
                        "maxLength",
                        1000,
                        "title",
                        "Description",
                        "type",
                        "string"),
                    "location",
                    object(
                        "default",
                        "",
                        "description",
                        "日程地点",
                        "maxLength",
                        128,
                        "title",
                        "Location",
                        "type",
                        "string"),
                    "start_at",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "开始时间，人能读的写法，如 `2026-10-22 14:00`；不带时区按北京时间。与 start_time 二选一",
                        "title",
                        "Start At"),
                    "end_at",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "结束时间，写法同 start_at；与 end_time 二选一",
                        "title",
                        "End At"),
                    "start_time",
                    object(
                        "anyOf",
                        array(object("minimum", 0, "type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "开始时间（Unix 秒）。一般用 start_at 就够了，这个留给已有时间戳的场景",
                        "title",
                        "Start Time"),
                    "end_time",
                    object(
                        "anyOf",
                        array(object("minimum", 0, "type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "结束时间（Unix 秒）",
                        "title",
                        "End Time"),
                    "is_whole_day",
                    object(
                        "default",
                        0,
                        "description",
                        "1 = 全天日程（此时只看日期）",
                        "enum",
                        array(0, 1),
                        "title",
                        "Is Whole Day",
                        "type",
                        "integer"),
                    "admins",
                    object(
                        "description",
                        "日程管理员，必须同时出现在 attendees 中（最多 3 人）",
                        "items",
                        object("type", "string"),
                        "maxItems",
                        3,
                        "title",
                        "Admins",
                        "type",
                        "array",
                        "default",
                        array()),
                    "attendees",
                    object(
                        "description",
                        "参与者，可简写为 [\"zhangsan\",\"lisi\"]，也可写 [{\"userid\":\"zhangsan\"}]",
                        "items",
                        object("$ref", "#/$defs/Attendee"),
                        "maxItems",
                        1000,
                        "title",
                        "Attendees",
                        "type",
                        "array",
                        "default",
                        array()),
                    "reminders",
                    object(
                        "anyOf",
                        array(object("$ref", "#/$defs/Reminders"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "提醒与重复规则；不传就是普通日程、不提醒"),
                    "agentid",
                    object(
                        "anyOf",
                        array(object("minimum", 0, "type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "授权方安装的应用 agentid；仅旧的第三方多应用套件需要，自建应用留空（网关按凭据自动带）",
                        "title",
                        "Agentid")),
                "title",
                "CreateScheduleParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "已转换日程时间、提醒与参与者。",
                "增加自然的 ISO 时间/时区表达与 prepare，不推断业务时区。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "时间规范化和基本日程结构清理。",
                "还需显式给成员与日历 ID，未做参与人和业务日历解析。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        316,
                        "private Map<String, Object> scheduleCreate("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/registry/service/ParameterRules.java",
                        93,
                        "key.equals(\"wecom.schedules.create\")"))))
        .build();
  }

  private static BuiltinOperation schedulesDelete() {
    return operation("wecom.schedules.delete")
        .summary("删除日程")
        .quota("120/min")
        .since("2026-10")
        .category("日程管理")
        .order(70)
        .cost("none")
        .tags("write")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "schedule_id",
                    object("minLength", 1, "title", "Schedule Id", "type", "string")),
                "required",
                array("schedule_id"),
                "title",
                "DeleteScheduleParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "按 schedule_id 删除。",
                "增加取消原因与影响预检，返回最终状态。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "按官方 ID 删除或更新日程字段。",
                "无状态预检、差异说明和版本冲突处理。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        205,
                        "if (key.equals(\"schedules.update\"))"),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        214,
                        "if (key.equals(\"schedules.delete\"))"))))
        .build();
  }

  private static BuiltinOperation schedulesGetMany() {
    return operation("wecom.schedules.get_many")
        .summary("获取日程详情")
        .quota("300/min")
        .retries(3)
        .since("2026-10")
        .category("日程管理")
        .order(60)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "schedule_id_list",
                    object(
                        "items",
                        object("type", "string"),
                        "maxItems",
                        1000,
                        "minItems",
                        1,
                        "title",
                        "Schedule Id List",
                        "type",
                        "array")),
                "required",
                array("schedule_id_list"),
                "title",
                "GetSchedulesParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "批量获取并规范状态与参与者响应。",
                "增加每项错误与未找到单号，避免整批丢失。",
                "",
                "ready",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "规范日程状态、时间与参与者响应。",
                "单项读取错误隔离和业务关联未完成。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        189,
                        "if (key.equals(\"schedules.get_many\"))"),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        585,
                        "private Map<String, Object> scheduleView("))))
        .build();
  }

  private static BuiltinOperation schedulesList() {
    return operation("wecom.schedules.list")
        .summary("获取日历下的日程列表")
        .description("仅可读本应用创建的日历。用 offset/limit 翻页，返回空列表表示已到末尾。被取消的日程也会返回，调用方需检查 status。")
        .quota("300/min")
        .retries(3)
        .since("2026-10")
        .category("日程管理")
        .order(50)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "cal_id",
                    object(
                        "description",
                        "要查的日历 ID",
                        "maxLength",
                        64,
                        "minLength",
                        1,
                        "title",
                        "Cal Id",
                        "type",
                        "string"),
                    "page_size",
                    object(
                        "default",
                        500,
                        "description",
                        "每页条数，默认 500（上游上限 1000）",
                        "maximum",
                        1000,
                        "minimum",
                        1,
                        "title",
                        "Page Size",
                        "type",
                        "integer"),
                    "cursor",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "翻页游标，原样传上一页返回的 page.next_cursor；不传就是第一页",
                        "title",
                        "Cursor"),
                    "offset",
                    object(
                        "default",
                        0,
                        "description",
                        "（旧写法）偏移量，新代码用 cursor 即可",
                        "minimum",
                        0,
                        "title",
                        "Offset",
                        "type",
                        "integer"),
                    "limit",
                    object(
                        "anyOf",
                        array(
                            object("maximum", 1000, "minimum", 1, "type", "integer"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "（旧写法）等价于 page_size，给了就覆盖它",
                        "title",
                        "Limit")),
                "required",
                array("cal_id"),
                "title",
                "ListSchedulesParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "已返回整理日程与 page.next_cursor。",
                "用于单页交互，批量读取可用 list_all。",
                "",
                "ready",
                List.of("wecom.schedules.list_all"),
                CapabilityReview.Depth.ENHANCED,
                "整理日程页，list_all 可自动翻页。",
                "list_all 超过 max_items 抛错，没有保留已有结果并返回续传位置。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        330,
                        "private Object schedules("))))
        .build();
  }

  private static BuiltinOperation schedulesListAll() {
    return operation("wecom.schedules.list_all")
        .summary("拉完一个日历下的所有日程（自动翻页）")
        .description(
            "不用自己 while offset，网关自动翻页并聚合成一个数组。默认 2000 条上限，超过会报 WECOM_TOO_MANY_ITEMS —— 避免一个巨型日历把内存拉爆。")
        .quota("120/min")
        .retries(2)
        .since("2026-10")
        .category("日程管理")
        .order(32)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "description",
                "自动翻页拉完一个日历下的所有日程。",
                "properties",
                object(
                    "cal_id",
                    object("maxLength", 64, "minLength", 1, "title", "Cal Id", "type", "string"),
                    "page_size",
                    object(
                        "default",
                        500,
                        "description",
                        "内部每页拉多少，一般不用改",
                        "maximum",
                        1000,
                        "minimum",
                        1,
                        "title",
                        "Page Size",
                        "type",
                        "integer"),
                    "max_items",
                    object(
                        "default",
                        2000,
                        "description",
                        "安全上限：超过就报错而不是继续拉，避免一个巨型日历把内存打爆",
                        "maximum",
                        20000,
                        "minimum",
                        1,
                        "title",
                        "Max Items",
                        "type",
                        "integer")),
                "required",
                array("cal_id"),
                "title",
                "ListAllSchedulesParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "已有自动翻页及 max_items 限制。",
                "改为达到上限时返回 partial 与续传游标，避免全部结果因超限报错丢失。",
                "当前超出 max_items 会报错；分页读取不是一致性快照。",
                "enhance",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "整理日程页，list_all 可自动翻页。",
                "list_all 超过 max_items 抛错，没有保留已有结果并返回续传位置。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        330,
                        "private Object schedules("))))
        .build();
  }

  private static BuiltinOperation schedulesUpdate() {
    return operation("wecom.schedules.update")
        .summary("更新日程")
        .description(
            "企微更新是覆盖式，且不可更新组织者和所属日历。重复日程：op_mode=0 改全部；1 仅改这一次；2 改此后全部；后两种必须给 op_start_time（原周期实例的开始时间）。")
        .quota("120/min")
        .since("2026-10")
        .category("日程管理")
        .order(20)
        .cost("none")
        .tags("write")
        .params(
            object(
                "$defs",
                object(
                    "Attendee",
                    object(
                        "additionalProperties",
                        false,
                        "properties",
                        object(
                            "userid",
                            object(
                                "description",
                                "成员 userid，区分大小写",
                                "maxLength",
                                64,
                                "minLength",
                                1,
                                "title",
                                "Userid",
                                "type",
                                "string")),
                        "required",
                        array("userid"),
                        "title",
                        "Attendee",
                        "type",
                        "object"),
                    "Reminders",
                    object(
                        "additionalProperties",
                        false,
                        "description",
                        "提醒与重复规则。字段含义见企微「创建日程」文档，这里只做取值收口。",
                        "properties",
                        object(
                            "is_remind",
                            object(
                                "default",
                                0,
                                "description",
                                "是否需要提醒",
                                "enum",
                                array(0, 1),
                                "title",
                                "Is Remind",
                                "type",
                                "integer"),
                            "is_repeat",
                            object(
                                "default",
                                0,
                                "description",
                                "是否重复日程",
                                "enum",
                                array(0, 1),
                                "title",
                                "Is Repeat",
                                "type",
                                "integer"),
                            "remind_before_event_secs",
                            object(
                                "anyOf",
                                array(
                                    object(
                                        "enum", array(0, 300, 900, 3600, 86400), "type", "integer"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "开始前多少秒提醒；与 remind_time_diffs 只生效一个",
                                "title",
                                "Remind Before Event Secs"),
                            "remind_time_diffs",
                            object(
                                "anyOf",
                                array(
                                    object("items", object("type", "integer"), "type", "array"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "相对开始时间的秒差，如 [0,-3600]；有值且非空时优先于 remind_before_event_secs",
                                "title",
                                "Remind Time Diffs"),
                            "repeat_type",
                            object(
                                "anyOf",
                                array(
                                    object("enum", array(0, 1, 2, 5, 7), "type", "integer"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "0 每日 / 1 每周 / 2 每月 / 5 每年 / 7 工作日",
                                "title",
                                "Repeat Type"),
                            "repeat_until",
                            object(
                                "anyOf",
                                array(
                                    object("minimum", 0, "type", "integer"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "重复结束时间戳，0 表示一直重复",
                                "title",
                                "Repeat Until"),
                            "is_custom_repeat",
                            object(
                                "anyOf",
                                array(
                                    object("enum", array(0, 1), "type", "integer"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "是否自定义重复",
                                "title",
                                "Is Custom Repeat"),
                            "repeat_interval",
                            object(
                                "anyOf",
                                array(
                                    object("minimum", 1, "type", "integer"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "重复间隔，仅自定义重复有效",
                                "title",
                                "Repeat Interval"),
                            "repeat_day_of_week",
                            object(
                                "anyOf",
                                array(
                                    object("items", object("type", "integer"), "type", "array"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "周几重复，1~7 = 周一~周日",
                                "title",
                                "Repeat Day Of Week"),
                            "repeat_day_of_month",
                            object(
                                "anyOf",
                                array(
                                    object("items", object("type", "integer"), "type", "array"),
                                    object("type", "null")),
                                "default",
                                null,
                                "description",
                                "每月哪几天重复，1~31",
                                "title",
                                "Repeat Day Of Month"),
                            "timezone",
                            object(
                                "default",
                                8,
                                "description",
                                "时区偏移小时数，北京时间为 8",
                                "maximum",
                                12,
                                "minimum",
                                -12,
                                "title",
                                "Timezone",
                                "type",
                                "integer")),
                        "title",
                        "Reminders",
                        "type",
                        "object")),
                "additionalProperties",
                false,
                "properties",
                object(
                    "schedule_id",
                    object(
                        "description",
                        "要改的日程 ID",
                        "minLength",
                        1,
                        "title",
                        "Schedule Id",
                        "type",
                        "string"),
                    "op_mode",
                    object(
                        "default",
                        0,
                        "description",
                        "重复日程改哪几期：0 全部 / 1 仅这一次 / 2 这一次及未来",
                        "enum",
                        array(0, 1, 2),
                        "title",
                        "Op Mode",
                        "type",
                        "integer"),
                    "op_start_time",
                    object(
                        "anyOf",
                        array(object("minimum", 0, "type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "仅 op_mode=1/2 时填：要改的那一期原本的开始时间（Unix 秒）",
                        "title",
                        "Op Start Time"),
                    "op_start_at",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "op_start_time 的可读写法，如 `2026-10-22 14:00`；二选一",
                        "title",
                        "Op Start At"),
                    "summary",
                    object(
                        "anyOf",
                        array(object("maxLength", 128, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "新标题",
                        "title",
                        "Summary"),
                    "description",
                    object(
                        "anyOf",
                        array(object("maxLength", 1000, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "title",
                        "Description"),
                    "location",
                    object(
                        "anyOf",
                        array(object("maxLength", 128, "type", "string"), object("type", "null")),
                        "default",
                        null,
                        "title",
                        "Location"),
                    "start_at",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "新的开始时间，如 `2026-10-22 15:00`",
                        "title",
                        "Start At"),
                    "end_at",
                    object(
                        "anyOf",
                        array(object("type", "string"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "新的结束时间",
                        "title",
                        "End At"),
                    "start_time",
                    object(
                        "anyOf",
                        array(object("minimum", 0, "type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "新的开始时间（Unix 秒）",
                        "title",
                        "Start Time"),
                    "end_time",
                    object(
                        "anyOf",
                        array(object("minimum", 0, "type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "新的结束时间（Unix 秒）",
                        "title",
                        "End Time"),
                    "is_whole_day",
                    object(
                        "anyOf",
                        array(
                            object("enum", array(0, 1), "type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "title",
                        "Is Whole Day"),
                    "admins",
                    object(
                        "anyOf",
                        array(
                            object(
                                "items", object("type", "string"), "maxItems", 3, "type", "array"),
                            object("type", "null")),
                        "default",
                        null,
                        "title",
                        "Admins"),
                    "attendees",
                    object(
                        "anyOf",
                        array(
                            object("items", object("$ref", "#/$defs/Attendee"), "type", "array"),
                            object("type", "null")),
                        "default",
                        null,
                        "description",
                        "参与者，可简写 [\"zhangsan\"]",
                        "title",
                        "Attendees"),
                    "reminders",
                    object(
                        "anyOf",
                        array(object("$ref", "#/$defs/Reminders"), object("type", "null")),
                        "default",
                        null),
                    "agentid",
                    object(
                        "anyOf",
                        array(object("minimum", 0, "type", "integer"), object("type", "null")),
                        "default",
                        null,
                        "description",
                        "授权方安装的应用 agentid；仅旧的第三方多应用套件需要，自建应用留空",
                        "title",
                        "Agentid")),
                "required",
                array("schedule_id"),
                "title",
                "UpdateScheduleParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "转换日程属性更新。",
                "增加差异预检、版本冲突提示与通知范围。",
                "",
                "enhance",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "按官方 ID 删除或更新日程字段。",
                "无状态预检、差异说明和版本冲突处理。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        205,
                        "if (key.equals(\"schedules.update\"))"),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        214,
                        "if (key.equals(\"schedules.delete\"))"))))
        .build();
  }

  private static BuiltinOperation usersGet() {
    return operation("wecom.users.get")
        .summary("查询成员信息")
        .quota("300/min")
        .retries(3)
        .since("2026-10")
        .category("通讯录")
        .order(10)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "userid",
                    object("maxLength", 64, "minLength", 1, "title", "Userid", "type", "string")),
                "required",
                array("userid"),
                "title",
                "GetUserParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "按 userid 查询成员。",
                "业务侧可优先使用目录 users 的批量和部门路径封装。",
                "",
                "ready",
                List.of("wecom.directory.users"),
                CapabilityReview.Depth.BASIC,
                "按 userid 返回官方成员资料。",
                "没有成员别名/部门路径解析；更高层能力在 directory.users。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        63,
                        "if (key.equals(\"users.get\"))"))))
        .build();
  }

  private static BuiltinOperation usersResolveCode() {
    return operation("wecom.users.resolve_code")
        .summary("网页授权 code 换 userid")
        .quota("120/min")
        .since("2026-10")
        .category("网页授权")
        .order(10)
        .cost("none")
        .tags("read")
        .params(
            object(
                "additionalProperties",
                false,
                "properties",
                object(
                    "code",
                    object(
                        "description",
                        "网页授权 code",
                        "minLength",
                        4,
                        "title",
                        "Code",
                        "type",
                        "string")),
                "required",
                array("code"),
                "title",
                "ExchangeCodeParams",
                "type",
                "object"))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "网页授权 code 换成员身份。",
                "作为登录流程原子能力，绑定回调 state/nonce 并由业务认证层验证。",
                "一次性 code 不宜重放或用于任意人员查询。",
                "low_level",
                List.of(),
                CapabilityReview.Depth.SUPPORTING,
                "提供应用令牌或网页授权换码的基础兼容入口。",
                "不作为业务深封完成项；业务侧优先使用领域接口和自己的登录流程。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        50,
                        "if (key.equals(\"auth.access_token\"))"),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        65,
                        "if (key.equals(\"users.resolve_code\"))"))))
        .build();
  }

  private static BuiltinOperation approvalTemplatesList() {
    return operation("wecom.approval_templates.list")
        .summary("列出已注册的业务审批模板")
        .description("仅列出当前环境、调用系统和模拟/真实模式下的模板别名。企微没有列出企业全部模板的服务端接口。")
        .quota("120/min")
        .timeout(60)
        .retries(2)
        .category("审批模板")
        .order(-90)
        .officialDocs("")
        .tags("read", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(),
                "required",
                array(),
                "examples",
                array(object())))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "仅列出当前环境、调用系统和模拟/真实模式下的模板别名。企微没有列出企业全部模板的服务端接口。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "读取当前系统已绑定的模板目录。",
                "这是本地目录查询，不是企业全部模板自动发现。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        47,
                        "if (key.equals(\"wecom.approval_templates.list\"))"))))
        .build();
  }

  private static BuiltinOperation approvalsTrack() {
    return operation("wecom.approvals.track")
        .summary("按业务请求号追踪审批结果")
        .description("用提交时的 request_id 找到本系统审批单，再读取最新状态、当前审批人、步骤与业务字段；隔离环境、模拟模式和上游应用。")
        .quota("120/min")
        .timeout(60)
        .retries(2)
        .category("通用审批")
        .order(-89)
        .officialDocs("https://developer.work.weixin.qq.com/document/path/91983")
        .tags("read", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "request_id",
                    object(
                        "type",
                        "string",
                        "minLength",
                        1,
                        "maxLength",
                        128,
                        "description",
                        "稳定业务请求编号；重试时保持相同，不能使用新的随机数")),
                "required",
                array("request_id"),
                "examples",
                array(object("request_id", "expense-20261105-001"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "用提交时的 request_id 找到本系统审批单，再读取最新状态、当前审批人、步骤与业务字段；隔离环境、模拟模式和上游应用。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "统一单据/节点状态、待处理人、备注和明细业务别名；track 按本系统业务请求号关联审批并验证上游应用。",
                "高级控件仍保留部分官方值；流程读取不是一致性快照；未记录的超时提单不能推断成未提交。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        703,
                        "private Map<String, Object> detail("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        136,
                        "if (key.equals(\"wecom.approvals.track\"))"),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        835,
                        "private static Map<String, Object> businessValues("))))
        .build();
  }

  private static BuiltinOperation approvalTemplatesGet() {
    return operation("wecom.approval_templates.get")
        .summary("获取可直接填写的审批表单")
        .description("自动整理字段、必填项、可选项、明细和示例。保留官方结构，支持全部官方控件的高级值模式。")
        .quota("120/min")
        .timeout(60)
        .retries(2)
        .category("审批模板")
        .order(-88)
        .officialDocs("https://developer.work.weixin.qq.com/document/path/91982")
        .tags("read", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "template",
                    object(
                        "type",
                        "string",
                        "minLength",
                        1,
                        "maxLength",
                        160,
                        "description",
                        "业务模板别名或企微模板 ID")),
                "required",
                array("template"),
                "examples",
                array(object("template", "demo-expense"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "自动整理字段、必填项、可选项、明细和示例。保留官方结构，支持全部官方控件的高级值模式。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "解析业务别名和真实模板，生成字段、必填项、选项、明细与业务示例。",
                "少数高级控件仍需官方值结构；未完成真实企业所有控件验收。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        77,
                        "key.equals(\"wecom.approval_templates.get\")"),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/ApprovalFormCodec.java",
                        61,
                        "public static Map<String, Object> describe("))))
        .build();
  }

  private static BuiltinOperation approvalTemplatesBind() {
    return operation("wecom.approval_templates.bind")
        .summary("绑定业务模板别名与字段别名")
        .description("别名按调用系统隔离；绑定前核对真实模板和控件 ID，不会修改上游模板。")
        .quota("120/min")
        .idempotent(true)
        .timeout(60)
        .category("审批模板")
        .order(-87)
        .officialDocs("")
        .tags("write", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "template_id",
                    object("type", "string", "minLength", 1, "maxLength", 160, "description", ""),
                    "alias",
                    object("type", "string", "minLength", 1, "maxLength", 64, "description", ""),
                    "field_aliases",
                    object("type", "object", "additionalProperties", true),
                    "description",
                    object("type", "string", "minLength", 0, "maxLength", 255, "description", "")),
                "required",
                array("template_id", "alias"),
                "examples",
                array(
                    object(
                        "template_id",
                        "demo-expense",
                        "alias",
                        "expense",
                        "field_aliases",
                        object("reason", "Text-01", "amount", "Money-02")))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "别名按调用系统隔离；绑定前核对真实模板和控件 ID，不会修改上游模板。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "核对真实控件并持久化当前系统的模板/字段别名。",
                "字段别名需要明确配置，不会猜测业务含义。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        77,
                        "key.equals(\"wecom.approval_templates.bind\")"),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        364,
                        "private void validateAliases("))))
        .build();
  }

  private static BuiltinOperation approvalTemplatesUnbind() {
    return operation("wecom.approval_templates.unbind")
        .summary("移除本系统的模板别名")
        .description("只移除本地别名，不删除企微模板。")
        .quota("120/min")
        .idempotent(true)
        .timeout(60)
        .category("审批模板")
        .order(-86)
        .officialDocs("")
        .tags("write", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "alias",
                    object("type", "string", "minLength", 1, "maxLength", 64, "description", "")),
                "required",
                array("alias"),
                "examples",
                array(object("alias", "expense"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "只移除本地别名，不删除企微模板。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "移除当前系统的本地模板别名。",
                "按原子配置职责保留，不修改上游模板。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        64,
                        "if (key.equals(\"wecom.approval_templates.unbind\"))"))))
        .build();
  }

  private static BuiltinOperation approvalTemplatesCreate() {
    return operation("wecom.approval_templates.create")
        .summary("按业务字段创建审批模板")
        .description("提供字段名与控件类型即可创建。新模板的审批规则需要在企微管理后台确认；支持直接传官方 template_content。")
        .quota("120/min")
        .sensitive(true)
        .idempotent(true)
        .timeout(60)
        .category("审批模板")
        .order(-85)
        .officialDocs("https://developer.work.weixin.qq.com/document/path/97437")
        .tags("write", "business", "approval", "idempotency_required")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "name",
                    object(
                        "type", "string", "minLength", 1, "maxLength", 40, "description", "模板名称"),
                    "fields",
                    object(
                        "type",
                        "array",
                        "items",
                        object(
                            "type",
                            "object",
                            "additionalProperties",
                            true,
                            "description",
                            "key、label、control、required；选择控件可传 options，Table 可传 children"),
                        "minItems",
                        1,
                        "maxItems",
                        100),
                    "template_content",
                    object(
                        "type",
                        "object",
                        "additionalProperties",
                        true,
                        "description",
                        "高级模式：官方模板内容，不能与 fields 同时传入"),
                    "alias",
                    object("type", "string", "minLength", 1, "maxLength", 64, "description", ""),
                    "description",
                    object("type", "string", "minLength", 0, "maxLength", 255, "description", ""),
                    "request_id",
                    object(
                        "type",
                        "string",
                        "minLength",
                        1,
                        "maxLength",
                        128,
                        "description",
                        "稳定业务请求编号；重试时保持相同，不能使用新的随机数")),
                "required",
                array("name"),
                "examples",
                array(
                    object(
                        "name",
                        "费用申请（测试）",
                        "fields",
                        array(
                            object(
                                "key",
                                "reason",
                                "label",
                                "事由",
                                "control",
                                "Text",
                                "required",
                                true),
                            object(
                                "key",
                                "amount",
                                "label",
                                "金额",
                                "control",
                                "Money",
                                "required",
                                true)),
                        "request_id",
                        "template-create-example"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "提供字段名与控件类型即可创建。新模板的审批规则需要在企微管理后台确认；支持直接传官方 template_content。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "按业务字段构建控件、数字 ID 和别名；更新优先保留原控件 ID。",
                "复杂 config 和审批规则仍需明确配置，未自动设计审批人或管理员规则。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        513,
                        "private Object saveTemplate("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        601,
                        "private List<Object> buildControls("))))
        .build();
  }

  private static BuiltinOperation approvalTemplatesUpdate() {
    return operation("wecom.approval_templates.update")
        .summary("更新审批模板内容")
        .description("保留企微既有流程和规则，只允许更新应用有权限的模板。提供完整字段定义或官方 template_content。")
        .quota("120/min")
        .sensitive(true)
        .idempotent(true)
        .timeout(60)
        .category("审批模板")
        .order(-84)
        .officialDocs("https://developer.work.weixin.qq.com/document/path/97438")
        .tags("write", "business", "approval", "idempotency_required")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "template",
                    object(
                        "type",
                        "string",
                        "minLength",
                        1,
                        "maxLength",
                        160,
                        "description",
                        "业务模板别名或企微模板 ID"),
                    "name",
                    object(
                        "type", "string", "minLength", 1, "maxLength", 40, "description", "模板名称"),
                    "fields",
                    object(
                        "type",
                        "array",
                        "items",
                        object(
                            "type",
                            "object",
                            "additionalProperties",
                            true,
                            "description",
                            "key、label、control、required；选择控件可传 options，Table 可传 children"),
                        "minItems",
                        1,
                        "maxItems",
                        100),
                    "template_content",
                    object(
                        "type",
                        "object",
                        "additionalProperties",
                        true,
                        "description",
                        "高级模式：官方模板内容，不能与 fields 同时传入"),
                    "alias",
                    object("type", "string", "minLength", 1, "maxLength", 64, "description", ""),
                    "description",
                    object("type", "string", "minLength", 0, "maxLength", 255, "description", ""),
                    "request_id",
                    object(
                        "type",
                        "string",
                        "minLength",
                        1,
                        "maxLength",
                        128,
                        "description",
                        "稳定业务请求编号；重试时保持相同，不能使用新的随机数")),
                "required",
                array("template", "name"),
                "examples",
                array(
                    object(
                        "template",
                        "demo-expense",
                        "name",
                        "费用申请（测试）",
                        "fields",
                        array(
                            object(
                                "key",
                                "reason",
                                "label",
                                "事由",
                                "control",
                                "Text",
                                "required",
                                true),
                            object(
                                "key",
                                "amount",
                                "label",
                                "金额",
                                "control",
                                "Money",
                                "required",
                                true)),
                        "request_id",
                        "template-update-example"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "保留企微既有流程和规则，只允许更新应用有权限的模板。提供完整字段定义或官方 template_content。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "按业务字段构建控件、数字 ID 和别名；更新优先保留原控件 ID。",
                "复杂 config 和审批规则仍需明确配置，未自动设计审批人或管理员规则。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        513,
                        "private Object saveTemplate("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        601,
                        "private List<Object> buildControls("))))
        .build();
  }

  private static BuiltinOperation approvalTemplatesClone() {
    return operation("wecom.approval_templates.clone")
        .summary("复制模板为新的业务模板")
        .description("读取原模板控件再创建新模板；不会复制原审批流程或管理员规则。")
        .quota("120/min")
        .sensitive(true)
        .idempotent(true)
        .timeout(60)
        .category("审批模板")
        .order(-83)
        .officialDocs("")
        .tags("write", "business", "approval", "idempotency_required")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "template",
                    object(
                        "type",
                        "string",
                        "minLength",
                        1,
                        "maxLength",
                        160,
                        "description",
                        "业务模板别名或企微模板 ID"),
                    "name",
                    object("type", "string", "minLength", 1, "maxLength", 40, "description", ""),
                    "alias",
                    object("type", "string", "minLength", 1, "maxLength", 64, "description", ""),
                    "description",
                    object("type", "string", "minLength", 0, "maxLength", 255, "description", ""),
                    "request_id",
                    object(
                        "type",
                        "string",
                        "minLength",
                        1,
                        "maxLength",
                        128,
                        "description",
                        "稳定业务请求编号；重试时保持相同，不能使用新的随机数")),
                "required",
                array("template", "name"),
                "examples",
                array(
                    object(
                        "template",
                        "demo-expense",
                        "name",
                        "新的费用申请（测试）",
                        "request_id",
                        "template-clone-example"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "读取原模板控件再创建新模板；不会复制原审批流程或管理员规则。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "读取模板并创建新模板，可复用业务字段别名。",
                "只复制控件，不复制原审批流程及管理员规则。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        525,
                        "if (operation.endsWith(\"clone\"))"))))
        .build();
  }

  private static BuiltinOperation approvalsPrepare() {
    return operation("wecom.approvals.prepare")
        .summary("预检审批申请并生成官方请求")
        .description("按模板校验全部字段，自动转换控件、选项与金额，生成摘要。只读取模板，不会提单。")
        .quota("120/min")
        .timeout(60)
        .retries(2)
        .category("通用审批")
        .order(-82)
        .officialDocs("https://developer.work.weixin.qq.com/document/path/91853")
        .tags("read", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "template",
                    object(
                        "type",
                        "string",
                        "minLength",
                        1,
                        "maxLength",
                        160,
                        "description",
                        "业务模板别名或企微模板 ID"),
                    "fields",
                    object(
                        "type",
                        "object",
                        "additionalProperties",
                        true,
                        "description",
                        "字段对象，支持业务别名、唯一标题或控件 ID；按模板类型自动转换"),
                    "apply_data",
                    object(
                        "type",
                        "object",
                        "additionalProperties",
                        true,
                        "description",
                        "高级模式：官方 contents，不可与 fields 同时传入"),
                    "applicant",
                    object(
                        "type",
                        "string",
                        "minLength",
                        1,
                        "maxLength",
                        64,
                        "description",
                        "申请人 userid"),
                    "department_id",
                    object("type", "integer", "minimum", 1),
                    "flow",
                    object(
                        "type",
                        "array",
                        "items",
                        object(
                            "type",
                            "object",
                            "additionalProperties",
                            false,
                            "properties",
                            object(
                                "type",
                                object(
                                    "type",
                                    "string",
                                    "enum",
                                    array("approve", "notify", "handle"),
                                    "default",
                                    "approve"),
                                "mode",
                                object(
                                    "type",
                                    "string",
                                    "enum",
                                    array("all", "any", "sequential"),
                                    "default",
                                    "all"),
                                "users",
                                object(
                                    "type",
                                    "array",
                                    "items",
                                    object(
                                        "type",
                                        "string",
                                        "minLength",
                                        1,
                                        "maxLength",
                                        64,
                                        "description",
                                        "成员 userid"),
                                    "minItems",
                                    1,
                                    "maxItems",
                                    100)),
                            "required",
                            array("users")),
                        "minItems",
                        0,
                        "maxItems",
                        20),
                    "summary",
                    object(
                        "type",
                        "array",
                        "items",
                        object(
                            "type",
                            "string",
                            "minLength",
                            1,
                            "maxLength",
                            20,
                            "description",
                            "摘要行，最多 20 个字符"),
                        "minItems",
                        0,
                        "maxItems",
                        3),
                    "template_version",
                    object(
                        "type",
                        "string",
                        "minLength",
                        1,
                        "maxLength",
                        64,
                        "description",
                        "预检返回的模板版本；提交时检测模板变更")),
                "required",
                array("template", "applicant"),
                "examples",
                array(
                    object(
                        "template",
                        "demo-expense",
                        "applicant",
                        "fixture-user",
                        "fields",
                        object("事由", "购买办公用品", "金额", "99.50", "费用类型", "办公")))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "按模板校验全部字段，自动转换控件、选项与金额，生成摘要。只读取模板，不会提单。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "自动读取模板、聚合校验、类型/选项/金额转换、摘要和流程生成；提交沿用持久防重。",
                "高级控件需要显式官方值；当前未在真实企业完成全部权限/控件联调。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        386,
                        "private Map<String, Object> prepare("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/ApprovalFormCodec.java",
                        161,
                        "public static Map<String, Object> encode("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/gateway/service/GatewayExecutor.java",
                        67,
                        "op.tags().contains(\"idempotency_required\")"))))
        .build();
  }

  private static BuiltinOperation approvalsSubmit() {
    return operation("wecom.approvals.submit")
        .summary("用业务字段一键提交审批")
        .description("自动读取最新模板、转换字段、校验流程和生成摘要。默认采用模板审批流程。request_id 防重复提交；超时结果不确定时不自动重发。")
        .quota("120/min")
        .sensitive(true)
        .idempotent(true)
        .timeout(60)
        .category("通用审批")
        .order(-81)
        .officialDocs("https://developer.work.weixin.qq.com/document/path/91853")
        .tags("write", "business", "approval", "idempotency_required")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "template",
                    object(
                        "type",
                        "string",
                        "minLength",
                        1,
                        "maxLength",
                        160,
                        "description",
                        "业务模板别名或企微模板 ID"),
                    "fields",
                    object(
                        "type",
                        "object",
                        "additionalProperties",
                        true,
                        "description",
                        "字段对象，支持业务别名、唯一标题或控件 ID；按模板类型自动转换"),
                    "apply_data",
                    object(
                        "type",
                        "object",
                        "additionalProperties",
                        true,
                        "description",
                        "高级模式：官方 contents，不可与 fields 同时传入"),
                    "applicant",
                    object(
                        "type",
                        "string",
                        "minLength",
                        1,
                        "maxLength",
                        64,
                        "description",
                        "申请人 userid"),
                    "department_id",
                    object("type", "integer", "minimum", 1),
                    "flow",
                    object(
                        "type",
                        "array",
                        "items",
                        object(
                            "type",
                            "object",
                            "additionalProperties",
                            false,
                            "properties",
                            object(
                                "type",
                                object(
                                    "type",
                                    "string",
                                    "enum",
                                    array("approve", "notify", "handle"),
                                    "default",
                                    "approve"),
                                "mode",
                                object(
                                    "type",
                                    "string",
                                    "enum",
                                    array("all", "any", "sequential"),
                                    "default",
                                    "all"),
                                "users",
                                object(
                                    "type",
                                    "array",
                                    "items",
                                    object(
                                        "type",
                                        "string",
                                        "minLength",
                                        1,
                                        "maxLength",
                                        64,
                                        "description",
                                        "成员 userid"),
                                    "minItems",
                                    1,
                                    "maxItems",
                                    100)),
                            "required",
                            array("users")),
                        "minItems",
                        0,
                        "maxItems",
                        20),
                    "summary",
                    object(
                        "type",
                        "array",
                        "items",
                        object(
                            "type",
                            "string",
                            "minLength",
                            1,
                            "maxLength",
                            20,
                            "description",
                            "摘要行，最多 20 个字符"),
                        "minItems",
                        0,
                        "maxItems",
                        3),
                    "template_version",
                    object(
                        "type",
                        "string",
                        "minLength",
                        1,
                        "maxLength",
                        64,
                        "description",
                        "预检返回的模板版本；提交时检测模板变更"),
                    "request_id",
                    object(
                        "type",
                        "string",
                        "minLength",
                        1,
                        "maxLength",
                        128,
                        "description",
                        "稳定业务请求编号；重试时保持相同，不能使用新的随机数")),
                "required",
                array("template", "applicant", "request_id"),
                "examples",
                array(
                    object(
                        "template",
                        "demo-expense",
                        "applicant",
                        "fixture-user",
                        "fields",
                        object("事由", "购买办公用品", "金额", "99.50", "费用类型", "办公"),
                        "request_id",
                        "expense-order-20261105-001"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "自动读取最新模板、转换字段、校验流程和生成摘要。默认采用模板审批流程。request_id 防重复提交；超时结果不确定时不自动重发。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "自动读取模板、聚合校验、类型/选项/金额转换、摘要和流程生成；提交沿用持久防重。",
                "高级控件需要显式官方值；当前未在真实企业完成全部权限/控件联调。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        386,
                        "private Map<String, Object> prepare("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/ApprovalFormCodec.java",
                        161,
                        "public static Map<String, Object> encode("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/gateway/service/GatewayExecutor.java",
                        67,
                        "op.tags().contains(\"idempotency_required\")"))))
        .build();
  }

  private static BuiltinOperation approvalsGet() {
    return operation("wecom.approvals.get")
        .summary("获取整理后的审批详情")
        .description("统一状态、申请人、字段、附件、明细和流程；保留 official，未知状态保持 unknown。")
        .quota("120/min")
        .timeout(60)
        .retries(2)
        .category("通用审批")
        .order(-80)
        .officialDocs("https://developer.work.weixin.qq.com/document/path/91983")
        .tags("read", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "approval_no",
                    object("type", "string", "minLength", 1, "maxLength", 64, "description", "")),
                "required",
                array("approval_no"),
                "examples",
                array(object("approval_no", "MOCK-1"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "统一状态、申请人、字段、附件、明细和流程；保留 official，未知状态保持 unknown。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "统一单据/节点状态、待处理人、备注和明细业务别名；track 按本系统业务请求号关联审批并验证上游应用。",
                "高级控件仍保留部分官方值；流程读取不是一致性快照；未记录的超时提单不能推断成未提交。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        703,
                        "private Map<String, Object> detail("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        136,
                        "if (key.equals(\"wecom.approvals.track\"))"),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        835,
                        "private static Map<String, Object> businessValues("))))
        .build();
  }

  private static BuiltinOperation approvalsGetMany() {
    return operation("wecom.approvals.get_many")
        .summary("批量获取审批详情（支持部分失败）")
        .description("去重后逐个读取详情，每个失败单独返回；不会因一个无效单号丢失其他结果。")
        .quota("120/min")
        .timeout(60)
        .retries(2)
        .category("通用审批")
        .order(-79)
        .officialDocs("https://developer.work.weixin.qq.com/document/path/91983")
        .tags("read", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "approval_nos",
                    object(
                        "type",
                        "array",
                        "items",
                        object(
                            "type", "string", "minLength", 1, "maxLength", 64, "description", ""),
                        "minItems",
                        1,
                        "maxItems",
                        100)),
                "required",
                array("approval_nos"),
                "examples",
                array(object("approval_nos", array("MOCK-1")))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "去重后逐个读取详情，每个失败单独返回；不会因一个无效单号丢失其他结果。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "自动去重单号、逐项读取并保留错误。",
                "是批处理便利，尚无业务订单映射和流程级补齐。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        928,
                        "private Map<String, Object> details("))))
        .build();
  }

  private static BuiltinOperation approvalsList() {
    return operation("wecom.approvals.list")
        .summary("按业务条件查询审批（自动补齐详情）")
        .description("支持易读状态、日期和模板别名。自动拆分 31 天窗口；只按游标判断结束，空页也能继续。游标绑定调用系统和筛选条件。")
        .quota("120/min")
        .timeout(60)
        .retries(2)
        .category("通用审批")
        .order(-78)
        .officialDocs("https://developer.work.weixin.qq.com/document/path/91816")
        .tags("read", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "start_at",
                    object("description", "ISO 日期时间、北京时间或 Unix 秒/毫秒；省略为最近 7 天"),
                    "end_at",
                    object("description", "结束时间；总范围最多 366 天"),
                    "template",
                    object("type", "string", "minLength", 1, "maxLength", 160, "description", ""),
                    "applicant",
                    object("type", "string", "minLength", 1, "maxLength", 64, "description", ""),
                    "status",
                    object(
                        "description",
                        "pending/approved/rejected/cancelled/approval_revoked/deleted/paid，或官方数字状态"),
                    "department_ids",
                    object(
                        "type",
                        "array",
                        "items",
                        object("type", "integer", "minimum", 1),
                        "minItems",
                        0,
                        "maxItems",
                        100),
                    "record_type",
                    object("type", "integer", "minimum", 1, "maximum", 9),
                    "cursor",
                    object("type", "string", "minLength", 0, "maxLength", 16000, "description", ""),
                    "page_size",
                    object("type", "integer", "default", 20, "minimum", 1, "maximum", 100),
                    "include_details",
                    object("type", "boolean", "default", true)),
                "required",
                array(),
                "examples",
                array(object("include_details", false))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "支持易读状态、日期和模板别名。自动拆分 31 天窗口；只按游标判断结束，空页也能继续。游标绑定调用系统和筛选条件。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "业务条件/模板别名转换、跨 31 天窗口、有界自动分页和详情补齐。",
                "不同批次需按单号去重，不提供一致性快照；失败详情需调用方处理。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        953,
                        "private Object query("))))
        .build();
  }

  private static BuiltinOperation approvalsCollect() {
    return operation("wecom.approvals.collect")
        .summary("有界汇总审批（自动分页与跨月查询）")
        .description("自动翻页和拆分时间窗口，按单号去重。达到 max_items/max_pages 时返回 partial 与续传游标，避免无限拉取或静默截断。")
        .quota("120/min")
        .timeout(90)
        .retries(2)
        .category("通用审批")
        .order(-77)
        .officialDocs("https://developer.work.weixin.qq.com/document/path/91816")
        .tags("read", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "start_at",
                    object("description", "ISO 日期时间、北京时间或 Unix 秒/毫秒；省略为最近 7 天"),
                    "end_at",
                    object("description", "结束时间；总范围最多 366 天"),
                    "template",
                    object("type", "string", "minLength", 1, "maxLength", 160, "description", ""),
                    "applicant",
                    object("type", "string", "minLength", 1, "maxLength", 64, "description", ""),
                    "status",
                    object(
                        "description",
                        "pending/approved/rejected/cancelled/approval_revoked/deleted/paid，或官方数字状态"),
                    "department_ids",
                    object(
                        "type",
                        "array",
                        "items",
                        object("type", "integer", "minimum", 1),
                        "minItems",
                        0,
                        "maxItems",
                        100),
                    "record_type",
                    object("type", "integer", "minimum", 1, "maximum", 9),
                    "cursor",
                    object("type", "string", "minLength", 0, "maxLength", 16000, "description", ""),
                    "page_size",
                    object("type", "integer", "default", 20, "minimum", 1, "maximum", 100),
                    "include_details",
                    object("type", "boolean", "default", true),
                    "max_items",
                    object("type", "integer", "default", 100, "minimum", 1, "maximum", 1000),
                    "max_pages",
                    object("type", "integer", "default", 10, "minimum", 1, "maximum", 100)),
                "required",
                array(),
                "examples",
                array(object("include_details", false, "max_items", 100))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "自动翻页和拆分时间窗口，按单号去重。达到 max_items/max_pages 时返回 partial 与续传游标，避免无限拉取或静默截断。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "业务条件/模板别名转换、跨 31 天窗口、有界自动分页和详情补齐。",
                "不同批次需按单号去重，不提供一致性快照；失败详情需调用方处理。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        953,
                        "private Object query("))))
        .build();
  }

  private static BuiltinOperation approvalsEngineGet() {
    return operation("wecom.approvals.engine_get")
        .summary("查询自建应用 JS-SDK 审批引擎状态")
        .description("用于通过企微客户端发起的引擎审批，单号使用开发者 third_no。")
        .quota("120/min")
        .timeout(60)
        .retries(2)
        .category("通用审批")
        .order(-76)
        .officialDocs("https://developer.work.weixin.qq.com/document/path/90269")
        .tags("read", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "third_no",
                    object("type", "string", "minLength", 1, "maxLength", 64, "description", "")),
                "required",
                array("third_no"),
                "examples",
                array(object("third_no", "fixture-third-001"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "用于通过企微客户端发起的引擎审批，单号使用开发者 third_no。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "按 third_no 获取并转交审批引擎 data。",
                "引擎详情及状态未归一成普通审批模型。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        159,
                        "if (key.equals(\"wecom.approvals.engine_get\"))"))))
        .build();
  }

  private static BuiltinOperation approvalsEngineLaunch() {
    return operation("wecom.approvals.engine_launch")
        .summary("生成客户端审批引擎调用参数")
        .description("生成 thirdPartyOpenPage 参数；必须在企微客户端完成 JS-SDK 初始化后调用，不会在服务端直接提单。")
        .quota("120/min")
        .timeout(60)
        .retries(2)
        .category("通用审批")
        .order(-75)
        .officialDocs("https://developer.work.weixin.qq.com/document/path/90269")
        .tags("read", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "template_id",
                    object("type", "string", "minLength", 1, "maxLength", 160, "description", ""),
                    "third_no",
                    object("type", "string", "minLength", 1, "maxLength", 64, "description", ""),
                    "mode",
                    object("type", "string", "enum", array("create", "view"), "default", "create"),
                    "fields",
                    object("type", "object", "additionalProperties", true)),
                "required",
                array("template_id", "third_no"),
                "examples",
                array(
                    object(
                        "template_id",
                        "fixture-engine-template",
                        "third_no",
                        "fixture-third-001",
                        "fields",
                        object("事由", "示例")))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "生成 thirdPartyOpenPage 参数；必须在企微客户端完成 JS-SDK 初始化后调用，不会在服务端直接提单。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "从字段对象构建客户端审批引擎调用参数。",
                "只生成 JS-SDK 参数，客户端初始化和实际操作仍需业务系统完成。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        169,
                        "if (key.equals(\"wecom.approvals.engine_launch\"))"))))
        .build();
  }

  private static BuiltinOperation approvalsLegacyGet() {
    return operation("wecom.approvals.legacy_get")
        .summary("读取旧版审批数据（兼容）")
        .description("官方旧接口不再维护，新集成请使用 approvals.list/collect。")
        .quota("120/min")
        .timeout(60)
        .retries(2)
        .deprecated(true)
        .category("通用审批")
        .order(-74)
        .officialDocs("https://developer.work.weixin.qq.com/document/path/91530")
        .tags("read", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "start_at",
                    object(),
                    "end_at",
                    object(),
                    "next_spnum",
                    object("type", "integer", "default", 0, "minimum", 0)),
                "required",
                array("start_at", "end_at"),
                "examples",
                array(object("start_at", "2026-11-01", "end_at", "2026-11-05"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "官方旧接口不再维护，新集成请使用 approvals.list/collect。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "校验 30 天日期窗口后读取旧接口。",
                "保留兼容，旧结果不做通用审批归一。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        197,
                        "if (key.equals(\"wecom.approvals.legacy_get\"))"))))
        .build();
  }

  private static BuiltinOperation approvalFilesUpload() {
    return operation("wecom.approval_files.upload")
        .summary("上传可用于审批的临时附件")
        .description("返回可直接填入 File 字段的 file_id。单文件最多 10 MB，单个审批单最多 6 个附件。")
        .quota("120/min")
        .idempotent(true)
        .timeout(60)
        .category("审批附件")
        .order(-73)
        .officialDocs("")
        .tags("write", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "filename",
                    object("type", "string", "minLength", 1, "maxLength", 128, "description", ""),
                    "content_base64",
                    object(
                        "type",
                        "string",
                        "minLength",
                        1,
                        "maxLength",
                        14000000,
                        "description",
                        "")),
                "required",
                array("filename", "content_base64"),
                "examples",
                array(object("filename", "fixture.txt", "content_base64", "Zml4dHVyZQ=="))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "返回可直接填入 File 字段的 file_id。单文件最多 10 MB，单个审批单最多 6 个附件。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "Base64/二进制转换、文件名与大小校验或 MIME 返回。",
                "是附件读写便利，未形成业务文件管理和申请关联流程。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        209,
                        "if (key.equals(\"wecom.approval_files.upload\"))"),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        240,
                        "if (key.equals(\"wecom.approval_files.download\"))"))))
        .build();
  }

  private static BuiltinOperation approvalFilesDownload() {
    return operation("wecom.approval_files.download")
        .summary("读取审批附件内容")
        .description("使用 file_id 获取附件，返回 Base64 内容与 MIME 类型，不接受任意远程 URL。")
        .quota("120/min")
        .timeout(60)
        .retries(2)
        .category("审批附件")
        .order(-72)
        .officialDocs("")
        .tags("read", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "file_id",
                    object("type", "string", "minLength", 1, "maxLength", 512, "description", "")),
                "required",
                array("file_id"),
                "examples",
                array(object("file_id", "mock-file-fixture"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "使用 file_id 获取附件，返回 Base64 内容与 MIME 类型，不接受任意远程 URL。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "Base64/二进制转换、文件名与大小校验或 MIME 返回。",
                "是附件读写便利，未形成业务文件管理和申请关联流程。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        209,
                        "if (key.equals(\"wecom.approval_files.upload\"))"),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        240,
                        "if (key.equals(\"wecom.approval_files.download\"))"))))
        .build();
  }

  private static BuiltinOperation approvalEventsList() {
    return operation("wecom.approval_events.list")
        .summary("增量读取审批事件")
        .description("按 after_id 增量读取当前调用系统的验签事件，使用 event_id 去重。与配置的签名 Webhook 转发可同时使用。")
        .quota("120/min")
        .timeout(60)
        .retries(2)
        .category("审批事件")
        .order(-71)
        .officialDocs("")
        .tags("read", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "after_id",
                    object(
                        "type",
                        "integer",
                        "default",
                        0,
                        "minimum",
                        0,
                        "maximum",
                        9007199254740991L),
                    "limit",
                    object("type", "integer", "default", 50, "minimum", 1, "maximum", 200)),
                "required",
                array(),
                "examples",
                array(object())))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "按 after_id 增量读取当前调用系统的验签事件，使用 event_id 去重。与配置的签名 Webhook 转发可同时使用。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "消费验签解密、按提交所有者分流、加密入库的持久事件，支持增量拉取和转发重试。",
                "外部发起的未知审批回到接收系统；凭据轮换后的旧单路由需核对。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        254,
                        "if (key.equals(\"wecom.approval_events.list\"))"),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalEvents.java",
                        55,
                        "public void receive("),
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalEvents.java",
                        134,
                        "public void deliverPending("))))
        .build();
  }

  private static BuiltinOperation approvalEventsRetry() {
    return operation("wecom.approval_events.retry")
        .summary("重试本系统失败的审批事件转发")
        .description("仅重试 failed/poll_only 事件，不更改审批业务状态。")
        .quota("120/min")
        .idempotent(true)
        .timeout(60)
        .category("审批事件")
        .order(-70)
        .officialDocs("")
        .tags("write", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "event_id",
                    object("type", "string", "minLength", 1, "maxLength", 64, "description", "")),
                "required",
                array("event_id"),
                "examples",
                array(
                    object(
                        "event_id",
                        "0000000000000000000000000000000000000000000000000000000000000000"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "仅重试 failed/poll_only 事件，不更改审批业务状态。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "限定当前系统与失败状态，重新排队转发。",
                "是事件恢复工具，不改变审批状态或自动修复业务失败。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        291,
                        "if (key.equals(\"wecom.approval_events.retry\"))"))))
        .build();
  }

  private static BuiltinOperation vacationConfig() {
    return operation("wecom.vacation.config")
        .summary("获取企业假期管理配置")
        .description("查询假期类型、计量粒度与余额规则，供通用请假表单使用。")
        .quota("120/min")
        .timeout(60)
        .retries(2)
        .category("假期管理")
        .order(-69)
        .officialDocs("https://developer.work.weixin.qq.com/document/path/93375")
        .tags("read", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(),
                "required",
                array(),
                "examples",
                array(object())))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "查询假期类型、计量粒度与余额规则，供通用请假表单使用。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "将企业假期配置整理为单位、日历和发放规则，保留官方值。",
                "未知规则保留 unknown；不替代实际排班、适用范围或可申请时长计算。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        1208,
                        "private static List<Map<String, Object>> vacationRules("))))
        .build();
  }

  private static BuiltinOperation vacationGet() {
    return operation("wecom.vacation.get")
        .summary("获取成员假期余额")
        .description("返回统一的秒数值和原始明细，按官方天/小时粒度转换，避免假定一天等于八小时。")
        .quota("120/min")
        .timeout(60)
        .retries(2)
        .category("假期管理")
        .order(-68)
        .officialDocs("https://developer.work.weixin.qq.com/document/path/93376")
        .tags("read", "business", "approval")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "userid",
                    object("type", "string", "minLength", 1, "maxLength", 64, "description", "")),
                "required",
                array("userid"),
                "examples",
                array(object("userid", "fixture-user"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "返回统一的秒数值和原始明细，按官方天/小时粒度转换，避免假定一天等于八小时。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "联查企业规则和成员余额，返回明确单位的 remaining 及原始秒数；规则查询失败保留余额和逐项错误。",
                "不是排班引擎，不计算某次请假的实际可用工作时长。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        1086,
                        "private Object vacation("))))
        .build();
  }

  private static BuiltinOperation vacationSet() {
    return operation("wecom.vacation.set")
        .summary("用天或小时设置假期余额")
        .description("显式选择单位并校验 0.1 单位的精度；需要 confirm=true 和稳定 request_id。")
        .quota("120/min")
        .sensitive(true)
        .idempotent(true)
        .timeout(60)
        .category("假期管理")
        .order(-67)
        .officialDocs("https://developer.work.weixin.qq.com/document/path/93377")
        .tags("write", "business", "approval", "idempotency_required")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "userid",
                    object("type", "string", "minLength", 1, "maxLength", 64, "description", ""),
                    "vacation_id",
                    object("type", "integer", "minimum", 1),
                    "remaining",
                    object("type", "number", "minimum", 0, "maximum", 24000),
                    "unit",
                    object("type", "string", "enum", array("days", "hours")),
                    "remarks",
                    object("type", "string", "minLength", 0, "maxLength", 200, "description", ""),
                    "confirm",
                    object("type", "boolean", "default", false),
                    "request_id",
                    object(
                        "type",
                        "string",
                        "minLength",
                        1,
                        "maxLength",
                        128,
                        "description",
                        "稳定业务请求编号；重试时保持相同，不能使用新的随机数")),
                "required",
                array("userid", "vacation_id", "remaining", "unit"),
                "examples",
                array(
                    object(
                        "userid",
                        "fixture-user",
                        "vacation_id",
                        1,
                        "remaining",
                        8,
                        "unit",
                        "hours",
                        "confirm",
                        true,
                        "request_id",
                        "vacation-set-fixture"))))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                "显式选择单位并校验 0.1 单位的精度；需要 confirm=true 和稳定 request_id。",
                "使用接入文档和 Python/TypeScript SDK；保留稳定业务编号、续传游标与逐项错误。",
                "真实环境需要对应应用权限；本次通过模拟上游、加密协议向量和隔离 MySQL 验证，未向供应商提交真实业务。",
                "recommended",
                List.of(),
                CapabilityReview.Depth.DEEP,
                "先核对企业假期 ID 与单位，再转换秒数并校验精度、范围、确认与稳定防重编号。",
                "余额为绝对设置值；不代算请假扣减或跨系统原子事务。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/approval/WecomApprovalService.java",
                        1155,
                        "if (!JsonUtils.bool(p.get(\"confirm\")))"))))
        .build();
  }

  private static BuiltinOperation hrGetFields() {
    return operation("wecom.hr.get_fields")
        .summary("读取企微花名册字段配置")
        .description("只读指定字段；需花名册权限。密钥及 token 由网关持有，字段值不记入调用摘要。")
        .quota("300/min")
        .sensitive(true)
        .timeout(40)
        .category("员工花名册")
        .order(10)
        .tags("read", "hr")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "app",
                    object("type", array("string", "null"), "pattern", "^[a-z][a-z0-9_-]{0,31}$")),
                "required",
                array()))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "按指定应用读取花名册字段配置或指定员工的指定字段；凭据由网关解析并持有。",
                "先读取字段配置，再按业务所需字段 ID 查询员工；接入前核对花名册应用权限。",
                "仅提供花名册只读能力，不负责组织同步、业务字段映射或真实企业权限验收。",
                "retain",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "将受控参数映射到单个花名册官方接口，属于基础只读封装。",
                "业务字段关联、同步策略和真实企业权限需要调用方及部署方确定。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        61,
                        "if (key.startsWith(\"hr.\"))"))))
        .build();
  }

  private static BuiltinOperation hrGetStaffInfo() {
    return operation("wecom.hr.get_staff_info")
        .summary("读取员工指定花名册字段")
        .description("只读指定字段；需花名册权限。密钥及 token 由网关持有，字段值不记入调用摘要。")
        .quota("300/min")
        .sensitive(true)
        .timeout(40)
        .category("员工花名册")
        .order(20)
        .tags("read", "hr")
        .params(
            object(
                "type",
                "object",
                "additionalProperties",
                false,
                "properties",
                object(
                    "app",
                    object("type", array("string", "null"), "pattern", "^[a-z][a-z0-9_-]{0,31}$"),
                    "userid",
                    object("type", "string", "minLength", 1, "maxLength", 128, "pattern", "^\\S+$"),
                    "fieldids",
                    object(
                        "type",
                        "array",
                        "minItems",
                        1,
                        "maxItems",
                        100,
                        "uniqueItems",
                        true,
                        "items",
                        object(
                            "type",
                            "object",
                            "additionalProperties",
                            false,
                            "properties",
                            object(
                                "fieldid",
                                object("type", "integer", "minimum", 1),
                                "sub_idx",
                                object("type", "integer", "const", 0, "default", 0)),
                            "required",
                            array("fieldid")))),
                "required",
                array("userid", "fieldids")))
        .review(
            new CapabilityReview(
                "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                "按指定应用读取花名册字段配置或指定员工的指定字段；凭据由网关解析并持有。",
                "先读取字段配置，再按业务所需字段 ID 查询员工；接入前核对花名册应用权限。",
                "仅提供花名册只读能力，不负责组织同步、业务字段映射或真实企业权限验收。",
                "retain",
                List.of(),
                CapabilityReview.Depth.BASIC,
                "将受控参数映射到单个花名册官方接口，属于基础只读封装。",
                "业务字段关联、同步策略和真实企业权限需要调用方及部署方确定。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/wecom/WecomAdapter.java",
                        61,
                        "if (key.startsWith(\"hr.\"))"))))
        .build();
  }
}
