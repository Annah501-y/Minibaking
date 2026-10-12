package com.example.minibank.util;

import com.example.minibank.repository.AccountRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class AccountNumberGenerator {

    private final AccountRepository accountRepository;
    private final SecureRandom random = new SecureRandom();

    public AccountNumberGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public String generate() {
        String accountNumber;

        do {
            // The range produces a 10-digit number without a leading zero.
            accountNumber = Long.toString(random.nextLong(
                    1_000_000_000L,
                    10_000_000_000L
            ));
        } while (accountRepository.existsByAccountNumber(accountNumber));

        return accountNumber;
    }
}