package com.example.minibank.dto.response;

import java.util.List;

public record TransactionHistoryResponse(
        List<TransactionHistoryItem> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}