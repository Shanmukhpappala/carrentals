package com.carrental.backend;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/** Ephemeral credentials used only by the HTTP integration-test context. */
public final class TestIntegrationProperties {

    public static final String JWT_SECRET = randomValue();
    public static final String ADMIN_EMAIL = "integration-admin-" + UUID.randomUUID() + "@example.test";
    public static final String ADMIN_PASSWORD = randomValue();

    private TestIntegrationProperties() {
    }

    private static String randomValue() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
