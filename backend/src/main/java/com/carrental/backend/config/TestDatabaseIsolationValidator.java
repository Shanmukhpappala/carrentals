package com.carrental.backend.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/** Verifies the explicit test datasource before Spring can initialize Flyway or Hibernate. */
public final class TestDatabaseIsolationValidator
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String TEST_DATABASE = "carrental_test";
    private static final String TEST_ROLE = "carrental_test_user";
    private static final String DEVELOPMENT_DATABASE = "carrentalsbackend";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        boolean testProfileActive = false;
        for (String profile : environment.getActiveProfiles()) {
            if ("test".equals(profile)) testProfileActive = true;
        }
        if (!testProfileActive) return;
        validateProfiles(environment.getActiveProfiles());

        String testUrl = environment.getProperty("TEST_DB_URL");
        String datasourceUrl = property(environment, "spring.datasource.url");
        String username = environment.getProperty("TEST_DB_USERNAME");
        String password = environment.getProperty("TEST_DB_PASSWORD");
        if (hasValue(property(environment, "spring.datasource.hikari.jdbc-url"))
                || hasValue(property(environment, "spring.datasource.jndi-name"))) {
            throw new IllegalStateException("Database tests may not override the guarded test datasource");
        }
        validateSettings(testUrl, datasourceUrl, username, password,
                environment.getProperty("spring.jpa.hibernate.ddl-auto"),
                environment.getProperty("spring.flyway.enabled"));
        verifyDatabaseAccess(testUrl, username, password);
    }

    static void validateSettings(String testUrl, String datasourceUrl, String username, String password,
            String ddlAuto, String flywayEnabled) {
        if (testUrl == null || testUrl.isBlank()
                || datasourceUrl == null || !testUrl.equals(datasourceUrl)
                || username == null || !TEST_ROLE.equals(username)
                || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Database tests require TEST_DB_URL, TEST_DB_USERNAME, and TEST_DB_PASSWORD; no fallback is permitted");
        }
        if (!isApprovedTestUrl(testUrl)) {
            throw new IllegalStateException(
                    "Database tests may target only 127.0.0.1:5433/carrental_test");
        }
        if (!"validate".equalsIgnoreCase(ddlAuto) || !"true".equalsIgnoreCase(flywayEnabled)) {
            throw new IllegalStateException("Database tests require Hibernate validation and explicit test migrations");
        }
    }

    static void validateProfiles(String... activeProfiles) {
        if (activeProfiles.length != 1 || !"test".equals(activeProfiles[0])) {
            throw new IllegalStateException("Database tests require the test profile to be active alone");
        }
    }

    private static boolean isApprovedTestUrl(String jdbcUrl) {
        return ("jdbc:postgresql://127.0.0.1:5433/" + TEST_DATABASE).equals(jdbcUrl);
    }

    private static void verifyDatabaseAccess(String url, String username, String password) {
        String sql = "SELECT current_database(), current_user, "
                + "EXISTS (SELECT 1 FROM pg_database d WHERE d.datname = ? "
                + "AND has_database_privilege(current_user, d.oid, 'CONNECT')), "
                + "has_schema_privilege(current_user, 'public', 'USAGE'), "
                + "has_schema_privilege(current_user, 'public', 'CREATE')";
        try (Connection connection = DriverManager.getConnection(url, username, password);
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, DEVELOPMENT_DATABASE);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException("Test database identity check returned no result");
                }
                validateDatabaseIdentity(result.getString(1), result.getString(2), result.getBoolean(3),
                        result.getBoolean(4), result.getBoolean(5));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Unable to verify the isolated test database; refusing to initialize Spring tests");
        }
    }

    static void validateDatabaseIdentity(String database, String username, boolean canConnectToDevelopment,
            boolean canUsePublicSchema, boolean canCreateInPublicSchema) {
        if (!TEST_DATABASE.equals(database) || !TEST_ROLE.equals(username)) {
            throw new IllegalStateException("Connected datasource is not carrental_test as carrental_test_user");
        }
        if (canConnectToDevelopment) {
            throw new IllegalStateException(
                    "Test database isolation failed: carrental_test_user can connect to the development database");
        }
        if (!canUsePublicSchema || !canCreateInPublicSchema) {
            throw new IllegalStateException("Test role lacks the public schema permissions required for migrations");
        }
    }

    private static String property(ConfigurableEnvironment environment, String name) {
        try {
            return environment.getProperty(name);
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            return null;
        }
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }
}
