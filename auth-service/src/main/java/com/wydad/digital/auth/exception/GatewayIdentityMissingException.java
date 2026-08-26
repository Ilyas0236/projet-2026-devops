package com.wydad.digital.auth.exception;

/**
 * §24 (défense-en-profondeur) : requête sans les headers d'identité posés
 * par la gateway (X-User-Email / X-User-Role), donc non passée par elle —
 * appel direct au port du service.
 * Toujours rejetée, même si l'email demandé correspondrait à un compte existant.
 */
public class GatewayIdentityMissingException extends RuntimeException {

    public GatewayIdentityMissingException() {
        super("Requête hors passerelle : headers d'identité absents");
    }
}
