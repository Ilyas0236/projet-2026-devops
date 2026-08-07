package com.wydad.digital.auth.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(String email) {
        super("Utilisateur non trouvé: " + email);
    }
}