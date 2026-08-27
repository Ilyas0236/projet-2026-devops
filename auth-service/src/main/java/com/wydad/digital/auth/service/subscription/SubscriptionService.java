package com.wydad.digital.auth.service.subscription;

import com.wydad.digital.auth.client.PaymentClient;
import com.wydad.digital.auth.dto.subscription.PurchaseSubscriptionRequest;
import com.wydad.digital.auth.dto.subscription.SubscriptionResponse;
import com.wydad.digital.auth.dto.subscription.SubscriptionZoneResponse;
import com.wydad.digital.auth.exception.UserNotFoundException;
import com.wydad.digital.auth.model.User;
import com.wydad.digital.auth.model.subscription.SubscriptionZoneCode;
import com.wydad.digital.auth.model.subscription.UserSubscription;
import com.wydad.digital.auth.model.subscription.UserSubscriptionStatus;
import com.wydad.digital.auth.repository.UserRepository;
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
import java.time.Year;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Logique métier des abonnements saisonniers.
 *
 * Remplace l'ancien AuthService.upgradeLevel() qui ne demandait aucun
 * paiement (faille corrigée). Tout achat d'abonnement DOIT passer par
 * payment-service.
 *
 * Cycle :
 *   1. Vérification que l'utilisateur n'a pas déjà un abonnement ACTIF
 *   2. Calcul du prix selon le statut adhérent
 *   3. Appel payment-service /card (mock en démo, vrai plus tard)
 *   4. Sur succès : création UserSubscription(ACTIVE) + QR + PDF
 *   5. Sur échec : aucune trace en base, exception propagée
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {

    private final UserRepository userRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final PaymentClient paymentClient;
    private final QrCodeService qrCodeService;
    private final PdfService pdfService;

    /**
     * Catalogue public des zones.
     * On filtre SOLD_OUT si l'utilisateur n'est pas ADMIN (un admin doit
     * pouvoir les voir pour les réactiver).
     */
    public List<SubscriptionZoneResponse> listZones(boolean includeSoldOut) {
        return Arrays.stream(SubscriptionZoneCode.values())
                .filter(z -> includeSoldOut || !z.isSoldOut())
                .map(SubscriptionZoneResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Achat d'un abonnement saisonnier.
     *
     * @param email email du JWT (sécurité IDOR)
     * @param request zone + carte simulée
     * @return l'abonnement créé (statut ACTIVE)
     * @throws AlreadySubscribedException si l'utilisateur a déjà un abonnement ACTIF
     * @throws PaymentClient.PaymentException si le paiement échoue
     */
    @Transactional
    public SubscriptionResponse purchase(String email, PurchaseSubscriptionRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        SubscriptionZoneCode zone = request.zoneCode();
        if (zone.isSoldOut()) {
            throw new IllegalArgumentException("Zone " + zone.getCode() + " n'est plus commercialisée");
        }

        // Garde-fou : pas de doublon d'abonnement actif
        subscriptionRepository.findActiveByUser(user).ifPresent(s -> {
            throw new AlreadySubscribedException(
                    "L'utilisateur a déjà un abonnement actif : " + s.getZoneCode().getCode());
        });

        // Calcul du prix selon que l'utilisateur est déjà adhérent ou pas.
        // Pour cette V1, on utilise le prix "adhérent" si l'utilisateur a
        // un MembershipLevel != LEGENDE et != JUNIOR. Cela permet de
        // récompenser les anciens membres Rouge/Or/Diamant.
        BigDecimal price = resolvePrice(zone, user);

        // 1) Paiement carte SIMULÉ
        String txRef = paymentClient.chargeCard(email, request, price);

        // 2) Création de l'abonnement
        LocalDateTime now = LocalDateTime.now();
        String season = currentSeason();
        LocalDateTime validFrom = now;
        LocalDateTime validTo = seasonEnd(season);

        UserSubscription sub = UserSubscription.builder()
                .user(user)
                .zoneCode(zone)
                .season(season)
                .paidAmount(price)
                .transactionRef(txRef)
                .paidAt(now)
                .validFrom(validFrom)
                .validTo(validTo)
                .status(UserSubscriptionStatus.ACTIVE)
                .build();

        // 3) Génération QR + PDF
        String qrPayload = buildQrPayload(sub, user);
        try {
            sub.setQrCodeBase64(qrCodeService.generateQrCode(qrPayload, 300, 300));
        } catch (Exception qrEx) {
            // QR code best-effort : on log et on continue, le PDF reste
            // l'élément critique pour l'accès au stade.
            log.warn("Échec génération QR pour abonnement {} : {}", sub.getId(), qrEx.getMessage());
        }
        sub.setPdfPath(pdfService.generateSubscriptionPdf(sub, user, qrPayload));

        UserSubscription saved = subscriptionRepository.save(sub);
        log.info("Abonnement {} créé pour {} (zone {}, saison {}, {} DH)",
                saved.getId(), email, zone.getCode(), season, price);

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

    private BigDecimal resolvePrice(SubscriptionZoneCode zone, User user) {
        // L'ancien MembershipLevel sert de "tampon" : Rouge/Or/Diamant
        // (payants) bénéficient du tarif adhérent sur la nouvelle grille.
        boolean isLegacyAdherent = user.getMembershipLevel() != null
                && user.getMembershipLevel().getPrice() > 0;
        int price = isLegacyAdherent ? zone.getPriceAdherent() : zone.getPriceRegular();
        return BigDecimal.valueOf(price);
    }

    private String buildQrPayload(UserSubscription sub, User user) {
        // Payload minimaliste que le contrôle d'accès au stade pourra scanner.
        return "WAC-SUB|" + sub.getId() + "|" + user.getEmail() + "|"
                + sub.getZoneCode().getCode() + "|" + sub.getSeason() + "|"
                + sub.getValidTo().toLocalDate();
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

    /** Levée quand l'utilisateur tente d'acheter un 2e abonnement actif. */
    public static class AlreadySubscribedException extends RuntimeException {
        public AlreadySubscribedException(String message) {
            super(message);
        }
    }
}
