package com.wydad.digital.notification.controller;

import com.wydad.digital.notification.model.Notification;
import com.wydad.digital.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B.11 — Cohérence du rôle JOUEUR : l'espace de notifications unique n'est
 * cohérent QUE SI chaque membre ne voit que SES propres notifications.
 *
 * Règle serveur (assertSelfOrAdmin dans NotificationController) :
 *   - un membre authentifié ne peut lire / compter / marquer comme lue
 *     que les notifications dont userId == son X-User-Id ;
 *   - ADMIN a accès aux notifications de tous ;
 *   - anonyme est rejeté par la SecurityConfig (anyRequest().authenticated()).
 *
 * Identité injectée via les en-têtes gateway X-User-* (comme en prod).
 * H2 en mémoire, mode PostgreSQL — revalidation sur PostgreSQL au déploiement.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:notification_ownership;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        // le dialecte est forcé PostgreSQL dans application.yml : on l'écrase
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "wydad.internal-secret=secret-test"
})
@AutoConfigureMockMvc
@Transactional
class NotificationOwnershipSecurityTest {

    private static final String BASE = "/api/notification";

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    /** Notification IN_APP lisible par son seul propriétaire (status SENT = non lue). */
    private Notification notifFor(long userId, String title) {
        Notification n = Notification.builder()
                .userId(userId)
                .title(title)
                .message("Message pour " + userId)
                .type(com.wydad.digital.notification.enums.NotificationType.IN_APP)
                .targetUrl("/joueur/dashboard")
                .build();
        // SENT = livrée, pas encore lue (c'est le statut "unread" côté inbox)
        n.setStatus(com.wydad.digital.notification.enums.NotificationStatus.SENT);
        return notificationRepository.save(n);
    }

    @BeforeEach
    void seed() {
        notifFor(42L, "Nouvelle convocation");
        notifFor(42L, "Mise à jour de votre statut médical");
        notifFor(99L, "Notification d'un autre membre");
    }

    @Test
    @DisplayName("Un membre ne voit que SES propres notifications (jamais celles des autres)")
    void membreNeVoitQueSesPropresNotifications() throws Exception {
        mockMvc.perform(get(BASE + "/user/42")
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "joueur@test.ma")
                        .header("X-User-Role", "JOUEUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get(BASE + "/user/99")
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "joueur@test.ma")
                        .header("X-User-Role", "JOUEUR"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Le compteur de non-lues ne compte que SES notifications")
    void compteurLimiteASesNotifications() throws Exception {
        mockMvc.perform(get(BASE + "/user/42/unread/count")
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "joueur@test.ma")
                        .header("X-User-Role", "JOUEUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(2));
    }

    @Test
    @DisplayName("Marquer une notification d'un AUTRE utilisateur -> 403, rien ne change")
    void marquerLaNotificationDUnAutreRefusee() throws Exception {
        Long otherId = notifFor(99L, "Autre").getId();

        mockMvc.perform(patch(BASE + "/" + otherId + "/read")
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "joueur@test.ma")
                        .header("X-User-Role", "JOUEUR"))
                .andExpect(status().isForbidden());

        // La cible reste non lue : la règle protège aussi la mutation.
        mockMvc.perform(get(BASE + "/user/99/unread/count")
                        .header("X-User-Id", "99")
                        .header("X-User-Email", "autre@test.ma")
                        .header("X-User-Role", "ADHERENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(2)); // 2 notifs seedées pour 99 + celle-ci
    }

    @Test
    @DisplayName("Marquer SA propre notification comme lue fonctionne")
    void marquerSaPropreNotificationFonctionne() throws Exception {
        Long mine = notifFor(42L, "Ma convocation").getId();

        mockMvc.perform(patch(BASE + "/" + mine + "/read")
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "joueur@test.ma")
                        .header("X-User-Role", "JOUEUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READ"));

        // Le compteur de non-lues baisse en conséquence : 2 seedées + celle-ci
        // créée puis immédiatement lue => 2 restent non lues.
        mockMvc.perform(get(BASE + "/user/42/unread/count")
                        .header("X-User-Id", "42")
                        .header("X-User-Email", "joueur@test.ma")
                        .header("X-User-Role", "JOUEUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(2));
    }

    @Test
    @DisplayName("ADMIN peut lire les notifications de tout le monde et tout lister")
    void adminAccedeAToutesLesNotifications() throws Exception {
        mockMvc.perform(get(BASE + "/user/99")
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)); // la notification seedée du membre 99

        mockMvc.perform(get(BASE + "/all")
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ADMIN marque la notification d'un membre comme lue")
    void adminMarqueUneNotificationDeMembre() throws Exception {
        Long target = notifFor(99L, "Lue par le staff club").getId();

        mockMvc.perform(patch(BASE + "/" + target + "/read")
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@wydad.ma")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(99))
                .andExpect(jsonPath("$.status").value("READ"));
    }

    @Test
    @DisplayName("Anonyme refusé partout (anyRequest authenticated)")
    void anonymeRefuse() throws Exception {
        // Aucun en-tête X-User-* : le UserContextFilter ne pose aucune
        // Authentication, la SecurityConfig rejette (anyRequest authenticated).
        // Sans AuthenticationEntryPoint custom, Spring renvoie 403.
        mockMvc.perform(get(BASE + "/user/42"))
                .andExpect(status().isForbidden());
    }
}
