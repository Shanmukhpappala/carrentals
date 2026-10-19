package com.carrental.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.carrental.backend.Dto.RegisterRequestDto;
import com.carrental.backend.Dto.UserResponseDto;
import com.carrental.backend.entity.User;
import com.carrental.backend.repositary.UserRepositary;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepositary userRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Captor
    private ArgumentCaptor<User> userCaptor;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks
    private AuthService authService;

    @Test
    void register_shouldHashPasswordAndForceCustomerRole() {
        String email = "customer-" + UUID.randomUUID() + "@example.com";
        RegisterRequestDto request = new RegisterRequestDto("Alice", email, "password123", "1234567890");

        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(userRepository.save(userCaptor.capture())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });

        authService = new AuthService(userRepository, passwordEncoder, authenticationManager, jwtService);

        UserResponseDto response = authService.register(request);

        assertThat(response.getRole()).isEqualTo("CUSTOMER");
        assertThat(response.getEmail()).isEqualTo(email);
        assertThat(passwordEncoder.matches("password123", userCaptor.getValue().getPassword())).isTrue();
        assertThat(userCaptor.getValue().getPassword()).isNotEqualTo("password123");
    }
}
