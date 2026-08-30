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
 *   2. Si l'utilisateur a déjà un abonnement ACTIF → on l'expire
 *      (pas de doublon facturé) avant de continuer
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
     * <p>Si l'utilisateur a déjà un abonnement ACTIF, il est passé à
     * {@code EXPIRED} (statut REPLACED) <strong>avant</strong> l'appel
     * paiement — l'invariant « un seul abonnement ACTIF par utilisateur »
     * est respecté, et le nouveau paiement n'est jamais facturé en double.</p>
     *
     * @param email email du JWT (sécurité IDOR)
     * @param request planCode + carte simulée
     * @return l'abonnement créé (statut ACTIVE)
     * @throws PlanNotFoundException si le code ne correspond à aucun plan
     * @throws PlanNotActiveException si le plan existe mais est désactivé
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

        // Remplacement : si l'utilisateur a déjà un abonnement ACTIF, on le
        // passe en REPLACED AVANT de facturer le nouveau. Garantit l'invariant
        // « au plus 1 abonnement ACTIF par utilisateur » sans doublon facturé.
        // (L'ancienne garde-fou AlreadySubscribedException a été supprimée :
        // l'UX attendue est « remplacer ma carte » — pas un 409 bloquant.)
        replaceActiveSubscription(user);

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
        String season = currentSeason();
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
     * Expire tous les abonnements ACTIF d'un utilisateur.
     *
     * <p>Appelé par {@link #purchase(String, PurchaseSubscriptionRequest)}
     * <strong>avant</strong> l'appel paiement pour garantir l'invariant
     * « un seul abonnement ACTIF par utilisateur ». Le statut choisi est
     * {@code REPLACED} (distinct de {@code EXPIRED} qui marque la fin
     * naturelle d'une saison) — permet aux rapports financiers de séparer
     * « fin de saison » et « l'utilisateur a racheté une autre carte ».</p>
     *
     * <p>Silencieux si l'utilisateur n'a aucun abonnement actif : c'est
     * le cas normal pour un premier achat.</p>
     */
    private void replaceActiveSubscription(User user) {
        List<UserSubscription> actives = subscriptionRepository.findByUserAndStatus(
                user, UserSubscriptionStatus.ACTIVE);
        if (actives.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (UserSubscription s : actives) {
            s.setStatus(UserSubscriptionStatus.REPLACED);
            // La fin réelle de la carte remplacée = maintenant (cohérent
            // avec l'usage immédiat de la nouvelle carte à l'achat).
            s.setValidTo(now);
        }
        subscriptionRepository.saveAll(actives);
        log.info("Abonnement(s) ACTIF de {} passé(s) en REPLACED avant nouvel achat (count={})",
                user.getEmail(), actives.size());
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
}
