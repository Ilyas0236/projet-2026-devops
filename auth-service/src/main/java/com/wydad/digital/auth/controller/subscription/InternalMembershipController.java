package com.wydad.digital.auth.controller.subscription;

import com.wydad.digital.auth.dto.subscription.ActiveMemberDTO;
import com.wydad.digital.auth.service.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * B.18 — Endpoints internes (service-à-service) liés aux abonnements.
 *
 * <p>Consommés par election-service (vérification du droit de vote et
 * dropdown candidats président) et plus généralement par tout service
 * qui veut conditionner une fonctionnalité à la qualité d'adhérent
 * effectif.</p>
 *
 * <p>Protégés par le filtre gateway « X-Internal-Secret ». En accès
 * direct, la SecurityConfig les expose en {@code permitAll()} — la
 * sécurité repose sur la whitelist des routes internes.</p>
 */
@RestController
@RequestMapping("/api/auth/internal")
@RequiredArgsConstructor
public class InternalMembershipController {

    private final SubscriptionService subscriptionService;

    /**
     * B.18 — Réponse JSON plate {email, active} : simple à consommer
     * côté Feign/RestTemplate, stable dans le temps (on n'expose pas
     * la liste des abonnements).
     */
    @GetMapping("/is-active-subscriber")
    public Map<String, Object> isActiveSubscriber(@RequestParam("email") String email) {
        boolean active = subscriptionService.isActiveAdherent(email);
        return Map.of("email", email, "active", active);
    }

    /**
     * B.8 — Liste les titulaires d'une carte d'abonnement ACTIVE non
     * expirée. Filtre optionnel par saison (null = toutes saisons).
     *
     * <p>Utilisé par election-service pour peupler le dropdown
     * « candidats président » côté admin : un candidat DOIT être un
     * titulaire actif. Réponse = liste d'ActiveMemberDTO (id, email,
     * nom, saison, validTo, subscriptionId).</p>
     *
     * <p>Cet endpoint ne doit jamais être exposé publiquement (gateway
     * block sur /api/auth/internal/**). L'admin ne le consomme pas
     * directement : c'est election-service qui l'appelle pour son UI.</p>
     */
    @GetMapping("/active-subscribers")
    public List<ActiveMemberDTO> listActiveSubscribers(
            @RequestParam(value = "season", required = false) String season) {
        return subscriptionService.listActiveAdherents(season);
    }

    /**
     * B.8 — Compte des titulaires ACTIVE à un instant donné. Utilisé
     * par election-service pour calculer la participation X/Y d'un
     * scrutin président (snapshot figé au moment de la clôture).
     *
     * <p>Réponse JSON plate {count, at} : pas de liste détaillée, juste
     * le chiffre qui sert de dénominateur au calcul de participation.</p>
     */
    @GetMapping("/active-subscribers/count")
    public Map<String, Object> countActiveSubscribers(
            @RequestParam("at") String atIso) {
        LocalDateTime at = LocalDateTime.parse(atIso);
        long count = subscriptionService.countActiveAdherentsAt(at);
        return Map.of("count", count, "at", atIso);
    }
}
