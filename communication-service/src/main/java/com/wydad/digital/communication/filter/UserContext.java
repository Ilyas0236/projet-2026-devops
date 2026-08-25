package com.wydad.digital.communication.filter;

/**
 * Contexte thread-local alimenté par {@link UserContextFilter} à partir des
 * en-têtes d'identité propagés par la gateway (X-User-Id/X-User-Email/
 * X-User-Role). Aucune confiance directe dans ces en-têtes : ils ne sont
 * lisibles que derrière la gateway qui les pose depuis un JWT validé.
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> EMAIL = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();

    private UserContext() {
    }

    public static void setCurrentUserId(Long id) {
        USER_ID.set(id);
    }

    public static Long getCurrentUserId() {
        return USER_ID.get();
    }

    public static void setCurrentUserEmail(String email) {
        EMAIL.set(email);
    }

    public static String getCurrentUserEmail() {
        return EMAIL.get();
    }

    public static void setCurrentUserRole(String role) {
        ROLE.set(role);
    }

    public static String getCurrentUserRole() {
        return ROLE.get();
    }

    public static void clear() {
        USER_ID.remove();
        EMAIL.remove();
        ROLE.remove();
    }
}
