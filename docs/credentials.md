# 上游凭据管理

生产 Common 从部署读取 Secret Manager 注入的敏感凭据，从当前环境的 PostgreSQL 读取非敏感配置。凭据页展示绑定状态、来源和作用域。运维在 Secret Manager 中新增版本，再由 Cloud Run 以固定版本发布。

## 变量清单

`{SYSTEM}` 表示调用系统或店铺作用域，`{ACCOUNT}` 表示上游账号。实例化时使用大写，分隔符用下划线。旧的默认账号别名沿用同一分类。

### 存 Google Secret Manager

- 企业微信应用和通讯录。 `WEWORK_SECRET`、`WEWORK_CONTACT_SECRET`、`WEWORK_APP_{SYSTEM}_SECRET`、`WEWORK_APP_{SYSTEM}_CONTACT_SECRET`、`WEWORK_APP_{SYSTEM}_HR_SECRET`。审批、HR 和回调使用 `WEWORK_APPROVAL_SECRET`、`WEWORK_HR_SECRET`、`WEWORK_CALLBACK_TOKEN`、`WEWORK_CALLBACK_AES_KEY` 及对应 `WEWORK_{SYSTEM}_*` 形式。代理使用 `WEWORK_PROXY_TOKEN`。
- Shopify。 `SHOPIFY_ACCESS_TOKEN`、`SHOPIFY_{SYSTEM}_ACCESS_TOKEN`、`SHOPIFY_{SYSTEM}_CLIENT_SECRET`。
- NAS。 `NAS_MINIO_ACCESS_KEY`、`NAS_MINIO_SECRET_KEY`、`NAS_MINIO_{SYSTEM}_ACCESS_KEY`、`NAS_MINIO_{SYSTEM}_SECRET_KEY`。
- DocuSign。 `DOCUSIGN_INTEGRATION_KEY`、`DOCUSIGN_PRIVATE_KEY` 及系统专属形式，还有 `DOCUSIGN_HMAC_KEY`。
- Gmail、Drive 和 GCS。 `GOOGLE_CLIENT_SECRET`、`GOOGLE_{SYSTEM}_CLIENT_SECRET`、`GMAIL_REFRESH_TOKEN`、`GMAIL_{SYSTEM}_REFRESH_TOKEN`、`GOOGLE_REFRESH_TOKEN`、`GOOGLE_{SYSTEM}_REFRESH_TOKEN`、`GCS_SA_JSON`、`GCS_{SYSTEM}_SA_JSON`、`GOOGLE_SA_JSON`、`GOOGLE_APPLICATION_CREDENTIALS`。
- 领星、马帮和万邑通。 `LINGXING_{ACCOUNT}_APP_SECRET`、`LINGXING_PROXY_TOKEN`、`MABANG_SECRET`、`MABANG_{SYSTEM}_SECRET`、`MABANG_PROXY_TOKEN`、`ERP_PROXY_TOKEN`、`WINIT_TOKEN`、`WINIT_CLIENT_SECRET` 及生产、沙箱和系统专属变体。
- QuickCEP。 `QUICKCEP_{ACCOUNT}_ACCESS_ID`、`QUICKCEP_{ACCOUNT}_PUBLIC_KEY` 按平台凭据成组保护。
- 客服、支付及 Discord。 `JUDGEME_{ACCOUNT}_API_TOKEN`、`TRUSTPILOT_{ACCOUNT}_API_KEY`、`TRUSTPILOT_{ACCOUNT}_ACCESS_TOKEN`、`PAYPAL_{ACCOUNT}_CLIENT_ID`、`PAYPAL_{ACCOUNT}_CLIENT_SECRET`、`PAYPAL_{ACCOUNT}_WEBHOOK_ID`、`STRIPE_{ACCOUNT}_API_KEY`、`STRIPE_{ACCOUNT}_WEBHOOK_SECRET`、`RD_{ACCOUNT}_ACCESS_TOKEN`、`DISCORD_{ACCOUNT}_BOT_TOKEN`、`DISCORD_PROXY`。
- 自定义和扩展凭据。 `CUSTOM_*` 及未登记为非敏感配置的凭据。数据库保存变量名引用。迁移自定义接口时，将既有请求头或 URL 中的鉴权值改为引用。
- 部署启动。数据库连接引用、管理员密码或摘要、JWT 签名密钥、`CREDENTIAL_MASTER_KEY`、Redis 连接凭据、Portal 签名和代理密钥。网关在连接数据库前读取这些部署配置。

### 入 PostgreSQL

- 应用和账号标识。企业微信 `CORP_ID`、`AGENT_ID`，Google 和 Shopify 的 `CLIENT_ID`，DocuSign 的 `USER_ID`、`ACCOUNT_ID`，领星的 `APP_ID`、`MAIL_EMAIL`，马帮和万邑通的账号 `APP_KEY`，万邑通 `CLIENT_ID`，Trustpilot `BUSINESS_UNIT_ID`，以及这些项目的已登记作用域变体。
- 域名、地址和资源。Shopify `STORE`、`API_VERSION`，NAS `ENDPOINT`、`BUCKET`、`REGION`，GCS `BUCKET`，DocuSign `ENV`、`BASE_URI`，无鉴权信息的企业微信、领星、马帮和 ERP 代理地址，QuickCEP `DOMAIN`，Judge.me `SHOP_DOMAIN`，RD `BASE_URL`，PayPal `ENV`，万邑通 `ENV`、`PLATFORM`、`API_URL`。已有非敏感部署变量仍可作为兼容来源，数据库优先。
- 账号选择和权限。各平台的 `*_ACCOUNTS`、默认 `*_ACCOUNT`、下述共享策略，以及系统、操作授权、配额、接口配置、审计记录。
- Common 签发给业务系统的 API Key。库里保存前缀和 SHA-256 哈希。完整 Key 由调用方保管。

逐项变量与模板以 [接口契约](../CYT-Common-API/src/main/resources/contract.json)、[业务契约](../CYT-Common-API/src/main/resources/business-contract.json)、[Discord 契约](../CYT-Common-API/src/main/resources/discord-contract.json) 和 [补充分类](../CYT-Common-API/src/main/resources/credential-storage.json) 为准。

## 共享与专属

- `CRED_SCOPE_<PLATFORM>=inherit` 允许专属项缺失时使用共享项。对指定系统使用 `CRED_SCOPE_<PLATFORM>_<SYSTEM>=strict` 禁止回退，系统级策略优先。现有配置沿用平台默认策略。
- 有系统模板的平台可用 `CRED_SPLIT_<PLATFORM>` 指定按系统拆分的变量名。配套的标识和密钥应成组配置。代理等平台基础设施可继续显式共享。
- 财务企业微信专属配置。数据库保存 `CRED_SCOPE_WECOM_FINANCIAL=strict` 和 `WEWORK_APP_FINANCIAL_CORP_ID`，Cloud Run 绑定 `WEWORK_APP_FINANCIAL_SECRET`。通讯录缺少专属通讯录 Secret 时使用财务应用自己的 Secret。审批可另配 `WEWORK_FINANCIAL_APPROVAL_SECRET`。
- 多账号平台使用 `CRED_ACCOUNTS_<PLATFORM>_<SYSTEM>` 限制系统可以选用的上游账号。适用于 Stripe、PayPal、Judge.me、Trustpilot、RD、Discord、领星和 QuickCEP。严格模式要求账号在清单内，`default` 表示允许默认账号。两个系统显式登记同一个账号即可共享。
- Shopify 严格模式以系统对应的店铺为边界。Gmail、Drive 严格模式需要系统专属的 Google OAuth 客户端和对应刷新令牌，GCS 需要专属服务账号凭据。自定义 HTTP 严格模式将 `CUSTOM_VENDOR_TOKEN` 映射为 `CUSTOM_<SYSTEM>_VENDOR_TOKEN`。
- Secret Manager 的 IAM 约束 Cloud Run 的运行身份。单个 Common 实例可以读取它绑定的各系统密钥，各业务系统之间的边界由 Common 的授权和凭据策略执行。

## 绑定与上线

1. 先按环境、平台和系统核对现有生产凭据。在 `gateway_secret` 按环境盘点 `name`，给生产服务账号逐项授予所需的 Secret 读取权限。
2. 在生产“环境与凭据”登记非敏感项和共享策略。使用 [云发布入口](../deploy/gcp/README.md) 注入敏感项。批量绑定使用 `--secret-bindings-file deploy/gcp/production-secrets.json`，文件只包含变量名和固定版本引用。

```json
{
  "WEWORK_APP_FINANCIAL_SECRET": "common-prod-wecom-financial-app-secret:7",
  "WEWORK_APP_OA_SECRET": "common-prod-wecom-oa-app-secret:3",
  "NAS_MINIO_RD_ACCESS_KEY": "common-prod-nas-rd-access-key:2",
  "NAS_MINIO_RD_SECRET_KEY": "common-prod-nas-rd-secret-key:2"
}
```

3. 发布后在凭据页核对绑定状态，按调用系统验证。后台仅展示生产敏感项的状态和来源。部署已绑定同名 Secret 后，才能从后台清理旧数据库副本。
4. 轮换时先增加 Secret 版本，更新绑定并发布。验证完成后再按供应商要求撤销旧凭据。

## CS Gmail 自助授权

Java Common 和 CS 同时配置 `GMAIL_OAUTH_SECRET_PROJECT`（使用项目编号）时，CS 将完整 OAuth 客户端与邮箱刷新令牌成组写入该项目的 `CYT_CS_GMAIL_OAUTH_<HASH>` Secret。
数据库只保存 `GMAIL_OAUTH_<HASH>_BINDING` 的系统、完整邮箱和固定版本引用。HASH 为规范化系统名、换行、完整邮箱的 SHA-256 前 32 位大写十六进制。
Common 使用自身 Cloud Run 身份读取固定版本，校验引用和密钥内的系统、邮箱一致，并校验响应校验和；这条读取不经过上游请求日志。
有引用但密钥无法读取时拒绝回退；没有引用的原有邮箱继续使用已绑定的原凭据。
CS 需要邮箱 Secret 的创建和版本写入权限，Common 只需要读取权限；不需要赋予运行身份 IAM 修改权限。
此路径支持同一调用系统接入多个邮箱，生产敏感凭据仍不进入 PostgreSQL。

## 同步到本地开发环境

在 `CYT-Common-API` 中用现有的 `gcloud` 登录生成元数据计划，核对后将开发凭据加密写入本机 PostgreSQL。不同的已有值默认保留。生产使用上面的部署模式。

```powershell
python -m pip install -r tools/requirements-credentials.txt
python tools/gcloud_credentials.py plan
python tools/gcloud_credentials.py apply
```
