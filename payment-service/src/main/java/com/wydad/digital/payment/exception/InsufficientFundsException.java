package com.wydad.digital.payment.exception;

/**
 * Levée lorsqu'un débit E-cash dépasse le solde disponible du compte.
 * Permet au contrôleur interne de renvoyer un 402 explicite aux services
 * appelants (billetterie, boutique) plutôt qu'un 500 générique.
 */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
