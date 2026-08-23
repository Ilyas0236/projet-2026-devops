package com.wydad.digital.payment.controller;

import com.wydad.digital.payment.filter.UserContext;
import com.wydad.digital.payment.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 0-BIS.3 : toute erreur métier du payment-service renvoie un statut et un
 * corps JSON {error, message, timestamp} cohérents, jamais une trace serveur
 * brute. Montage standalone avec le GlobalExceptionHandler réel.
 */
class PaymentExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = buildMvc(Mockito.mock(PaymentService.class));
        // Simule le UserContextFilter de production : headers gateway -> contexte
        UserContext.setCurrentUserEmail("fan@test.ma");
        UserContext.setCurrentUserRole("ADHERENT");
    }

    private MockMvc buildMvc(PaymentService svc) {
        return MockMvcBuilders.standaloneSetup(new PaymentController(svc))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("Solde insuffisant -> 402 PAYMENT_REQUIRED + corps JSON homogène")
    void insufficientFunds_returns402WithJsonBody() throws Exception {
        PaymentService svc = Mockito.mock(PaymentService.class);
        when(svc.debit(anyString(), Mockito.any(BigDecimal.class), Mockito.any()))
                .thenThrow(new com.wydad.digital.payment.exception.InsufficientFundsException("Solde insuffisant"));
        MockMvc mvc = buildMvc(svc);

        mvc.perform(post("/api/payment/debit")
                        .param("amount", "100")
                        .param("description", "test"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error").value("PAYMENT_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Solde insuffisant"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Wallet introuvable -> 404 NOT_FOUND + corps JSON homogène")
    void entityNotFound_returns404WithJsonBody() throws Exception {
        PaymentService svc = Mockito.mock(PaymentService.class);
        when(svc.getBalance(anyString()))
                .thenThrow(new jakarta.persistence.EntityNotFoundException("Compte wallet introuvable"));
        MockMvc mvc = buildMvc(svc);

        mvc.perform(get("/api/payment/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Compte wallet introuvable"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Accès au wallet d'un autre utilisateur -> 403 FORBIDDEN + corps JSON homogène")
    void accessDenied_returns403WithJsonBody() throws Exception {
        MockMvc mvc = buildMvc(Mockito.mock(PaymentService.class));

        mvc.perform(get("/api/payment/balance").param("email", "autre@test.ma"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
