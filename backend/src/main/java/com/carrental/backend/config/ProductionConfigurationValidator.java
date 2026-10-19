package com.carrental.backend.config;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/** Validates production settings before Spring creates a datasource or runs Flyway. */
public final class ProductionConfigurationValidator
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment environment = applicationContext.getEnvironment();
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equals(profile)) {
                if (environment.getActiveProfiles().length != 1) {
                    throw new IllegalStateException("Invalid production configuration: activate prod without additional Spring profiles");
                }
                Map<String, String> settings = new HashMap<>();
                for (String key : List.of(
                        "spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
                        "app.jwt.secret", "app.cors.allowed-origins", "app.admin.bootstrap.enabled",
                        "app.admin.bootstrap.rotate-existing", "app.admin.email", "app.admin.password",
                        "app.jwt.expiration-ms", "spring.flyway.enabled")) {
                    settings.put(key, property(environment, key));
                }
                validate(settings);
                return;
            }
        }
    }

    private static String property(ConfigurableEnvironment environment, String name) {
        try {
            return environment.getProperty(name);
        } catch (IllegalArgumentException unresolvedPlaceholder) {
            return null;
        }
    }

    public static void validate(Map<String, String> values) {
        List<String> errors = new ArrayList<>();
        String jdbcUrl = required(values, "spring.datasource.url", errors);
        required(values, "spring.datasource.username", errors);
        required(values, "spring.datasource.password", errors);
        String jwtSecret = required(values, "app.jwt.secret", errors);
        String corsOrigins = required(values, "app.cors.allowed-origins", errors);
        String tokenLifetime = required(values, "app.jwt.expiration-ms", errors);
        String flywayEnabled = required(values, "spring.flyway.enabled", errors);

        if (jdbcUrl != null && !isValidPostgresUrl(jdbcUrl)) {
            errors.add("DB_URL must be a PostgreSQL JDBC URL with a host and database name");
        }
        if (jwtSecret != null && !hasStrongSigningKeyLength(jwtSecret)) {
            errors.add("JWT_SECRET must be valid Base64 encoding of at least 32 bytes");
        }
        if (corsOrigins != null && !hasTrustedHttpsOrigins(corsOrigins)) {
            errors.add("CORS_ALLOWED_ORIGINS must contain only explicit HTTPS origins without paths or wildcards");
        }

        if (tokenLifetime != null) {
            try {
                if (Long.parseLong(tokenLifetime) <= 0) errors.add("JWT_EXPIRATION_MS must be a positive integer");
            } catch (NumberFormatException exception) {
                errors.add("JWT_EXPIRATION_MS must be a positive integer");
            }
        }
        boolean flywayValid = isBoolean(values.get("spring.flyway.enabled"));
        if (!flywayValid) errors.add("FLYWAY_ENABLED must be true or false");

        String bootstrapSetting = values.get("app.admin.bootstrap.enabled");
        String rotationSetting = values.get("app.admin.bootstrap.rotate-existing");
        boolean bootstrapValid = isBoolean(bootstrapSetting);
        boolean rotationValid = isBoolean(rotationSetting);
        if (!bootstrapValid) errors.add("APP_ADMIN_BOOTSTRAP_ENABLED must be true or false");
        if (!rotationValid) errors.add("APP_ADMIN_ROTATE_EXISTING must be true or false");
        boolean bootstrapEnabled = Boolean.parseBoolean(bootstrapSetting);
        boolean rotateExisting = Boolean.parseBoolean(rotationSetting);
        if (rotateExisting && !bootstrapEnabled) {
            errors.add("APP_ADMIN_ROTATE_EXISTING requires APP_ADMIN_BOOTSTRAP_ENABLED=true");
        }
        if (bootstrapEnabled) {
            String adminEmail = required(values, "app.admin.email", errors);
            String adminPassword = required(values, "app.admin.password", errors);
            if (adminEmail != null && !adminEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                errors.add("APP_ADMIN_EMAIL must be a valid email address");
            }
            if (adminPassword != null && adminPassword.length() < 16) {
                errors.add("APP_ADMIN_PASSWORD must contain at least 16 characters when bootstrap is enabled");
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid production configuration: " + String.join("; ", errors));
        }
    }

    private static boolean isBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }

    private static String required(Map<String, String> values, String key, List<String> errors) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            errors.add(key + " is required");
            return null;
        }
        return value.trim();
    }

    private static boolean isValidPostgresUrl(String value) {
        if (!value.startsWith("jdbc:postgresql://")) return false;
        try {
            URI uri = URI.create(value.substring("jdbc:".length()));
            return "postgresql".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && uri.getRawPath() != null
                    && uri.getRawPath().length() > 1
                    && uri.getUserInfo() == null
                    && !containsCredentialQuery(uri.getRawQuery());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean containsCredentialQuery(String query) {
        if (query == null) return false;
        return query.matches("(?is).*(^|&)(user|password)=.*");
    }

    private static boolean hasStrongSigningKeyLength(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            return decoded.length >= 32;
        } catch (IllegalArgumentException exception) {
            try {
                byte[] decoded = Base64.getUrlDecoder().decode(value);
                return decoded.length >= 32;
            } catch (IllegalArgumentException invalidBase64) {
                return false;
            }
        }
    }

    private static boolean hasTrustedHttpsOrigins(String values) {
        String[] origins = values.split(",", -1);
        if (origins.length == 0) return false;
        for (String item : origins) {
            String origin = item.trim();
            if (origin.isEmpty() || origin.contains("*")) return false;
            try {
                URI uri = URI.create(origin);
                if (!"https".equalsIgnoreCase(uri.getScheme())
                        || uri.getHost() == null
                        || isLocalHost(uri.getHost())
                        || uri.getUserInfo() != null
                        || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())
                        || uri.getRawQuery() != null
                        || uri.getRawFragment() != null) {
                    return false;
                }
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLocalHost(String value) {
        String host = value.toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host.equals("localhost")
                || host.endsWith(".localhost")
                || host.equals("localhost.localdomain")
                || host.matches("127(?:\\.[0-9]{1,3}){3}")
                || host.equals("::1")
                || host.equals("0:0:0:0:0:0:0:1");
    }
}
