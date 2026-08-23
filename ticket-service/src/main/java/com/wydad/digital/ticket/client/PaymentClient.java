package com.wydad.digital.ticket.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * Client service-à-service vers payment-service pour le débit E-cash
 * à l'achat de billets.
 *
 * Contrairement à NotificationClient, ce client est FAIL-FAST : un échec
 * de paiement (solde insuffisant, payment-service indisponible) DOIT faire
 * échouer l'achat — on n'émet jamais de billet non payé.
 */
@Slf4j
@Component
public class PaymentClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String debitUrl;
    private final String refundUrl;
    private final String internalSecret;

    public PaymentClient(
            @Value("${wydad.payment-service-uri:http://payment-service:8083}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.debitUrl = baseUrl + "/api/payment/internal/debit";
        this.refundUrl = baseUrl + "/api/payment/internal/refund";
        this.internalSecret = internalSecret;
    }

    /**
     * Débite le wallet E-cash de l'acheteur. Lève une RuntimeException si le
     * paiement échoue (solde insuffisant, service indisponible) — la
     * transaction d'achat est alors annulée (rollback des places).
     */
    public void debitEcash(String email, BigDecimal amount, String reference) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalSecret != null && !internalSecret.isEmpty()) {
            headers.set("X-Internal-Secret", internalSecret);
        }

        var body = new java.util.HashMap<String, Object>();
        body.put("email", email);
        body.put("amount", amount);
        body.put("reference", reference);

        try {
            restTemplate.exchange(debitUrl, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Void.class);
            log.info("Paiement E-cash OK : {} DH pour {} (ref {})", amount, email, reference);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.FORBIDDEN) {
                // Secret partagé non configuré ou incorrect côté appelant
                log.error("Appel interne refuse par payment-service (403) - verifier WYDAD_INTERNAL_SECRET");
                throw new IllegalStateException(
                        "Paiement refuse : configuration interne invalide", e);
            }
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new IllegalArgumentException("Requete de paiement invalide", e);
            }
            // 402/500 etc. : solde insuffisant ou erreur paiement -> achat echoue
            log.warn("Paiement E-cash refuse pour {} : {}", email, e.getResponseBodyAsString());
            throw new RuntimeException(
                    "Paiement refuse : solde E-cash insuffisant ou erreur de paiement", e);
        } catch (Exception e) {
            // payment-service injoignable : on ne vend JAMAIS de billet gratuit
            log.error("payment-service injoignable lors du debit de {}", email, e);
            throw new RuntimeException("Paiement indisponible, achat annule. Reessayez plus tard.", e);
        }
    }

    /**
     * Rembourse le montant d'un billet annulé. Best-effort assumé et
     * journalisé : si payment-service est momentanément indisponible,
     * l'annulation reste valide (places restituées) mais le statut passe
     * à CANCELLED — le remboursement devra être retraité par un ADMIN.
     * On ne bloque jamais l'utilisateur pour une panne en aval.
     */
    public boolean refundEcash(String email, BigDecimal amount, String reference) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (internalSecret != null && !internalSecret.isEmpty()) {
            headers.set("X-Internal-Secret", internalSecret);
        }

        var body = new java.util.HashMap<String, Object>();
        body.put("email", email);
        body.put("amount", amount);
        body.put("reference", reference);

        try {
            restTemplate.exchange(refundUrl, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Void.class);
            log.info("Remboursement E-cash OK : {} DH pour {} (ref {})", amount, email, reference);
            return true;
        } catch (Exception e) {
            log.error("ECHEC remboursement E-cash pour {} (ref {}) - a retraiter manuellement",
                    email, reference, e);
            return false;
        }
    }
}
