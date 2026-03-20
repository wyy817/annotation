package com.wsi.platform.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Sets the WebSocket session Principal to the authenticated user's ID string.
 * This is required for SimpMessagingTemplate.convertAndSendToUser() to route
 * messages to the correct session via /user/queue/notifications.
 */
public class JwtHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(
            @org.springframework.lang.NonNull ServerHttpRequest request,
            @org.springframework.lang.NonNull WebSocketHandler wsHandler,
            @org.springframework.lang.NonNull Map<String, Object> attributes) {
        Long userId = (Long) attributes.get("userId");
        if (userId == null) return null;
        String name = String.valueOf(userId);
        return () -> name;  // Principal name = userId string
    }
}
