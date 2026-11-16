package group.cyt.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.DefaultBootstrapContext;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

class ProfileConfigurationTest {
  @Test
  void defaultProfileLoadsLocalPostgresDefaults() {
    var loaded = load(null, Map.of());
    var env = loaded.environment();
    assertArrayEquals(new String[] {"local"}, env.getDefaultProfiles());
    assertEquals("local", env.getProperty("ENV"));
    assertEquals("org.postgresql.Driver", env.getProperty("spring.datasource.driver-class-name"));
    assertEquals(
        "jdbc:postgresql://127.0.0.1:5432/cyt_common_api_java",
        env.getProperty("spring.datasource.url"));
    assertEquals("cyt_gateway", env.getProperty("spring.datasource.username"));
    assertEquals(true, env.getProperty("spring.flyway.enabled", Boolean.class));
    assertTrue(loaded.resources().localPropertiesRequested);
  }

  @Test
  void prodLoadsSafeDefaultsWithoutImportingLocalProperties() {
    var loaded = load("prod", Map.of());
    var env = loaded.environment();
    assertArrayEquals(new String[] {"prod"}, env.getActiveProfiles());
    assertEquals("production", env.getProperty("ENV"));
    assertEquals(false, env.getProperty("MOCK_ALL", Boolean.class));
    assertEquals(false, env.getProperty("GATEWAY_DEV_MODE", Boolean.class));
    assertEquals(false, env.getProperty("JAVA_SEED_ENABLED", Boolean.class));
    assertEquals(false, env.getProperty("spring.flyway.enabled", Boolean.class));
    assertFalse(loaded.resources().localPropertiesRequested);
    for (var source : env.getPropertySources())
      assertFalse(source.getName().contains("application-local.properties"), source.getName());
  }

  @ParameterizedTest
  @ValueSource(strings = {"local", "prod"})
  void migrationPoliciesRejectDestructiveOrUntrackedChanges(String profile) {
    var env = load(profile, Map.of()).environment();
    assertEquals(true, env.getProperty("spring.flyway.clean-disabled", Boolean.class));
    assertEquals(false, env.getProperty("spring.flyway.baseline-on-migrate", Boolean.class));
    assertEquals(true, env.getProperty("spring.flyway.validate-on-migrate", Boolean.class));
    assertEquals(true, env.getProperty("spring.flyway.validate-migration-naming", Boolean.class));
    assertEquals(true, env.getProperty("spring.flyway.fail-on-missing-locations", Boolean.class));
    assertEquals(false, env.getProperty("spring.flyway.out-of-order", Boolean.class));
    assertEquals(false, env.getProperty("spring.flyway.placeholder-replacement", Boolean.class));
  }

  @ParameterizedTest
  @ValueSource(strings = {"local", "prod"})
  void postgresEnvironmentVariablesConfigureEachProfile(String profile) {
    var env =
        load(
                profile,
                Map.of(
                    "POSTGRES_URL", "jdbc:postgresql://db:5432/fixture",
                    "POSTGRES_USERNAME", "fixture-user",
                    "POSTGRES_PASSWORD", "fixture-password"))
            .environment();
    assertEquals("jdbc:postgresql://db:5432/fixture", env.getProperty("spring.datasource.url"));
    assertEquals("fixture-user", env.getProperty("spring.datasource.username"));
    assertEquals("fixture-password", env.getProperty("spring.datasource.password"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"local", "prod"})
  void standardDatasourceEnvironmentVariablesOverridePostgresAliases(String profile) {
    var env =
        load(
                profile,
                Map.of(
                    "POSTGRES_URL", "jdbc:postgresql://db:5432/alias",
                    "POSTGRES_USERNAME", "alias-user",
                    "POSTGRES_PASSWORD", "alias-password",
                    "SPRING_DATASOURCE_URL", "jdbc:postgresql://db:5432/standard",
                    "SPRING_DATASOURCE_USERNAME", "standard-user",
                    "SPRING_DATASOURCE_PASSWORD", "standard-password"))
            .environment();
    assertEquals("jdbc:postgresql://db:5432/standard", env.getProperty("spring.datasource.url"));
    assertEquals("standard-user", env.getProperty("spring.datasource.username"));
    assertEquals("standard-password", env.getProperty("spring.datasource.password"));
  }

  private record Loaded(StandardEnvironment environment, IsolatedResources resources) {}

  private Loaded load(String profile, Map<String, String> variables) {
    var environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    environment
        .getPropertySources()
        .replace(
            StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource("systemEnvironment", new HashMap<>(variables)));
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "testConfigLocation",
                Map.of("spring.config.location", "classpath:/application.yml")));
    if (profile != null) environment.setActiveProfiles(profile);
    var resources = new IsolatedResources();
    ConfigDataEnvironmentPostProcessor.applyTo(
        environment, resources, new DefaultBootstrapContext());
    return new Loaded(environment, resources);
  }

  private static class IsolatedResources extends DefaultResourceLoader {
    boolean localPropertiesRequested;

    @Override
    public Resource getResource(String location) {
      if (location.startsWith("file:") && location.endsWith("application-local.properties")) {
        localPropertiesRequested = true;
        // Exercise optional imports without reading the developer's private credentials.
        return new ClassPathResource("profile-test-missing/application-local.properties");
      }
      return super.getResource(location);
    }
  }
}
