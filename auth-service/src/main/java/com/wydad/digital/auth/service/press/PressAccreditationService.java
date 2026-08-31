package com.wydad.digital.auth.service.press;

import com.wydad.digital.auth.client.ContentClient;
import com.wydad.digital.auth.client.NotificationClient;
import com.wydad.digital.auth.dto.press.PressAccreditationRequest;
import com.wydad.digital.auth.dto.press.PressAccreditationResponse;
import com.wydad.digital.auth.model.Role;
import com.wydad.digital.auth.model.StatutCompte;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.model.press.PressAccreditation;
import com.wydad.digital.auth.model.press.PressAccreditationStatus;
import com.wydad.digital.auth.repository.UserRepository;
import com.wydad.digital.auth.repository.press.PressAccreditationRepository;
import com.wydad.digital.auth.service.PdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * B.17 — Workflow d'accréditation presse (multi-matchs) :
 *   1. Le journaliste authentifié (compte VALIDE + photo obligatoire) envoie
 *      une demande pour un match RÉEL du calendrier.
 *   2. L'admin reçoit la demande EN_ATTENTE, valide ou refuse avec motif.
 *   3. À la validation, un PDF (badge) est généré à la volée avec photo,
 *      identité, média, n° carte de presse et match couvert.
 *   4. Le journaliste peut re-télécharger son badge à tout moment.
 *
 * Règles de garde :
 *   - 400 PHOTO_REQUIRED : le journaliste n'a pas de photo de profil.
 *   - 400 MATCH_NOT_FOUND : le matchId ne correspond à aucun match.
 *   - 409 DUPLICATE_ACCREDITATION : une demande existe déjà pour ce couple
 *     (user, matchId) — contrainte d'unicité en BDD + check applicatif.
 *   - 404 ACCREDITATION_NOT_FOUND : id inconnu.
 *
 * Les notifications in-app sont best-effort (cf. NotificationClient) :
 * elles ne doivent JAMAIS faire échouer une transition.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PressAccreditationService {

    private final PressAccreditationRepository repository;
    private final UserRepository userRepository;
    private final ContentClient contentClient;
    private final NotificationClient notificationClient;
    private final PdfService pdfService;

    // ───────────────────────── Exceptions ─────────────────────────

    /** Le journaliste n'a pas de photo de profil — obligatoire pour badge. */
    public static class PhotoRequiredException extends RuntimeException {
        public PhotoRequiredException() {
            super("Téléversez votre photo de profil avant de demander une accréditation");
        }
    }

    /** Une demande existe déjà pour ce couple (user, matchId). */
    public static class DuplicateAccreditationException extends RuntimeException {
        public DuplicateAccreditationException() {
            super("Vous avez déjà une demande d'accréditation pour ce match");
        }
    }

    /** Aucune demande d'accréditation ne correspond à l'id fourni. */
    public static class AccreditationNotFoundException extends RuntimeException {
        public AccreditationNotFoundException(Long id) {
            super("Demande d'accréditation introuvable : " + id);
        }
    }

    /** Le matchId ne correspond à aucun match du calendrier. */
    public static class MatchNotFoundException extends RuntimeException {
        public MatchNotFoundException(Long matchId) {
            super("Match introuvable dans le calendrier du club : " + matchId);
        }
    }

    // ───────────────────────── Journaliste ─────────────────────────

    /**
     * Crée une demande d'accréditation EN_ATTENTE.
     * Vérifie : role=JOURNALISTE, statutCompte=VALIDE, photo présente,
     * match réel, pas de doublon.
     */
    @Transactional
    public PressAccreditationResponse createAccreditation(String email, PressAccreditationRequest request) {
        User user = loadJournalistOrThrow(email);

        // Garde 1 — photo de profil obligatoire
        if (user.getPhotoUrl() == null || user.getPhotoUrl().isBlank()) {
            throw new PhotoRequiredException();
        }

        // Garde 2 — match réel (vérif via content-service)
        String label = contentClient.fetchMatchLabel(request.matchId());
        if (label == null) {
            throw new MatchNotFoundException(request.matchId());
        }

        // Garde 3 — pas de doublon (user, match)
        if (repository.existsByUserAndMatchId(user, request.matchId())) {
            throw new DuplicateAccreditationException();
        }

        // Dénormalisation du matchDate pour tri (best-effort — peut être null
        // si la réponse du content-service n'expose pas la date).
        LocalDate matchDate = parseDateFromLabel(label);

        PressAccreditation entity = PressAccreditation.builder()
                .user(user)
                .matchId(request.matchId())
                .matchLabel(label)
                .matchDate(matchDate)
                .organismePresse(user.getOrganismePresse())
                .statut(PressAccreditationStatus.EN_ATTENTE)
                .build();
        repository.save(entity);
        log.info("[B.17] Nouvelle demande d'accréditation id={} user={} match={}", entity.getId(), user.getId(), request.matchId());

        // Notif admin (best-effort) — titre explicite côté back-office
        notificationClient.notifyUser(
                null,
                "admin@wac.ma",
                "Nouvelle demande d'accréditation",
                user.getFirstName() + " " + user.getLastName() + " — " + label,
                "/admin/presse/demandes"
        );

        return PressAccreditationResponse.from(entity, false);
    }

    /** Liste les demandes du journaliste connecté (vue journaliste). */
    @Transactional(readOnly = true)
    public List<PressAccreditationResponse> listMine(String email) {
        User user = loadJournalistOrThrow(email);
        return repository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(a -> PressAccreditationResponse.from(a, false))
                .collect(Collectors.toList());
    }

    /** Renvoie le PDF (badge) pour une demande : self ou ADMIN. */
    @Transactional(readOnly = true)
    public byte[] generateBadgeFor(Long accreditationId, String requesterEmail, boolean isAdmin) throws Exception {
        PressAccreditation a = repository.findById(accreditationId)
                .orElseThrow(() -> new AccreditationNotFoundException(accreditationId));

        if (!isAdmin) {
            // Garde : le journaliste ne peut demander QUE son propre badge
            User requester = userRepository.findByEmailIgnoreCase(requesterEmail)
                    .orElseThrow(() -> new AccreditationNotFoundException(accreditationId));
            if (a.getUser() == null || !a.getUser().getId().equals(requester.getId())) {
                throw new AccreditationNotFoundException(accreditationId);
            }
        }
        if (a.getStatut() != PressAccreditationStatus.VALIDE) {
            throw new IllegalStateException("Badge disponible uniquement après validation par l'admin");
        }
        return pdfService.generatePressBadge(a, a.getUser());
    }

    // ───────────────────────── Admin ─────────────────────────

    /** Liste des demandes EN_ATTENTE (file admin). */
    @Transactional(readOnly = true)
    public List<PressAccreditationResponse> listPending() {
        return repository.findByStatutOrderByCreatedAtAsc(PressAccreditationStatus.EN_ATTENTE).stream()
                .map(a -> PressAccreditationResponse.from(a, true))
                .collect(Collectors.toList());
    }

    /** Valide une demande : passe VALIDE, déclenche la génération du badge. */
    @Transactional
    public PressAccreditationResponse validate(Long id, String adminEmail) {
        PressAccreditation a = repository.findById(id)
                .orElseThrow(() -> new AccreditationNotFoundException(id));
        if (a.getStatut() == PressAccreditationStatus.VALIDE) {
            return PressAccreditationResponse.from(a, true);
        }
        if (a.getStatut() == PressAccreditationStatus.REFUSE) {
            throw new IllegalStateException("Une demande refusée ne peut pas être validée telle quelle — le journaliste doit en créer une nouvelle");
        }
        User admin = userRepository.findByEmailIgnoreCase(adminEmail).orElse(null);
        a.setStatut(PressAccreditationStatus.VALIDE);
        a.setDecidedAt(LocalDateTime.now());
        a.setDecidedBy(admin);
        a.setMotifRefus(null);
        repository.save(a);

        // Notif journaliste
        if (a.getUser() != null) {
            User journalist = a.getUser();
            notificationClient.notifyUser(
                    journalist.getId(),
                    journalist.getEmail(),
                    "Accréditation validée",
                    "Votre demande pour « " + a.getMatchLabel() + " » a été acceptée. Téléchargez votre badge depuis votre espace.",
                    "/journaliste/accueil"
            );
        }

        return PressAccreditationResponse.from(a, true);
    }

    /** Refuse une demande avec motif écrit (obligatoire). */
    @Transactional
    public PressAccreditationResponse refuse(Long id, String motif, String adminEmail) {
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Le motif de refus est obligatoire");
        }
        PressAccreditation a = repository.findById(id)
                .orElseThrow(() -> new AccreditationNotFoundException(id));
        if (a.getStatut() == PressAccreditationStatus.REFUSE) {
            return PressAccreditationResponse.from(a, true);
        }
        if (a.getStatut() == PressAccreditationStatus.VALIDE) {
            throw new IllegalStateException("Une demande déjà validée ne peut pas être refusée");
        }
        User admin = userRepository.findByEmailIgnoreCase(adminEmail).orElse(null);
        a.setStatut(PressAccreditationStatus.REFUSE);
        a.setDecidedAt(LocalDateTime.now());
        a.setDecidedBy(admin);
        a.setMotifRefus(motif.trim());
        repository.save(a);

        if (a.getUser() != null) {
            User journalist = a.getUser();
            notificationClient.notifyUser(
                    journalist.getId(),
                    journalist.getEmail(),
                    "Accréditation refusée",
                    "Votre demande pour « " + a.getMatchLabel() + " » a été refusée. Motif : " + motif.trim(),
                    "/journaliste/demandes"
            );
        }
        return PressAccreditationResponse.from(a, true);
    }

    // ───────────────────────── Helpers ─────────────────────────

    /** Charge l'utilisateur courant et vérifie qu'il est JOURNALISTE VALIDE. */
    private User loadJournalistOrThrow(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
        if (user.getRole() != Role.JOURNALISTE) {
            throw new IllegalArgumentException("Action réservée aux journalistes");
        }
        if (user.getStatutCompte() != StatutCompte.VALIDE) {
            throw new IllegalArgumentException("Compte journaliste non validé par l'administration");
        }
        return user;
    }

    /**
     * Extrait la date « dd/MM/yyyy » à la fin du label généré par
     * ContentClient.fetchMatchLabel. Best-effort : renvoie null si
     * format inattendu (la date n'est pas critique, elle sert juste
     * au tri dans la liste admin).
     */
    private LocalDate parseDateFromLabel(String label) {
        if (label == null) return null;
        int idx = label.lastIndexOf("le ");
        if (idx < 0) return null;
        String datePart = label.substring(idx + 3).trim();
        try {
            return LocalDate.parse(datePart,
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            return null;
        }
    }
}
