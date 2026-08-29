package com.wydad.digital.sports.service;

import com.wydad.digital.sports.client.NotificationClient;
import com.wydad.digital.sports.dto.SessionDto;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.filter.SportsUserContext;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.Session;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.repository.PlayerRepository;
import com.wydad.digital.sports.repository.SessionRepository;
import com.wydad.digital.sports.repository.StaffRepository;
import com.wydad.digital.sports.util.TargetUrlResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final SessionRepository sessionRepository;
    private final PlayerRepository playerRepository;
    private final StaffRepository staffRepository;
    private final NotificationClient notificationClient;

    /**
     * Création d'une séance d'entraînement par le STAFF (ou l'ADMIN) pour un
     * sport/catégorie donnés. Chaque joueur du groupe visé reçoit une
     * notification IN_APP (best-effort : une panne de notification ne doit
     * pas empêcher la création de la séance).
     *
     * <p>Isolation §6/§24 : un STAFF ne peut créer que pour SON groupe —
     * sport/catégorie forcés depuis sa fiche, les valeurs du client sont
     * ignorées. L'ADMIN peut cibler n'importe quel groupe.</p>
     */
    public SessionDto createSession(SessionDto dto) {
        SportType sportType = dto.getSportType();
        Category category = dto.getCategory();
        Long createdByStaffId = dto.getCreatedByStaffId();

        if (!SportsUserContext.isAdmin()) {
            Staff fiche = staffRepository.findByUserId(SportsUserContext.getCurrentUserId())
                    .orElseThrow(() -> new AccessDeniedException(
                            "Aucun profil encadrement rattaché à ce compte"));
            sportType = fiche.getSportType();
            category = fiche.getAssignedCategory();
            createdByStaffId = fiche.getId(); // ownership réel, jamais le client
        }

        Session session = Session.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .location(dto.getLocation())
                .sessionDate(dto.getSessionDate())
                .sportType(sportType)
                .category(category)
                .createdByStaffId(createdByStaffId)
                .build();
        Session saved = sessionRepository.save(session);
        notifyGroup(saved);
        return mapToDto(saved);
    }

    public List<SessionDto> getSessionsByCategory(SportType sportType, Category category) {
        return sessionRepository.findBySportTypeAndCategoryOrderBySessionDateAsc(sportType, category)
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public List<SessionDto> getSessionsByStaff(Long staffId) {
        return sessionRepository.findByCreatedByStaffIdOrderBySessionDateDesc(staffId)
                .stream().map(this::mapToDto).collect(Collectors.toList());
    }

    /** Notifie tous les joueurs du groupe (sport + catégorie) visé par la séance. */
    private void notifyGroup(Session session) {
        List<Player> players = playerRepository
                .findBySportTypeAndCategory(session.getSportType(), session.getCategory());
        String quand = session.getSessionDate().toLocalDate().toString();
        for (Player player : players) {
            if (player.getUserId() == null) continue;
            try {
                notificationClient.notifyUser(
                        player.getUserId(),
                        null,
                        "Nouvelle séance d'entraînement",
                        session.getTitle() + " le " + quand
                                + (session.getLocation() != null ? " — " + session.getLocation() : "")
                                + ". Consultez votre planning.",
                        TargetUrlResolver.resolve("JOUEUR", "/seances"));
            } catch (Exception e) {
                log.warn("Notification séance non envoyée au joueur {}: {}",
                        player.getId(), e.getMessage());
            }
        }
    }

    private SessionDto mapToDto(Session session) {
        SessionDto dto = new SessionDto();
        dto.setId(session.getId());
        dto.setTitle(session.getTitle());
        dto.setDescription(session.getDescription());
        dto.setLocation(session.getLocation());
        dto.setSessionDate(session.getSessionDate());
        dto.setSportType(session.getSportType());
        dto.setCategory(session.getCategory());
        dto.setCreatedByStaffId(session.getCreatedByStaffId());
        return dto;
    }
}
