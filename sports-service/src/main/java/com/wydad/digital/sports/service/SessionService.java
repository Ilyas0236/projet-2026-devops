package com.wydad.digital.sports.service;

import com.wydad.digital.sports.dto.SessionDto;
import com.wydad.digital.sports.enums.Category;
import com.wydad.digital.sports.enums.SportType;
import com.wydad.digital.sports.filter.SportsUserContext;
import com.wydad.digital.sports.model.Session;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.repository.SessionRepository;
import com.wydad.digital.sports.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Création et consultation des séances d'entraînement.
 *
 * <p>La sélection des joueurs convoqués (notification personnalisée
 * « Vous êtes convoqué ») est déléguée à {@link SessionConvocationService}.
 * L'isolation §6/§24 est appliquée ici (sport/category/staffId forcés
 * depuis la fiche Staff pour les non-ADMIN) ; l'anti-IDOR sur la liste
 * des joueurs est appliqué dans le service de convocation.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final SessionRepository sessionRepository;
    private final StaffRepository staffRepository;
    private final SessionConvocationService sessionConvocationService;

    /**
     * Création d'une séance d'entraînement. Pour un non-ADMIN, sport/category
     * sont forcés depuis la fiche Staff et createdByStaffId est la valeur
     * réelle de la fiche (jamais le client). L'ADMIN peut cibler n'importe
     * quel groupe.
     *
     * <p>Les convocations et notifications in-app sont déléguées à
     * {@link SessionConvocationService#createConvocationsAndNotify}.</p>
     */
    @Transactional
    public SessionDto createSession(SessionDto dto) {
        SportType sportType = dto.getSportType();
        Category category = dto.getCategory();
        Long createdByStaffId = dto.getCreatedByStaffId();
        Long callerUserId = SportsUserContext.getCurrentUserId();

        if (!SportsUserContext.isAdmin()) {
            Staff fiche = staffRepository.findByUserId(callerUserId)
                    .orElseThrow(() -> new AccessDeniedException(
                            "Aucun profil encadrement rattaché à ce compte"));
            sportType = fiche.getSportType();
            category = fiche.getAssignedCategory();
            createdByStaffId = fiche.getId();
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

        // Convocations personnalisées : 1 ligne par joueur coché + 1 notif
        // in-app par joueur. Best-effort : une notif en échec ne casse pas
        // la création.
        long callerStaffUserId = SportsUserContext.isAdmin() || callerUserId == null
                ? 0L
                : callerUserId;
        sessionConvocationService.createConvocationsAndNotify(
                saved, dto.getJoueurUserIds(), callerStaffUserId);

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
