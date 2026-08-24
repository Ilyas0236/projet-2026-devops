package com.wydad.digital.notification.service;

import com.wydad.digital.notification.model.NewsletterSubscriber;
import com.wydad.digital.notification.repository.NewsletterSubscriberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Newsletter publique — inscription anonyme (footer du site public).
 *
 * Règles serveur :
 *  - validation de format email stricte (rejet des entrées sans @, sans
 *    domaine, trop courtes ou trop longues) ;
 *  - unicité insensible à la casse : ré-inscrire un email existant
 *    réactive simplement l'abonnement s'il avait été résilié ;
 *  - désinscription par token imprévisible (jamais l'id séquentiel).
 */
@Service
@RequiredArgsConstructor
public class NewsletterService {

    /**
     * Format pragmatique : local@domaine.tld — local non vide sans point
     * initial/final, domaine avec au moins un point et un TLD de 2+ lettres.
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^(?!\\.)(?!.*\\.\\.)[A-Za-z0-9._%+-]{1,64}(?<!\\.)@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*\\.[A-Za-z]{2,}$");

    /** RFC 5321 : 254 caractères maximum pour une adresse complète. */
    private static final int MAX_EMAIL_LENGTH = 254;

    private final NewsletterSubscriberRepository repository;

    @Transactional
    public NewsletterSubscriber subscribe(String rawEmail) {
        String email = normalize(rawEmail);

        if (email.length() > MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException("Email trop long (254 caractères maximum).");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Adresse email invalide.");
        }

        return repository.findByEmailIgnoreCase(email).map(existing -> {
            // Ré-inscription : réactive un abonnement résilié (idempotent).
            if (!existing.isActive()) {
                existing.setActive(true);
                existing.setSubscribedAt(LocalDateTime.now());
                existing.setUnsubscribedAt(null);
            }
            return repository.save(existing);
        }).orElseGet(() -> repository.save(NewsletterSubscriber.builder()
                .email(email)
                .build()));
    }

    @Transactional
    public void unsubscribe(String token) {
        NewsletterSubscriber subscriber = repository.findByUnsubscribeToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Lien de désinscription inconnu."));
        if (subscriber.isActive()) {
            subscriber.setActive(false);
            subscriber.setUnsubscribedAt(LocalDateTime.now());
            repository.save(subscriber);
        }
        // Déjà résilié : no-op volontaire (lien réutilisable sans erreur).
    }

    private String normalize(String rawEmail) {
        if (rawEmail == null || rawEmail.isBlank()) {
            throw new IllegalArgumentException("L'adresse email est obligatoire.");
        }
        return rawEmail.trim().toLowerCase();
    }
}
