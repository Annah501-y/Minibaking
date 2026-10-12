package com.example.minibank.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank
        @Size(max = 150)
        String fullName,

        @NotBlank
        @Email
        @Size(max = 255)
        String email,

        @NotBlank
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$",
                message = "Phone number must use international format, such as +255712345678")
        String phoneNumber,

        @NotBlank
        @Size(min = 8, max = 72)
        String password
) {
}