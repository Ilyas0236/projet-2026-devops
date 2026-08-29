package com.wydad.digital.communication.util;

import com.wydad.digital.communication.filter.UserContext;

import java.util.Locale;
import java.util.Map;

/**
 * Quality-final — résout l'URL front ({@code targetUrl}) d'une notification
 * en fonction du rôle de l'utilisateur. Voir
 * {@code sports-service/.../util/TargetUrlResolver} pour le détail de la
 * logique. Cette classe est un duplicata intentionnel pour éviter un
 * couplage inter-service (communication-service n'a pas accès au code
 * source de sports-service et inversement).
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

    public static String resolveFromCurrentContext(String fallback) {
        return resolve(UserContext.getCurrentUserRole(), fallback);
    }

    public static String resolve(String role, String fallback) {
        if (role == null) return fallback;
        String url = ROLE_TO_URL.get(role.toUpperCase(Locale.ROOT));
        return url != null ? url : fallback;
    }
}
