package com.wydad.digital.auth.service.subscription;

import com.wydad.digital.auth.client.PaymentClient;
import com.wydad.digital.auth.dto.subscription.PurchaseSubscriptionRequest;
import com.wydad.digital.auth.dto.subscription.SubscriptionResponse;
import com.wydad.digital.auth.dto.subscription.SubscriptionZoneResponse;
import com.wydad.digital.auth.exception.UserNotFoundException;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.model.subscription.SubscriptionPlan;
import com.wydad.digital.auth.model.subscription.SubscriptionZoneCode;
import com.wydad.digital.auth.model.subscription.UserSubscription;
import com.wydad.digital.auth.model.subscription.UserSubscriptionStatus;
import com.wydad.digital.auth.repository.UserRepository;
import com.wydad.digital.auth.repository.subscription.SubscriptionPlanRepository;
import com.wydad.digital.auth.repository.subscription.UserSubscriptionRepository;
import com.wydad.digital.auth.service.PdfService;
import com.wydad.digital.auth.service.QrCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Logique métier des abonnements saisonniers.
 *
 * Cycle :
 *   1. Résolution du plan (subscription_plans) par code, contrôle isActive
 *   2. Vérification de l'invariant « un seul abonnement par saison » :
 *      si l'utilisateur a déjà un ACTIVE ou REPLACED pour la saison
 *      courante, on refuse l'achat (409 ALREADY_SUBSCRIBED) — pas
 *      d'upgrade, pas de ré-achat.
 *   3. Calcul du prix (tarif adhérent si l'utilisateur a déjà payé
 *      au moins 1 abonnement par le passé — peu importe la saison)
 *   4. Appel payment-service /card (mock en démo, vrai plus tard)
 *   5. Sur succès : création UserSubscription(ACTIVE) + QR + PDF
 *      (FK plan_id + zone_code legacy dérivés du plan)
 *   6. Sur échec : aucune trace en base, exception propagée
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final UserRepository userRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final SubscriptionPlanRepository planRepository;
    private final PaymentClient paymentClient;
    private final QrCodeService qrCodeService;
    private final PdfService pdfService;

    /**
     * Catalogue public des zones.
     * @deprecated conservé pour la rétro-compat front le temps de la
     *             bascule : tout passe désormais par {@code listPlans()}.
     *             Le contrôleur public expose les deux endpoints.
     */
    @Deprecated
    public List<SubscriptionZoneResponse> listZones(boolean includeSoldOut) {
        return Arrays.stream(SubscriptionZoneCode.values())
                .filter(z -> includeSoldOut || !z.isSoldOut())
                .map(SubscriptionZoneResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Achat d'un abonnement saisonnier.
     *
     * <p>Un utilisateur ne peut acheter qu'<strong>un seul</strong> abonnement
     * par saison (règle métier « un seul par saison », pas d'upgrade, pas de
     * ré-achat). Si l'utilisateur a déjà un abonnement {@code ACTIVE} ou
     * {@code REPLACED} pour la saison courante, l'achat est refusé en
     * {@link AlreadySubscribedException} (HTTP 409). Un {@code EXPIRED}
     * (saison précédente) ou aucun historique autorise l'achat.</p>
     *
     * <p>Le check est effectué <strong>avant</strong> l'appel paiement
     * pour ne jamais débiter le wallet d'un user qui n'a pas le droit
     * d'acheter (sinon on devrait faire un refund).</p>
     *
     * @param email email du JWT (sécurité IDOR)
     * @param request planCode + carte simulée
     * @return l'abonnement créé (statut ACTIVE)
     * @throws PlanNotFoundException si le code ne correspond à aucun plan
     * @throws PlanNotActiveException si le plan existe mais est désactivé
     * @throws AlreadySubscribedException si l'user a déjà un abonnement pour la saison
     * @throws PaymentClient.PaymentException si le paiement échoue
     */
    @Transactional
    public SubscriptionResponse purchase(String email, PurchaseSubscriptionRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        // Résolution du plan par code (la source de vérité est désormais la table).
        SubscriptionPlan plan = planRepository.findByCode(request.planCode())
                .orElseThrow(() -> new PlanNotFoundException(request.planCode()));
        if (Boolean.FALSE.equals(plan.getIsActive())) {
            throw new PlanNotActiveException(request.planCode());
        }

        // Calcul de la saison courante (logique métier : août → juillet)
        // et check de l'invariant « un seul abonnement par saison » :
        // si l'utilisateur a déjà un ACTIVE ou REPLACED, on refuse AVANT
        // de débiter. EXPIRED et CANCELLED n'empêchent pas l'achat.
        String season = currentSeason();
        List<UserSubscription> alreadyThere = subscriptionRepository
                .findByUserAndSeasonAndStatusIn(user, season,
                        List.of(UserSubscriptionStatus.ACTIVE, UserSubscriptionStatus.REPLACED));
        if (!alreadyThere.isEmpty()) {
            log.info("Achat refusé pour {} (saison {}) : déjà {} abonnement(s) ACTIVE/REPLACED",
                    email, season, alreadyThere.size());
            throw new AlreadySubscribedException(season);
        }

        // Calcul du prix : tarif adhérent si l'utilisateur a déjà payé au
        // moins 1 abonnement par le passé (toutes saisons, ACTIVE ou EXPIRED),
        // sinon tarif régulier. On ne lit plus MembershipLevel (champ legacy
        // jamais valorisé pour les nouveaux comptes depuis la refonte B.12).
        BigDecimal price = resolvePrice(plan, user);

        // 1) Débit E-Cash (paiement simulé via payment-service). Le user doit
        // avoir rechargé son wallet au préalable — si le solde est insuffisant,
        // payment-service renvoie 402 et on propage l'exception.
        // Référence interne = "B12-{planCode}-{userId}-{tsEpochMs}" pour
        // permettre le rapprochement côté payment_db.transactions.
        String reference = "B12-" + plan.getCode() + "-" + user.getId() + "-" + System.currentTimeMillis();
        String txRef = paymentClient.debitEcash(email, price, reference);

        // 2) Création de l'abonnement
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime validFrom = now;
        LocalDateTime validTo = seasonEnd(season);

        UserSubscription sub = UserSubscription.builder()
                .user(user)
                .plan(plan)
                // Cohabitation : on dérive zoneCode du plan si possible, pour
                // garder la colonne legacy alimentée (PDF, audit, SELECT ad hoc).
                .zoneCode(toLegacyZone(plan.getCode()))
                .season(season)
                .paidAmount(price)
                .transactionRef(txRef)
                .paidAt(now)
                .validFrom(validFrom)
                .validTo(validTo)
                .status(UserSubscriptionStatus.ACTIVE)
                .build();

        // 3) Génération QR + PDF
        String qrPayload = buildQrPayload(sub, user, plan);
        try {
            sub.setQrCodeBase64(qrCodeService.generateQrCode(qrPayload, 300, 300));
        } catch (Exception qrEx) {
            // QR code best-effort : on log et on continue, le PDF reste
            // l'élément critique pour l'accès au stade.
            log.warn("Échec génération QR pour abonnement {} : {}", sub.getId(), qrEx.getMessage());
        }
        sub.setPdfPath(pdfService.generateSubscriptionPdf(sub, user, qrPayload));

        UserSubscription saved = subscriptionRepository.save(sub);
        log.info("Abonnement {} créé pour {} (plan {}, saison {}, {} DH)",
                saved.getId(), email, plan.getCode(), season, price);

        return SubscriptionResponse.from(saved);
    }

    /** Historique complet d'un utilisateur. */
    public List<SubscriptionResponse> myHistory(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
        return subscriptionRepository.findByUserOrderByPaidAtDesc(user).stream()
                .map(SubscriptionResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * B.12 — Inventaire admin des abonnements (filtres date + email).
     */
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<SubscriptionResponse> adminFilter(
            LocalDateTime startDate,
            LocalDateTime endDate,
            String userEmail,
            org.springframework.data.domain.Pageable pageable) {
        return subscriptionRepository.adminFilter(
                        startDate, endDate, userEmail, pageable)
                .map(SubscriptionResponse::from);
    }

    /** Abonnement actif courant. */
    public SubscriptionResponse myActive(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));
        return subscriptionRepository.findActiveByUser(user)
                .map(SubscriptionResponse::from)
                .orElse(null);
    }

    /**
     * Indique si l'utilisateur a un abonnement ACTIF non expiré.
     * Endpoint utile pour ticket-service / shop-service : appliquent
     * les avantages (réduction 15%, priorité 48h sur les matchs
     * exceptionnels) si l'utilisateur est considéré comme adhérent.
     */
    public boolean isActiveAdherent(String email) {
        return userRepository.findByEmail(email)
                .flatMap(subscriptionRepository::findActiveByUser)
                .map(s -> s.getValidTo().isAfter(LocalDateTime.now()))
                .orElse(false);
    }

    // -------- helpers --------

    /**
     * Calcule le prix d'un plan pour un utilisateur donné.
     *
     * <p>Logique : si l'utilisateur a déjà au moins un abonnement payé
     * (toutes saisons, ACTIVE/EXPIRED/REPLACED), il est considéré comme
     * « adhérent fidèle » et bénéficie du tarif {@code adherentPrice} ;
     * sinon il paye le tarif {@code regularPrice}.</p>
     *
     * <p>Remplace l'ancien test sur {@code MembershipLevel.getPrice() > 0}
     * qui n'a plus de sens : la carte est 100% pilotée par l'abonnement
     * (cf. refonte B.12), et {@code MembershipLevel} n'est plus valorisé
     * pour les nouveaux comptes.</p>
     */
    private BigDecimal resolvePrice(SubscriptionPlan plan, User user) {
        boolean isFaithful = subscriptionRepository.countByUser(user) > 0;
        return isFaithful ? plan.getAdherentPrice() : plan.getRegularPrice();
    }

    /**
     * Mappe un code plan (ex. "PEL-6") vers l'enum legacy pour rétro-compat
     * PDF/audit. Renvoie null si l'enum n'a pas d'équivalent (cas d'un plan
     * 100% admin, sans legacy) — la colonne reste alors NULL, ce qui est
     * toléré désormais (FK plan_id est la source de vérité).
     */
    private SubscriptionZoneCode toLegacyZone(String planCode) {
        if (planCode == null) return null;
        for (SubscriptionZoneCode z : SubscriptionZoneCode.values()) {
            if (z.getCode().equals(planCode)) return z;
        }
        return null;
    }

    private String buildQrPayload(UserSubscription sub, User user, SubscriptionPlan plan) {
        String code = plan != null ? plan.getCode() : sub.getZoneCode().getCode();
        // Payload minimaliste que le contrôle d'accès au stade pourra scanner.
        return "WAC-SUB|" + sub.getId() + "|" + user.getEmail() + "|"
                + code + "|" + sub.getSeason() + "|" + sub.getValidTo().toLocalDate();
    }

    private String currentSeason() {
        int year = LocalDate.now().getYear();
        int month = LocalDate.now().getMonthValue();
        // Saison sportive marocaine : commence en août
        int startYear = (month >= 8) ? year : year - 1;
        return startYear + "-" + (startYear + 1);
    }

    private LocalDateTime seasonEnd(String season) {
        int startYear = Integer.parseInt(season.substring(0, 4));
        // Fin de saison : 31 juillet de l'année de fin
        return LocalDateTime.of(startYear + 1, 7, 31, 23, 59, 59);
    }

    /** Plan introuvable. → 404. */
    public static class PlanNotFoundException extends RuntimeException {
        public PlanNotFoundException(String code) {
            super("Plan d'abonnement introuvable : code=" + code);
        }
    }

    /** Plan désactivé (isActive=false). → 409. */
    public static class PlanNotActiveException extends RuntimeException {
        public PlanNotActiveException(String code) {
            super("Plan d'abonnement désactivé : code=" + code);
        }
    }

    /**
     * L'utilisateur a déjà un abonnement (ACTIVE ou REPLACED) pour la
     * saison courante : la règle « un seul abonnement par saison » refuse
     * l'achat, l'upgrade ou le ré-achat. → 409 ALREADY_SUBSCRIBED.
     *
     * <p>Le message est en français et directement affichable côté front
     * (le composant abonnement l'affiche tel quel dans le bandeau d'erreur
     * du dialog de paiement).</p>
     */
    public static class AlreadySubscribedException extends RuntimeException {
        public AlreadySubscribedException(String season) {
            super("Vous avez déjà un abonnement pour la saison " + season
                    + " — un seul abonnement par saison est autorisé.");
        }
    }
}
