package com.wydad.digital.sports.service;

import com.wydad.digital.sports.client.AuthClient;
import com.wydad.digital.sports.client.NotificationClient;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.filter.SportsUserContext;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.ScheduledCall;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.repository.PlayerRepository;
import com.wydad.digital.sports.repository.ScheduledCallRepository;
import com.wydad.digital.sports.repository.StaffRepository;
import com.wydad.digital.sports.service.ScheduledCallService.CreateCallRequest;
import com.wydad.digital.sports.service.ScheduledCallService.CallToken;
import com.wydad.digital.sports.service.ScheduledCallService.TargetType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Phase 5 — appels vidéo/vocaux programmés : autorisations de création
 * (ENTRAINEUR catégorie forcée depuis sa fiche, PRESIDENT premium/liste,
 * JOUEUR borné aux coéquipiers de son groupe), audience liste fermée,
 * jeton refusé hors liste, annulation organisateur uniquement,
 * notifications émises.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:calls;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "wydad.notification-service-uri=http://localhost:1",
        "livekit.url=wss://test.livekit.cloud",
        "livekit.api-key=testKey",
        "livekit.api-secret=testSecretThatIsLongEnoughForHmac"
})
class ScheduledCallServiceTest {

    @Autowired ScheduledCallService callService;
    @Autowired ScheduledCallRepository callRepository;
    @Autowired PlayerRepository playerRepository;
    @Autowired StaffRepository staffRepository;

    @MockBean NotificationClient notificationClient;
    @MockBean AuthClient authClient;

    private static final Long COACH = 8L;
    private static final Long JOUEUR_A = 9L;
    private static final Long JOUEUR_B = 10L;
    private static final Long PRESIDENT = 11L;
    private static final Long EXTERIEUR = 99L;

    @BeforeEach
    void setup() {
        playerRepository.save(Player.builder().userId(JOUEUR_A).fullName("Joueur A")
                .sportType(SportType.FOOTBALL).category(Category.SENIOR).build());
        playerRepository.save(Player.builder().userId(JOUEUR_B).fullName("Joueur B")
                .sportType(SportType.FOOTBALL).category(Category.SENIOR).build());
        staffRepository.save(Staff.builder().userId(COACH).fullName("Coach Test")
                .role(com.wydad.digital.sports.enums.StaffRole.HEAD_COACH)
                .sportType(SportType.FOOTBALL).assignedCategory(Category.SENIOR).build());
    }

    @AfterEach
    void clean() {
        SportsUserContext.clear();
        callRepository.deleteAll();
        playerRepository.deleteAll();
        staffRepository.deleteAll();
    }

    private void as(Long userId, String role) {
        SportsUserContext.setCurrentUserId(userId);
        SportsUserContext.setCurrentUserRole(role);
    }

    private CreateCallRequest reqCoach(TargetType target) {
        // sport/catégorie ignorés pour l'entraîneur : forcés depuis sa fiche.
        return new CreateCallRequest("Briefing avant match", SportType.BASKETBALL,
                Category.U20, LocalDateTime.now().plusHours(2), 30, target, null);
    }

    @Test
    void entraineurCreeAppelPourSaCategorieAvecJoueursConvoques() {
        as(COACH, "ENTRAINEUR");
        ScheduledCall call = callService.createCall(reqCoach(TargetType.CATEGORIE_EQUIPE));

        assertThat(call.getSportType()).isEqualTo(SportType.FOOTBALL); // forcé depuis la fiche, pas BASKETBALL
        assertThat(call.getCategory()).isEqualTo(Category.SENIOR);
        assertThat(call.getRoomName()).startsWith("wac-call-");
        assertThat(call.getStatus()).isEqualTo(ScheduledCall.CallStatus.PROGRAMME);
        // Joueurs A+B + coach, PAS un utilisateur extérieur :
        assertThat(call.getParticipantUserIds())
                .containsExactlyInAnyOrder(JOUEUR_A, JOUEUR_B, COACH);
        // Notifications envoyées aux 2 joueurs (pas au coach lui-même) :
        verify(notificationClient, org.mockito.Mockito.times(2))
                .notifyUser(anyLong(), any(), anyString(), anyString(), anyString());
    }

    @Test
    void joueurCreeAppelPourSesCoequipiersUniquement() {
        as(JOUEUR_A, "JOUEUR");
        // sport/catégorie ignorés : forcés depuis SA fiche (FOOTBALL SENIOR).
        ScheduledCall call = callService.createCall(new CreateCallRequest(
                "Entraînement entre nous", SportType.BASKETBALL, Category.U20,
                LocalDateTime.now().plusHours(3), 30, TargetType.EQUIPE_JOUEURS, null));

        // Groupe forcé depuis la fiche, PAS BASKETBALL/U20 :
        assertThat(call.getSportType()).isEqualTo(SportType.FOOTBALL);
        assertThat(call.getCategory()).isEqualTo(Category.SENIOR);
        // Coéquipier B + organisateur A — JAMAIS le coach ni un extérieur :
        assertThat(call.getParticipantUserIds())
                .containsExactlyInAnyOrder(JOUEUR_A, JOUEUR_B);
        verify(notificationClient).notifyUser(org.mockito.ArgumentMatchers.eq(JOUEUR_B),
                any(), org.mockito.ArgumentMatchers.contains("Appel programmé"),
                anyString(), anyString());
    }

    @Test
    void joueurNePeutPasInclureLeStaffNiCiblerAutreChose() {
        as(JOUEUR_A, "JOUEUR");
        // Cible équipe complète (staff inclus) : refusée au joueur.
        assertThatThrownBy(() -> callService.createCall(reqCoach(TargetType.CATEGORIE_EQUIPE)))
                .isInstanceOf(AccessDeniedException.class);
        // Cibles présidentielles : refusées aussi.
        assertThatThrownBy(() -> callService.createCall(new CreateCallRequest(
                "Premium", null, null, null, 30, TargetType.PREMIUM, null)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(callRepository.count()).isZero();
    }

    @Test
    void entraineurSansFicheRefuse() {
        as(JOUEUR_B, "ENTRAINEUR"); // rôle entraîneur mais aucune fiche staff
        assertThatThrownBy(() -> callService.createCall(reqCoach(TargetType.CATEGORIE_EQUIPE)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void presidentPeutCiblerLesAdherentsPremium() {
        as(PRESIDENT, "PRESIDENT");
        org.mockito.Mockito.when(authClient.getAllActiveUsers()).thenReturn(List.of(
                new AuthClient.UserProfile(JOUEUR_A, "a@x.ma", "A", "One", "JOUEUR", "ROUGE", "VALIDE", true),
                // « Premium » = DIAMANT/LEGENDE (grille réelle 2026/2027)
                new AuthClient.UserProfile(50L, "p@x.ma", "P", "Two", "ADHERENT", "DIAMANT", "VALIDE", true),
                new AuthClient.UserProfile(51L, "s@x.ma", "S", "Three", "ADHERENT", "LEGENDE", "VALIDE", true),
                // offres pelouse/tribune : jamais conviés via la cible PREMIUM
                new AuthClient.UserProfile(53L, "r@x.ma", "R", "Five", "ADHERENT", "ROUGE", "VALIDE", true),
                new AuthClient.UserProfile(54L, "o@x.ma", "O", "Six", "ADHERENT", "OR", "VALIDE", true),
                // invalide / refusé : jamais convié
                new AuthClient.UserProfile(52L, "x@x.ma", "X", "Four", "ADHERENT", "DIAMANT", "REFUSE", true)));

        ScheduledCall call = callService.createCall(new CreateCallRequest(
                "Réunion premium", null, null, LocalDateTime.now().plusHours(1), 45,
                TargetType.PREMIUM, null));

        assertThat(call.getParticipantUserIds()).containsExactlyInAnyOrder(50L, 51L, PRESIDENT);
    }

    @Test
    void entraineurNePeutPasCiblerPremium() {
        as(COACH, "ENTRAINEUR");
        assertThatThrownBy(() -> callService.createCall(new CreateCallRequest(
                "Détournement", null, null, LocalDateTime.now().plusHours(1), 30,
                TargetType.PREMIUM, null)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void presidentListeExpliciteFiltreLesComptesInvalides() {
        as(PRESIDENT, "PRESIDENT");
        org.mockito.Mockito.when(authClient.getAllActiveUsers()).thenReturn(List.of(
                new AuthClient.UserProfile(JOUEUR_A, "a@x.ma", "A", "One", "JOUEUR", "ROUGE", "VALIDE", true),
                new AuthClient.UserProfile(52L, "x@x.ma", "X", "Four", "JOUEUR", "ROUGE", "EN_ATTENTE", true)));

        ScheduledCall call = callService.createCall(new CreateCallRequest(
                "Entretien", null, null, null, 20, TargetType.UTILISATEURS,
                Set.of(JOUEUR_A, 52L, 404L)));

        assertThat(call.getParticipantUserIds()).containsExactly(JOUEUR_A, PRESIDENT);
    }

    @Test
    void jetonRefusePourNonConvoie() {
        as(COACH, "ENTRAINEUR");
        ScheduledCall call = callService.createCall(reqCoach(TargetType.CATEGORIE_JOUEURS));

        as(EXTERIEUR, "ADHERENT"); // pas dans la liste fermée
        assertThatThrownBy(() -> callService.joinToken(call.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void jetonDelivreAuParticipantAvecRoomEtIdentite() {
        as(COACH, "ENTRAINEUR");
        ScheduledCall call = callService.createCall(reqCoach(TargetType.CATEGORIE_JOUEURS));

        as(JOUEUR_A, "JOUEUR");
        CallToken token = callService.joinToken(call.getId());
        assertThat(token.roomName()).isEqualTo(call.getRoomName());
        assertThat(token.url()).isEqualTo("wss://test.livekit.cloud");
        assertThat(token.organizer()).isFalse();
        assertThat(token.token()).isNotBlank();

        // Le jeton est un JWT signé HS256 avec issuer = api key :
        var parts = token.token().split("\\.");
        assertThat(parts).hasSize(3);
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
        assertThat(payload).contains("\"room\":\"" + call.getRoomName() + "\"");
        assertThat(payload).contains("\"roomJoin\":true");
    }

    @Test
    void jetonOrganisateurAvecDroitsAdmin() {
        as(COACH, "ENTRAINEUR");
        ScheduledCall call = callService.createCall(reqCoach(TargetType.CATEGORIE_JOUEURS));

        CallToken token = callService.joinToken(call.getId());
        assertThat(token.organizer()).isTrue();
        String payload = new String(java.util.Base64.getUrlDecoder()
                .decode(token.token().split("\\.")[1]));
        assertThat(payload).contains("\"roomAdmin\":true");
        assertThat(payload).contains("\"roomCreate\":true");
    }

    @Test
    void jetonRefuseSurAppelAnnule() {
        as(COACH, "ENTRAINEUR");
        ScheduledCall call = callService.createCall(reqCoach(TargetType.CATEGORIE_JOUEURS));
        callService.cancelCall(call.getId());

        as(JOUEUR_A, "JOUEUR"); // pourtant convié
        assertThatThrownBy(() -> callService.joinToken(call.getId()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void annulationParNonOrganisateurRefuse() {
        as(COACH, "ENTRAINEUR");
        ScheduledCall call = callService.createCall(reqCoach(TargetType.CATEGORIE_EQUIPE));

        as(JOUEUR_A, "JOUEUR"); // participant mais pas organisateur
        assertThatThrownBy(() -> callService.cancelCall(call.getId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(callRepository.findById(call.getId()).orElseThrow().getStatus())
                .isEqualTo(ScheduledCall.CallStatus.PROGRAMME);
    }

    @Test
    void agendaNeMontreQueMesAppels() {
        as(COACH, "ENTRAINEUR");
        ScheduledCall call = callService.createCall(reqCoach(TargetType.CATEGORIE_JOUEURS));

        as(JOUEUR_A, "JOUEUR"); // convié → voit l'appel
        assertThat(callService.getMyCalls()).extracting(ScheduledCall::getId)
                .containsExactly(call.getId());

        as(EXTERIEUR, "ADHERENT"); // hors liste → agenda vide
        assertThat(callService.getMyCalls()).isEmpty();
    }

    @Test
    void titreVideOuDatePasseeRefuses() {
        as(COACH, "ENTRAINEUR");
        assertThatThrownBy(() -> callService.createCall(new CreateCallRequest(
                "  ", SportType.FOOTBALL, Category.SENIOR, null, 30,
                TargetType.CATEGORIE_EQUIPE, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> callService.createCall(new CreateCallRequest(
                "Trop tard", SportType.FOOTBALL, Category.SENIOR,
                LocalDateTime.now().minusHours(3), 30,
                TargetType.CATEGORIE_EQUIPE, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(callRepository.count()).isZero();
    }

    @Test
    void liveKitNonConfigureBloqueLeJetonPasLaProgrammation() {
        as(COACH, "ENTRAINEUR");
        ScheduledCall call = callService.createCall(reqCoach(TargetType.CATEGORIE_JOUEURS));

        // Simule une config absente : nouveau service sans clés.
        LiveKitTokenService degraded = new LiveKitTokenService("", "");
        assertThat(degraded.isConfigured()).isFalse();
        assertThatThrownBy(() -> degraded.createToken(call.getRoomName(), 9L, "J", false, 60))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("indisponible");
        // La programmation, elle, reste possible (mode dégradé assumé).
        assertThat(callRepository.findById(call.getId())).isPresent();
    }
}
