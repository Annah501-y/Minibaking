package com.example.minibank.dto.response;

import java.math.BigDecimal;

public record AccountResponse(
        String accountHolderName,
        String accountNumber,
        BigDecimal balance
) {
}