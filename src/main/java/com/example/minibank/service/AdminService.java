package com.example.minibank.service;

import com.example.minibank.dto.response.AdminAccountResponse;
import com.example.minibank.dto.response.AdminUserResponse;
import com.example.minibank.dto.request.CreateAdminRequest;
import com.example.minibank.entity.Account;
import com.example.minibank.entity.User;
import com.example.minibank.repository.AccountRepository;
import com.example.minibank.repository.UserRepository;
import com.example.minibank.exception.EmailAlreadyRegisteredException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Locale;

/** Provides paginated customer and account summaries to administrators. */
@Service
public class AdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(UserRepository userRepository, AccountRepository accountRepository,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AdminUserResponse createAdmin(CreateAdminRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException();
        }
        User admin = userRepository.save(new User(
                request.fullName().trim(), email, request.phoneNumber().trim(),
                passwordEncoder.encode(request.password()), "ADMIN"));
        return toUserResponse(admin);
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getUsers(int page, int size) {
        validatePage(page, size);
        return userRepository.findAll(pageRequest(page, size))
                .map(this::toUserResponse);
    }

    @Transactional(readOnly = true)
    public Page<AdminAccountResponse> getAccounts(int page, int size) {
        validatePage(page, size);
        return accountRepository.findAll(pageRequest(page, size))
                .map(this::toAccountResponse);
    }

    private PageRequest pageRequest(int page, int size) {
        return PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page must be non-negative and size must be between 1 and 100");
        }
    }

    private AdminUserResponse toUserResponse(User user) {
        return new AdminUserResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getPhoneNumber(), user.getRole(), user.getCreatedAt());
    }

    private AdminAccountResponse toAccountResponse(Account account) {
        User user = account.getUser();
        return new AdminAccountResponse(account.getId(), user.getId(), user.getFullName(),
                user.getEmail(), account.getAccountNumber(), account.getBalance(), account.getCreatedAt());
    }
}
