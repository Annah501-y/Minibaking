package com.example.minibank.dto.response;

import java.math.BigDecimal;

public record RegistrationResponse(
        Long userId,
        String fullName,
        String email,
        String accountNumber,
        BigDecimal balance,
        String role,
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
