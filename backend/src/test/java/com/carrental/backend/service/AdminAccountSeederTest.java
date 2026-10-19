package com.carrental.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.carrental.backend.entity.User;
import com.carrental.backend.repositary.UserRepositary;

@ExtendWith(MockitoExtension.class)
class AdminAccountSeederTest {

    @Mock
    private UserRepositary userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApplicationArguments arguments;

    private AdminAccountSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new AdminAccountSeeder(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(seeder, "adminEmail", " Admin@Example.test ");
        ReflectionTestUtils.setField(seeder, "adminPassword", "provisioning-password");
        ReflectionTestUtils.setField(seeder, "bootstrapEnabled", true);
        ReflectionTestUtils.setField(seeder, "rotateExisting", false);
    }

    @Test
    void bootstrapDisabledDoesNotLookUpOrChangeAccounts() {
        ReflectionTestUtils.setField(seeder, "bootstrapEnabled", false);

        seeder.run(arguments);

        verify(userRepository, never()).findByEmail(org.mockito.ArgumentMatchers.anyString());
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void provisionsNewAdminWhenBootstrapEnabledAndRotationDisabled() {
        when(userRepository.findByEmail("admin@example.test")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("provisioning-password")).thenReturn("encoded-password");

        seeder.run(arguments);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@example.test");
        assertThat(saved.getValue().getRole()).isEqualTo("ADMIN");
        assertThat(saved.getValue().getPassword()).isEqualTo("encoded-password");
    }

    @Test
    void existingAdminPasswordIsPreservedWhenRotationDisabled() {
        User existing = user("ADMIN", "old-hash");
        when(userRepository.findByEmail("admin@example.test")).thenReturn(Optional.of(existing));

        seeder.run(arguments);

        assertThat(existing.getPassword()).isEqualTo("old-hash");
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void existingAdminPasswordRotatesOnlyWhenExplicitlyEnabled() {
        ReflectionTestUtils.setField(seeder, "rotateExisting", true);
        User existing = user("ADMIN", "old-hash");
        when(userRepository.findByEmail("admin@example.test")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("provisioning-password")).thenReturn("new-hash");

        seeder.run(arguments);

        verify(userRepository).save(existing);
        assertThat(existing.getPassword()).isEqualTo("new-hash");
        assertThat(existing.getRole()).isEqualTo("ADMIN");
    }

    @Test
    void rotationForMissingAccountFailsWithoutCreatingAdmin() {
        ReflectionTestUtils.setField(seeder, "rotateExisting", true);
        when(userRepository.findByEmail("admin@example.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> seeder.run(arguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires an existing administrator account")
                .hasMessageNotContaining("provisioning-password");

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void matchingCustomerAccountIsNeverPromotedOrPasswordChanged() {
        User existing = user("CUSTOMER", "customer-hash");
        when(userRepository.findByEmail("admin@example.test")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> seeder.run(arguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-admin account")
                .hasMessageNotContaining("provisioning-password");

        assertThat(existing.getRole()).isEqualTo("CUSTOMER");
        assertThat(existing.getPassword()).isEqualTo("customer-hash");
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void repositorySaveFailurePropagatesWithoutExposingConfiguredPassword() {
        when(userRepository.findByEmail("admin@example.test")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("provisioning-password")).thenReturn("encoded-password");
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenThrow(new IllegalStateException("repository unavailable"));

        assertThatThrownBy(() -> seeder.run(arguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("repository unavailable")
                .hasMessageNotContaining("provisioning-password");
    }

    private User user(String role, String password) {
        User user = new User();
        user.setEmail("admin@example.test");
        user.setRole(role);
        user.setPassword(password);
        return user;
    }
}
