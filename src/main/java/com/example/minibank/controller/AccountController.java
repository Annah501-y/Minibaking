package com.example.minibank.controller;

import com.example.minibank.dto.request.MoneyRequest;
import com.example.minibank.dto.request.TransferRequest;
import com.example.minibank.dto.response.AccountResponse;
import com.example.minibank.dto.response.TransactionHistoryResponse;
import com.example.minibank.dto.response.TransactionResponse;
import com.example.minibank.dto.response.TransferResponse;
import com.example.minibank.entity.TransactionType;
import com.example.minibank.service.AccountService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;

@RestController
@RequestMapping("/api/account")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ResponseEntity<AccountResponse> getAccount(
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.ok(accountService.getAccount(userId));
    }

    @PostMapping("/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MoneyRequest request
    ) {
        return ResponseEntity.ok(
                accountService.deposit(userId, request.amount())
        );
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody MoneyRequest request
    ) {
        return ResponseEntity.ok(
                accountService.withdraw(userId, request.amount())
        );
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransferResponse> transfer(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TransferRequest request
    ) {
        return ResponseEntity.ok(accountService.transfer(
                userId, request.recipientAccountNumber(), request.amount()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<TransactionHistoryResponse> getTransactionHistory(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to
    ) {
        return ResponseEntity.ok(
                accountService.getTransactionHistory(userId, page, size, type, from, to)
        );
    }
}
