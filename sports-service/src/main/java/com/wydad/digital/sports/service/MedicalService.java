package com.wydad.digital.sports.service;

import com.wydad.digital.sports.client.NotificationClient;
import com.wydad.digital.sports.enums.MedicalStatus;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.repository.PlayerRepository;
import com.wydad.digital.sports.repository.StaffRepository;
import com.wydad.digital.sports.filter.SportsUserContext;
import com.wydad.digital.sports.util.TargetUrlResolver;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * B.6 — Statut médical strict.
 *
 * Règles serveur :
 * <ul>
 *   <li>seul le staff MÉDICAL (DOCTOR / PHYSIOTHERAPIST) encadrant la
 *       catégorie du joueur peut poser APT / INAPTE ;</li>
 *   <li>un coach ou manager → 403 ;</li>
 *   <li>l'ADMIN peut agir sur tous les joueurs ;</li>
 *   <li>toute modification notifie le joueur (best-effort) ;</li>
 *   <li>la convocation d'un joueur INAPTE est refusée (règle appliquée
 *       dans PlayerSpaceService.createConvocation).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class MedicalService {

    private final PlayerRepository playerRepository;
    private final StaffRepository staffRepository;
    private final NotificationClient notificationClient;

    @Transactional
    public Player setMedicalStatus(Long joueurUserId, MedicalStatus status, String note) {
        Long me = SportsUserContext.getCurrentUserId();
        if (me == null) {
            throw new AccessDeniedException("Identité introuvable dans le contexte de sécurité");
        }
        if (status == null) {
            throw new IllegalArgumentException("Le statut médical est obligatoire");
        }

        Long staffId = 0L; // 0 = action administrative (ADMIN)
        if (!SportsUserContext.isAdmin()) {
            Staff staff = staffRepository.findByUserId(me)
                    .orElseThrow(() -> new AccessDeniedException(
                            "Aucun profil staff lié à votre compte"));
            // Rôle strictement médical
            if (staff.getRole() != com.wydad.digital.sports.enums.StaffRole.DOCTOR
                    && staff.getRole() != com.wydad.digital.sports.enums.StaffRole.PHYSIOTHERAPIST) {
                throw new AccessDeniedException(
                        "Seul le staff médical (médecin ou kiné) peut modifier le statut médical");
            }
            Player target = playerRepository.findByUserId(joueurUserId)
                    .orElseThrow(() -> new EntityNotFoundException("Joueur non trouvé: " + joueurUserId));
            boolean sameCategory = staff.getSportType() == target.getSportType()
                    && staff.getAssignedCategory() == target.getCategory();
            if (!sameCategory) {
                throw new AccessDeniedException(
                        "Le staff médical n'intervient que dans sa catégorie");
            }
            staffId = staff.getId();
        }

        Player player = playerRepository.findByUserId(joueurUserId)
                .orElseThrow(() -> new EntityNotFoundException("Joueur non trouvé: " + joueurUserId));

        boolean changed = player.getMedicalStatus() != status;
        player.setMedicalStatus(status);
        player.setMedicalNote(note != null && !note.isBlank() ? note.trim() : null);
        player.setMedicalUpdatedAt(java.time.LocalDateTime.now());
        player.setMedicalUpdatedByStaffId(staffId);
        Player saved = playerRepository.save(player);

        if (changed) {
            notifyPlayer(saved, status);
        }
        return saved;
    }

    /** Variante DTO pour le contrôleur (évite d'exposer l'entité brute). */
    @Transactional
    public com.wydad.digital.sports.dto.PlayerSpaceDtos.MedicalResponse setMedicalStatusAndRespond(
            Long joueurUserId, MedicalStatus status, String note) {
        Player p = setMedicalStatus(joueurUserId, status, note);
        return com.wydad.digital.sports.dto.PlayerSpaceDtos.MedicalResponse.builder()
                .joueurUserId(p.getUserId())
                .status(p.getMedicalStatus())
                .note(p.getMedicalNote())
                .updatedAt(p.getMedicalUpdatedAt())
                .build();
    }

    /** Statut médical d'un joueur — staff de la catégorie ou admin. */
    public MedicalStatus getStatus(Long joueurUserId) {
        return playerRepository.findByUserId(joueurUserId)
                .map(Player::getMedicalStatus)
                .orElseThrow(() -> new EntityNotFoundException("Joueur non trouvé: " + joueurUserId));
    }

    private void notifyPlayer(Player p, MedicalStatus status) {
        String message = switch (status) {
            case INAPTE -> "Votre statut médical est passé à INAPTE : aucune convocation ne sera possible tant qu'il n'est pas revenu à APT."
                    + (p.getMedicalNote() != null ? " Motif : " + p.getMedicalNote() : "");
            case APT -> "Votre statut médical est APT : vous pouvez être convoqué normalement.";
        };
        notificationClient.notifyUser(p.getUserId(), null,
                "Statut médical mis à jour", message,
                TargetUrlResolver.resolve("JOUEUR", "/medical"));
    }
}
