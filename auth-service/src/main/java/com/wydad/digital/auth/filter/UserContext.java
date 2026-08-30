package com.wydad.digital.auth.filter;

/**
 * Contexte utilisateur (ThreadLocal) — exposé aux services internes
 * (ex: {@link com.wydad.digital.auth.client.PaymentClient}) qui doivent
 * retransmettre les en-têtes X-User-Email / X-User-Role / X-User-Id à
 * un service frère (payment-service, etc.).
 *
 * <p>Aligné sur le {@code UserContext} de payment-service pour faciliter
 * la lecture depuis n'importe quel composant Spring.
 *
 * <p>Le {@link UserContextFilter} alimente ces champs à chaque requête
 * HTTP entrante (avec en-têtes injectés par la gateway) et fait un
 * {@code clear()} en fin de chaîne pour éviter les fuites entre threads
 * du pool Tomcat.
 */
public final class UserContext {

    private static final ThreadLocal<String> EMAIL = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {}

    public static void setCurrentUserEmail(String email) { EMAIL.set(email); }
    public static String getCurrentUserEmail() { return EMAIL.get(); }

    public static void setCurrentUserRole(String role) { ROLE.set(role); }
    public static String getCurrentUserRole() { return ROLE.get(); }

    public static void setCurrentUserId(Long userId) { USER_ID.set(userId); }
    public static Long getCurrentUserId() { return USER_ID.get(); }

    public static boolean isAdmin() { return "ADMIN".equals(ROLE.get()); }

    /** À appeler en sortie du filter pour ne pas laisser fuiter entre requêtes. */
    public static void clear() {
        EMAIL.remove();
        ROLE.remove();
        USER_ID.remove();
    }
}
