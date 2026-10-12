package com.example.minibank.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminAccountResponse(
        Long accountId,
        Long userId,
        String accountHolderName,
        String email,
        String accountNumber,
        BigDecimal balance,
        Instant createdAt
) {
}
