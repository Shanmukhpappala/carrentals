package com.carrental.backend.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class TestDatabaseIsolationValidatorTest {

    private static final String URL = "jdbc:postgresql://127.0.0.1:5433/carrental_test";
    private static final String TEST_ONLY_DUMMY_VALUE = "unit-test-placeholder";

    @Test
    void acceptsOnlyExplicitTestDatasourceSettings() {
        assertThatCode(() -> TestDatabaseIsolationValidator.validateSettings(
                URL, URL, "carrental_test_user", TEST_ONLY_DUMMY_VALUE, "validate", "true"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingCredentialsWithoutFallback() {
        assertThatThrownBy(() -> TestDatabaseIsolationValidator.validateSettings(
                URL, URL, "carrental_test_user", null, "validate", "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no fallback is permitted");
    }

    @Test
    void rejectsAnyDatasourceOtherThanTheApprovedLocalTestDatabase() {
        for (String unsafeUrl : new String[] {
                "jdbc:postgresql://localhost:5432/carrentalsbackend",
                "jdbc:postgresql://localhost:5432/production",
                "jdbc:postgresql://other-host:5432/carrental_test",
                "jdbc:postgresql://localhost:5432/carrental_test?user=another-user"
        }) {
            assertThatThrownBy(() -> TestDatabaseIsolationValidator.validateSettings(
                    unsafeUrl, unsafeUrl, "carrental_test_user", TEST_ONLY_DUMMY_VALUE, "validate", "true"))
                    .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only 127.0.0.1:5433/carrental_test");
        }
    }

    @Test
    void rejectsAlternateHostsAndPortsEvenWhenTheyNameTheTestDatabase() {
        for (String unsafeUrl : new String[] {
                "jdbc:postgresql://localhost:5433/carrental_test",
                "jdbc:postgresql://127.0.0.1:5432/carrental_test"
        }) {
            assertThatThrownBy(() -> TestDatabaseIsolationValidator.validateSettings(
                    unsafeUrl, unsafeUrl, "carrental_test_user", TEST_ONLY_DUMMY_VALUE, "validate", "true"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("only 127.0.0.1:5433/carrental_test");
        }
    }

    @Test
    void rejectsWrongRoleOrUnsafeSchemaSettings() {
        assertThatThrownBy(() -> TestDatabaseIsolationValidator.validateSettings(
                URL, URL, "postgres", TEST_ONLY_DUMMY_VALUE, "validate", "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no fallback is permitted");

        assertThatThrownBy(() -> TestDatabaseIsolationValidator.validateSettings(
                URL, URL, "carrental_test_user", TEST_ONLY_DUMMY_VALUE, "update", "true"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Hibernate validation");
    }

    @Test
    void rejectsCombiningTestWithAnotherSpringProfile() {
        assertThatCode(() -> TestDatabaseIsolationValidator.validateProfiles("test"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> TestDatabaseIsolationValidator.validateProfiles("test", "local"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test profile to be active alone");
    }

    @Test
    void missingEnvironmentVariablesFailBeforeAnyDatabaseConnection() {
        GenericApplicationContext context = new GenericApplicationContext();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        context.setEnvironment(environment);

        assertThatThrownBy(() -> new TestDatabaseIsolationValidator().initialize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no fallback is permitted");
    }

    @Test
    void refusesDatabaseIdentityOrPrivilegesThatAreNotIsolated() {
        assertThatThrownBy(() -> TestDatabaseIsolationValidator.validateDatabaseIdentity(
                "carrentalsbackend", "carrental_test_user", false, true, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not carrental_test");

        assertThatThrownBy(() -> TestDatabaseIsolationValidator.validateDatabaseIdentity(
                "carrental_test", "carrental_test_user", true, true, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("can connect to the development database");
    }
}
