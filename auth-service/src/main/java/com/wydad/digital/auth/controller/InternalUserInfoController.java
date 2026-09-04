package com.wydad.digital.auth.controller;

import com.wydad.digital.auth.exception.UserNotFoundException;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * C.21 — Endpoints internes service-à-service pour récupérer des attributs
 * d'un utilisateur (rôle, discipline) sans passer par l'API publique.
 * Consommé par sports-service (TeamIsolationService.ensureCanQueryDiscipline).
 *
 * <p>La discipline est portée par le profil (champ disciplineDemandee) et
 * renvoyée au format upper-case (FOOTBALL / BASKETBALL / HANDBALL) ou
 * null si l'utilisateur n'a pas de discipline (ADHERENT, etc.).</p>
 *
 * <p>Protégé par la whitelist X-Internal-Secret de la gateway — pas
 * d'auth utilisateur sur ces endpoints (le service appelant est
 * identifié par le secret partagé).</p>
 */
@RestController
@RequestMapping("/api/auth/internal/users")
@RequiredArgsConstructor
public class InternalUserInfoController {

    private final UserRepository userRepository;

    public record UserDisciplineResponse(Long id, String email, String role, String discipline) {}

    @GetMapping("/{id}/discipline")
    public UserDisciplineResponse getDiscipline(@PathVariable("id") Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("user " + id));
        return new UserDisciplineResponse(
                user.getId(),
                user.getEmail(),
                user.getRole() == null ? null : user.getRole().name(),
                user.getDisciplineDemandee());
    }

    /**
     * V2.5 — liste les utilisateurs d'un rôle donné, éventuellement filtrés
     * par discipline. Sert à communication-service pour résoudre « président
     * de la discipline X » et « staff/entraîneur de la discipline X » (sélecteur
     * libre). Discipline omise = tous les utilisateurs de ce rôle.
     */
    @GetMapping("/by-role-and-discipline")
    public java.util.List<UserDisciplineResponse> findByRoleAndDiscipline(
            @RequestParam String role,
            @RequestParam(required = false) String discipline) {
        com.wydad.digital.auth.model.Role roleEnum;
        try {
            roleEnum = com.wydad.digital.auth.model.Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return java.util.Collections.emptyList();
        }
        String normDiscipline = (discipline == null || discipline.isBlank())
                ? null : discipline.toUpperCase();
        List<User> users = userRepository.findByRoleAndDisciplineDemandee(roleEnum, normDiscipline);
        return users.stream()
                .map(u -> new UserDisciplineResponse(
                        u.getId(),
                        u.getEmail(),
                        u.getRole() == null ? null : u.getRole().name(),
                        u.getDisciplineDemandee()))
                .toList();
    }
}
