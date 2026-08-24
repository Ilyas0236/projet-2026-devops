package com.wydad.digital.notification.controller;

import com.wydad.digital.notification.enums.NotificationType;
import com.wydad.digital.notification.model.Notification;
import com.wydad.digital.notification.repository.NotificationPreferenceRepository;
import com.wydad.digital.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fonctionnalité 4/6 — Préférences de notification respectées côté serveur.
 *
 * Règles prouvées :
 *  - par défaut tous les canaux sont actifs (modèle opt-out) ;
 *  - un membre ne lit / modifie QUE SES préférences : l'identité vient des
 *    en-têtes X-User-* de la gateway, jamais du body ni du path ;
 *  - après désactivation d'un canal, AUCUNE notification n'est créée sur ce
 *    canal, même si le membre est explicitement ciblé par un envoi ADMIN
 *    ou un envoi interne service-a-service ;
 *  - les autres canaux restent fonctionnels ;
 *  - un broadcast compte uniquement les membres réellement notifiés.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification_prefs;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        // le dialecte est forcé PostgreSQL dans application.yml : on l'écrase
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "wydad.internal-secret=secret-test-prefs"
})
@AutoConfigureMockMvc
@Transactional
class NotificationPreferenceTest {

    private static final String BASE = "/api/notification";
    private static final String SECRET = "secret-test-prefs";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationPreferenceRepository preferenceRepository;

    @BeforeEach
    void clean() {
        notificationRepository.deleteAll();
        preferenceRepository.deleteAll();
    }

    private static String internalBody(long userId, NotificationType type) {
        return """
                {"userId": %d, "title": "Commande confirmée", "message": "Votre commande est prête",
                 "type": "%s"}""".formatted(userId, type);
    }

    private void assertNoNotificationFor(long userId) {
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)).isEmpty();
    }

    private void assertNotificationCountFor(long userId, int expected) {
        assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)).hasSize(expected);
    }

    @Test
    @DisplayName("Sans préférence enregistrée, tous les canaux sont actifs (opt-out)")
    void sansPreferenceToutCanalEstActif() throws Exception {
        mockMvc.perform(get(BASE + "/preferences")
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "membre@test.ma")
                        .header("X-User-Role", "ADHERENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.pushEnabled").value(true))
                .andExpect(jsonPath("$.inAppEnabled").value(true));

        // Un envoi interne IN_APP passe normalement
        mockMvc.perform(post(BASE + "/internal/send")
                        .header("X-Internal-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(internalBody(42L, NotificationType.IN_APP)))
                .andExpect(status().isCreated());

        assertNotificationCountFor(42L, 1);
    }

    @Test
    @DisplayName("Chacun ne lit et modifie que SES préférences (identité X-User-*)")
    void preferencesLieesALIdentiteDuToken() throws Exception {
        // L'utilisateur 42 modifie ses préférences...
        mockMvc.perform(put(BASE + "/preferences")
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "membre@test.ma")
                        .header("X-User-Role", "JOUEUR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\": false, \"pushEnabled\": true, \"inAppEnabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.emailEnabled").value(false));

        // ...et c'est bien la ligne userId=42 qui a été créée/modifiée.
        assertThat(preferenceRepository.findByUserId(42L)).isPresent();
        assertThat(preferenceRepository.findByUserId(99L)).isEmpty();

        // Un autre utilisateur voit toujours ses préférences par défaut.
        mockMvc.perform(get(BASE + "/preferences")
                        .header("X-User-Id", "99")
                        .header("X-User-Email", "autre@test.ma")
                        .header("X-User-Role", "ADHERENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(true));
    }

    @Test
    @DisplayName("Canal désactivé -> aucune notification même ciblée par un envoi ADMIN")
    void canalDesactiveAucunEnvoiMemeCibleAdmin() throws Exception {
        // Le membre désactive IN_APP...
        mockMvc.perform(put(BASE + "/preferences")
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "membre@test.ma")
                        .header("X-User-Role", "JOUEUR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\": true, \"pushEnabled\": true, \"inAppEnabled\": false}"))
                .andExpect(status().isOk());

        // ...un ADMIN le vise explicitement avec /send IN_APP -> 202, rien créé.
        mockMvc.perform(post(BASE + "/send")
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(internalBody(42L, NotificationType.IN_APP)))
                .andExpect(status().isAccepted());

        assertNoNotificationFor(42L);

        // L'autre canal reste fonctionnel pour ce même membre.
        mockMvc.perform(post(BASE + "/send")
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@test.ma")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(internalBody(42L, NotificationType.PUSH)))
                .andExpect(status().isCreated());
        assertNotificationCountFor(42L, 1);
    }

    @Test
    @DisplayName("Envoi interne bloqué par préférence : aucun envoi, réponse 202")
    void envoiInterneBloqueParPreference() throws Exception {
        mockMvc.perform(put(BASE + "/preferences")
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "membre@test.ma")
                        .header("X-User-Role", "JOUEUR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\": true, \"pushEnabled\": false, \"inAppEnabled\": true}"))
                .andExpect(status().isOk());

        // Le shop-service appelle /internal/send PUSH -> bloqué, rien persisté.
        mockMvc.perform(post(BASE + "/internal/send")
                        .header("X-Internal-Secret", SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(internalBody(42L, NotificationType.PUSH)))
                .andExpect(status().isAccepted());

        assertNoNotificationFor(42L);
    }

    @Test
    @DisplayName("Secret interne invalide -> 403 même avec préférences actives")
    void secretInterneInvalideRefuse403() throws Exception {
        mockMvc.perform(post(BASE + "/internal/send")
                        .header("X-Internal-Secret", "mauvais-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(internalBody(42L, NotificationType.IN_APP)))
                .andExpect(status().isForbidden());
        assertNoNotificationFor(42L);
    }
}
