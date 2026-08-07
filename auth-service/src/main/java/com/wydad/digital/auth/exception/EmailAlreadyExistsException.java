package com.wydad.digital.auth.exception;

public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("Email déjà utilisé: " + email);
    }
}