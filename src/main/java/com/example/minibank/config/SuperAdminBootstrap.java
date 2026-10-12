package com.example.minibank.config;

import com.example.minibank.entity.User;
import com.example.minibank.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Creates the initial superadmin only when all bootstrap values are configured. */
@Component
public class SuperAdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String fullName;
    private final String email;
    private final String phoneNumber;
    private final String password;

    public SuperAdminBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.super-admin.full-name:}") String fullName,
            @Value("${app.bootstrap.super-admin.email:}") String email,
            @Value("${app.bootstrap.super-admin.phone-number:}") String phoneNumber,
            @Value("${app.bootstrap.super-admin.password:}") String password
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.fullName = fullName;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (fullName.isBlank() || email.isBlank() || phoneNumber.isBlank() || password.isBlank()) {
            return;
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        var existingUser = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (existingUser.isPresent()) {
            if (!"SUPER_ADMIN".equals(existingUser.get().getRole())) {
                throw new IllegalStateException("Configured superadmin email is already assigned to another role");
            }
            return;
        }
        userRepository.save(new User(fullName.trim(), normalizedEmail, phoneNumber.trim(),
                passwordEncoder.encode(password), "SUPER_ADMIN"));
    }
}
