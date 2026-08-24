package com.wydad.digital.sports.ws;

import com.wydad.digital.sports.config.TeamChatAuthInterceptor.TeamChatPrincipal;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.filter.SportsUserContext;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.repository.PlayerRepository;
import com.wydad.digital.sports.repository.TeamMessageRepository;
import com.wydad.digital.sports.service.TeamChatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.security.Principal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4 — preuve du chemin WEBSOCKET du chat : l'identité vient du
 * Principal posé au CONNECT (JWT), PAS du ThreadLocal HTTP. Le contrôleur
 * projette le principal dans le contexte avant d'appeler le service.
 *
 * <p>Reproduit le bug de prod « Identité introuvable dans le contexte de
 * sécurité » : sans la projection, un envoi STOMP d'un membre valide est
 * rejeté alors que le même envoi en REST réussit.</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:teamchatws;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "wydad.notification-service-uri=http://localhost:1"
})
@AutoConfigureMockMvc
class TeamChatWsControllerTest {

    @Autowired TeamChatWsController controller;
    @Autowired PlayerRepository playerRepository;
    @Autowired TeamMessageRepository teamMessageRepository;

    @MockBean com.wydad.digital.sports.client.NotificationClient notificationClient;
    // SimpMessagingTemplate réel n'est pas actif en test : mocké, on vérifie
    // seulement la logique métier/persistance du contrôleur.
    @MockBean org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    private static final Principal COACH =
            new TeamChatPrincipal(8L, "ENTRAINEUR");
    private static final Principal JOUEUR =
            new TeamChatPrincipal(9L, "JOUEUR");

    @Autowired com.wydad.digital.sports.repository.StaffRepository staffRepository;

    @BeforeEach
    void setup() {
        playerRepository.save(Player.builder()
                .userId(9L).fullName("Joueur WS").sportType(SportType.FOOTBALL)
                .category(Category.U19).build());
        staffRepository.save(com.wydad.digital.sports.model.Staff.builder()
                .userId(8L).fullName("Coach WS")
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(SportType.FOOTBALL).assignedCategory(Category.U19).build());
    }

    @AfterEach
    void clean() {
        SportsUserContext.clear();
        teamMessageRepository.deleteAll();
        playerRepository.deleteAll();
        staffRepository.deleteAll();
    }

    private Message<byte[]> stompSend(String content) {
        return MessageBuilder.withPayload(
                        ("{\"content\":\"" + content + "\"}").getBytes())
                .setHeader("simpMappingDestination", "/app/chat/FOOTBALL/U19/send")
                .build();
    }

    @Test
    void envoiStompAvecPrincipalPersisteLeMessage() {
        controller.send("FOOTBALL", "U19",
                new TeamChatWsController.ChatPayload("Prêt pour demain !"),
                COACH);

        var saved = teamMessageRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getSenderUserId()).isEqualTo(8L);
        assertThat(saved.get(0).getSenderName()).isEqualTo("Coach WS"); // nom résolu depuis la fiche staff
        // Le contexte ThreadLocal a été nettoyé après traitement :
        assertThat(SportsUserContext.getCurrentUserId()).isNull();
    }

    @Test
    void envoiStompSansPrincipalRefuse() {
        assertThatThrownBy(() -> controller.send("FOOTBALL", "U19",
                new TeamChatWsController.ChatPayload("anon"), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(teamMessageRepository.count()).isZero();
    }

    @Test
    void envoiStompHorsGroupeRefuseViaFiche() {
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
