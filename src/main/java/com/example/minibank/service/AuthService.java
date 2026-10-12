package com.example.minibank.service;

import com.example.minibank.dto.request.LoginRequest;
import com.example.minibank.dto.request.RegisterRequest;
import com.example.minibank.dto.response.RegistrationResponse;
import com.example.minibank.dto.response.TokenResponse;
import com.example.minibank.entity.Account;
import com.example.minibank.entity.User;
import com.example.minibank.exception.EmailAlreadyRegisteredException;
import com.example.minibank.exception.InvalidCredentialsException;
import com.example.minibank.repository.AccountRepository;
import com.example.minibank.repository.UserRepository;
import com.example.minibank.security.JwtService;
import com.example.minibank.security.LoginAttemptLimiter;
import com.example.minibank.util.AccountNumberGenerator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** Handles customer registration and credential authentication. */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthService(
            UserRepository userRepository,
            AccountRepository accountRepository,
            AccountNumberGenerator accountNumberGenerator,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            LoginAttemptLimiter loginAttemptLimiter
    ) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptLimiter = loginAttemptLimiter;
    }

    /** Registers a customer and creates their zero-balance account atomically. */
    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = userRepository.save(new User(
                request.fullName().trim(),
                email,
                request.phoneNumber().trim(),
                passwordEncoder.encode(request.password())
        ));

        Account account = accountRepository.save(new Account(
                user,
                accountNumberGenerator.generate()
        ));

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new RegistrationResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                account.getAccountNumber(),
                account.getBalance(),
                user.getRole(),
                token,
                "Bearer",
                jwtService.getExpirationSeconds()
        );
    }

    /** Verifies credentials and returns a signed access token. */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request, String clientAddress) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        loginAttemptLimiter.checkAllowed(clientAddress, email);

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptLimiter.recordFailure(clientAddress, email);
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        return new TokenResponse(token, "Bearer", jwtService.getExpirationSeconds());
    }
}
