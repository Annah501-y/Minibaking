package com.example.minibank.dto.response;

public record TransferResponse(
        TransactionResponse debit,
        TransactionResponse credit
) {
}
