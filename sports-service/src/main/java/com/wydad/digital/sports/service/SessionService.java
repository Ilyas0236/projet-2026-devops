package com.wydad.digital.sports.service;

import com.wydad.digital.sports.client.NotificationClient;
import com.wydad.digital.sports.dto.SessionDto;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.Session;
import com.wydad.digital.sports.repository.PlayerRepository;
import com.wydad.digital.sports.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final SessionRepository sessionRepository;
    private final PlayerRepository playerRepository;
    private final NotificationClient notificationClient;

    /**
     * Création d'une séance d'entraînement par le STAFF (ou l'ADMIN) pour un
     * sport/catégorie donnés. Chaque joueur du groupe visé reçoit une
     * notification IN_APP (best-effort : une panne de notification ne doit
     * pas empêcher la création de la séance).
     */
    public SessionDto createSession(SessionDto dto) {
        Session session = Session.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .location(dto.getLocation())
                .sessionDate(dto.getSessionDate())
                .sportType(dto.getSportType())
                .category(dto.getCategory())
                .createdByStaffId(dto.getCreatedByStaffId())
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
                        "/joueur/dashboard");
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
