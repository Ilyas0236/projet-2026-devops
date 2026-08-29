package com.wydad.digital.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service d'envoi d'emails — MOCK.
 *
 * <p>Statut actuel : <b>MOCK</b>. Le service journalise l'email et simule un
 * succès après un délai court. Aucun SMTP / fournisseur externe n'est appelé.
 * Justification du choix MOCK :
 * <ul>
 *   <li>VM B2s_v2 (1 Go de RAM) : pas de daemon SMTP local (Postfix/Exim)
 *       — ajouter un service ferait grimper la RAM et la complexité ;</li>
 *   <li>SendGrid / Mailgun / AWS SES exigent une clé API payante + un nom
 *       de domaine vérifié DKIM/SPF — pas fournis par le propriétaire ;</li>
 *   <li>Le projet utilise déjà des MOCK cohérents (carte bleue, OTP, etc.) :
 *       la production réelle n'est pas l'objectif immédiat.</li>
 * </ul>
 *
 * <p>Pour basculer en production réelle plus tard, sans recompiler :
 * <ol>
 *   <li>positionner {@code notification.email.mock=false} ;</li>
 *   <li>ajouter les paramètres SMTP / SendGrid dans {@code application.yml} ;</li>
 *   <li>remplacer la branche {@code mock} par l'appel réel au client
 *       (JavaMailSender, SendGrid SDK, etc.).</li>
 * </ol>
 *
 * <p>Pour tester l'envoi côté orchestration, voir
 * {@code scripts/test-email-mock.sh} : il vérifie que le log contient bien
 * « MOCK SENDGRID » et que la notification passe en status SENT.
 */
@Service
@Slf4j
public class EmailService {

    /** Active le mode MOCK (par défaut) ou laisse passer à un client réel. */
    @Value("${notification.email.mock:true}")
    private boolean mock;

    /**
     * Envoie un email. En mode MOCK : log + sleep + return true.
     * En mode réel : délègue au client configuré (à implémenter).
     *
     * @return true si l'envoi a réussi, false sinon
     */
    public boolean sendEmail(String to, String subject, String body) {
        if (mock) {
            log.info("📧 MOCK SENDGRID - Envoi d'email à : {}", to);
            log.info("Sujet: {}", subject);
            log.info("Message: {}", body);

            // Simule un délai réseau
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Simule un succès
            return true;
        }
        // Mode réel — à implémenter quand les credentials seront fournis.
        // Exemple JavaMailSender / SendGrid SDK / AWS SES.
        log.warn("⚠️ EmailService en mode réel non implémenté — fallback MOCK pour {}", to);
        return sendMock(to, subject, body);
    }

    private boolean sendMock(String to, String subject, String body) {
        log.info("📧 [fallback MOCK] email à {} | sujet: {}", to, subject);
        return true;
    }
}
