package com.miaoyu.ticket.common.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** 只允许在 CI 一次性 MySQL 8.4 空库中验证当前全部 Flyway 迁移及重复执行。 */
@EnabledIfEnvironmentVariable(named = "CINEWISE_MYSQL_MIGRATION_IT", matches = "true")
class FlywayLatestMySqlIntegrationTest {

    private static final String REQUIRED_HOST = "127.0.0.1";
    private static final String REQUIRED_DATABASE = "cinewise_migration_ci";
    private static final String REQUIRED_USERNAME = "cinewise_ci";

    @Test
    void givenEmptyMySqlEight_whenMigratingLatestTwice_thenNoMigrationRemainsPending() throws SQLException {
        String host = requiredEnvironment("MYSQL_HOST");
        String port = requiredEnvironment("MYSQL_PORT");
        String database = requiredEnvironment("MYSQL_DATABASE");
        String username = requiredEnvironment("MYSQL_USER");
        String password = requiredEnvironment("MYSQL_PASSWORD");
        requireCiDatabase(host, port, database, username);

        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&useSSL=false&allowPublicKeyRetrieval=true";
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .target(MigrationVersion.LATEST)
                .validateMigrationNaming(true)
                .load();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            assertThat(queryString(connection, "SELECT DATABASE()"))
                    .as("Flyway MySQL 守卫只能使用 CI 一次性迁移库")
                    .isEqualTo(REQUIRED_DATABASE);
            assertThat(queryString(connection, "SELECT SUBSTRING_INDEX(CURRENT_USER(), '@', 1)"))
                    .isEqualTo(REQUIRED_USERNAME);
            assertThat(queryString(connection, "SELECT VERSION()")).startsWith("8.4.");

            MigrateResult initial = flyway.migrate();
            assertThat(initial.success).isTrue();
            assertThat(initial.migrationsExecuted).isPositive();

            MigrationInfoService migrationInfo = flyway.info();
            assertThat(migrationInfo.current()).isNotNull();
            assertThat(migrationInfo.pending()).isEmpty();
            assertThat(queryInteger(connection, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0"))
                    .isZero();

            MigrateResult repeated = flyway.migrate();
            assertThat(repeated.success).isTrue();
            assertThat(repeated.migrationsExecuted).isZero();
            assertThat(flyway.info().pending()).isEmpty();
        }
    }

    private static void requireCiDatabase(String host, String port, String database, String username) {
        int portNumber;
        try {
            portNumber = Integer.parseInt(port);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Flyway MySQL 守卫端口不合法", exception);
        }
        if (!REQUIRED_HOST.equals(host)
                || portNumber < 1
                || portNumber > 65535
                || !REQUIRED_DATABASE.equals(database)
                || !REQUIRED_USERNAME.equals(username)) {
            throw new IllegalStateException("Flyway MySQL 守卫只能连接 CI 一次性迁移库");
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少 MySQL CI 环境变量: " + name);
        }
        return value;
    }

    private static String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private static int queryInteger(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }
}
