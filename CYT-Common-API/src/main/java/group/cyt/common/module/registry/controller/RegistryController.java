package group.cyt.common.module.registry.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.auth.service.AuthenticationService;
import group.cyt.common.module.registry.catalog.platform.TikTokCatalog;
import group.cyt.common.module.registry.model.OperationDefinition;
import group.cyt.common.module.registry.service.DocumentationContracts;
import group.cyt.common.module.registry.service.OperationRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/api")
public class RegistryController {
  private final OperationRegistry registry;
  private final GatewayStore store;
  private final DocumentationContracts contracts;

  public RegistryController(
      OperationRegistry registry, GatewayStore store, DocumentationContracts contracts) {
    this.registry = registry;
    this.store = store;
    this.contracts = contracts;
  }

  @GetMapping({"/registry", "/docs"})
  public Object registry() {
    return JsonUtils.obj(
        "operations",
        registry.operations().stream().map(contracts::describe).toList(),
        "categories",
        categories(),
        "custom_categories",
        store.rows("SELECT * FROM gateway_op_category ORDER BY sort_order,id"),
        "category_notes",
        Map.of());
  }

  @GetMapping("/capability-review")
  public Object capabilityReview() {
    try (var input = getClass().getResourceAsStream("/capability-review.json")) {
      var review = (ObjectNode) JsonUtils.MAPPER.readTree(Objects.requireNonNull(input));
      var extra = TikTokCatalog.catalog().capabilityReview();
      var items = review.withArray("items");
      items.addAll(extra.withArray("items"));
      var summary = review.withObject("/depth_summary");
      var counts = summary.withObject("/counts");
      extra.path("depth_summary").path("counts").fields()
          .forEachRemaining(entry -> counts.put(entry.getKey(),
              counts.path(entry.getKey()).asInt() + entry.getValue().asInt()));
      summary.put("total", items.size());
      summary.put("all_deep", !items.isEmpty() && counts.path("deep").asInt() == items.size());
      return review;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Cannot load capability review", e);
    }
  }

  private Object categories() {
    Map<String, Map<String, Integer>> groups = new LinkedHashMap<>();
    for (var o : registry.operations())
      groups
          .computeIfAbsent(o.platform(), k -> new LinkedHashMap<>())
          .merge(o.category(), 1, Integer::sum);
    Map<String, Object> out = new LinkedHashMap<>();
    groups.forEach(
        (p, c) ->
            out.put(
                p,
                c.entrySet().stream()
                    .map(e -> JsonUtils.obj("name", e.getKey(), "count", e.getValue(), "note", ""))
                    .toList()));
    return out;
  }

  @GetMapping("/op-order")
  public Object order() {
    return JsonUtils.obj(
        "items", store.rows("SELECT * FROM gateway_op_order ORDER BY sort_order,id"));
  }

  @PostMapping("/op-order")
  public Object saveOrder(@RequestBody Map<String, Object> body, HttpServletRequest req) {
    store.atomic(
        () -> {
          for (Object v : JsonUtils.list(body.get("items"))) {
            var item = JsonUtils.map(v);
            String key = JsonUtils.str(item, "key");
            registry.get(key);
            String category = JsonUtils.str(item, "category");
            if (category.length() > 64) throw GatewayException.invalid("分类名过长");
            var old = store.one("SELECT id FROM gateway_op_order WHERE operation_key=?", key);
            var data =
                JsonUtils.obj(
                    "operation_key",
                    key,
                    "category",
                    category,
                    "sort_order",
                    JsonUtils.integer(item.get("sort_order"), 0),
                    "updated_by",
                    AuthenticationService.actor(req),
                    "updated_at",
                    GatewayStore.now());
            if (old == null) store.insert("gateway_op_order", data);
            else store.edit("gateway_op_order", ((Number) old.get("id")).longValue(), data);
          }
          store.audit(AuthenticationService.actor(req), "operation_order", "registry", Map.of());
          return null;
        });
    return JsonUtils.obj("ok", true);
  }

  @GetMapping("/op-categories")
  public Object opCategories() {
    return JsonUtils.obj(
        "items", store.rows("SELECT * FROM gateway_op_category ORDER BY sort_order,id"));
  }

  @PostMapping("/op-categories")
  public Object createCategory(@RequestBody Map<String, Object> body, HttpServletRequest req) {
    String platform = JsonUtils.str(body, "platform"), name = JsonUtils.str(body, "name");
    if (name.isBlank() || name.length() > 64 || platform.length() > 32)
      throw GatewayException.invalid("分类名称或平台标识无效");
    return store.atomic(
        () -> {
          long id =
              store.insert(
                  "gateway_op_category",
                  JsonUtils.obj(
                      "platform",
                      platform,
                      "name",
                      name,
                      "note",
                      JsonUtils.str(body, "note"),
                      "sort_order",
                      JsonUtils.integer(body.get("sort_order"), 100),
                      "updated_by",
                      AuthenticationService.actor(req)));
          store.audit(
              AuthenticationService.actor(req), "category_create", platform + ":" + name, Map.of());
          return JsonUtils.obj("ok", true, "id", id);
        });
  }

  @DeleteMapping("/op-categories/{id}")
  public Object deleteCategory(@PathVariable long id, HttpServletRequest req) {
    store.atomic(
        () -> {
          store.update("DELETE FROM gateway_op_category WHERE id=?", id);
          store.audit(
              AuthenticationService.actor(req), "category_delete", Long.toString(id), Map.of());
          return null;
        });
    return JsonUtils.obj("ok", true);
  }

  public Object openapi() {
    return openapiFor(registry.operations());
  }

  @GetMapping("/openapi.json")
  public Object openapiByDirection(@RequestParam(defaultValue = "all") String direction) {
    var spec = openapiFor(contracts.filter(registry.operations(), direction));
    spec.put("x-cyt-direction", direction);
    return spec;
  }

  public Map<String, Object> openapiFor(List<OperationDefinition> operations) {
    Map<String, Object> paths = new LinkedHashMap<>(), schemas = new LinkedHashMap<>();
    for (var op : operations) {
      String prefix = op.key().replace('.', '_');
      Map<String, Object> schema = JsonUtils.map(JsonUtils.parse(op.schema().toString()));
      Map<String, Object> defs = JsonUtils.map(schema.remove("$defs"));
      defs.forEach(
          (name, definition) -> schemas.put(prefix + "_" + name, rewrite(definition, prefix)));
      schemas.put(prefix, rewrite(schema, prefix));
      paths.put(
          op.path(),
          JsonUtils.obj(
              op.httpMethod().toLowerCase(Locale.ROOT),
              JsonUtils.obj(
                  "operationId",
                  prefix,
                  "x-cyt-operation",
                  op.key(),
                  "x-cyt-category",
                  op.category(),
                  "x-cyt-direction",
                  contracts.direction(op),
                  "x-cyt-read-only",
                  op.readOnly(),
                  "x-cyt-idempotency-required",
                  op.tags() != null && op.tags().contains("idempotency_required"),
                  "summary",
                  op.summary(),
                  "description",
                  op.description(),
                  "tags",
                  List.of(op.platform()),
                  "security",
                  List.of(JsonUtils.obj("BearerAuth", List.of())),
                  "parameters",
                  List.of(
                      JsonUtils.obj(
                          "name",
                          "Idempotency-Key",
                          "in",
                          "header",
                          "required",
                          false,
                          "schema",
                          JsonUtils.obj("type", "string", "maxLength", 128))),
                  "requestBody",
                  JsonUtils.obj(
                      "required",
                      true,
                      "content",
                      JsonUtils.obj(
                          "application/json",
                          JsonUtils.obj(
                              "schema", JsonUtils.obj("$ref", "#/components/schemas/" + prefix)))),
                  "responses",
                  JsonUtils.obj(
                      "200",
                      JsonUtils.obj(
                          "description",
                          "统一成功响应：ok / data / meta",
                          "content",
                          JsonUtils.obj(
                              "application/json",
                              JsonUtils.obj("schema", responseSchema(op, prefix, schemas)))),
                      "401",
                      JsonUtils.obj("description", "身份认证失败"),
                      "403",
                      JsonUtils.obj("description", "操作未获授权"),
                      "422",
                      JsonUtils.obj("description", "参数校验失败"),
                      "429",
                      JsonUtils.obj("description", "配额耗尽")))));
    }
    for (var op : operations) {
      if (!op.rawBinary()) continue;
      var item = JsonUtils.map(paths.get(op.path()));
      var upload = JsonUtils.map(item.get("put"));
      upload.put("x-cyt-transport", op.transport());
      upload.put("x-cyt-sensitive", true);
      var parameters = new ArrayList<Object>();
      op.schema()
          .path("properties")
          .fields()
          .forEachRemaining(
              field ->
                  parameters.add(
                      JsonUtils.obj(
                          "name",
                          field.getKey(),
                          "in",
                          "query",
                          "required",
                          field.getKey().equals("filename"),
                          "schema",
                          JsonUtils.parse(field.getValue().toString()))));
      parameters.add(
          JsonUtils.obj(
              "name",
              "Content-Length",
              "in",
              "header",
              "required",
              true,
              "schema",
              JsonUtils.obj("type", "integer", "minimum", 0),
              "description",
              "准确的文件字节数；不支持 Transfer-Encoding/chunked"));
      upload.put("parameters", parameters);
      upload.put(
          "requestBody",
          JsonUtils.obj(
              "required",
              true,
              "content",
              JsonUtils.obj(
                  "application/octet-stream",
                  JsonUtils.obj("schema", JsonUtils.obj("type", "string", "format", "binary")))));
      var responses = JsonUtils.map(upload.get("responses"));
      for (var error :
          Map.of(
                  "411",
                  "必须提供准确 Content-Length，不支持 chunked",
                  "413",
                  "文件超过大小限制",
                  "502",
                  "NAS 上游错误",
                  "503",
                  "未配置 NAS 凭据或网关不可用",
                  "504",
                  "文件上传超时")
              .entrySet())
        responses.put(error.getKey(), JsonUtils.obj("description", error.getValue()));
      upload.put("responses", responses);
      item.put("put", upload);
      paths.put(op.path(), item);
    }
    return JsonUtils.obj(
        "openapi",
        "3.1.0",
        "info",
        JsonUtils.obj("title", "CYT Common API", "version", "1.0.0"),
        "paths",
        paths,
        "components",
        JsonUtils.obj(
            "securitySchemes",
            JsonUtils.obj("BearerAuth", JsonUtils.obj("type", "http", "scheme", "bearer")),
            "schemas",
            schemas));
  }

  private Object responseSchema(
      OperationDefinition op, String prefix, Map<String, Object> schemas) {
    var schema = contracts.envelope(op);
    var defs = JsonUtils.map(schema.remove("$defs"));
    defs.forEach(
        (name, definition) ->
            schemas.put(prefix + "_response_" + name, rewrite(definition, prefix + "_response")));
    return rewrite(schema, prefix + "_response");
  }

  private Object rewrite(Object v, String prefix) {
    if (v instanceof Map<?, ?> m) {
      Map<String, Object> r = new LinkedHashMap<>();
      m.forEach(
          (k, value) ->
              r.put(
                  k.toString(),
                  k.equals("$ref") && value.toString().startsWith("#/$defs/")
                      ? "#/components/schemas/" + prefix + "_" + value.toString().substring(8)
                      : rewrite(value, prefix)));
      return r;
    }
    if (v instanceof List<?> l) return l.stream().map(x -> rewrite(x, prefix)).toList();
    return v;
  }

  @GetMapping(value = "/docs.html", produces = "text/html")
  public ResponseEntity<String> html() {
    StringBuilder html =
        new StringBuilder(
            "<!doctype html><html lang=\"zh-CN\"><meta charset=\"utf-8\"><title>CYT Common API 接入文档</title><style>body{max-width:1000px;margin:40px auto;padding:0 24px;font:14px/1.8 system-ui;color:#30364b}section{border-top:1px solid #ddd;padding:20px 0}pre{overflow:auto;background:#f6f7fb;padding:20px;font:12px/1.7 monospace}h1{font-size:26px}h2{font-size:18px}</style><h1>CYT Common API 接入文档</h1><p>认证使用 Authorization: Bearer &lt;系统 API Key 或 Google ID token&gt;。JSON 操作使用 POST；原始文件上传使用 PUT，参数位于 query，必须提供准确 Content-Length。</p>");
    for (var op : registry.operations())
      html.append("<section><h2>")
          .append(escape(op.summary()))
          .append("</h2><p>")
          .append(escape(op.httpMethod() + " " + op.path()))
          .append("</p><p>")
          .append(escape(op.description()))
          .append("</p><pre>")
          .append(escape(JsonUtils.write(op.schema())))
          .append("</pre></section>");
    return ResponseEntity.ok()
        .header("Content-Disposition", "attachment; filename=cyt-common-api.html")
        .body(html.append("</html>").toString());
  }

  private static String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
