package com.wsi.platform.websocket;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks active WebSocket sessions in memory.
 * Suitable for single-instance deployment (no Redis needed).
 *
 * Principal name = userId string (set by JwtHandshakeHandler).
 */
@Component
public class UserSessionRegistry {

    /** userId -> set of STOMP session IDs (one user may have multiple tabs) */
    private final Map<Long, Set<String>> userSessions = new ConcurrentHashMap<>();

    /** sessionId -> userId (for fast lookup on disconnect) */
    private final Map<String, Long> sessionUser = new ConcurrentHashMap<>();

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        Map<String, Object> attrs = accessor.getSessionAttributes();
        if (sessionId != null && attrs != null && attrs.containsKey("userId")) {
            Long userId = (Long) attrs.get("userId");
            userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
            sessionUser.put(sessionId, userId);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        if (sessionId == null) return;
        Long userId = sessionUser.remove(sessionId);
        if (userId != null) {
            Set<String> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) userSessions.remove(userId);
            }
        }
    }

    /** Returns true if the user has at least one active WebSocket connection. */
    public boolean isOnline(Long userId) {
        Set<String> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    /** Returns an unmodifiable view of all currently online user IDs. */
    public Set<Long> onlineUsers() {
        return Collections.unmodifiableSet(userSessions.keySet());
    }
}
