package com.wydad.digital.gamification.controller;

import com.wydad.digital.gamification.client.ContentClient;
import com.wydad.digital.gamification.client.NotificationClient;
import com.wydad.digital.gamification.repository.UserPointsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fidélité — preuves serveur de l'endpoint interne /points/credit :
 * - sans le secret partagé X-Internal-Secret -> 403, aucun point crédité ;
 * - avec un secret invalide -> 403 ;
 * - appel interne légitime : 250 DH payés -> 25 points (barème serveur
 *   1 pt / 10 DH), persistés dans user_points avec le niveau recalculé ;
 * - payload invalide (amountDh <= 0 ou manquant) -> 400.
 *
 * C'est cet endpoint qu'appellent shop-service et ticket-service après un
 * paiement confirmé — la règle métier (barème) est prouvée ICI, côté serveur.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gamification_loyalty;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        // Secret partagé connu des services internes uniquement
        "wydad.internal-secret=secret-test-fidelite"
})
@AutoConfigureMockMvc
@Transactional
class InternalPointsCreditTest {

    private static final String URL = "/api/gamification/internal/points/credit";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserPointsRepository userPointsRepository;

    @MockBean
    private ContentClient contentClient;

    @MockBean
    private NotificationClient notificationClient;

    @BeforeEach
    void setUp() {
        userPointsRepository.deleteAll();
        Mockito.doNothing().when(notificationClient).notifyUser(
                Mockito.anyLong(), Mockito.any(), Mockito.anyString(),
                Mockito.anyString(), Mockito.any());
    }

    @Test
    void sansSecretPartageRefuse403EtNeCrediteRien() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 42, \"amountDh\": 250.00}"))
                .andExpect(status().isForbidden());
        assertThat(userPointsRepository.findById(42L)).isEmpty();
    }

    @Test
    void secretInvalideRefuse403() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-Internal-Secret", "mauvais-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 42, \"amountDh\": 250.00}"))
                .andExpect(status().isForbidden());
        assertThat(userPointsRepository.findById(42L)).isEmpty();
    }

    @Test
    void creditEffectifBaremeUnPointParDixDirhams() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-Internal-Secret", "secret-test-fidelite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 42, \"amountDh\": 250.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.pointsCredited").value(25));

        var points = userPointsRepository.findById(42L).orElseThrow();
        assertThat(points.getTotalPoints()).isEqualTo(25);
        assertThat(points.getLevel()).isEqualTo(1); // 25 < 500

        // Un second achat cumule les points
        mockMvc.perform(post(URL)
                        .header("X-Internal-Secret", "secret-test-fidelite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 42, \"amountDh\": 5000.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsCredited").value(500));

        points = userPointsRepository.findById(42L).orElseThrow();
        assertThat(points.getTotalPoints()).isEqualTo(525);
        assertThat(points.getLevel()).isEqualTo(2); // (525/500)+1 = 2
    }

    @Test
    void montantInferieurADixDirhamsNeCrediteAucunPoint() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-Internal-Secret", "secret-test-fidelite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 7, \"amountDh\": 9.99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pointsCredited").value(0));
        // Rien n'est créé en base pour 0 point
        assertThat(userPointsRepository.count()).isZero();
    }

    @Test
    void payloadInvalideRenvoie400() throws Exception {
        // amountDh négatif
        mockMvc.perform(post(URL)
                        .header("X-Internal-Secret", "secret-test-fidelite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 42, \"amountDh\": -10}"))
                .andExpect(status().isBadRequest());

        // userId manquant
        mockMvc.perform(post(URL)
                        .header("X-Internal-Secret", "secret-test-fidelite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountDh\": 100}"))
                .andExpect(status().isBadRequest());

        assertThat(userPointsRepository.count()).isZero();
    }
}
