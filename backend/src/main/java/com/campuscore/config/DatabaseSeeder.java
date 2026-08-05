package com.campuscore.config;

import com.campuscore.entity.User;
import com.campuscore.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// @Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("Checking database for seed data...");

        // Seed ONLY the System Admin account. No programs, courses, hostel rooms,
        // or other users are created — everything else is added through the app.
        if (userRepository.count() == 0) {
            log.info("Seeding System Admin...");
            User admin = User.builder()
                    .name("System Admin")
                    .email("admin@campuscore.com")
                    .password(passwordEncoder.encode("admin123"))
                    .phone("1234567890")
                    .role(User.Role.ADMIN)
                    .status(User.UserStatus.ACTIVE)
                    .build();

            userRepository.save(admin);
        }

        log.info("Database seeding complete.");
    }
}
