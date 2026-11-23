package group.cyt.common.module.registry.catalog.platform;

import static group.cyt.common.module.registry.catalog.BuiltinOperation.operation;
import static group.cyt.common.module.registry.catalog.ContractNodes.*;

import group.cyt.common.module.registry.catalog.*;
import java.util.List;

/** Built-in discord contracts. Edit metadata, parameters and review together here. */
public final class DiscordCatalog implements PlatformCatalog {
  @Override
  public void register(CatalogBuilder catalog) {
    catalog.platform("discord", "Discord", "120/min");
    catalog.credentials(credentials());
    catalog.operation(identityGet());
    catalog.operation(accountsList());
    catalog.operation(guildsList());
    catalog.operation(botsList());
    catalog.operation(channelsList());
    catalog.operation(channelsGet());
    catalog.operation(threadsList());
    catalog.operation(messagesList());
    catalog.operation(messagesGet());
    catalog.operation(eventsList());
    catalog.operation(messagesSend());
  }

  private static PlatformCredentials credentials() {
    return new PlatformCredentials(
        "discord",
        "Discord",
        "https://docs.discord.com/developers/quick-start/getting-started",
        "Bot Token 仅由网关保存；开启 Message Content Intent。机器人个人私信与员工个人私信各自独立。",
        "inherit",
        false,
        false,
        "DISCORD_ACCOUNTS",
        "",
        "",
        List.of(
            new PlatformCredentials.Variable(
                "DISCORD_{ACCOUNT}_BOT_TOKEN", "Bot Token", true, true, "", "", false, "", true)));
  }

  private static BuiltinOperation identityGet() {
    return operation("discord.identity.get")
        .summary("验证机器人身份")
        .description("验证机器人身份")
        .quota("120/min")
        .category("消息与频道")
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "maxLength",
                        100,
                        "pattern",
                        "^[\\w.-]*$",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "refresh",
                    object("default", false, "title", "Refresh", "type", "boolean")),
                "title",
                "Account",
                "type",
                "object"))
        .response(
            object(
                "type",
                "object",
                "description",
                "Discord 原始字段保留；items 为该次授权读取结果。",
                "properties",
                object(
                    "id",
                    object("type", "string"),
                    "username",
                    object("type", "string"),
                    "bot",
                    object("type", "boolean"))))
        .review(
            new CapabilityReview(
                null,
                "验证机器人身份",
                "保留原始字段，按调用方授权配置访问。",
                null,
                null,
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "已实现原生请求、参数验证、权限错误及凭据隔离缓存；实时事件由持久化监听供给。",
                "需要真实账号与权限验收；大量服务器部署需要额外配置分片。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/discord/DiscordAdapter.java",
                        1,
                        "public class DiscordAdapter"))))
        .build();
  }

  private static BuiltinOperation accountsList() {
    return operation("discord.accounts.list")
        .summary("发现已登记的机器人账号（不返回凭据）")
        .description("发现已登记的机器人账号（不返回凭据）")
        .quota("120/min")
        .category("消息与频道")
        .order(1)
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "maxLength",
                        100,
                        "pattern",
                        "^[\\w.-]*$",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "refresh",
                    object("default", false, "title", "Refresh", "type", "boolean")),
                "title",
                "Account",
                "type",
                "object"))
        .response(
            object(
                "type",
                "object",
                "description",
                "Discord 原始字段保留；items 为该次授权读取结果。",
                "properties",
                object("items", object("type", "array", "items", object("type", "object")))))
        .review(
            new CapabilityReview(
                null,
                "发现已登记的机器人账号（不返回凭据）",
                "保留原始字段，按调用方授权配置访问。",
                null,
                null,
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "已实现原生请求、参数验证、权限错误及凭据隔离缓存；实时事件由持久化监听供给。",
                "需要真实账号与权限验收；大量服务器部署需要额外配置分片。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/discord/DiscordAdapter.java",
                        1,
                        "public class DiscordAdapter"))))
        .build();
  }

  private static BuiltinOperation guildsList() {
    return operation("discord.guilds.list")
        .summary("列出已加入的服务器")
        .description("列出已加入的服务器")
        .quota("120/min")
        .category("消息与频道")
        .order(2)
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "maxLength",
                        100,
                        "pattern",
                        "^[\\w.-]*$",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "refresh",
                    object("default", false, "title", "Refresh", "type", "boolean")),
                "title",
                "Account",
                "type",
                "object"))
        .response(
            object(
                "type",
                "object",
                "description",
                "Discord 原始字段保留；items 为该次授权读取结果。",
                "properties",
                object("items", object("type", "array", "items", object("type", "object")))))
        .review(
            new CapabilityReview(
                null,
                "列出已加入的服务器",
                "保留原始字段，按调用方授权配置访问。",
                null,
                null,
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "已实现原生请求、参数验证、权限错误及凭据隔离缓存；实时事件由持久化监听供给。",
                "需要真实账号与权限验收；大量服务器部署需要额外配置分片。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/discord/DiscordAdapter.java",
                        1,
                        "public class DiscordAdapter"))))
        .build();
  }

  private static BuiltinOperation botsList() {
    return operation("discord.bots.list")
        .summary("列出服务器机器人托管角色")
        .description("列出服务器机器人托管角色")
        .quota("120/min")
        .category("消息与频道")
        .order(3)
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "maxLength",
                        100,
                        "pattern",
                        "^[\\w.-]*$",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "refresh",
                    object("default", false, "title", "Refresh", "type", "boolean"),
                    "guild_id",
                    object("pattern", "^\\d{15,22}$", "title", "Guild Id", "type", "string")),
                "required",
                array("guild_id"),
                "title",
                "Guild",
                "type",
                "object"))
        .response(
            object(
                "type",
                "object",
                "description",
                "Discord 原始字段保留；items 为该次授权读取结果。",
                "properties",
                object("items", object("type", "array", "items", object("type", "object")))))
        .review(
            new CapabilityReview(
                null,
                "列出服务器机器人托管角色",
                "保留原始字段，按调用方授权配置访问。",
                null,
                null,
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "已实现原生请求、参数验证、权限错误及凭据隔离缓存；实时事件由持久化监听供给。",
                "需要真实账号与权限验收；大量服务器部署需要额外配置分片。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/discord/DiscordAdapter.java",
                        1,
                        "public class DiscordAdapter"))))
        .build();
  }

  private static BuiltinOperation channelsList() {
    return operation("discord.channels.list")
        .summary("列出服务器频道")
        .description("列出服务器频道")
        .quota("120/min")
        .category("消息与频道")
        .order(4)
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "maxLength",
                        100,
                        "pattern",
                        "^[\\w.-]*$",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "refresh",
                    object("default", false, "title", "Refresh", "type", "boolean"),
                    "guild_id",
                    object("pattern", "^\\d{15,22}$", "title", "Guild Id", "type", "string")),
                "required",
                array("guild_id"),
                "title",
                "Guild",
                "type",
                "object"))
        .response(
            object(
                "type",
                "object",
                "description",
                "Discord 原始字段保留；items 为该次授权读取结果。",
                "properties",
                object("items", object("type", "array", "items", object("type", "object")))))
        .review(
            new CapabilityReview(
                null,
                "列出服务器频道",
                "保留原始字段，按调用方授权配置访问。",
                null,
                null,
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "已实现原生请求、参数验证、权限错误及凭据隔离缓存；实时事件由持久化监听供给。",
                "需要真实账号与权限验收；大量服务器部署需要额外配置分片。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/discord/DiscordAdapter.java",
                        1,
                        "public class DiscordAdapter"))))
        .build();
  }

  private static BuiltinOperation channelsGet() {
    return operation("discord.channels.get")
        .summary("读取频道及所属服务器")
        .description("读取频道及所属服务器")
        .quota("120/min")
        .category("消息与频道")
        .order(5)
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "maxLength",
                        100,
                        "pattern",
                        "^[\\w.-]*$",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "refresh",
                    object("default", false, "title", "Refresh", "type", "boolean"),
                    "channel_id",
                    object("pattern", "^\\d{15,22}$", "title", "Channel Id", "type", "string")),
                "required",
                array("channel_id"),
                "title",
                "Channel",
                "type",
                "object"))
        .response(
            object(
                "type",
                "object",
                "description",
                "Discord 原始字段保留；items 为该次授权读取结果。",
                "properties",
                object(
                    "id",
                    object("type", "string"),
                    "guild_id",
                    object("type", "string"),
                    "channel_id",
                    object("type", "string"),
                    "content",
                    object("type", "string"))))
        .review(
            new CapabilityReview(
                null,
                "读取频道及所属服务器",
                "保留原始字段，按调用方授权配置访问。",
                null,
                null,
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "已实现原生请求、参数验证、权限错误及凭据隔离缓存；实时事件由持久化监听供给。",
                "需要真实账号与权限验收；大量服务器部署需要额外配置分片。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/discord/DiscordAdapter.java",
                        1,
                        "public class DiscordAdapter"))))
        .build();
  }

  private static BuiltinOperation threadsList() {
    return operation("discord.threads.list")
        .summary("读取论坛活动和归档帖子")
        .description("读取论坛活动和归档帖子")
        .quota("120/min")
        .category("消息与频道")
        .order(6)
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "maxLength",
                        100,
                        "pattern",
                        "^[\\w.-]*$",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "refresh",
                    object("default", false, "title", "Refresh", "type", "boolean"),
                    "channel_id",
                    object("pattern", "^\\d{15,22}$", "title", "Channel Id", "type", "string"),
                    "guild_id",
                    object("pattern", "^\\d{15,22}$", "title", "Guild Id", "type", "string"),
                    "archived",
                    object("default", false, "title", "Archived", "type", "boolean"),
                    "before",
                    object("default", "", "maxLength", 64, "title", "Before", "type", "string"),
                    "limit",
                    object(
                        "default", 100, "maximum", 100, "minimum", 1, "title", "Limit", "type",
                        "integer")),
                "required",
                array("channel_id", "guild_id"),
                "title",
                "Threads",
                "type",
                "object"))
        .response(
            object(
                "type",
                "object",
                "description",
                "Discord 原始字段保留；items 为该次授权读取结果。",
                "properties",
                object("items", object("type", "array", "items", object("type", "object")))))
        .review(
            new CapabilityReview(
                null,
                "读取论坛活动和归档帖子",
                "保留原始字段，按调用方授权配置访问。",
                null,
                null,
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "已实现原生请求、参数验证、权限错误及凭据隔离缓存；实时事件由持久化监听供给。",
                "需要真实账号与权限验收；大量服务器部署需要额外配置分片。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/discord/DiscordAdapter.java",
                        1,
                        "public class DiscordAdapter"))))
        .build();
  }

  private static BuiltinOperation messagesList() {
    return operation("discord.messages.list")
        .summary("分页读取频道或帖子消息")
        .description("分页读取频道或帖子消息")
        .quota("120/min")
        .category("消息与频道")
        .order(7)
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "maxLength",
                        100,
                        "pattern",
                        "^[\\w.-]*$",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "refresh",
                    object("default", false, "title", "Refresh", "type", "boolean"),
                    "channel_id",
                    object("pattern", "^\\d{15,22}$", "title", "Channel Id", "type", "string"),
                    "before",
                    object(
                        "default",
                        "",
                        "pattern",
                        "^\\d{0,22}$",
                        "title",
                        "Before",
                        "type",
                        "string"),
                    "limit",
                    object(
                        "default", 100, "maximum", 100, "minimum", 1, "title", "Limit", "type",
                        "integer"),
                    "preview",
                    object("default", false, "title", "Preview", "type", "boolean")),
                "required",
                array("channel_id"),
                "title",
                "Messages",
                "type",
                "object"))
        .response(
            object(
                "type",
                "object",
                "description",
                "Discord 原始字段保留；items 为该次授权读取结果。",
                "properties",
                object(
                    "items",
                    object("type", "array", "items", object("type", "object")),
                    "before",
                    object("type", "string"),
                    "has_more",
                    object("type", "boolean"))))
        .review(
            new CapabilityReview(
                null,
                "分页读取频道或帖子消息",
                "保留原始字段，按调用方授权配置访问。",
                null,
                null,
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "已实现原生请求、参数验证、权限错误及凭据隔离缓存；实时事件由持久化监听供给。",
                "需要真实账号与权限验收；大量服务器部署需要额外配置分片。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/discord/DiscordAdapter.java",
                        1,
                        "public class DiscordAdapter"))))
        .build();
  }

  private static BuiltinOperation messagesGet() {
    return operation("discord.messages.get")
        .summary("读取原消息及回复上下文")
        .description("读取原消息及回复上下文")
        .quota("120/min")
        .category("消息与频道")
        .order(8)
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "maxLength",
                        100,
                        "pattern",
                        "^[\\w.-]*$",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "refresh",
                    object("default", false, "title", "Refresh", "type", "boolean"),
                    "channel_id",
                    object("pattern", "^\\d{15,22}$", "title", "Channel Id", "type", "string"),
                    "message_id",
                    object("pattern", "^\\d{15,22}$", "title", "Message Id", "type", "string")),
                "required",
                array("channel_id", "message_id"),
                "title",
                "Message",
                "type",
                "object"))
        .response(
            object(
                "type",
                "object",
                "description",
                "Discord 原始字段保留；items 为该次授权读取结果。",
                "properties",
                object(
                    "id",
                    object("type", "string"),
                    "guild_id",
                    object("type", "string"),
                    "channel_id",
                    object("type", "string"),
                    "content",
                    object("type", "string"))))
        .review(
            new CapabilityReview(
                null,
                "读取原消息及回复上下文",
                "保留原始字段，按调用方授权配置访问。",
                null,
                null,
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "已实现原生请求、参数验证、权限错误及凭据隔离缓存；实时事件由持久化监听供给。",
                "需要真实账号与权限验收；大量服务器部署需要额外配置分片。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/discord/DiscordAdapter.java",
                        1,
                        "public class DiscordAdapter"))))
        .build();
  }

  private static BuiltinOperation eventsList() {
    return operation("discord.events.list")
        .summary("读取持久化实时消息及私信事件")
        .description("读取持久化实时消息及私信事件")
        .quota("120/min")
        .category("消息与频道")
        .order(9)
        .tags("read")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "maxLength",
                        100,
                        "pattern",
                        "^[\\w.-]*$",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "refresh",
                    object("default", false, "title", "Refresh", "type", "boolean"),
                    "after",
                    object("default", 0, "minimum", 0, "title", "After", "type", "integer"),
                    "limit",
                    object(
                        "default", 200, "maximum", 500, "minimum", 1, "title", "Limit", "type",
                        "integer")),
                "title",
                "Events",
                "type",
                "object"))
        .response(
            object(
                "type",
                "object",
                "description",
                "Discord 原始字段保留；items 为该次授权读取结果。",
                "properties",
                object(
                    "items",
                    object("type", "array", "items", object("type", "object")),
                    "cursor",
                    object("type", "integer"),
                    "has_more",
                    object("type", "boolean"),
                    "listener",
                    object("type", "object"))))
        .review(
            new CapabilityReview(
                null,
                "读取持久化实时消息及私信事件",
                "保留原始字段，按调用方授权配置访问。",
                null,
                null,
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "已实现原生请求、参数验证、权限错误及凭据隔离缓存；实时事件由持久化监听供给。",
                "需要真实账号与权限验收；大量服务器部署需要额外配置分片。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/discord/DiscordEvents.java",
                        1,
                        "public class DiscordEvents"))))
        .build();
  }

  private static BuiltinOperation messagesSend() {
    return operation("discord.messages.send")
        .summary("确认后发送机器人消息")
        .description("确认后发送机器人消息")
        .quota("120/min")
        .sensitive(true)
        .idempotent(true)
        .retries(0)
        .category("消息与频道")
        .order(10)
        .tags("write")
        .params(
            object(
                "properties",
                object(
                    "account",
                    object(
                        "default",
                        "",
                        "maxLength",
                        100,
                        "pattern",
                        "^[\\w.-]*$",
                        "title",
                        "Account",
                        "type",
                        "string"),
                    "refresh",
                    object("default", false, "title", "Refresh", "type", "boolean"),
                    "channel_id",
                    object("pattern", "^\\d{15,22}$", "title", "Channel Id", "type", "string"),
                    "guild_id",
                    object(
                        "default",
                        "",
                        "pattern",
                        "^\\d{0,22}$",
                        "title",
                        "Guild Id",
                        "type",
                        "string"),
                    "content",
                    object("maxLength", 2000, "minLength", 1, "title", "Content", "type", "string"),
                    "nonce",
                    object("pattern", "^\\d{1,25}$", "title", "Nonce", "type", "string"),
                    "reply_to",
                    object(
                        "default",
                        "",
                        "pattern",
                        "^\\d{0,22}$",
                        "title",
                        "Reply To",
                        "type",
                        "string"),
                    "confirm",
                    object("default", false, "title", "Confirm", "type", "boolean")),
                "required",
                array("channel_id", "content", "nonce"),
                "title",
                "Send",
                "type",
                "object"))
        .response(
            object(
                "type",
                "object",
                "description",
                "Discord 原始字段保留；items 为该次授权读取结果。",
                "properties",
                object(
                    "id",
                    object("type", "string"),
                    "guild_id",
                    object("type", "string"),
                    "channel_id",
                    object("type", "string"),
                    "content",
                    object("type", "string"))))
        .review(
            new CapabilityReview(
                null,
                "确认后发送机器人消息",
                "保留原始字段，按调用方授权配置访问。",
                null,
                null,
                List.of(),
                CapabilityReview.Depth.ENHANCED,
                "已实现原生请求、参数验证、权限错误及凭据隔离缓存；实时事件由持久化监听供给。",
                "需要真实账号与权限验收；大量服务器部署需要额外配置分片。",
                List.of(
                    new CapabilityReview.Evidence(
                        "src/main/java/group/cyt/common/module/adapter/discord/DiscordAdapter.java",
                        1,
                        "public class DiscordAdapter"))))
        .build();
  }
}
