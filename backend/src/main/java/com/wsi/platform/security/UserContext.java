package com.wsi.platform.security;

/**
 * Thread-local holder for the currently authenticated user.
 * Populated by AuthInterceptor on each request, cleared in afterCompletion.
 */
public class UserContext {

    private static final ThreadLocal<UserInfo> HOLDER = new ThreadLocal<>();

    public static void set(UserInfo user) { HOLDER.set(user); }
    public static UserInfo get()          { return HOLDER.get(); }
    public static void clear()            { HOLDER.remove(); }

    public record UserInfo(Long id, String username, String role) {
        public boolean isReviewer()  { return "REVIEWER".equals(role); }
        public boolean isAnnotator() { return "ANNOTATOR".equals(role); }
    }
}
