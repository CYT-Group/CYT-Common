package group.cyt.common.module.adapter.nas;

import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.http.BinaryUpload;
import group.cyt.common.core.http.UpstreamHttp;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.adapter.PlatformAdapter;
import group.cyt.common.module.credential.service.CredentialService;
import group.cyt.common.module.registry.model.OperationDefinition;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;

/** Path-style S3 SigV4, including endpoints behind a reverse-proxy path prefix. */
@Component
public class NasAdapter implements PlatformAdapter {
  private record Config(URI endpoint, String access, String secret, String region, String bucket) {}

  private static final int MAX_BATCH_FOLDERS = 100;

  private final CredentialService credentials;
  private final UpstreamHttp http;

  public NasAdapter(CredentialService credentials, UpstreamHttp http) {
    this.credentials = credentials;
    this.http = http;
  }

  public boolean supports(String platform) {
    return platform.equals("nas");
  }

  public Object invoke(OperationDefinition op, Map<String, Object> p, String system) {
    if (op.rawBinary()) throw GatewayException.invalid("此操作需要 PUT 原始文件流");
    Config cfg = config(p, system);
    String key = op.key().substring(4), bucket = cfg.bucket();
    return switch (key) {
      case "objects.presign_get" -> {
        List<Object> items = new ArrayList<>();
        for (Object object : JsonUtils.list(p.get("keys"))) {
          String name = validKey(object.toString());
          items.add(
              JsonUtils.obj(
                  "key",
                  name,
                  "url",
                  presign(cfg, "GET", bucket, name, JsonUtils.integer(p.get("expires"), 3600))));
        }
        yield JsonUtils.obj(
            "bucket",
            bucket,
            "items",
            items,
            "count",
            items.size(),
            "expires_in",
            p.get("expires"));
      }
      case "objects.presign_put" -> {
        String name = JsonUtils.str(p, "key"), filename = JsonUtils.str(p, "filename");
        if (name.isBlank()) {
          String folder = JsonUtils.str(p, "folder").replaceAll("^/+|/+$", "");
          String extension =
              filename.contains(".")
                  ? filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT)
                  : "";
          if (extension.isBlank()) throw GatewayException.invalid("文件名需要扩展名");
          name =
              (folder.isBlank() ? "" : folder + "/")
                  + LocalDate.now(ZoneOffset.UTC)
                  + "/"
                  + UUID.randomUUID()
                  + extension;
        }
        name = validKey(name);
        String mime = java.net.URLConnection.guessContentTypeFromName(filename);
        yield JsonUtils.obj(
            "bucket",
            bucket,
            "key",
            name,
            "upload_url",
            presign(cfg, "PUT", bucket, name, JsonUtils.integer(p.get("expires"), 3600)),
            "method",
            "PUT",
            "content_type",
            mime == null ? "application/octet-stream" : mime,
            "expires_in",
            p.get("expires"));
      }
      case "objects.list" -> list(cfg, p);
      case "folders.create_batch" ->
          createFolderBatch(p, bucket, (name, deadline) -> createFolder(cfg, name, deadline));
      case "objects.stat" -> {
        String name = validKey(JsonUtils.str(p, "key"));
        var response = send(cfg, "HEAD", bucket, name, Map.of(), Map.of(), null);
        yield JsonUtils.obj(
            "bucket",
            bucket,
            "key",
            name,
            "size_bytes",
            Long.parseLong(response.headers().getOrDefault("Content-Length", "0")),
            "content_type",
            response.headers().get("Content-Type"),
            "etag",
            response.headers().getOrDefault("ETag", "").replace("\"", ""),
            "last_modified",
            response.headers().get("Last-Modified"));
      }
      case "objects.read_head" -> {
        String name = validKey(JsonUtils.str(p, "key"));
        int limit = JsonUtils.integer(p.get("head_bytes"), 4096);
        var response =
            send(
                cfg,
                "GET",
                bucket,
                name,
                Map.of(),
                Map.of("Range", "bytes=0-" + (limit - 1)),
                null);
        String range = response.headers().getOrDefault("Content-Range", "");
        long size =
            range.contains("/")
                ? Long.parseLong(range.substring(range.lastIndexOf('/') + 1))
                : response.body().length;
        byte[] head = Arrays.copyOf(response.body(), Math.min(limit, response.body().length));
        yield JsonUtils.obj(
            "bucket",
            bucket,
            "key",
            name,
            "size_bytes",
            size,
            "returned_bytes",
            head.length,
            "content_type",
            response.headers().get("Content-Type"),
            "content_base64",
            Base64.getEncoder().encodeToString(head),
            "inspection",
            FileHeaderInspector.inspect(
                head, response.headers().get("Content-Type"), head.length < size));
      }
      case "objects.delete" -> {
        String name = validKey(JsonUtils.str(p, "key"));
        send(cfg, "DELETE", bucket, name, Map.of(), Map.of(), null);
        yield JsonUtils.obj("bucket", bucket, "key", name, "deleted", true);
      }
      case "objects.delete_batch" ->
          delete(cfg, JsonUtils.list(p.get("keys")).stream().map(Object::toString).toList());
      case "objects.copy" ->
          copy(
              cfg,
              JsonUtils.str(p, "source_bucket", bucket),
              validKey(JsonUtils.str(p, "source_key")),
              JsonUtils.str(p, "dest_bucket", bucket),
              validKey(JsonUtils.str(p, "dest_key")));
      case "objects.copy_prefix" -> copyPrefix(cfg, p);
      case "buckets.list" -> {
        Document doc = xml(send(cfg, "GET", "", "", Map.of(), Map.of(), null).body());
        List<Object> items = new ArrayList<>();
        for (Element el : elements(doc, "Bucket"))
          items.add(
              JsonUtils.obj("name", text(el, "Name"), "creation_date", text(el, "CreationDate")));
        yield JsonUtils.obj("buckets", items, "count", items.size());
      }
      case "buckets.create" -> {
        String region = JsonUtils.str(p, "region", cfg.region());
        if (region.isBlank()) region = cfg.region();
        byte[] body =
            region.equals("us-east-1")
                ? null
                : ("<CreateBucketConfiguration><LocationConstraint>"
                        + escape(region)
                        + "</LocationConstraint></CreateBucketConfiguration>")
                    .getBytes(StandardCharsets.UTF_8);
        send(cfg, "PUT", bucket, "", Map.of(), Map.of(), body);
        yield JsonUtils.obj("bucket", bucket, "created", true, "region", region);
      }
      case "buckets.delete" -> {
        send(cfg, "DELETE", bucket, "", Map.of(), Map.of(), null);
        yield JsonUtils.obj("bucket", bucket, "deleted", true);
      }
      default -> throw GatewayException.config("未知 NAS 操作");
    };
  }

  private Config config(Map<String, Object> p, String system) {
    String endpoint = credentials.required("NAS_MINIO_ENDPOINT", system);
    URI uri = URI.create(endpoint);
    if (uri.getHost() == null
        || !Set.of("http", "https").contains(uri.getScheme())
        || uri.getUserInfo() != null) throw GatewayException.config("NAS endpoint 无效");
    String bucket = JsonUtils.str(p, "bucket");
    if (bucket.isBlank()) bucket = credentials.resolve("NAS_MINIO_BUCKET", system);
    if (bucket.isBlank()) bucket = "cyt-training";
    validBucket(bucket);
    String region = credentials.resolve("NAS_MINIO_REGION", system);
    return new Config(
        uri,
        credentials.required("NAS_MINIO_ACCESS_KEY", system),
        credentials.required("NAS_MINIO_SECRET_KEY", system),
        region.isBlank() ? "us-east-1" : region,
        bucket);
  }

  public Object upload(Map<String, Object> p, String system, BinaryUpload source, boolean mock) {
    String filename = JsonUtils.str(p, "filename"), name = JsonUtils.str(p, "key");
    if (filename.isBlank() || filename.length() > 200 || filename.matches(".*[\\x00-\\x1f\\x7f].*"))
      throw GatewayException.invalid("filename 必须为 1–200 字符的文件名");
    if (name.isBlank()) {
      String folder = JsonUtils.str(p, "folder").replaceAll("^/+|/+$", "");
      String extension =
          filename.contains(".")
              ? filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT)
              : "";
      if (extension.isBlank()) throw GatewayException.invalid("文件名需要扩展名，或提供完整 key");
      name =
          (folder.isBlank() ? "" : folder + "/")
              + LocalDate.now(ZoneOffset.UTC)
              + "/"
              + UUID.randomUUID()
              + extension;
    }
    name = validKey(name);
    String bucket, etag;
    if (mock) {
      bucket = JsonUtils.str(p, "bucket");
      if (bucket.isBlank()) bucket = credentials.resolve("NAS_MINIO_BUCKET", system);
      if (bucket.isBlank()) bucket = "cyt-training";
      validBucket(bucket);
      try {
        source.writeTo(java.io.OutputStream.nullOutputStream());
      } catch (java.io.IOException e) {
        throw GatewayException.invalid("文件接收中断");
      }
      etag = "mock-upload";
    } else {
      Config cfg = config(p, system);
      bucket = cfg.bucket();
      Map<String, String> supplied =
          Map.of(
              "Content-Length",
              Long.toString(source.length()),
              "Content-Type",
              source.contentType());
      String target =
          cfg.endpoint().getScheme()
              + "://"
              + cfg.endpoint().getRawAuthority()
              + path(cfg, bucket, name);
      var response =
          http.upload(
              target,
              signedHeaders(
                  cfg, "PUT", path(cfg, bucket, name), Map.of(), supplied, "UNSIGNED-PAYLOAD"),
              source);
      etag = response.headers().getOrDefault("ETag", "").replace("\"", "");
    }
    var result =
        JsonUtils.obj(
            "bucket",
            bucket,
            "key",
            name,
            "size_bytes",
            source.length(),
            "content_type",
            source.contentType(),
            "etag",
            etag);
    if (mock) result.put("_mock", true);
    return result;
  }

  public static Object mockCreateFolders(Map<String, Object> p) {
    String bucket = JsonUtils.str(p, "bucket");
    if (bucket.isBlank()) bucket = "cyt-training";
    validBucket(bucket);
    var result = createFolderBatch(p, bucket, (key, deadline) -> true);
    result.put("_mock", true);
    return result;
  }

  private static Map<String, Object> createFolderBatch(
      Map<String, Object> p, String bucket, BiPredicate<String, Long> createFolder) {
    var parents = folderPlan(p);
    var failed = new HashMap<String, Boolean>();
    List<Object> items = new ArrayList<>();
    int created = 0;
    int exists = 0;
    // Finish collecting per-folder outcomes before the gateway's 120-second timeout.
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(110);
    for (var folder : parents.entrySet()) {
      String key = folder.getKey();
      var item = JsonUtils.obj("key", key);
      items.add(item);
      try {
        Boolean parentRetryable = failed.get(folder.getValue());
        if (parentRetryable != null)
          throw new GatewayException(502, "upstream_error", "父目录未确认创建", parentRetryable)
              .code("PARENT_FOLDER_FAILED");
        if (createFolder.test(key, deadline)) {
          item.put("status", "created");
          created++;
        } else {
          item.put("status", "exists");
          exists++;
        }
      } catch (GatewayException e) {
        boolean retryable = e.retryable || "UPSTREAM_HTTP_409".equals(e.code);
        failed.put(key, retryable);
        item.put("status", "failed");
        item.put(
            "error",
            JsonUtils.obj(
                "code",
                e.code == null ? e.type : e.code,
                "message",
                e.getMessage(),
                "retryable",
                retryable));
      }
    }
    return JsonUtils.obj(
        "bucket",
        bucket,
        "items",
        items,
        "total",
        items.size(),
        "created",
        created,
        "exists",
        exists,
        "failed",
        failed.size());
  }

  private static Map<String, String> folderPlan(Map<String, Object> p) {
    var paths = JsonUtils.list(p.get("paths"));
    if (paths.isEmpty() || paths.size() > MAX_BATCH_FOLDERS)
      throw GatewayException.invalid("paths 必须包含 1–100 个目录路径");
    String base = JsonUtils.str(p, "base_prefix");
    if (!base.isEmpty()) base = folderPrefix(base);
    var parents = new LinkedHashMap<String, String>();
    for (Object value : paths) {
      if (!(value instanceof String path)) throw GatewayException.invalid("paths 必须为字符串数组");
      String prefix = folderPrefix(path);
      validKey(base + prefix);
      String parent = base;
      for (int end = prefix.indexOf('/'); end >= 0; end = prefix.indexOf('/', end + 1)) {
        String key = base + prefix.substring(0, end + 1);
        parents.putIfAbsent(key, parent);
        if (parents.size() > MAX_BATCH_FOLDERS)
          throw GatewayException.invalid("补齐父目录后单批最多 100 个目录");
        parent = key;
      }
    }
    return parents;
  }

  private static String folderPrefix(String path) {
    String name = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    if (name.indexOf('\\') >= 0
        || name.codePoints().anyMatch(Character::isISOControl)
        || !StandardCharsets.UTF_8.newEncoder().canEncode(name)
        || Arrays.stream(name.split("/", -1))
            .anyMatch(segment -> segment.isBlank() || segment.equals(".") || segment.equals("..")))
      throw GatewayException.invalid("目录须使用相对路径，禁止空路径段、.、..、反斜杠或控制字符");
    return name + "/";
  }

  private boolean createFolder(Config cfg, String key, long deadline) {
    // A prefix containing a file is already visible as a folder even without a marker object.
    var listing =
        send(
            cfg,
            "GET",
            cfg.bucket(),
            "",
            Map.of("list-type", "2", "max-keys", "1", "prefix", key, "encoding-type", "url"),
            Map.of(),
            null,
            folderTimeout(deadline));
    var document = xml(listing.body());
    if (!"ListBucketResult".equals(document.getDocumentElement().getLocalName()))
      throw new GatewayException(502, "upstream_error", "NAS 返回了无效的目录列表", false);
    if (!elements(document, "Contents").isEmpty()) return false;
    try {
      send(
          cfg,
          "PUT",
          cfg.bucket(),
          key,
          Map.of(),
          Map.of("if-none-match", "*", "Content-Type", "application/octet-stream"),
          new byte[0],
          folderTimeout(deadline));
      return true;
    } catch (GatewayException e) {
      if ("UPSTREAM_HTTP_412".equals(e.code)) return false;
      throw e;
    }
  }

  private static double folderTimeout(long deadline) {
    double remaining = (deadline - System.nanoTime()) / 1_000_000_000.0;
    if (Thread.currentThread().isInterrupted() || remaining < 1)
      throw new GatewayException(504, "upstream_timeout", "批量建目录已到执行时限", true)
          .code("FOLDER_BATCH_TIMEOUT");
    return Math.min(5, remaining);
  }

  private Object list(Config cfg, Map<String, Object> p) {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("list-type", "2");
    query.put("max-keys", JsonUtils.str(p, "max_keys", "1000"));
    if (!JsonUtils.str(p, "prefix").isBlank()) query.put("prefix", JsonUtils.str(p, "prefix"));
    if (!JsonUtils.bool(p.get("recursive")))
      query.put("delimiter", JsonUtils.str(p, "delimiter", "/"));
    if (!JsonUtils.str(p, "continuation_token").isBlank())
      query.put("continuation-token", JsonUtils.str(p, "continuation_token"));
    Document doc = xml(send(cfg, "GET", cfg.bucket(), "", query, Map.of(), null).body());
    List<Object> items = new ArrayList<>(), prefixes = new ArrayList<>();
    for (Element e : elements(doc, "Contents"))
      items.add(
          JsonUtils.obj(
              "key",
              text(e, "Key"),
              "size_bytes",
              Long.parseLong(text(e, "Size")),
              "last_modified",
              text(e, "LastModified"),
              "etag",
              text(e, "ETag").replace("\"", "")));
    for (Element e : elements(doc, "CommonPrefixes")) prefixes.add(text(e, "Prefix"));
    return JsonUtils.obj(
        "bucket",
        cfg.bucket(),
        "items",
        items,
        "prefixes",
        prefixes,
        "count",
        items.size(),
        "truncated",
        Boolean.parseBoolean(text(doc.getDocumentElement(), "IsTruncated")),
        "next_token",
        emptyNull(text(doc.getDocumentElement(), "NextContinuationToken")));
  }

  private Object copy(Config cfg, String fromBucket, String from, String toBucket, String to) {
    if (fromBucket.isBlank()) fromBucket = cfg.bucket();
    if (toBucket.isBlank()) toBucket = cfg.bucket();
    validBucket(fromBucket);
    validBucket(toBucket);
    var response =
        send(
            cfg,
            "PUT",
            toBucket,
            to,
            Map.of(),
            Map.of("x-amz-copy-source", "/" + encode(fromBucket) + "/" + encodePath(from)),
            null);
    Document doc = xml(response.body());
    if (!elements(doc, "Error").isEmpty())
      throw new GatewayException(502, "upstream_error", "NAS 拒绝复制对象", false);
    return JsonUtils.obj(
        "source",
        fromBucket + "/" + from,
        "bucket",
        toBucket,
        "key",
        to,
        "copied",
        true,
        "etag",
        text(doc.getDocumentElement(), "ETag").replace("\"", ""));
  }

  private Object delete(Config cfg, List<String> keys) {
    keys.forEach(NasAdapter::validKey);
    List<Object> deleted = new ArrayList<>(), errors = new ArrayList<>();
    for (int i = 0; i < keys.size(); i += 200) {
      StringBuilder b = new StringBuilder("<Delete>");
      for (String key : keys.subList(i, Math.min(i + 200, keys.size())))
        b.append("<Object><Key>").append(escape(key)).append("</Key></Object>");
      b.append("</Delete>");
      byte[] body = b.toString().getBytes(StandardCharsets.UTF_8);
      Document doc =
          xml(
              send(
                      cfg,
                      "POST",
                      cfg.bucket(),
                      "",
                      Map.of("delete", ""),
                      Map.of(
                          "Content-Type",
                          "application/xml",
                          "Content-MD5",
                          Base64.getEncoder().encodeToString(JsonUtils.digest("MD5", body))),
                      body)
                  .body());
      for (Element e : elements(doc, "Deleted")) deleted.add(text(e, "Key"));
      for (Element e : elements(doc, "Error"))
        errors.add(
            JsonUtils.obj(
                "key", text(e, "Key"), "code", text(e, "Code"), "message", text(e, "Message")));
    }
    return JsonUtils.obj(
        "bucket", cfg.bucket(), "deleted", deleted, "errors", errors, "count", deleted.size());
  }

  private Object copyPrefix(Config cfg, Map<String, Object> p) {
    String source = validKey(JsonUtils.str(p, "source_prefix").replaceAll("^/+|/+$", "") + "/"),
        dest = validKey(JsonUtils.str(p, "dest_prefix").replaceAll("^/+|/+$", "") + "/");
    if (dest.startsWith(source)) throw GatewayException.invalid("目标目录不能位于源目录内或与源目录相同");
    List<String> keys = new ArrayList<>();
    String token = "";
    do {
      var page =
          JsonUtils.map(
              list(
                  cfg,
                  JsonUtils.obj(
                      "prefix",
                      source,
                      "recursive",
                      true,
                      "max_keys",
                      1000,
                      "continuation_token",
                      token)));
      for (Object item : JsonUtils.list(page.get("items")))
        keys.add(JsonUtils.str(JsonUtils.map(item), "key"));
      String next = JsonUtils.str(page, "next_token");
      if (next.equals(token) && JsonUtils.bool(page.get("truncated")))
        throw new GatewayException(502, "upstream_error", "NAS 返回重复游标", false);
      token = JsonUtils.bool(page.get("truncated")) ? next : "";
      if (keys.size() > 50000) throw GatewayException.invalid("目录超过 50000 个对象，请分批操作");
    } while (!token.isBlank());
    List<Object> failed = new ArrayList<>();
    int copied = 0;
    for (String key : keys)
      try {
        copy(cfg, cfg.bucket(), key, cfg.bucket(), dest + key.substring(source.length()));
        copied++;
      } catch (GatewayException e) {
        failed.add(JsonUtils.obj("key", key, "error", e.type));
      }
    int deleted = 0;
    if (JsonUtils.bool(p.get("delete_source")) && failed.isEmpty() && !keys.isEmpty()) {
      var r = JsonUtils.map(delete(cfg, keys));
      deleted = JsonUtils.list(r.get("deleted")).size();
      failed.addAll(JsonUtils.list(r.get("errors")));
    }
    return JsonUtils.obj(
        "bucket",
        cfg.bucket(),
        "source_prefix",
        source,
        "dest_prefix",
        dest,
        "total",
        keys.size(),
        "copied",
        copied,
        "deleted",
        deleted,
        "failed",
        failed);
  }

  private UpstreamHttp.ResponseData send(
      Config cfg,
      String method,
      String bucket,
      String key,
      Map<String, String> query,
      Map<String, String> supplied,
      byte[] body) {
    return send(cfg, method, bucket, key, query, supplied, body, 120);
  }

  private UpstreamHttp.ResponseData send(
      Config cfg,
      String method,
      String bucket,
      String key,
      Map<String, String> query,
      Map<String, String> supplied,
      byte[] body,
      double timeout) {
    String path = path(cfg, bucket, key),
        payload =
            HexFormat.of()
                .formatHex(JsonUtils.digest("SHA-256", body == null ? new byte[0] : body));
    Map<String, String> headers = signedHeaders(cfg, method, path, query, supplied, payload);
    String url = cfg.endpoint().getScheme() + "://" + cfg.endpoint().getRawAuthority() + path;
    String q = canonicalQuery(query);
    if (!q.isBlank()) url += "?" + q;
    return http.exchange(method, url, headers, body, timeout, null);
  }

  private Map<String, String> signedHeaders(
      Config cfg,
      String method,
      String path,
      Map<String, String> query,
      Map<String, String> supplied,
      String payload) {
    Instant now = Instant.now();
    String
        date =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(now),
        day = date.substring(0, 8),
        scope = day + "/" + cfg.region() + "/s3/aws4_request";
    SortedMap<String, String> signed = new TreeMap<>();
    signed.put("host", cfg.endpoint().getRawAuthority());
    signed.put("x-amz-date", date);
    signed.put("x-amz-content-sha256", payload);
    supplied.forEach((k, v) -> signed.put(k.toLowerCase(Locale.ROOT), v.trim()));
    String names = String.join(";", signed.keySet());
    String canonicalHeaders =
        signed.entrySet().stream()
            .map(e -> e.getKey() + ":" + e.getValue() + "\n")
            .reduce("", String::concat);
    String canonical =
        method
            + "\n"
            + path
            + "\n"
            + canonicalQuery(query)
            + "\n"
            + canonicalHeaders
            + "\n"
            + names
            + "\n"
            + payload;
    String stringToSign =
        "AWS4-HMAC-SHA256\n" + date + "\n" + scope + "\n" + JsonUtils.sha(canonical);
    String signature =
        HexFormat.of()
            .formatHex(
                JsonUtils.hmac(
                    signingKey(cfg.secret(), day, cfg.region()),
                    stringToSign.getBytes(StandardCharsets.UTF_8)));
    Map<String, String> headers = new HashMap<>(signed);
    headers.put(
        "Authorization",
        "AWS4-HMAC-SHA256 Credential="
            + cfg.access()
            + "/"
            + scope
            + ", SignedHeaders="
            + names
            + ", Signature="
            + signature);
    return headers;
  }

  private String presign(Config cfg, String method, String bucket, String key, int expires) {
    if (expires < 1 || expires > 604800) throw GatewayException.invalid("签名有效期必须为 1–604800 秒");
    String
        date =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now()),
        day = date.substring(0, 8),
        scope = day + "/" + cfg.region() + "/s3/aws4_request",
        path = path(cfg, bucket, key);
    Map<String, String> query =
        new HashMap<>(
            Map.of(
                "X-Amz-Algorithm",
                "AWS4-HMAC-SHA256",
                "X-Amz-Credential",
                cfg.access() + "/" + scope,
                "X-Amz-Date",
                date,
                "X-Amz-Expires",
                Integer.toString(expires),
                "X-Amz-SignedHeaders",
                "host"));
    String canonical =
        method
            + "\n"
            + path
            + "\n"
            + canonicalQuery(query)
            + "\nhost:"
            + cfg.endpoint().getRawAuthority()
            + "\n\nhost\nUNSIGNED-PAYLOAD";
    String toSign = "AWS4-HMAC-SHA256\n" + date + "\n" + scope + "\n" + JsonUtils.sha(canonical);
    query.put(
        "X-Amz-Signature",
        HexFormat.of()
            .formatHex(
                JsonUtils.hmac(
                    signingKey(cfg.secret(), day, cfg.region()),
                    toSign.getBytes(StandardCharsets.UTF_8))));
    return cfg.endpoint().getScheme()
        + "://"
        + cfg.endpoint().getRawAuthority()
        + path
        + "?"
        + canonicalQuery(query);
  }

  public static byte[] signingKey(String secret, String day, String region) {
    byte[] k =
        JsonUtils.hmac(
            ("AWS4" + secret).getBytes(StandardCharsets.UTF_8),
            day.getBytes(StandardCharsets.UTF_8));
    k = JsonUtils.hmac(k, region.getBytes(StandardCharsets.UTF_8));
    k = JsonUtils.hmac(k, "s3".getBytes(StandardCharsets.UTF_8));
    return JsonUtils.hmac(k, "aws4_request".getBytes(StandardCharsets.UTF_8));
  }

  private static String path(Config cfg, String bucket, String key) {
    return encodePath(Objects.toString(cfg.endpoint().getPath(), "").replaceAll("/$", ""))
        + "/"
        + (bucket.isBlank() ? "" : encode(bucket) + (key.isBlank() ? "" : "/" + encodePath(key)));
  }

  private static String canonicalQuery(Map<String, String> query) {
    return query.entrySet().stream()
        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
        .sorted()
        .reduce((a, b) -> a + "&" + b)
        .orElse("");
  }

  private static String encode(String text) {
    return UpstreamHttp.segment(text).replace("*", "%2A").replace("%7E", "~");
  }

  private static String encodePath(String path) {
    return Arrays.stream(path.split("/", -1))
        .map(NasAdapter::encode)
        .reduce((a, b) -> a + "/" + b)
        .orElse("");
  }

  public static String validKey(String key) {
    if (key.isBlank()
        || key.startsWith("/")
        || Arrays.asList(key.split("/")).contains("..")
        || key.matches(".*[\\x00-\\x1f\\x7f].*")
        || key.getBytes(StandardCharsets.UTF_8).length > 500)
      throw GatewayException.invalid("对象键不能为空，不能包含越级路径、控制字符或超过 500 字节");
    return key;
  }

  private static void validBucket(String bucket) {
    if (!bucket.matches("[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]")
        || bucket.contains("..")
        || bucket.contains(".-")
        || bucket.contains("-.")) throw GatewayException.invalid("桶名不符合 S3 规范");
  }

  private static Document xml(byte[] bytes) {
    try {
      var f = DocumentBuilderFactory.newInstance();
      f.setNamespaceAware(true);
      f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
      f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      return f.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    } catch (Exception e) {
      throw new GatewayException(502, "upstream_error", "NAS 返回了无效 XML", false);
    }
  }

  private static List<Element> elements(Node node, String name) {
    NodeList list =
        node instanceof Document d
            ? d.getElementsByTagNameNS("*", name)
            : ((Element) node).getElementsByTagNameNS("*", name);
    List<Element> result = new ArrayList<>();
    for (int i = 0; i < list.getLength(); i++) result.add((Element) list.item(i));
    return result;
  }

  private static String text(Element parent, String tag) {
    var nodes = elements(parent, tag);
    return nodes.isEmpty() ? "" : nodes.getFirst().getTextContent();
  }

  private static Object emptyNull(String text) {
    return text.isBlank() ? null : text;
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
