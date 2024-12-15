package com.example.demo2.config;

import com.example.demo2.entity.User;
import com.example.demo2.enums.AuthProvider;
import com.example.demo2.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            // Check if test user already exists
            if (!userRepository.existsByEmail("test@example.com")) {
                User user = new User();
                user.setEmail("test@example.com");
                user.setPassword(passwordEncoder.encode("password123"));
                user.setName("Test User");
                user.setProvider(AuthProvider.LOCAL);
                Set<String> roles = new HashSet<>();
                roles.add("ROLE_USER");
                user.setRoles(roles);
                user.setEnabled(true);

                userRepository.save(user);
            }
        };
    }
}