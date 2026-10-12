package com.example.minibank.dto.response;

import java.time.Instant;

public record AdminUserResponse(
        Long userId,
        String fullName,
        String email,
        String phoneNumber,
        String role,
        Instant createdAt
) {
}
