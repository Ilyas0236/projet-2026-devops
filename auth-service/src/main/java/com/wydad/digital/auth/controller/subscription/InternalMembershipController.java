package com.wydad.digital.auth.controller.subscription;

import com.wydad.digital.auth.service.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * B.18 — Endpoint interne (service-à-service) indiquant si un email
 * correspond à un utilisateur ayant un abonnement saisonnier ACTIF.
 *
 * <p>Consommé par election-service (vérification du droit de vote aux
 * élections présidentielles) et par tout service qui veut conditionner
 * une fonctionnalité à la qualité d'adhérent effectif (pas seulement
 * « a payé une fois dans sa vie »).</p>
 *
 * <p>Protégé par le filtre gateway « X-Internal-Secret ». En accès
 * direct, la SecurityConfig l'expose en {@code permitAll()} — la
 * sécurité repose sur la whitelist des routes internes.</p>
 */
@RestController
@RequestMapping("/api/auth/internal")
@RequiredArgsConstructor
public class InternalMembershipController {

    private final SubscriptionService subscriptionService;

    @GetMapping("/is-active-subscriber")
    public java.util.Map<String, Object> isActiveSubscriber(@RequestParam("email") String email) {
        boolean active = subscriptionService.isActiveAdherent(email);
        // Réponse JSON plate {email, active} : simple à consommer côté Feign/RestTemplate
        // et stable dans le temps (on n'expose pas la liste des abonnements).
        return java.util.Map.of("email", email, "active", active);
    }
}
