package com.carrental.backend.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.api.Test;

class ProductionConfigurationValidatorTest {

    private static final String VALID_SECRET = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    @Test
    void acceptsExplicitProductionSettings() {
        assertThatCode(() -> ProductionConfigurationValidator.validate(validSettings()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingDatabaseSettingsWithoutEchoingValues() {
        Map<String, String> settings = validSettings();
        settings.put("spring.datasource.password", "");

        assertThatThrownBy(() -> ProductionConfigurationValidator.validate(settings))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.password is required")
                .hasMessageNotContaining("never-print-this");
    }

    @Test
    void rejectsWeakJwtSecretsAndUntrustedCorsOrigins() {
        Map<String, String> settings = validSettings();
        settings.put("app.jwt.secret", "short-secret");
        settings.put("app.cors.allowed-origins", "*");

        assertThatThrownBy(() -> ProductionConfigurationValidator.validate(settings))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET must be valid Base64 encoding of at least 32 bytes")
                .hasMessageContaining("CORS_ALLOWED_ORIGINS must contain only explicit HTTPS origins")
                .hasMessageNotContaining("short-secret");
    }

    @Test
    void rejectsLocalhostAndLoopbackCorsOrigins() {
        for (String origin : new String[] {
                "https://localhost", "https://admin.localhost", "https://localhost.localdomain",
                "https://127.0.0.1", "https://[::1]"
        }) {
            Map<String, String> settings = validSettings();
            settings.put("app.cors.allowed-origins", origin);

            assertThatThrownBy(() -> ProductionConfigurationValidator.validate(settings))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CORS_ALLOWED_ORIGINS must contain only explicit HTTPS origins")
                    .hasMessageNotContaining(origin);
        }
    }

    @Test
    void requiresExplicitStrongAdminCredentialsForBootstrapAndRotation() {
        Map<String, String> settings = validSettings();
        settings.put("app.admin.bootstrap.enabled", "true");
        settings.put("app.admin.bootstrap.rotate-existing", "true");
        settings.put("app.admin.email", "admin@example.test");
        settings.put("app.admin.password", "too-short");

        assertThatThrownBy(() -> ProductionConfigurationValidator.validate(settings))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_ADMIN_PASSWORD must contain at least 16 characters");
    }

    @Test
    void rejectsInvalidDatabaseUrlAndRuntimeFlagsBeforeStartup() {
        Map<String, String> settings = validSettings();
        settings.put("spring.datasource.url", "jdbc:postgresql://db.example.test/milepost?password=hidden");
        settings.put("app.jwt.expiration-ms", "0");
        settings.put("spring.flyway.enabled", "sometimes");

        assertThatThrownBy(() -> ProductionConfigurationValidator.validate(settings))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL must be a PostgreSQL JDBC URL")
                .hasMessageContaining("JWT_EXPIRATION_MS must be a positive integer")
                .hasMessageContaining("FLYWAY_ENABLED must be true or false")
                .hasMessageNotContaining("hidden");
    }

    @Test
    void rejectsCombiningProductionWithAnotherSpringProfile() {
        GenericApplicationContext context = new GenericApplicationContext();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod", "local");
        context.setEnvironment(environment);

        assertThatThrownBy(() -> new ProductionConfigurationValidator().initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("activate prod without additional Spring profiles");
    }

    @Test
    void productionInitializerFailsBeforeDatasourceCreationWhenSettingsAreMissing() {
        GenericApplicationContext context = new GenericApplicationContext();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        context.setEnvironment(environment);

        assertThatThrownBy(() -> new ProductionConfigurationValidator().initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.datasource.url is required")
                .hasMessageContaining("app.jwt.secret is required")
                .hasMessageContaining("app.cors.allowed-origins is required");
    }

    private Map<String, String> validSettings() {
        Map<String, String> settings = new HashMap<>();
        settings.put("spring.datasource.url", "jdbc:postgresql://db.example.test:5432/milepost");
        settings.put("spring.datasource.username", "db-user");
        settings.put("spring.datasource.password", "never-print-this");
        settings.put("app.jwt.secret", VALID_SECRET);
        settings.put("app.cors.allowed-origins", "https://rental.example.test");
        settings.put("app.admin.bootstrap.enabled", "false");
        settings.put("app.admin.bootstrap.rotate-existing", "false");
        settings.put("app.admin.email", "");
        settings.put("app.admin.password", "");
        settings.put("app.jwt.expiration-ms", "3600000");
        settings.put("spring.flyway.enabled", "false");
        return settings;
    }
}
