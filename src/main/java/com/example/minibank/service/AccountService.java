package com.example.minibank.service;

import com.example.minibank.dto.response.AccountResponse;
import com.example.minibank.dto.response.TransactionHistoryItem;
import com.example.minibank.dto.response.TransactionHistoryResponse;
import com.example.minibank.dto.response.TransactionResponse;
import com.example.minibank.dto.response.TransferResponse;
import com.example.minibank.entity.Account;
import com.example.minibank.entity.Transaction;
import com.example.minibank.entity.TransactionType;
import com.example.minibank.exception.AccountNotFoundException;
import com.example.minibank.exception.InsufficientFundsException;
import com.example.minibank.repository.AccountRepository;
import com.example.minibank.repository.TransactionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Applies account rules and records balance changes with their ledger entries. */
@Service
public class AccountService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AccountService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    /** Returns the account associated with the authenticated customer. */
    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long userId) {
        Account account = accountRepository.findByUser_Id(userId)
                .orElseThrow(AccountNotFoundException::new);

        return new AccountResponse(
                account.getUser().getFullName(),
                account.getAccountNumber(),
                account.getBalance()
        );
    }

    /** Credits an account and records the resulting balance atomically. */
    @Transactional
    public TransactionResponse deposit(Long userId, BigDecimal amount) {
        validateAmount(amount);

        Account account = accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(AccountNotFoundException::new);

        BigDecimal newBalance = account.getBalance().add(amount);
        account.updateBalance(newBalance);

        Transaction transaction = transactionRepository.save(
                Transaction.create(
                        account,
                        TransactionType.DEPOSIT,
                        amount,
                        newBalance
                )
        );

        return toTransactionResponse(transaction);
    }

    /** Debits an account only when sufficient funds are available. */
    @Transactional
    public TransactionResponse withdraw(Long userId, BigDecimal amount) {
        validateAmount(amount);

        Account account = accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(AccountNotFoundException::new);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }

        BigDecimal newBalance = account.getBalance().subtract(amount);
        account.updateBalance(newBalance);

        Transaction transaction = transactionRepository.save(
                Transaction.create(
                        account,
                        TransactionType.WITHDRAWAL,
                        amount,
                        newBalance
                )
        );

        return toTransactionResponse(transaction);
    }

    /** Transfers funds atomically and records a debit and credit ledger entry. */
    @Transactional
    public TransferResponse transfer(Long userId, String recipientAccountNumber, BigDecimal amount) {
        validateAmount(amount);
        if (recipientAccountNumber == null || !recipientAccountNumber.matches("\\d{10}")) {
            throw new IllegalArgumentException("Recipient account number must contain 10 digits");
        }

        Account senderReference = accountRepository.findByUser_Id(userId)
                .orElseThrow(AccountNotFoundException::new);
        String senderAccountNumber = senderReference.getAccountNumber();
        if (senderAccountNumber.equals(recipientAccountNumber)) {
            throw new IllegalArgumentException("Cannot transfer money to the same account");
        }

        List<Account> lockedAccounts = accountRepository
                .findAllByAccountNumberInForUpdate(List.of(senderAccountNumber, recipientAccountNumber));
        Map<String, Account> accountsByNumber = lockedAccounts.stream()
                .collect(Collectors.toMap(Account::getAccountNumber, Function.identity()));
        Account sender = accountsByNumber.get(senderAccountNumber);
        Account recipient = accountsByNumber.get(recipientAccountNumber);
        if (sender == null || recipient == null || !sender.getUser().getId().equals(userId)) {
            throw new AccountNotFoundException();
        }
        if (sender.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }

        BigDecimal senderBalance = sender.getBalance().subtract(amount);
        BigDecimal recipientBalance = recipient.getBalance().add(amount);
        sender.updateBalance(senderBalance);
        recipient.updateBalance(recipientBalance);

        Transaction debit = transactionRepository.save(Transaction.create(
                sender, TransactionType.TRANSFER_OUT, amount, senderBalance));
        Transaction credit = transactionRepository.save(Transaction.create(
                recipient, TransactionType.TRANSFER_IN, amount, recipientBalance));

        return new TransferResponse(toTransactionResponse(debit), toTransactionResponse(credit));
    }

    /** Returns newest-first transaction history for the customer's account. */
    @Transactional(readOnly = true)
    public TransactionHistoryResponse getTransactionHistory(
            Long userId,
            int page,
            int size
    ) {
        return getTransactionHistory(userId, page, size, null, null, null);
    }

    /** Returns paginated history filtered by optional transaction type and time range. */
    @Transactional(readOnly = true)
    public TransactionHistoryResponse getTransactionHistory(
            Long userId,
            int page,
            int size,
            TransactionType type,
            Instant fromDate,
            Instant toDate
    ) {
        validatePagination(page, size);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("from must be earlier than or equal to to");
        }

        Account account = accountRepository.findByUser_Id(userId)
                .orElseThrow(AccountNotFoundException::new);

        Specification<Transaction> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("account").get("id"), account.getId()));
            if (type != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), type));
            }
            if (fromDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.<Instant>get("createdAt"), fromDate));
            }
            if (toDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.<Instant>get("createdAt"), toDate));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
        Page<Transaction> transactions = transactionRepository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by(
                        Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
        );

        return new TransactionHistoryResponse(
                transactions.getContent()
                        .stream()
                        .map(this::toHistoryItem)
                        .toList(),
                transactions.getNumber(),
                transactions.getSize(),
                transactions.getTotalElements(),
                transactions.getTotalPages()
        );
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException(
                    "Amount must be positive and have at most two decimal places"
            );
        }
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and " + MAX_PAGE_SIZE
            );
        }
    }

    private TransactionResponse toTransactionResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getCreatedAt()
        );
    }

    private TransactionHistoryItem toHistoryItem(Transaction transaction) {
        return new TransactionHistoryItem(
                transaction.getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getCreatedAt()
        );
    }
}
