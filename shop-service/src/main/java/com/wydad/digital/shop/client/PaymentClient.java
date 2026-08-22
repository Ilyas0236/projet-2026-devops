package com.wydad.digital.shop.client;

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
 * Client service-à-service vers payment-service pour le débit E-cash au
 * checkout boutique.
 *
 * FAIL-FAST comme côté billetterie : un échec de paiement (solde
 * insuffisant, payment-service indisponible) DOIT faire échouer la commande
 * — on ne confirme jamais une commande non payée.
 */
@Slf4j
@Component
public class PaymentClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String debitUrl;
    private final String internalSecret;

    public PaymentClient(
            @Value("${wydad.payment-service-uri:http://payment-service:8083}") String baseUrl,
            @Value("${wydad.internal-secret:}") String internalSecret) {
        this.debitUrl = baseUrl + "/api/payment/internal/debit";
        this.internalSecret = internalSecret;
    }

    /**
     * Débite le wallet E-cash du client. Lève une RuntimeException si le
     * paiement échoue — la transaction de commande est alors annulée
     * (rollback du stock et du code promo).
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
                log.error("Appel interne refuse par payment-service (403) - verifier WYDAD_INTERNAL_SECRET");
                throw new IllegalStateException(
                        "Paiement refuse : configuration interne invalide", e);
            }
            if (e.getStatusCode() == HttpStatus.BAD_REQUEST) {
                throw new IllegalArgumentException("Requete de paiement invalide", e);
            }
            log.warn("Paiement E-cash refuse pour {} : {}", email, e.getResponseBodyAsString());
            throw new RuntimeException(
                    "Paiement refuse : solde E-cash insuffisant ou erreur de paiement", e);
        } catch (Exception e) {
            log.error("payment-service injoignable lors du debit de {}", email, e);
            throw new RuntimeException("Paiement indisponible, commande annulee. Reessayez plus tard.", e);
        }
    }
}
