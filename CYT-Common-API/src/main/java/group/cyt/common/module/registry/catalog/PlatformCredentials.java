package group.cyt.common.module.registry.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record PlatformCredentials(
    String platform,
    String label,
    String docs,
    String note,
    @JsonProperty("scope_policy") String scopePolicy,
    @JsonProperty("strict_recommended") boolean strictRecommended,
    @JsonProperty("system_aware") boolean systemAware,
    @JsonProperty("accounts_var") String accountsVar,
    @JsonProperty("default_account_var") String defaultAccountVar,
    @JsonProperty("account_fallback") String accountFallback,
    List<Variable> vars) {
  public PlatformCredentials {
    vars = List.copyOf(vars);
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Variable(
      String name,
      String label,
      boolean required,
      boolean secret,
      String hint,
      String example,
      @JsonProperty("per_system") boolean perSystem,
      String scoped,
      @JsonProperty("account_scoped") Boolean accountScoped) {}
}
