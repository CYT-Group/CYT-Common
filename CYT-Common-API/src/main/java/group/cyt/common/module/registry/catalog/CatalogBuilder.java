package group.cyt.common.module.registry.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CatalogBuilder {
  final Map<String, BuiltinOperation> operations = new LinkedHashMap<>();
  final Map<String, Platform> platforms = new LinkedHashMap<>();
  final Map<String, PlatformCredentials> credentials = new LinkedHashMap<>();
  final Map<String, String> aliases = new LinkedHashMap<>();

  public record Platform(
      String platform,
      @JsonProperty("display_name") String displayName,
      @JsonProperty("platform_quota") String platformQuota) {}

  public void platform(String key, String displayName, String quota) {
    unique(platforms, key, new Platform(key, displayName, quota));
  }

  public void credentials(PlatformCredentials value) {
    unique(credentials, value.platform(), value);
  }

  public void operation(BuiltinOperation value) {
    unique(operations, value.definition().key(), value);
  }

  public void alias(String legacy, String current) {
    unique(aliases, legacy, current);
  }

  private static <T> void unique(Map<String, T> target, String key, T value) {
    if (target.putIfAbsent(key, value) != null)
      throw new IllegalArgumentException("Duplicate catalogue entry: " + key);
  }
}
