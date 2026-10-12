package com.example.minibank.exception;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException() {
        super("Insufficient balance for this withdrawal");
    }
}