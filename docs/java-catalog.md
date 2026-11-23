# Java 内置接口目录

内置接口由 `CYT-Common-API/src/main/java/group/cyt/common/module/registry/catalog/` 维护。原来的七份运行资源 JSON 已移除，Java 是唯一的内置定义来源。

## 组织方式

- `platform/*Catalog.java`：每个平台一个类；每个接口一个方法，集中声明元数据、入参规则、响应说明和能力审阅。平台凭据及旧名称别名也在该类登记。
- `BuiltinOperation`：使用有名称的 Java builder 方法声明限流、超时、敏感权限、幂等、分类等信息，生成现有 `OperationDefinition`。
- `ContractNodes`：以 Java 对象构造标准 JSON Schema，继续供原有参数校验、调试表单和 OpenAPI 使用。不会解析 JSON 文件或 JSON 字符串。
- `CapabilityReview`：使用 Java record 和深度枚举维护说明；接口标识、平台和摘要直接取接口定义，数量及各深度统计自动计算。
- `CatalogDefaults`：本地演示调用方和授权种子；生产默认不生成演示数据。
- `ShopifyQuery`：GraphQL 查询及 mutation 使用 Java enum 和文本块；适配器通过枚举引用，拼错名称会编译失败。

目录在进程内只构建一次。构造时拒绝重复接口、重复凭据变量、缺少平台、无效别名及不存在的推荐接口；导出快照和参数结构使用副本，调用方修改副本不会污染共享目录。

## 新增或调整内置接口

1. 在对应平台的 `*Catalog` 中添加或修改接口方法，并在其 `register` 方法登记。
2. 使用 `operation("platform.resource.action")` builder 设置摘要、参数和执行策略；默认路径从标识生成，旧接口的特殊路径可以显式指定。
3. `.params(object(...))` 保留标准 JSON Schema 规则；`.response(object(...))` 登记已明确的业务响应。未登记响应的接口继续明确显示“尚未登记”，不从模拟数据推测。
4. `.review(new CapabilityReview(...))` 声明实际行为、限制和代码证据；新增接口必须同时登记审阅。
5. 在平台适配器实现执行逻辑，补充独立的参数兼容夹具及行为测试。测试目录里的 JSON 是输入与期望值，不参与生产目录加载。

新增平台时实现 `PlatformCatalog`，并在 `BuiltinCatalog.Standard` 的列表中登记一次；凭据和别名跟随该平台类维护。普通 HTTP 接口仍可通过后台存入 PostgreSQL，不需要发布 Java 代码。复杂签名、业务编排和原生协议使用 Java 适配器。

## 构建、导出与验证

```sh
cd CYT-Common-API
mvn test
mvn package -DskipTests
java -jar target/common-api-1.0.0-SNAPSHOT.jar --export-catalog
python tools/export_catalog.py --output target/catalog-export.json
```

`--export-catalog` 在 Spring 启动前输出已编译目录，不连接数据库、不读取本机私密配置。JSON 是从 Java 派生的交换格式，导出文件不作为运行时输入。凭据同步工具也使用这个导出入口，因此执行前需要重新打包 JAR。

原 Python 契约及能力审阅生成器已移除。历史参数兼容夹具继续保留，Java 目录、兼容夹具和审阅覆盖必须一致，防止出现只改其中一份的情况。

## Java 与 JSON 的使用边界

当前内置目录和适配器随版本一起发布，Java 便于重构、类型检查和统一审阅。后台可编辑的自定义接口存在数据库中。API 请求、响应、OpenAPI 及标准 JSON Schema 继续采用 JSON 协议；本地和生产环境配置使用 YAML。

若以后确实需要让多个语言服务独立加载同一份配置，或由非开发人员在发布之外维护版本化契约，可以再增加外部契约发布机制；当前只维护 Java 源定义及派生导出。
