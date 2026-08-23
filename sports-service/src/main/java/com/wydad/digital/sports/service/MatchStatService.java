package com.wydad.digital.sports.service;

import com.wydad.digital.sports.dto.MatchStatDtos.MatchStatRequest;
import com.wydad.digital.sports.dto.MatchStatDtos.MatchStatResponse;
import com.wydad.digital.sports.model.MatchStat;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.repository.MatchStatRepository;
import com.wydad.digital.sports.repository.PlayerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Statistiques de match réelles (B.4) : le staff de la catégorie du
 * joueur saisit une ligne par rencontre ; les totaux de la fiche sont
 * recalculés par agrégation à chaque écriture.
 */
@Service
@RequiredArgsConstructor
public class MatchStatService {

    private final MatchStatRepository matchStatRepository;
    private final PlayerRepository playerRepository;

    /** Liste détaillée des matchs du joueur connecté (route self-service). */
    public List<MatchStatResponse> getMyStats() {
        Long me = requireCurrentUserId();
        return getStatsOf(me);
    }

    /** Stats détaillées d'un joueur donné (accès staff déjà scoping côté contrôleur). */
    public List<MatchStatResponse> getStatsOf(Long joueurUserId) {
        return matchStatRepository.findByJoueurUserIdOrderByMatchDateDesc(joueurUserId)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Saisie d'une stat de match par le STAFF (ou l'ADMIN). Le scoping
     * catégorie est vérifié par le contrôleur (ensureStaffCanManage).
     */
    @Transactional
    public MatchStatResponse addStat(Long joueurUserId, MatchStatRequest req, Long staffId) {
        Player player = playerRepository.findByUserId(joueurUserId)
                .orElseThrow(() -> new EntityNotFoundException("Joueur non trouvé: " + joueurUserId));

        if (req.opponent() == null || req.opponent().isBlank()) {
            throw new IllegalArgumentException("L'adversaire est obligatoire");
        }
        if (req.matchDate() == null) {
            throw new IllegalArgumentException("La date du match est obligatoire");
        }

        MatchStat saved = matchStatRepository.save(MatchStat.builder()
                .joueurUserId(joueurUserId)
                .sportType(player.getSportType())
                .category(player.getCategory())
                .opponent(req.opponent().trim())
                .matchDate(req.matchDate())
                .goals(req.goals() != null ? Math.max(0, req.goals()) : 0)
                .assists(req.assists() != null ? Math.max(0, req.assists()) : 0)
                .minutesPlayed(req.minutesPlayed())
                .competition(req.competition())
                .createdByStaffId(staffId)
                .build());

        recomputeTotals(player);
        return toResponse(saved);
    }

    private void recomputeTotals(Player p) {
        var stats = matchStatRepository.findByJoueurUserIdOrderByMatchDateDesc(p.getUserId());
        p.setMatchesPlayed(stats.size());
        p.setGoals(stats.stream().mapToInt(MatchStat::getGoals).sum());
        p.setAssists(stats.stream().mapToInt(MatchStat::getAssists).sum());
        playerRepository.save(p);
    }

    private MatchStatResponse toResponse(MatchStat m) {
        return MatchStatResponse.builder()
                .id(m.getId())
                .joueurUserId(m.getJoueurUserId())
                .sportType(m.getSportType())
                .category(m.getCategory())
                .opponent(m.getOpponent())
                .matchDate(m.getMatchDate())
                .goals(m.getGoals())
                .assists(m.getAssists())
                .minutesPlayed(m.getMinutesPlayed())
                .competition(m.getCompetition())
                .createdAt(m.getCreatedAt())
                .build();
    }

    private Long requireCurrentUserId() {
        Long id = com.wydad.digital.sports.filter.SportsUserContext.getCurrentUserId();
        if (id == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Identité introuvable dans le contexte de sécurité");
        }
        return id;
    }
}
