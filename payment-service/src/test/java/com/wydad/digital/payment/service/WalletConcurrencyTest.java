package com.wydad.digital.payment.service;

import com.wydad.digital.payment.dto.TransactionResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 0-BIS.1 — Test de non-regression du verrou pessimiste du wallet.
 * Deux debits concurrents sur un solde insuffisant ne doivent JAMAIS produire
 * un solde negatif : le second appel doit echouer avec InsufficientFundsException
 * apres attente du verrou (PESSIMISTIC_WRITE), pas lire un solde stale.
 *
 * H2 en mode compatibilite PostgreSQL : supporte SELECT ... FOR UPDATE
 * (le mecanisme exact du verrou pessimiste), sans dependance a un demon
 * Docker sur la machine de build. Le comportement reste a valider aussi en
 * integration contre le PostgreSQL reel (docker-compose).
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:wallettest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // L'application.yml force le dialecte PostgreSQL (qui emet
        // FOR NO KEY UPDATE, ignore par H2) : on le remplace ici.
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WalletConcurrencyTest {

    private static final String EMAIL = "concurrency-test@wydad.ma";

    @Autowired
    private PaymentService paymentService;

    @BeforeAll
    void seedAccount() {
        paymentService.credit(new com.wydad.digital.payment.dto.CreditRequest(
                EMAIL, new BigDecimal("100.00"), "Seed test concurrence"));
    }

    /**
     * Deux debits concurrents de 80 DH sur un solde de 100 DH :
     * exactement un doit reussir (solde final 20 DH), l'autre echouer.
     */
    @Test
    void deuxDebitsConcurrentsSurSoldeInsuffisantNeProduisentJamaisUnSoldeNegatif() throws Exception {
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> f1 = pool.submit(() -> tryDebit(startGate));
            Future<Boolean> f2 = pool.submit(() -> tryDebit(startGate));

            startGate.countDown();
            boolean oneSucceeded = f1.get(30, TimeUnit.SECONDS) ^ f2.get(30, TimeUnit.SECONDS);

            assertEquals(true, oneSucceeded,
                    "Exactement un des deux debits concurrents doit reussir");
            assertEquals(new BigDecimal("20.00"),
                    paymentService.getBalance(EMAIL).balance(),
                    "Solde final : un seul debit de 80 DH sur les 100 DH initiaux");
        } finally {
            pool.shutdownNow();
        }
    }

    /** Deux dons concurrents couvrant tout le solde : meme invariant. */
    @Test
    void deuxDonsConcurrentsNeDecouvrentJamaisLeCompte() throws Exception {
        String email = "don-concurrency-test@wydad.ma";
        paymentService.credit(new com.wydad.digital.payment.dto.CreditRequest(
                email, new BigDecimal("50.00"), "Seed don"));

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> f1 = pool.submit(() -> tryDon(startGate, email));
            Future<Boolean> f2 = pool.submit(() -> tryDon(startGate, email));

            startGate.countDown();
            boolean oneSucceeded = f1.get(30, TimeUnit.SECONDS) ^ f2.get(30, TimeUnit.SECONDS);

            assertEquals(true, oneSucceeded,
                    "Exactement un des deux dons concurrents doit reussir");
            assertEquals(0, paymentService.getBalance(email).balance().compareTo(BigDecimal.ZERO),
                    "Solde final : un seul don de 50 DH sur les 50 DH initiaux");
        } finally {
            pool.shutdownNow();
        }
    }

    private Boolean tryDon(CountDownLatch startGate, String email) {
        try {
            startGate.await();
            paymentService.don(new com.wydad.digital.payment.dto.DonRequest(
                    email, new BigDecimal("50.00"), "MASSARI", null, false));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Boolean tryDebit(CountDownLatch startGate) {
        try {
            startGate.await();
            paymentService.debit(EMAIL, new BigDecimal("80.00"), "Test concurrence");
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
