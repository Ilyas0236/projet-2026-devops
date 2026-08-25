package com.wydad.digital.communication.ws;

import com.wydad.digital.communication.client.RosterClient;
import com.wydad.digital.communication.config.TeamChatAuthInterceptor.TeamChatPrincipal;
import com.wydad.digital.communication.filter.UserContext;
import com.wydad.digital.communication.repository.TeamMessageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Preuve du chemin WEBSOCKET du chat : l'identité vient du Principal posé
 * au CONNECT (JWT), PAS du ThreadLocal HTTP. Le contrôleur projette le
 * principal dans le contexte avant d'appeler le service.
 *
 * <p>Reproduit le bug de prod « Identité introuvable dans le contexte de
 * sécurité » : sans la projection, un envoi STOMP d'un membre valide est
 * rejeté alors que le même envoi en REST réussit.</p>
 *
 * <p>Adaptation sports → communication : le roster est MOCKÉ.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:teamchatws;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureMockMvc
class TeamChatWsControllerTest {

    @Autowired TeamChatWsController controller;
    @Autowired TeamMessageRepository teamMessageRepository;

    @MockBean com.wydad.digital.communication.client.NotificationClient notificationClient;
    @MockBean RosterClient rosterClient;
    // SimpMessagingTemplate réel n'est pas actif en test : mocké, on vérifie
    // seulement la logique métier/persistance du contrôleur.
    @MockBean org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    private static final Principal COACH =
            new TeamChatPrincipal(8L, "ENTRAINEUR");
    private static final Principal JOUEUR =
            new TeamChatPrincipal(9L, "JOUEUR");

    @BeforeEach
    void setup() {
        when(rosterClient.findMembership(8L)).thenReturn(
                new RosterClient.MembershipInfo(8L, "FOOTBALL", "U19", "STAFF", "Coach WS"));
        when(rosterClient.findMembership(9L)).thenReturn(
                new RosterClient.MembershipInfo(9L, "FOOTBALL", "U19", "JOUEUR", "Joueur WS"));
    }

    @AfterEach
    void clean() {
        UserContext.clear();
        teamMessageRepository.deleteAll();
    }

    @Test
    void envoiStompAvecPrincipalPersisteLeMessage() {
        controller.send("FOOTBALL", "U19",
                new TeamChatWsController.ChatPayload("Prêt pour demain !"),
                COACH);

        var saved = teamMessageRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getSenderUserId()).isEqualTo(8L);
        assertThat(saved.get(0).getSenderName()).isEqualTo("Coach WS"); // nom résolu depuis le roster
        // Le contexte ThreadLocal a été nettoyé après traitement :
        assertThat(UserContext.getCurrentUserId()).isNull();
    }

    @Test
    void envoiStompSansPrincipalRefuse() {
        assertThatThrownBy(() -> controller.send("FOOTBALL", "U19",
                new TeamChatWsController.ChatPayload("anon"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(teamMessageRepository.count()).isZero();
    }

    @Test
    void envoiStompHorsGroupeRefuseViaRoster() {
        // Le joueur 9 est FOOTBALL/U19 : il ne peut pas écrire dans PRO.
        assertThatThrownBy(() -> controller.send("FOOTBALL", "PRO",
                new TeamChatWsController.ChatPayload("intrusion"), JOUEUR))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(teamMessageRepository.count()).isZero();
    }

    @Test
    void presenceMetAJourLesSessionsEnLigne() {
        controller.presence("FOOTBALL", "U19",
                new TeamChatWsController.PresencePayload(true), JOUEUR);
        controller.presence("FOOTBALL", "U19",
                new TeamChatWsController.PresencePayload(true), COACH);

        // Accès réflexif au map interne pour prouver l'état « en ligne ».
        var field = assertOnlineMap();
        @SuppressWarnings("unchecked")
        var online = (java.util.Set<Long>) field.get("football:u19");
        assertThat(online).containsExactlyInAnyOrder(9L, 8L);

        controller.presence("FOOTBALL", "U19",
                new TeamChatWsController.PresencePayload(false), JOUEUR);
        @SuppressWarnings("unchecked")
        var after = (java.util.Set<Long>) field.get("football:u19");
        assertThat(after).containsExactly(8L);
    }

    private java.util.Map<String, Set<Long>> assertOnlineMap() {
        try {
            var f = TeamChatWsController.class.getDeclaredField("onlineByGroup");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            var map = (java.util.Map<String, Set<Long>>) f.get(controller);
            return map;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
