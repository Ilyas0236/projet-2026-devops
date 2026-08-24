package com.wydad.digital.notification.controller;

import com.wydad.digital.notification.model.NewsletterSubscriber;
import com.wydad.digital.notification.service.NewsletterService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Newsletter publique — inscription anonyme depuis le footer.
 * Aucune identité requise : la seule protection est la validation serveur
 * du format email et l'unicité (preuves dans NewsletterSecurityTest).
 */
@RestController
@RequestMapping("/api/notification/newsletter")
@RequiredArgsConstructor
public class NewsletterController {

    private final NewsletterService newsletterService;

    public record SubscribeRequest(@NotBlank String email) {}

    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribe(@Valid @RequestBody SubscribeRequest request) {
        NewsletterSubscriber subscriber = newsletterService.subscribe(request.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Inscription confirmée. Merci de soutenir les Rouges !",
                "email", subscriber.getEmail()));
    }

    /**
     * Désinscription par token (lien email, sans authentification).
     * Le token est imprévisible — jamais l'id séquentiel exposé.
     */
    @GetMapping("/unsubscribe/{token}")
    public ResponseEntity<Map<String, Object>> unsubscribe(@PathVariable String token) {
        newsletterService.unsubscribe(token);
        return ResponseEntity.ok(Map.of("message", "Désinscription confirmée."));
    }
}
