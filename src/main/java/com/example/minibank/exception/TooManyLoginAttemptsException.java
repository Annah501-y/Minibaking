package com.example.minibank.exception;

public class TooManyLoginAttemptsException extends RuntimeException {

    public TooManyLoginAttemptsException() {
        super("Too many failed login attempts. Try again in one minute.");
    }
}
