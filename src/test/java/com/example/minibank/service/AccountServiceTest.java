package com.example.minibank.service;

import com.example.minibank.entity.Account;
import com.example.minibank.entity.User;
import com.example.minibank.entity.Transaction;
import com.example.minibank.entity.TransactionType;
import com.example.minibank.exception.AccountNotFoundException;
import com.example.minibank.exception.InsufficientFundsException;
import com.example.minibank.repository.AccountRepository;
import com.example.minibank.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;

    @InjectMocks private AccountService accountService;

    @Test
    void depositUpdatesBalanceAndRecordsTransaction() {
        Account account = new Account(
                new User("Ada Lovelace", "ada@example.com", "+255712345678", "hash"),
                "2026000001"
        );
        when(accountRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = accountService.deposit(7L, new BigDecimal("25.00"));

        assertEquals(new BigDecimal("25.00"), account.getBalance());
        assertEquals(new BigDecimal("25.00"), response.balanceAfter());
        verify(transactionRepository).save(any());
    }

    @Test
    void withdrawalRejectsInsufficientFundsWithoutWritingTransaction() {
        Account account = new Account(
                new User("Ada Lovelace", "ada@example.com", "+255712345678", "hash"),
                "2026000001"
        );
        when(accountRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.of(account));

        assertThrows(InsufficientFundsException.class,
                () -> accountService.withdraw(7L, new BigDecimal("1.00")));

        assertEquals(BigDecimal.ZERO, account.getBalance());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void getAccountReturnsAccountOwnedByUser() {
        Account account = new Account(
                new User("Ada Lovelace", "ada@example.com", "+255712345678", "hash"),
                "2026000001"
        );
        when(accountRepository.findByUser_Id(7L)).thenReturn(Optional.of(account));

        var response = accountService.getAccount(7L);

        assertEquals("Ada Lovelace", response.accountHolderName());
        assertEquals("2026000001", response.accountNumber());
    }

    @Test
    void withdrawUpdatesBalanceAndRecordsTransaction() {
        Account account = new Account(
                new User("Ada Lovelace", "ada@example.com", "+255712345678", "hash"),
                "2026000001"
        );
        account.updateBalance(new BigDecimal("40.00"));
        when(accountRepository.findByUserIdForUpdate(7L)).thenReturn(Optional.of(account));
        when(transactionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = accountService.withdraw(7L, new BigDecimal("15.00"));

        assertEquals(new BigDecimal("25.00"), account.getBalance());
        assertEquals(TransactionType.WITHDRAWAL, response.type());
        verify(transactionRepository).save(any());
    }

    @Test
    void historyReturnsPagedTransactionsForAccount() {
        Account account = org.mockito.Mockito.mock(Account.class);
        Transaction transaction = Transaction.create(
                account, TransactionType.DEPOSIT,
                new BigDecimal("10.00"), new BigDecimal("10.00")
        );
        when(accountRepository.findByUser_Id(7L)).thenReturn(Optional.of(account));
        when(transactionRepository.findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(transaction)));

        var response = accountService.getTransactionHistory(7L, 0, 10);

        verify(transactionRepository).findAll(
                any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)
        );
        assertEquals(1, response.content().size());
        assertEquals(TransactionType.DEPOSIT, response.content().get(0).type());
        assertEquals(1, response.totalElements());
    }

    @Test
    void accountLookupRejectsUserWithoutAccount() {
        when(accountRepository.findByUser_Id(7L)).thenReturn(Optional.empty());

        assertThrows(AccountNotFoundException.class, () -> accountService.getAccount(7L));
    }

    @Test
    void depositRejectsMoreThanTwoFractionDigits() {
        assertThrows(IllegalArgumentException.class,
                () -> accountService.deposit(7L, new BigDecimal("1.001")));
        verify(transactionRepository, never()).save(any());
    }
}
