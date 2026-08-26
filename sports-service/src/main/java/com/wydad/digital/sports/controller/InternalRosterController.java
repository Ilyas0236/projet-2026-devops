package com.wydad.digital.sports.controller;

import com.wydad.digital.sports.config.InternalSecretValidator;
import com.wydad.digital.sports.model.Player;
import com.wydad.digital.sports.model.Staff;
import com.wydad.digital.sports.repository.PlayerRepository;
import com.wydad.digital.sports.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API interne « roster » de sports-service : SEUL point d'accès pour les
 * autres services qui ont besoin de savoir « qui appartient à quelle
 * équipe ». Protégée par X-Internal-Secret (comparaison à temps constant)
 * ; la gateway bloque /api/sports/internal/** en amont.
 *
 * <p>Consommateur principal : communication-service (messagerie + chat de
 * groupe), qui ne doit pas lire les tables players/staff directement.</p>
 */
@RestController
@RequestMapping("/api/sports/internal/roster")
@RequiredArgsConstructor
public class InternalRosterController {

    private final PlayerRepository playerRepository;
    private final StaffRepository staffRepository;
    private final InternalSecretValidator secretValidator;

    /**
     * Fiche d'adhésion d'un utilisateur : priorité JOUEUR puis STAFF.
     * 200 avec corps, ou 404 si aucune fiche joueur/staff ne lui correspond.
     */
    @GetMapping("/membership/{userId}")
    public ResponseEntity<?> membership(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @PathVariable Long userId) {
        if (!secretValidator.isInternalCallAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return playerRepository.findByUserId(userId)
                .<ResponseEntity<?>>map(p -> ResponseEntity.ok(Map.of(
                        "userId", p.getUserId(),
                        "sportType", p.getSportType().name(),
                        "category", p.getCategory().name(),
                        "rosterRole", "JOUEUR",
                        "fullName", p.getFullName())))
                .orElseGet(() -> staffRepository.findByUserId(userId)
                        .<ResponseEntity<?>>map(s -> ResponseEntity.ok(Map.of(
                                "userId", s.getUserId(),
                                "sportType", s.getSportType().name(),
                                "category", s.getAssignedCategory().name(),
                                "rosterRole", "STAFF",
                                "fullName", s.getFullName())))
                        .orElseGet(() -> ResponseEntity.notFound().build()));
    }

    /**
     * Membres d'un groupe {sportType, category} : joueurs + staff encadrant.
     * Liste vide (jamais d'erreur) pour un groupe sans membre — le chat d'une
     * équipe doit rester fonctionnel même en effectif réduit.
     */
    @GetMapping("/members")
    public ResponseEntity<?> members(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestParam String sportType,
            @RequestParam String category) {
        if (!secretValidator.isInternalCallAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        var sport = com.wydad.digital.sports.enums.SportType.valueOf(sportType.toUpperCase());
        var cat = com.wydad.digital.sports.enums.Category.valueOf(category.toUpperCase());

        List<Map<String, Object>> members = new java.util.ArrayList<>();
        for (Player p : playerRepository.findBySportTypeAndCategory(sport, cat)) {
            members.add(Map.of(
                    "userId", p.getUserId(),
                    "fullName", p.getFullName(),
                    "rosterRole", "JOUEUR"));
        }
        for (Staff s : staffRepository.findBySportTypeAndAssignedCategory(sport, cat)) {
            members.add(Map.of(
                    "userId", s.getUserId(),
                    "fullName", s.getFullName(),
                    "rosterRole", "STAFF"));
        }
        return ResponseEntity.ok(members);
    }

    /**
     * Création interne d'une fiche joueur — appelée par auth-service à la
     * validation d'un compte JOUEUR par l'ADMIN. Idempotent (upsert par
     * userId) ; les champs sportifs détaillés restent modifiables ensuite
     * depuis le back-office ADMIN.
     */
    @PostMapping("/players")
    public ResponseEntity<?> createPlayer(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestBody Map<String, Object> body) {
        if (!secretValidator.isInternalCallAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            Long userId = Long.valueOf(String.valueOf(body.get("userId")));
            Player player = playerRepository.findByUserId(userId).orElseGet(Player::new);
            player.setUserId(userId);
            player.setFullName(String.valueOf(body.getOrDefault("fullName", "")));
            player.setSportType(com.wydad.digital.sports.enums.SportType
                    .valueOf(String.valueOf(body.get("sportType")).toUpperCase()));
            player.setCategory(com.wydad.digital.sports.enums.Category
                    .valueOf(String.valueOf(body.get("category")).toUpperCase()));
            playerRepository.save(player);
            return ResponseEntity.ok(Map.of("userId", userId, "created", true));
        } catch (IllegalArgumentException e) {
            // Enum inconnu (discipline/catégorie hors référentiel)
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Création interne d'une fiche staff (ENTRAINEUR / STAFF validés).
     * Rôle par défaut MANAGER, ajustable ensuite depuis le back-office.
     */
    @PostMapping("/staff")
    public ResponseEntity<?> createStaff(
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            @RequestBody Map<String, Object> body) {
        if (!secretValidator.isInternalCallAuthorized(secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            Long userId = Long.valueOf(String.valueOf(body.get("userId")));
            Staff staff = staffRepository.findByUserId(userId).orElseGet(Staff::new);
            staff.setUserId(userId);
            staff.setFullName(String.valueOf(body.getOrDefault("fullName", "")));
            staff.setRole(com.wydad.digital.sports.enums.StaffRole
                    .valueOf(String.valueOf(body.getOrDefault("role", "MANAGER")).toUpperCase()));
            staff.setSportType(com.wydad.digital.sports.enums.SportType
                    .valueOf(String.valueOf(body.get("sportType")).toUpperCase()));
            staff.setAssignedCategory(com.wydad.digital.sports.enums.Category
                    .valueOf(String.valueOf(body.get("assignedCategory")).toUpperCase()));
            staffRepository.save(staff);
            return ResponseEntity.ok(Map.of("userId", userId, "created", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
