package com.wydad.digital.auth.controller.subscription;

import com.wydad.digital.auth.dto.subscription.SubscriptionPlanResponse;
import com.wydad.digital.auth.service.subscription.SubscriptionPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catalogue public des plans d'abonnement — piloté par l'entité
 * {@code SubscriptionPlan} (et non plus par l'enum legacy).
 *
 * La sécurité repose sur le filtre principal {@code /api/auth/**} en
 * permitAll (SecurityConfig) ; aucun rôle n'est requis pour voir la home
 * et la liste des abonnements.
 */
@RestController
@RequestMapping("/api/auth/subscriptions/plans")
@RequiredArgsConstructor
public class SubscriptionPlanController {

    private final SubscriptionPlanService planService;

    @GetMapping
    public ResponseEntity<List<SubscriptionPlanResponse>> listActive() {
        return ResponseEntity.ok(planService.listActive());
    }
}
