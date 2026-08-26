package com.wydad.digital.sports.filter;

/**
 * Contexte utilisateur propagé par la gateway via les en-têtes X-User-*.
 * L'ID, l'email et le rôle proviennent du JWT validé à la gateway —
 * ils ne doivent jamais être lus depuis le path/body d'une requête
 * pour une décision d'autorisation.
 */
public final class SportsUserContext {

    private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USER_EMAIL = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_USER_ROLE = new ThreadLocal<>();

    private SportsUserContext() {
    }

    public static void setCurrentUserId(Long id) {
        CURRENT_USER_ID.set(id);
    }

    public static Long getCurrentUserId() {
        return CURRENT_USER_ID.get();
    }

    public static void setCurrentUserEmail(String email) {
        CURRENT_USER_EMAIL.set(email);
    }

    public static String getCurrentUserEmail() {
        return CURRENT_USER_EMAIL.get();
    }

    public static void setCurrentUserRole(String role) {
        CURRENT_USER_ROLE.set(role);
    }

    public static String getCurrentUserRole() {
        return CURRENT_USER_ROLE.get();
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(CURRENT_USER_ROLE.get());
    }

    public static boolean isPresident() {
        return "PRESIDENT".equals(CURRENT_USER_ROLE.get());
    }

    /** À appeler en fin de requête pour éviter les fuites entre threads. */
    public static void clear() {
        CURRENT_USER_ID.remove();
        CURRENT_USER_EMAIL.remove();
        CURRENT_USER_ROLE.remove();
    }
}
