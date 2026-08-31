package com.wydad.digital.auth.controller;

import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.service.ChildUserService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * B.18 — Endpoint interne (service-à-service) permettant à ticket-service
 * de récupérer l'User shadow d'un enfant académie, ou de le créer
 * s'il n'existe pas encore.
 *
 * <p>Protégé par le filtre gateway « X-Internal-Secret » (whitelist des
 * routes {@code /api/auth/internal/**}). En accès direct, la SecurityConfig
 * l'expose en {@code permitAll()} mais l'absence du header force un
 * downstream reject (le secret est vérifié par {@code InternalSecretValidator}
 * dans la gateway).</p>
 *
 * <p>Idempotent : le second appel pour un même academyMemberId renvoie
 * toujours le même childUserId. Voir {@link ChildUserService#ensureChildUser}
 * pour la stratégie de dérivation d'email/phone.</p>
 */
@RestController
@RequestMapping("/api/auth/internal/ensure-child-user")
@RequiredArgsConstructor
public class InternalChildUserController {

    private final ChildUserService childUserService;

    public record EnsureChildUserRequest(
            @NotNull Long parentUserId,
            @NotBlank String childFullName,
            @NotNull Long academyMemberId
    ) {}

    public record EnsureChildUserResponse(
            Long childUserId,
            String email
    ) {}

    @PostMapping
    public EnsureChildUserResponse ensure(@RequestBody EnsureChildUserRequest request) {
        User child = childUserService.ensureChildUser(
                request.parentUserId(),
                request.childFullName(),
                request.academyMemberId());
        return new EnsureChildUserResponse(child.getId(), child.getEmail());
    }
}
