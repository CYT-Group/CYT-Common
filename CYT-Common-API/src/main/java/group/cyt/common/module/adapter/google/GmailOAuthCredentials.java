package group.cyt.common.module.adapter.google;

import group.cyt.common.core.config.GatewaySettings;
import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.credential.service.CredentialService;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32C;
import org.springframework.stereotype.Service;

/** 自助授权按调用系统和完整邮箱隔离；数据库只存 Secret Manager 固定版本引用。 */
@Service
public class GmailOAuthCredentials {
  private final CredentialService credentials;
  private final GatewaySettings settings;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  private final Map<String, Map<String, Object>> cache = new ConcurrentHashMap<>();
  private String accessToken = "";
  private long accessTokenUntil;

  public GmailOAuthCredentials(CredentialService credentials, GatewaySettings settings) {
    this.credentials = credentials;
    this.settings = settings;
  }

  public static String mailboxKey(String system, String account) {
    String normalized =
        system.strip().toLowerCase(Locale.ROOT) + "\n" + account.strip().toLowerCase(Locale.ROOT);
    return JsonUtils.sha(normalized).substring(0, 32).toUpperCase(Locale.ROOT);
  }

  public Map<String, Object> resolve(String system, String account) {
    String project = settings.get("GMAIL_OAUTH_SECRET_PROJECT").strip();
    if (project.isBlank() || account.isBlank()) return Map.of();
    if (!project.matches("[a-z][a-z0-9-]{4,61}[a-z0-9]|[0-9]+"))
      throw GatewayException.config("Gmail 授权密钥项目配置无效");
    String key = mailboxKey(system, account);
    String raw = credentials.raw("GMAIL_OAUTH_" + key + "_BINDING");
    if (raw.isBlank()) return Map.of();
    Map<String, Object> binding = JsonUtils.read(raw);
    requireIdentity(binding, system, account);
    String version = JsonUtils.str(binding, "version");
    String prefix = "projects/" + project + "/secrets/CYT_CS_GMAIL_OAUTH_" + key + "/versions/";
    if (!version.startsWith(prefix) || !version.substring(prefix.length()).matches("[0-9]+"))
      throw GatewayException.config("Gmail 授权必须引用本邮箱的固定 Secret Manager 版本");
    Map<String, Object> bundle = cache.computeIfAbsent(version, this::readVersion);
    requireIdentity(bundle, system, account);
    for (String name : List.of("client_id", "client_secret", "refresh_token"))
      if (JsonUtils.str(bundle, name).isBlank()) throw GatewayException.config("Gmail 授权凭据不完整");
    return bundle;
  }

  private static void requireIdentity(Map<String, Object> value, String system, String account) {
    if (!JsonUtils.str(value, "system").equalsIgnoreCase(system)
        || !JsonUtils.str(value, "account").equalsIgnoreCase(account))
      throw GatewayException.forbidden("Gmail 授权引用与调用系统或邮箱不一致");
  }

  protected Map<String, Object> readVersion(String version) {
    try {
      var request =
          HttpRequest.newBuilder(
                  URI.create("https://secretmanager.googleapis.com/v1/" + version + ":access"))
              .timeout(Duration.ofSeconds(15))
              .header("Authorization", "Bearer " + accessToken())
              .GET()
              .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) throw new IllegalStateException();
      var payload = JsonUtils.map(JsonUtils.read(response.body()).get("payload"));
      byte[] data = Base64.getDecoder().decode(JsonUtils.str(payload, "data"));
      if (data.length > 65536) throw new IllegalStateException();
      if (payload.containsKey("dataCrc32c")) {
        var crc = new CRC32C();
        crc.update(data);
        if (crc.getValue() != Long.parseLong(JsonUtils.str(payload, "dataCrc32c")))
          throw new IllegalStateException();
      }
      return Collections.unmodifiableMap(JsonUtils.read(new String(data, StandardCharsets.UTF_8)));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw GatewayException.config("读取 Gmail 授权密钥中断");
    } catch (Exception e) {
      // 不把密钥服务响应或授权材料放入网关错误、trace 或日志。
      throw GatewayException.config("无法读取 Gmail 授权密钥，请检查 Secret Manager 绑定与服务账号权限");
    }
  }

  private synchronized String accessToken() throws Exception {
    if (System.currentTimeMillis() < accessTokenUntil) return accessToken;
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token"))
            .timeout(Duration.ofSeconds(5))
            .header("Metadata-Flavor", "Google")
            .GET()
            .build();
    var response = http.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) throw new IllegalStateException();
    var body = JsonUtils.read(response.body());
    accessToken = JsonUtils.str(body, "access_token");
    if (accessToken.isBlank()) throw new IllegalStateException();
    accessTokenUntil =
        System.currentTimeMillis()
            + Math.max(0, JsonUtils.integer(body.get("expires_in"), 0) - 60) * 1000L;
    return accessToken;
  }
}
