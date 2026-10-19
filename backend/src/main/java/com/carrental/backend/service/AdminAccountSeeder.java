package com.carrental.backend.service;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.carrental.backend.entity.User;
import com.carrental.backend.repositary.UserRepositary;

@Component
public class AdminAccountSeeder implements ApplicationRunner {

    private final UserRepositary userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String adminEmail;

    @Value("${app.admin.password:}")
    private String adminPassword;

    @Value("${app.admin.bootstrap.enabled:false}")
    private boolean bootstrapEnabled;

    @Value("${app.admin.bootstrap.rotate-existing:false}")
    private boolean rotateExisting;

    public AdminAccountSeeder(UserRepositary userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!bootstrapEnabled) {
            return;
        }

        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("Admin bootstrap is enabled but APP_ADMIN_EMAIL or APP_ADMIN_PASSWORD is missing");
        }

        String normalizedEmail = adminEmail.trim().toLowerCase(Locale.ROOT);
        var existingUser = userRepository.findByEmail(normalizedEmail);
        if (rotateExisting && existingUser.isEmpty()) {
            throw new IllegalStateException("Admin password rotation requires an existing administrator account");
        }
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
                throw new IllegalStateException("Admin bootstrap email belongs to a non-admin account");
            }
            if (rotateExisting) {
                user.setPassword(passwordEncoder.encode(adminPassword));
                userRepository.save(user);
            }
            return;
        }

        User admin = new User();
        admin.setName("Administrator");
        admin.setEmail(normalizedEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setPhone("0000000000");
        admin.setRole("ADMIN");
        userRepository.save(admin);
    }
}
