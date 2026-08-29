package com.wydad.digital.sports.util;

import com.wydad.digital.sports.filter.SportsUserContext;

import java.util.Locale;
import java.util.Map;

/**
 * Quality-final — résout l'URL front ({@code targetUrl}) d'une notification
 * en fonction du rôle de l'utilisateur. Avant ce helper, les services
 * hardcodaient {@code "/joueur/dashboard"} ce qui produisait des 404 côté
 * front quand la cloche ouvrait un message destiné à un STAFF,
 * ENTRAINEUR, JOURNALISTE, PARENT, PRÉSIDENT ou ADMIN.
 *
 * <p>Source du rôle : {@link SportsUserContext} (thread-local alimenté par
 * la gateway via les en-têtes X-User-*). Pour les broadcasts où le rôle
 * du destinataire n'est pas dans le contexte courant, on le prend en
 * paramètre explicite.</p>
 *
 * <p>Pas de couplage avec notification-service : la logique est dupliquée
 * côté sports-service et communication-service (chacun avec son propre
 * UserContext) pour éviter une dépendance inter-service.</p>
 */
public final class TargetUrlResolver {

    private TargetUrlResolver() {
    }

    private static final Map<String, String> ROLE_TO_URL = Map.ofEntries(
            Map.entry("JOUEUR",      "/joueur/dashboard"),
            Map.entry("STAFF",       "/staff/dashboard"),
            Map.entry("ENTRAINEUR",  "/entraineur/dashboard"),
            Map.entry("JOURNALISTE", "/journaliste/accueil"),
            Map.entry("PARENT",      "/parent/dashboard"),
            Map.entry("PRESIDENT",   "/president/dashboard"),
            Map.entry("ADMIN",       "/admin")
    );

    /**
     * Résout l'URL front depuis le rôle du contexte thread-local.
     *
     * @param fallback URL par défaut si le rôle est inconnu / null
     *                 (ex. broadcast où le destinataire n'est pas l'appelant).
     * @return URL adaptée, jamais null si fallback est non null.
     */
    public static String resolveFromCurrentContext(String fallback) {
        return resolve(SportsUserContext.getCurrentUserRole(), fallback);
    }

    /**
     * Résout l'URL front depuis un rôle explicite.
     *
     * @param role     rôle de l'utilisateur cible (peut être null).
     * @param fallback URL par défaut.
     */
    public static String resolve(String role, String fallback) {
        if (role == null) return fallback;
        String url = ROLE_TO_URL.get(role.toUpperCase(Locale.ROOT));
        return url != null ? url : fallback;
    }
}
