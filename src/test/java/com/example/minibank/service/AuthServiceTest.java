package com.example.minibank.service;

import com.example.minibank.dto.request.LoginRequest;
import com.example.minibank.dto.request.RegisterRequest;
import com.example.minibank.entity.Account;
import com.example.minibank.entity.User;
import com.example.minibank.exception.InvalidCredentialsException;
import com.example.minibank.exception.EmailAlreadyRegisteredException;
import com.example.minibank.repository.AccountRepository;
import com.example.minibank.repository.UserRepository;
import com.example.minibank.security.JwtService;
import com.example.minibank.security.LoginAttemptLimiter;
import com.example.minibank.util.AccountNumberGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountNumberGenerator accountNumberGenerator;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private LoginAttemptLimiter loginAttemptLimiter;

    @InjectMocks private AuthService authService;

    @Test
    void registerStoresNormalizedEmailAndEncodedPassword() {
        RegisterRequest request = new RegisterRequest(
                " Ada Lovelace ", " ADA@example.com ", "+255712345678", "passphrase"
        );
        when(userRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(passwordEncoder.encode("passphrase")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountNumberGenerator.generate()).thenReturn("2026000001");

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("ada@example.com", captor.getValue().getEmail());
        assertEquals("encoded-password", captor.getValue().getPasswordHash());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyRegisteredException.class,
                () -> authService.register(new RegisterRequest(
                        "Ada Lovelace", "ada@example.com", "+255712345678", "passphrase"
                )));
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        User user = new User("Ada Lovelace", "ada@example.com", "+255712345678", "encoded");
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("passphrase", "encoded")).thenReturn(true);
        when(jwtService.generateToken(null, "ada@example.com", "USER")).thenReturn("signed-token");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        var response = authService.login(new LoginRequest(" ada@example.com ", "passphrase"), "127.0.0.1");

        assertEquals("signed-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600L, response.expiresIn());
    }

    @Test
    void loginRejectsIncorrectPassword() {
        User user = new User("Ada Lovelace", "ada@example.com", "+255712345678", "encoded");
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("ada@example.com", "wrong"), "127.0.0.1"));
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com"))
                .thenReturn(java.util.Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("missing@example.com", "passphrase"), "127.0.0.1"));
    }
}
