package com.wydad.digital.auth.exception;

/**
 * Levée au login lorsque l'email ou le mot de passe est incorrect.
 * Message volontairement identique pour les deux cas afin d'empêcher
 * l'énumération des comptes.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email ou mot de passe incorrect");
    }
}
