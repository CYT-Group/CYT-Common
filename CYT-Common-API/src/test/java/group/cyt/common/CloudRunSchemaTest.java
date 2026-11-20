package group.cyt.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TEST_PG_URL", matches = ".+")
class CloudRunSchemaTest {
  @Test
  void migrationAndRuntimeUseCommonSchemaEvenWhenJdbcUrlNamesPortal() throws Exception {
    String base = System.getenv("TEST_PG_URL");
    if (!base.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/?"))
      throw new IllegalStateException("The test requires an explicitly configured loopback PostgreSQL server");
    if (!base.endsWith("/")) base += "/";
    String user = System.getenv().getOrDefault("TEST_PG_USERNAME", "postgres");
    String password = System.getenv().getOrDefault("TEST_PG_PASSWORD", "");
    String name = "cyt_common_schema_check_" + UUID.randomUUID().toString().substring(0, 8);
    String jdbcUrl = base + name + "?currentSchema=cyt_portal";
    try (var maintenance = DriverManager.getConnection(base + "postgres", user, password);
        var statement = maintenance.createStatement()) {
      statement.execute("CREATE DATABASE " + name);
      try {
        try (var portal = DriverManager.getConnection(jdbcUrl, user, password);
            var setup = portal.createStatement()) {
          setup.execute("CREATE SCHEMA cyt_portal");
          setup.execute("CREATE TABLE cyt_portal.gateway_environment (sentinel TEXT)");
          setup.execute("INSERT INTO cyt_portal.gateway_environment VALUES ('portal')");
        }
        var flyway = Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .locations("classpath:db/migration", "filesystem:../deploy/gcp/flyway")
            .defaultSchema("cyt_common")
            .schemas("cyt_common")
            .createSchemas(true)
            .load();
        assertTrue(flyway.migrate().migrationsExecuted > 0);
        assertEquals(0, flyway.migrate().migrationsExecuted);

        var config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(password);
        config.setSchema("cyt_common");
        try (var pool = new HikariDataSource(config);
            var connection = pool.getConnection();
            var verify = connection.createStatement()) {
          try (var rows = verify.executeQuery(
              "SELECT current_schema(), is_production FROM gateway_environment WHERE key='production'")) {
            assertTrue(rows.next());
            assertEquals("cyt_common", rows.getString(1));
            assertTrue(rows.getBoolean(2));
          }
          try (var rows = verify.executeQuery("SELECT sentinel FROM cyt_portal.gateway_environment")) {
            assertTrue(rows.next());
            assertEquals("portal", rows.getString(1));
          }
          try (var rows = verify.executeQuery("SELECT COUNT(*) FROM flyway_schema_history WHERE success")) {
            assertTrue(rows.next());
            assertTrue(rows.getInt(1) > 0);
          }
        }
      } finally {
        statement.execute("DROP DATABASE " + name);
      }
    }
  }
}
