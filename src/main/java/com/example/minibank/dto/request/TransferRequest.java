package com.example.minibank.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record TransferRequest(
        @NotBlank
        @Pattern(regexp = "\\d{10}", message = "Recipient account number must contain 10 digits")
        String recipientAccountNumber,
        @NotNull
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most two decimal places")
        BigDecimal amount
) {
}
