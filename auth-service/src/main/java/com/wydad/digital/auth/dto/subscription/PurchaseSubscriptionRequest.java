package com.wydad.digital.auth.dto.subscription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Demande d'achat d'un abonnement saisonnier.
 * L'email est TOUJOURS dérivé du JWT (sécurité IDOR) — on ne le demande pas ici.
 *
 * Le paiement est SIMULÉ (mock) tant qu'on n'a pas de passerelle réelle :
 * on accepte n'importe quel numéro de carte à 16 chiffres + OTP à 6 chiffres.
 * Le contrôleur appellera payment-service /api/payment/card pour valider.
 *
 * Le code du plan est désormais un STRING (référence à {@code subscription_plans.code})
 * — il était auparavant un enum Java {@code SubscriptionZoneCode} figé.
 *
 * <p>PARENT — achat pour un fils académie : si {@code beneficiaryAcademyMemberId}
 * est fourni, l'abonnement est créé pour l'User shadow de l'enfant
 * (cf. {@code ChildUserService}), pas pour le parent connecté. Le débit
 * E-Cash reste sur le parent (le wallet du fils n'est pas exposé).</p>
 */
public record PurchaseSubscriptionRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z0-9_-]{1,32}$",
                message = "planCode doit être en MAJUSCULES/chiffres/_/- uniquement")
        String planCode,

        @NotNull @Pattern(regexp = "\\d{16}") String cardNumber,
        @NotNull @Pattern(regexp = "(0[1-9]|1[0-2])/\\d{2}") String expiryDate,
        @NotNull @Pattern(regexp = "\\d{3}") String cvv,
        @NotNull @Pattern(regexp = "\\d{6}") String otp,

        /**
         * Optionnel — id de l'{@code AcademyMember} (sports-service) au nom
         * duquel l'abonnement doit être créé. Réservé au rôle PARENT : un
         * ADHERENT qui passerait cette valeur se la verrait refusée (le
         * service vérifie que l'enfant lui appartient).
         */
        Long beneficiaryAcademyMemberId
) {}
