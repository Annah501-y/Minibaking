package com.example.minibank.dto.response;

import com.example.minibank.entity.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionHistoryItem(
        Long id,
        TransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        Instant createdAt
) {
}