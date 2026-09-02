package com.wydad.digital.sports.service;

import com.wydad.digital.sports.client.NotificationClient;
import com.wydad.digital.sports.dto.SessionDtos.ConvokedPlayer;
import com.wydad.digital.sports.dto.SessionDtos.MyConvokedSession;
import com.wydad.digital.sports.dto.SessionDtos.SessionWithPlayersResponse;
import com.wydad.digital.sports.enums.MedicalStatus;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.Session;
import com.wydad.digital.sports.model.SessionConvocation;
import com.wydad.digital.sports.repository.PlayerRepository;
import com.wydad.digital.sports.repository.SessionConvocationRepository;
import com.wydad.digital.sports.repository.SessionRepository;
import com.wydad.digital.sports.util.TargetUrlResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Convocations personnalisées aux séances d'entraînement.
 *
 * <p>L'entraîneur (ou l'ADMIN) sélectionne un sous-ensemble de joueurs du
 * groupe visé par la séance ; chaque joueur sélectionné reçoit une
 * notification in-app personnalisée « Vous êtes convoqué à la séance X ».
 * L'ADMIN peut consulter la liste des joueurs convoqués pour chaque
 * séance, en lecture seule.</p>
 *
 * <p>Différent de {@link com.wydad.digital.sports.service.MatchConvocationService}
 * (workflow DRAFT → SOUMISE → PUBLIEE) : ici, pas de publication sur un
 * site public, l'admin consulte en lecture. La table
 * {@code convocations} (B.3.a) gère un workflow de réponse joueur
 * distinct (CONFIRME/ABSENT/RETARD) et n'est pas utilisée ici.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionConvocationService {

    private final SessionConvocationRepository repository;
    private final SessionRepository sessionRepository;
    private final PlayerRepository playerRepository;
    private final NotificationClient notificationClient;

    /**
     * Crée les convocations d'une séance (déjà persistée) et notifie chaque
     * joueur sélectionné. Anti-IDOR strict : tout joueur hors groupe
     * (sportType, category) provoque un 403.
     *
     * @param session       séance persistée (jamais null)
     * @param joueurUserIds liste des userId auth-service à convoquer
     * @param callerStaffId userId de l'appelant (0 si ADMIN)
     * @return nombre de convocations effectivement créées
     */
    @Transactional
    public int createConvocationsAndNotify(Session session, List<Long> joueurUserIds, Long callerStaffId) {
        if (joueurUserIds == null || joueurUserIds.isEmpty()) {
            return 0;
        }

        // Anti-IDOR : tous les joueurs doivent appartenir au groupe de la séance.
        // On charge en bulk par userId (champ du DTO) puis on vérifie ; un
        // seul hors groupe = 403 immédiat.
        List<Player> players = playerRepository.findByUserIdIn(joueurUserIds);
        Map<Long, Player> byUserId = players.stream()
                .collect(Collectors.toMap(Player::getUserId, p -> p, (a, b) -> a));
        for (Long userId : joueurUserIds) {
            Player p = byUserId.get(userId);
            if (p == null
                    || p.getSportType() != session.getSportType()
                    || p.getCategory() != session.getCategory()) {
                throw new AccessDeniedException(
                        "Joueur " + userId + " hors du groupe ("
                                + session.getSportType() + "/" + session.getCategory() + ")");
            }
        }

        int created = 0;
        String when = session.getSessionDate().toLocalDate().toString();
        String lieu = session.getLocation() != null ? " — " + session.getLocation() : "";
        for (Long userId : joueurUserIds) {
            // Pas de doublon : si déjà convoqué, on ne crée pas une 2e ligne.
            if (repository.existsBySessionIdAndJoueurUserId(session.getId(), userId)) {
                continue;
            }
            Player p = byUserId.get(userId);
            repository.save(SessionConvocation.builder()
                    .sessionId(session.getId())
                    .sportType(session.getSportType())
                    .category(session.getCategory())
                    .joueurUserId(userId)
                    .status(SessionConvocation.Status.CONVOQUE)
                    .createdByStaffUserId(callerStaffId)
                    .build());
            created++;

            // Joueur inapte : on garde la ligne de convocation pour la
            // traçabilité staff (l'entraîneur/adult voit qui était prévu
            // même si le joueur est blessé), mais on ne le notifie pas.
            if (p.getMedicalStatus() == MedicalStatus.INAPTE) {
                log.info("Joueur {} INAPTE — convocation session {} persistée mais non notifiée",
                        userId, session.getId());
                continue;
            }

            // Notification in-app best-effort (cf. MatchConvocationService).
            // TODO V2 : pousser aussi via WebSocket sur /topic/sessions/{userId}
            // (voir communication-service WebSocketConfig) pour le temps réel.
            // V1 : polling 30s côté front (cloche getMyUnreadCount) suffit.
            try {
                notificationClient.notifyUser(
                        p.getUserId(),
                        null,
                        "Convocation à une séance",
                        "Vous êtes convoqué à la séance « " + session.getTitle()
                                + " » le " + when + lieu + ".",
                        // La map ROLE_TO_URL["JOUEUR"]="/joueur/dashboard" gagne
                        // sur le fallback "/convocations" : on atterrit sur le
                        // dashboard joueur qui affichera ?focus=convocations.
                        TargetUrlResolver.resolve("JOUEUR", "/convocations"));
            } catch (Exception e) {
                log.warn("Notification convocation séance non envoyée à user {}: {}",
                        userId, e.getMessage());
            }
        }
        return created;
    }

    /** Joueurs convoqués pour une séance (vue admin). */
    public List<ConvokedPlayer> getConvokedPlayers(Long sessionId) {
        List<SessionConvocation> convocations = repository.findBySessionId(sessionId);
        if (convocations.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = convocations.stream()
                .map(SessionConvocation::getJoueurUserId)
                .collect(Collectors.toSet());
        Map<Long, Player> byUserId = new HashMap<>();
        for (Player p : playerRepository.findByUserIdIn(userIds)) {
            byUserId.put(p.getUserId(), p);
        }
        List<ConvokedPlayer> out = new ArrayList<>();
        for (SessionConvocation c : convocations) {
            Player p = byUserId.get(c.getJoueurUserId());
            out.add(ConvokedPlayer.builder()
                    .joueurUserId(c.getJoueurUserId())
                    .fullName(p != null ? p.getFullName() : null)
                    .jerseyNumber(p != null ? p.getJerseyNumber() : null)
                    .build());
        }
        return out;
    }

    /** Toutes les séances d'un groupe avec leur liste de joueurs convoqués. */
    public List<SessionWithPlayersResponse> getSessionsForAdmin(
            com.wydad.digital.sports.enums.SportType sportType,
            com.wydad.digital.sports.enums.Category category) {
        List<Session> sessions = sessionRepository
                .findBySportTypeAndCategoryOrderBySessionDateAsc(sportType, category);
        if (sessions.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> sessionIds = sessions.stream().map(Session::getId).toList();
        List<SessionConvocation> all = repository.findBySessionIdIn(sessionIds);
        Map<Long, List<SessionConvocation>> bySession = all.stream()
                .collect(Collectors.groupingBy(SessionConvocation::getSessionId));

        // Charger tous les joueurs convoqués en un seul aller-retour, par
        // userId (clé du DTO SessionConvocation.joueurUserId).
        Set<Long> userIds = all.stream()
                .map(SessionConvocation::getJoueurUserId)
                .collect(Collectors.toSet());
        Map<Long, Player> playerByUserId = new HashMap<>();
        for (Player p : playerRepository.findByUserIdIn(userIds)) {
            playerByUserId.put(p.getUserId(), p);
        }

        List<SessionWithPlayersResponse> out = new ArrayList<>();
        for (Session s : sessions) {
            List<ConvokedPlayer> players = new ArrayList<>();
            for (SessionConvocation c : bySession.getOrDefault(s.getId(), Collections.emptyList())) {
                Player p = playerByUserId.get(c.getJoueurUserId());
                players.add(ConvokedPlayer.builder()
                        .joueurUserId(c.getJoueurUserId())
                        .fullName(p != null ? p.getFullName() : null)
                        .jerseyNumber(p != null ? p.getJerseyNumber() : null)
                        .build());
            }
            out.add(SessionWithPlayersResponse.builder()
                    .id(s.getId())
                    .title(s.getTitle())
                    .description(s.getDescription())
                    .location(s.getLocation())
                    .sessionDate(s.getSessionDate())
                    .sportType(s.getSportType())
                    .category(s.getCategory())
                    .createdByStaffId(s.getCreatedByStaffId())
                    .createdAt(s.getCreatedAt())
                    .convokedPlayers(players)
                    .build());
        }
        return out;
    }

    /** Séances où le joueur connecté est convoqué (vue joueur). */
    public List<MyConvokedSession> getMyConvokedSessions(Long joueurUserId) {
        if (joueurUserId == null) {
            return Collections.emptyList();
        }
        List<SessionConvocation> convocations =
                repository.findByJoueurUserId(joueurUserId);
        if (convocations.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> sessionIds = convocations.stream()
                .map(SessionConvocation::getSessionId)
                .collect(Collectors.toSet());
        Map<Long, Session> byId = new HashMap<>();
        for (Session s : sessionRepository.findAllById(sessionIds)) {
            byId.put(s.getId(), s);
        }
        // Tri par date de séance croissante pour l'affichage joueur.
        return convocations.stream()
                .map(SessionConvocation::getSessionId)
                .distinct()
                .map(byId::get)
                .filter(s -> s != null)
                .sorted((a, b) -> a.getSessionDate().compareTo(b.getSessionDate()))
                .map(s -> MyConvokedSession.builder()
                        .id(s.getId())
                        .title(s.getTitle())
                        .description(s.getDescription())
                        .location(s.getLocation())
                        .sessionDate(s.getSessionDate())
                        .sportType(s.getSportType())
                        .category(s.getCategory())
                        .createdByStaffId(s.getCreatedByStaffId())
                        .build())
                .toList();
    }
}
