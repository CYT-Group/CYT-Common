package group.cyt.common.module.credential.service;

import com.fasterxml.jackson.databind.JsonNode;
import group.cyt.common.core.util.JsonUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Classifies concrete credential names, including scoped names and legacy account aliases. */
final class CredentialStorage {
  private static final JsonNode EXTRA = loadExtra();
  private final List<Pattern> databaseConfig = new ArrayList<>();
  private final List<Pattern> secrets = new ArrayList<>();
  private final Map<String, String> accountAliases = new HashMap<>();

  CredentialStorage(JsonNode contract) {
    for (var platform : contract.path("credentials"))
      for (var variable : platform.path("vars")) {
        String name = variable.path("name").asText();
        List<Pattern> target = variable.path("secret").asBoolean() ? secrets : databaseConfig;
        add(target, name);
        add(target, variable.path("scoped").asText());
        if (name.contains("{ACCOUNT}_")) {
          String alias = name.replace("{ACCOUNT}_", "");
          add(target, alias);
          accountAliases.put(name.replace("{ACCOUNT}", "DEFAULT"), alias);
        }
      }
    EXTRA.path("database_config").forEach(name -> add(databaseConfig, name.asText()));
    EXTRA.path("secret_manager").forEach(name -> add(secrets, name.asText()));
    add(databaseConfig, "CRED_SCOPE_{SYSTEM}");
    add(databaseConfig, "CRED_SPLIT_{SYSTEM}");
    add(databaseConfig, "CRED_ACCOUNTS_{SYSTEM}");
  }

  boolean sensitive(String name) {
    return secrets.stream().anyMatch(pattern -> pattern.matcher(name).matches())
        || databaseConfig.stream().noneMatch(pattern -> pattern.matcher(name).matches());
  }

  String defaultAccountAlias(String name) {
    return accountAliases.getOrDefault(name, "");
  }

  private static void add(List<Pattern> names, String template) {
    if (!template.isBlank())
      names.add(Pattern.compile(Pattern.quote(template)
          .replace("{SYSTEM}", "\\E[A-Z0-9_]+\\Q")
          .replace("{ACCOUNT}", "\\E[A-Z0-9_]+\\Q")));
  }

  private static JsonNode loadExtra() {
    try (var input = CredentialStorage.class.getResourceAsStream("/credential-storage.json")) {
      return JsonUtils.MAPPER.readTree(input);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
