package group.cyt.common.module.credential.service;

import com.fasterxml.jackson.databind.JsonNode;
import group.cyt.common.core.config.GatewaySettings;
import group.cyt.common.core.exception.GatewayException;
import group.cyt.common.core.persistence.GatewayStore;
import group.cyt.common.core.util.JsonUtils;
import group.cyt.common.module.registry.service.OperationRegistry;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.springframework.stereotype.Service;

/** Fernet wire format is compatible with the previous Python credential store. */
@Service
public class CredentialService {
  private final GatewayStore store;
  private final GatewaySettings settings;
  private final OperationRegistry registry;
  private volatile CredentialStorage storage;

  public CredentialService(
      GatewayStore store, GatewaySettings settings, OperationRegistry registry) {
    this.store = store;
    this.settings = settings;
    this.registry = registry;
  }

  public boolean writable() {
    return !settings.get("CREDENTIAL_MASTER_KEY").isBlank();
  }

  public boolean sensitive(String name) {
    return storagePolicy().sensitive(name);
  }

  private CredentialStorage storagePolicy() {
    var policy = storage;
    if (policy == null) storage = policy = new CredentialStorage(registry.contract());
    return policy;
  }

  public boolean secretManager(String env) {
    if (settings.production() || GatewaySettings.isProductionName(env)) return true;
    if (settings.current().equals(env)) return false;
    var row = store.one("SELECT is_production FROM gateway_environment WHERE key=?", env);
    return row != null && JsonUtils.bool(row.get("is_production"));
  }

  private void requireDatabaseCredential(boolean managed, String name) {
    if (managed && sensitive(name))
      throw GatewayException.forbidden("生产敏感凭据由 Secret Manager 管理: " + name);
  }

  private byte[] key() {
    if (!writable()) throw GatewayException.config("未配置 CREDENTIAL_MASTER_KEY，无法存储凭据");
    return JsonUtils.digest(
        "SHA-256", settings.get("CREDENTIAL_MASTER_KEY").trim().getBytes(StandardCharsets.UTF_8));
  }

  public String encrypt(String plain) {
    try {
      byte[] key = key(), iv = new byte[16];
      new SecureRandom().nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(
          Cipher.ENCRYPT_MODE,
          new SecretKeySpec(Arrays.copyOfRange(key, 16, 32), "AES"),
          new IvParameterSpec(iv));
      byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      ByteBuffer body = ByteBuffer.allocate(25 + encrypted.length);
      body.put((byte) 0x80).putLong(Instant.now().getEpochSecond()).put(iv).put(encrypted);
      byte[] bytes = body.array();
      return Base64.getUrlEncoder()
          .encodeToString(
              ByteBuffer.allocate(bytes.length + 32)
                  .put(bytes)
                  .put(JsonUtils.hmac(Arrays.copyOfRange(key, 0, 16), bytes))
                  .array());
    } catch (GatewayException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Credential encryption failed", e);
    }
  }

  public String decrypt(String token) {
    try {
      byte[] bytes = Base64.getUrlDecoder().decode(token), key = key();
      if (bytes.length < 73 || bytes[0] != (byte) 0x80) throw new GeneralSecurityException();
      byte[] body = Arrays.copyOfRange(bytes, 0, bytes.length - 32);
      if (!MessageDigest.isEqual(
          Arrays.copyOfRange(bytes, bytes.length - 32, bytes.length),
          JsonUtils.hmac(Arrays.copyOfRange(key, 0, 16), body)))
        throw new GeneralSecurityException();
      Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(Arrays.copyOfRange(key, 16, 32), "AES"),
          new IvParameterSpec(Arrays.copyOfRange(bytes, 9, 25)));
      return new String(
          cipher.doFinal(Arrays.copyOfRange(bytes, 25, bytes.length - 32)), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw GatewayException.config("凭据无法解密，请检查主密钥");
    }
  }

  /**
   * Account name → the segment used inside an env var name. Adapters and the credential page must
   * derive it here: two spellings mean the UI reports 「已配」 while every call answers 缺凭证.
   */
  public static String accountKey(String name) {
    return name == null ? "" : name.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_");
  }

  public String raw(String name) {
    boolean managed = settings.production() && sensitive(name);
    if (writable() && !managed) {
      var row =
          store.one(
              "SELECT ciphertext FROM gateway_secret WHERE env=? AND name=?",
              settings.current(),
              name);
      if (row != null) {
        String value = decrypt(JsonUtils.str(row, "ciphertext"));
        if (!settings.production() || !looksSensitive(value)) return value;
      }
    }
    String value = settings.get(name);
    if (!managed || !value.isBlank()) return value;
    String alias = storagePolicy().defaultAccountAlias(name);
    return alias.isBlank() ? "" : settings.get(alias);
  }

  private static final String ACCOUNT_TOKEN = "{ACCOUNT}";
  private static final String PORTAL_SYSTEM = "portal";

  /** (value, source)：db = 后台存的，env = 环境变量。和适配器 raw() 同一优先级。 */
  public String[] readWithSource(String name) {
    if (settings.production() && sensitive(name)) {
      String value = raw(name);
      return new String[] {value, value.isBlank() ? "" : "secret_manager"};
    }
    if (writable()) {
      var row =
          store.one(
              "SELECT ciphertext FROM gateway_secret WHERE env=? AND name=?",
              settings.current(),
              name);
      if (row != null) {
        String value = decrypt(JsonUtils.str(row, "ciphertext"));
        if (!settings.production() || !looksSensitive(value)) return new String[] {value, "db"};
      }
    }
    var value = settings.get(name);
    if (!value.isBlank()) return new String[] {value, "env"};
    for (var alias : defaultAccountAliases(name)) {
      value = settings.get(alias);
      if (!value.isBlank()) return new String[] {value, "env"};
    }
    return new String[] {"", ""};
  }

  /**
   * Explicit admin disclosure of one managed credential; inventory never includes secret values.
   */
  public Map<String, Object> disclose(
      String env, String name, String action, String actor, List<String> systems) {
    if (!name.matches("[A-Z][A-Z0-9_]{0,63}") || !Set.of("view", "copy").contains(action))
      throw GatewayException.invalid("凭据名称或读取操作无效");
    boolean managerEnvironment = secretManager(env);
    requireDatabaseCredential(managerEnvironment, name);
    var stored =
        store.one("SELECT ciphertext FROM gateway_secret WHERE env=? AND name=?", env, name);
    String value;
    String source;
    if (stored != null) {
      value = decrypt(JsonUtils.str(stored, "ciphertext"));
      source = "db";
    } else {
      // Another deployment's environment variables must never be inferred from this process.
      if (!settings.current().equals(env)) throw GatewayException.missing("此环境未保存该凭据");
      boolean managed = false;
      for (var platform : inventory(systems))
        for (var variable : JsonUtils.list(JsonUtils.map(platform).get("vars"))) {
          var entry = JsonUtils.map(variable);
          if (name.equals(entry.get("name"))) managed = true;
          for (var scoped : JsonUtils.list(entry.get("scoped")))
            if (name.equals(JsonUtils.map(scoped).get("name"))) managed = true;
        }
      if (!managed) throw GatewayException.forbidden("仅支持读取凭据列表中的变量");
      var resolved = readWithSource(name);
      value = resolved[0];
      source = resolved[1];
    }
    if (value.isEmpty()) throw GatewayException.missing("该凭据尚未配置");
    if (managerEnvironment && looksSensitive(value))
      throw GatewayException.forbidden("此值包含敏感凭据");
    store.audit(actor, "secrets_" + action, env + ":" + name, JsonUtils.obj("source", source));
    return JsonUtils.obj("name", name, "value", value, "source", source);
  }

  /** LINGXING_DEFAULT_APP_ID → [LINGXING_APP_ID]。默认账号在环境变量里常省略账号段。 */
  private List<String> defaultAccountAliases(String name) {
    var parts = name.split("_");
    if (parts.length <= 2) return List.of();
    var out = new ArrayList<String>();
    for (int i = 0; i < parts.length; i++) {
      if (!parts[i].equals("DEFAULT")) continue;
      var keep = new ArrayList<String>();
      for (int j = 0; j < parts.length; j++) if (j != i) keep.add(parts[j]);
      out.add(String.join("_", keep));
    }
    return out;
  }

  private static String preview(boolean secret, String raw) {
    if (raw == null || raw.isBlank()) return "";
    return secret || looksSensitive(raw) ? raw.length() + " 字符" : raw;
  }

  private static boolean looksSensitive(String value) {
    var lower = value.toLowerCase(Locale.ROOT);
    return lower.contains("\"private_key\"")
        || lower.contains("\"client_secret\"")
        || lower.contains("\"refresh_token\"")
        || lower.contains("\"access_token\"")
        || (lower.contains("-----begin ") && lower.contains("private key-----"))
        || AUTH_URL.matcher(lower).find();
  }

  private static final java.util.regex.Pattern AUTH_URL =
      java.util.regex.Pattern.compile(
          "://[^/\\s]+@|[?&](?:token|secret|password|signature|access_token|api_key|client_secret|x-amz-signature)=");

  /** 变量名 → (平台, scoped 模板, 代码默认是否按系统分)。只收录显式写了模板的变量。 */
  private Map<String, String[]> scopedTemplates() {
    var out = new LinkedHashMap<String, String[]>();
    for (var platform : registry.contract().path("credentials"))
      for (var variable : platform.path("vars")) {
        var tpl = variable.path("scoped").asText("");
        if (tpl.isBlank()) continue;
        out.put(
            variable.path("name").asText(),
            new String[] {
              platform.path("platform").asText(),
              tpl,
              variable.path("per_system").asBoolean() ? "1" : "0"
            });
      }
    return out;
  }

  /** CRED_SPLIT_<PLATFORM> 覆盖哪些变量按系统分。null = 没配过，用代码默认。 */
  private Set<String> splitOverride(String platform) {
    var read = readWithSource("CRED_SPLIT_" + platform.toUpperCase(Locale.ROOT));
    if (read[1].isBlank()) return null;
    var raw = read[0].strip().toUpperCase(Locale.ROOT);
    if (raw.isBlank() || raw.equals("NONE") || raw.equals("-")) return Set.of();
    return Arrays.stream(raw.split(","))
        .map(String::strip)
        .filter(x -> !x.isBlank())
        .collect(java.util.stream.Collectors.toSet());
  }

  public boolean isPerSystem(String name) {
    var entry = scopedTemplates().get(name);
    if (entry == null) return false;
    var override = splitOverride(entry[0]);
    if (override == null) return entry[2].equals("1");
    return override.contains(name);
  }

  public boolean isSplittable(String name) {
    return scopedTemplates().containsKey(name);
  }

  /** 共享变量名 + 系统 → 系统级变量名。门户默认可使用共享配置。 */
  public String scopedName(String name, String system) {
    var entry = scopedTemplates().get(name);
    if (entry == null || system == null || system.isBlank()) return null;
    if (!isPerSystem(name)) return null;
    String scoped = entry[1].replace("{SYSTEM}", accountKey(system));
    return system.equals(PORTAL_SYSTEM)
            && !requiresDedicated(entry[0], system)
            && raw(scoped).isBlank()
        ? null
        : scoped;
  }

  private String defaultPolicy(String platform) {
    for (var p : registry.contract().path("credentials"))
      if (p.path("platform").asText().equals(platform))
        return p.path("scope_policy").asText("inherit");
    return "inherit";
  }

  /** CRED_SCOPE_<PLATFORM>=strict 可覆盖代码里的借用策略。 */
  public String effectivePolicy(String platform) {
    var raw =
        readWithSource("CRED_SCOPE_" + platform.toUpperCase(Locale.ROOT))[0]
            .strip()
            .toLowerCase(Locale.ROOT);
    return raw.isBlank() ? defaultPolicy(platform) : validateScopePolicy(raw);
  }

  private static String validateScopePolicy(String policy) {
    if (!Set.of("strict", "inherit").contains(policy))
      throw GatewayException.invalid("凭据共享策略只能是 strict 或 inherit");
    return policy;
  }

  public boolean requiresDedicated(String platform, String system) {
    if (system == null || system.isBlank()) return false;
    String policy =
        raw("CRED_SCOPE_" + accountKey(platform) + "_" + accountKey(system))
            .strip()
            .toLowerCase(Locale.ROOT);
    if (!policy.isBlank()) validateScopePolicy(policy);
    return switch (policy) {
      case "strict" -> true;
      case "inherit" -> false;
      default -> !system.equals(PORTAL_SYSTEM) && effectivePolicy(platform).equals("strict");
    };
  }

  public void requireAccount(String platform, String account, String system) {
    if (system == null || system.isBlank()) return;
    var setting =
        readWithSource("CRED_ACCOUNTS_" + accountKey(platform) + "_" + accountKey(system));
    if (setting[1].isBlank() && !requiresDedicated(platform, system)) return;
    String selected = account == null || account.isBlank() ? "DEFAULT" : accountKey(account);
    boolean allowed =
        Arrays.stream(setting[0].split(","))
            .map(CredentialService::accountKey)
            .anyMatch(selected::equals);
    if (!allowed) throw GatewayException.forbidden("该调用系统未获准使用此上游账号");
  }

  private Map<String, Object> scopedState(String varName, boolean secret, String system) {
    var scoped = scopedName(varName, system);
    if (WEWORK_NAMED.contains(varName)) scoped = "WEWORK_APP_" + accountKey(system) + varName.substring(6);
    var resolved = scoped == null ? varName : scoped;
    var read = readWithSource(resolved);
    return JsonUtils.obj(
        "system",
        system,
        "name",
        resolved,
        "set",
        !read[0].isBlank(),
        "source",
        read[1],
        "preview",
        preview(secret, read[0]),
        "manager_managed",
        settings.production() && sensitive(resolved),
        "scope_policy",
        requiresDedicated(WEWORK_NAMED.contains(varName) ? "wecom" : scopedTemplates().get(varName)[0], system)
            ? "strict"
            : "inherit",
        "is_shared",
        resolved.equals(varName));
  }

  private boolean isMultiAccount(JsonNode spec) {
    for (var v : spec.path("vars"))
      if (v.path("name").asText().contains(ACCOUNT_TOKEN)) return true;
    return false;
  }

  private static final Set<String> WEWORK_NAMED =
      Set.of("WEWORK_CORP_ID", "WEWORK_SECRET", "WEWORK_CONTACT_SECRET", "WEWORK_AGENT_ID");

  /** 账号列表：声明变量 + 默认账号 + 从已有变量名反推，兜底 account_fallback。 */
  public List<String> accounts(String platform) {
    for (var spec : registry.contract().path("credentials"))
      if (spec.path("platform").asText().equals(platform)) return accountsOf(spec);
    return List.of();
  }

  private List<String> accountsOf(JsonNode spec) {
    var seen = new LinkedHashMap<String, String>();
    java.util.function.Consumer<String> add =
        raw -> {
          var name = raw == null ? "" : raw.strip();
          var key = accountKey(name);
          if (!name.isBlank() && !key.replace("_", "").isBlank() && !seen.containsKey(key))
            seen.put(key, name);
        };
    var accountsVar = spec.path("accounts_var").asText("");
    if (!accountsVar.isBlank())
      for (var part : readWithSource(accountsVar)[0].replace(";", ",").split(",")) add.accept(part);
    var defaultVar = spec.path("default_account_var").asText("");
    if (!defaultVar.isBlank()) add.accept(readWithSource(defaultVar)[0]);

    var candidates = new TreeSet<String>();
    if (writable())
      for (var row : store.rows("SELECT name FROM gateway_secret WHERE env=?", settings.current()))
        candidates.add(JsonUtils.str(row, "name"));
    candidates.addAll(System.getenv().keySet());
    scan:
    for (var full : candidates) {
      for (var v : spec.path("vars")) {
        var name = v.path("name").asText();
        var idx = name.indexOf(ACCOUNT_TOKEN);
        if (idx < 0) continue;
        var pre = name.substring(0, idx);
        var suf = name.substring(idx + ACCOUNT_TOKEN.length());
        if (!full.startsWith(pre) || !full.endsWith(suf)) continue;
        // Matching both ends does not prove there is an account between them: the store-level
        // LINGXING_APP_ID overlaps both LINGXING_{ACCOUNT}_APP_ID's prefix and suffix, and slicing
        // it past the end threw and turned the whole credential inventory into a 500.
        var end = full.length() - suf.length();
        if (end <= pre.length()) continue;
        var inner = full.substring(pre.length(), end);
        if (!inner.isBlank() && !readWithSource(full)[0].isBlank()) add.accept(inner);
        continue scan;
      }
    }
    if (seen.isEmpty()) {
      var fallback = spec.path("account_fallback").asText("");
      if (!fallback.isBlank()) add.accept(fallback);
    }
    return new ArrayList<>(seen.values());
  }

  /** 凭据清单：只回变量名、长度和用途，不回值。与 Python 版 inventory() 对齐。 */
  public List<Object> inventory(List<String> systems) {
    var sys = systems == null ? List.<String>of() : systems;
    var out = new ArrayList<>();
    for (var spec : registry.contract().path("credentials")) {
      var platform = spec.path("platform").asText();
      var multi = isMultiAccount(spec);
      var accounts = multi ? accountsOf(spec) : List.<String>of();
      var varsOut = new ArrayList<Map<String, Object>>();
      for (var v : spec.path("vars")) {
        var name = v.path("name").asText();
        var expanded = new ArrayList<Map<String, Object>>();
        if (!name.contains(ACCOUNT_TOKEN)) expanded.add(JsonUtils.obj("name", name, "account", ""));
        else
          for (var acc : accounts)
            expanded.add(
                JsonUtils.obj(
                    "name", name.replace(ACCOUNT_TOKEN, accountKey(acc)), "account", acc));
        for (var ex : expanded) {
          var varName = JsonUtils.str(ex, "name");
          var read = readWithSource(varName);
          var perSystem = isPerSystem(varName) || WEWORK_NAMED.contains(varName);
          var sensitive = v.path("secret").asBoolean() || settings.production() && sensitive(varName);
          var item =
              JsonUtils.obj(
                  "name", varName,
                  "label", v.path("label").asText(),
                  "required", v.path("required").asBoolean(),
                  "secret", sensitive,
                  "hint", v.path("hint").asText(),
                  "example", v.path("example").asText(),
                  "set", !read[0].isBlank(),
                  "source", read[1],
                  "preview", preview(sensitive, read[0]),
                  "manager_managed", settings.production() && sensitive,
                  "per_system", perSystem,
                  "splittable", isSplittable(varName),
                  "account", JsonUtils.str(ex, "account"));
          if (perSystem && !sys.isEmpty())
            item.put(
                "scoped",
                sys.stream()
                    .map(s -> scopedState(varName, sensitive, s))
                    .toList());
          varsOut.add(item);
        }
      }
      var missing =
          varsOut.stream()
              .filter(x -> JsonUtils.bool(x.get("required")) && !JsonUtils.bool(x.get("set")))
              .map(x -> JsonUtils.str(x, "name"))
              .toList();
      var accountMissing = multi && accounts.isEmpty();
      var perSystemVars =
          varsOut.stream()
              .filter(x -> JsonUtils.bool(x.get("per_system")))
              .map(x -> JsonUtils.str(x, "name"))
              .toList();
      var splittableVars = new ArrayList<String>();
      var perSysSpecs = new ArrayList<JsonNode>();
      spec.path("vars")
          .forEach(
              v -> {
                var varName = v.path("name").asText();
                if (isSplittable(varName)) splittableVars.add(varName);
                if (isPerSystem(varName)) perSysSpecs.add(v);
              });
      var systemsReady =
          sys.stream()
              .filter(
                  s ->
                      !perSysSpecs.isEmpty()
                          && perSysSpecs.stream()
                              .filter(v -> v.path("required").asBoolean())
                              .allMatch(
                                  v ->
                                      JsonUtils.bool(
                                          scopedState(
                                                  v.path("name").asText(),
                                                  v.path("secret").asBoolean(),
                                                  s)
                                              .get("set"))))
              .toList();
      out.add(
          JsonUtils.obj(
              "platform",
              platform,
              "label",
              spec.path("label").asText(),
              "docs",
              spec.path("docs").asText(),
              "note",
              spec.path("note").asText(),
              "vars",
              varsOut,
              "missing",
              missing,
              "configured",
              missing.isEmpty() && !accountMissing,
              "multi_account",
              multi,
              "accounts",
              accounts,
              "accounts_var",
              spec.path("accounts_var").asText(),
              "default_account_var",
              spec.path("default_account_var").asText(),
              "account_fallback",
              spec.path("account_fallback").asText(),
              "account_missing",
              accountMissing,
              "per_system_vars",
              perSystemVars,
              "splittable_vars",
              splittableVars,
              "scope_policy",
              effectivePolicy(platform),
              "scope_policy_default",
              spec.path("scope_policy").asText("inherit"),
              "strict_recommended",
              spec.path("strict_recommended").asBoolean(),
              "system_aware",
              spec.path("system_aware").asBoolean(),
              "systems_ready",
              systemsReady));
    }
    return out;
  }

  public Map<String, Object> summary(List<Object> items) {
    int configured = 0, missingVars = 0;
    var missingPlatforms = new ArrayList<String>();
    var perSystem = new ArrayList<String>();
    var strict = new ArrayList<String>();
    for (var o : items) {
      var i = JsonUtils.map(o);
      var platform = JsonUtils.str(i, "platform");
      if (JsonUtils.bool(i.get("configured"))) configured++;
      else missingPlatforms.add(platform);
      missingVars += JsonUtils.list(i.get("missing")).size();
      if (!JsonUtils.list(i.get("per_system_vars")).isEmpty()) perSystem.add(platform);
      if ("strict".equals(JsonUtils.str(i, "scope_policy"))) strict.add(platform);
    }
    return JsonUtils.obj(
        "platforms", items.size(),
        "configured", configured,
        "missing_platforms", missingPlatforms,
        "missing_vars", missingVars,
        "per_system_platforms", perSystem,
        "strict_platforms", strict);
  }

  /**
   * 按店铺域名反查该用哪个系统段读取凭据。调用方只用一个系统身份时，由请求里的 store 决定实际店铺的 SHOPIFY_{SYSTEM}_* 凭据，免去为每个店铺登记一个接入系统。
   * 找不到返回 null：适配器会保持原有「请求店铺与实际授权店铺不一致」的拒绝行为。
   */
  public String systemForStore(String varName, String requestedStore) {
    if (requestedStore == null || requestedStore.isBlank()) return null;
    var entry = scopedTemplates().get(varName);
    if (entry == null) return null;
    String tpl = entry[1];
    int idx = tpl.indexOf("{SYSTEM}");
    if (idx < 0) return null;
    String pre = tpl.substring(0, idx);
    String suf = tpl.substring(idx + "{SYSTEM}".length());
    var names = new TreeSet<String>();
    if (writable())
      for (var row :
          store.rows(
              "SELECT name FROM gateway_secret WHERE env=? AND name LIKE ?",
              settings.current(),
              pre + "%" + suf)) names.add(JsonUtils.str(row, "name"));
    for (var key : System.getenv().keySet()) names.add(key);
    String wanted = normalizeStore(requestedStore);
    for (var full : names) {
      int end = full.length() - suf.length();
      if (!full.startsWith(pre) || !full.endsWith(suf) || end <= pre.length()) continue;
      String inner = full.substring(pre.length(), end);
      if (inner.isBlank()) continue;
      String value = normalizeStore(raw(full));
      if (!value.isBlank() && value.equalsIgnoreCase(wanted)) return inner.toLowerCase(Locale.ROOT);
    }
    return null;
  }

  private static String normalizeStore(String s) {
    return s == null ? "" : s.trim().replaceFirst("^https?://", "").replaceAll("/+$", "");
  }

  public String resolve(String name, String system) {
    if (name.startsWith("CUSTOM_") && system != null && !system.isBlank()) {
      String dedicated = "CUSTOM_" + accountKey(system) + "_" + name.substring("CUSTOM_".length());
      String value = raw(dedicated);
      if (!value.isBlank()) return value;
      if (requiresDedicated("custom", system))
        throw GatewayException.config("缺少系统专属凭据: " + dedicated);
    }
    String scoped = scopedName(name, system);
    if (scoped != null) {
      String value = raw(scoped);
      if (!value.isBlank()) return value;
      if (requiresDedicated(scopedTemplates().get(name)[0], system))
        throw GatewayException.config("系统 " + system + " 必须配置专属凭据: " + scoped);
    }
    return raw(name);
  }

  public String required(String name, String system) {
    String value = resolve(name, system);
    if (value.isBlank()) throw GatewayException.config("缺少凭据: " + name);
    return value;
  }

  public void save(String env, Map<String, String> values, String actor) {
    if (!writable()) throw GatewayException.config("未启用凭据加密存储");
    boolean managed = secretManager(env);
    for (var e : values.entrySet()) {
      if (!e.getKey().matches("[A-Z][A-Z0-9_]{0,63}")
          || e.getValue() == null
          || e.getValue().length() > 1_000_000)
        throw GatewayException.invalid("凭据名称或长度无效");
      requireDatabaseCredential(managed, e.getKey());
      if (e.getKey().startsWith("CRED_SCOPE_"))
        validateScopePolicy(e.getValue().strip().toLowerCase(Locale.ROOT));
      if (managed && looksSensitive(e.getValue()))
        throw GatewayException.invalid("请将带有密钥材料或 URL 凭据的值保存到 Secret Manager");
    }
    store.atomic(
        () -> {
          for (var e : values.entrySet()) {
            var old =
                store.one("SELECT id FROM gateway_secret WHERE env=? AND name=?", env, e.getKey());
            var data =
                JsonUtils.obj(
                    "ciphertext",
                    encrypt(e.getValue()),
                    "updated_by",
                    actor,
                    "updated_at",
                    GatewayStore.now());
            if (old == null) {
              data.put("env", env);
              data.put("name", e.getKey());
              store.insert("gateway_secret", data);
            } else store.edit("gateway_secret", ((Number) old.get("id")).longValue(), data);
          }
          store.audit(actor, "secrets_update", env, JsonUtils.obj("names", values.keySet()));
          return null;
        });
  }
}
