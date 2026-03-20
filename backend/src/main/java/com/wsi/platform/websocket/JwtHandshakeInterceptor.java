package com.wsi.platform.websocket;

import com.wsi.platform.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Validates the JWT token supplied as a query parameter (?token=xxx) during
 * the WebSocket handshake and populates session attributes with user info.
 *
 * WebSocket connections cannot send custom HTTP headers from the browser,
 * so the token must be passed as a URL query parameter.
 */
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(
            @org.springframework.lang.NonNull ServerHttpRequest request,
            @org.springframework.lang.NonNull ServerHttpResponse response,
            @org.springframework.lang.NonNull WebSocketHandler wsHandler,
            @org.springframework.lang.NonNull Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }
        String token = servletRequest.getServletRequest().getParameter("token");
        if (token == null || !jwtUtil.isValid(token)) {
            return false;
        }
        Claims claims = jwtUtil.parse(token);
        attributes.put("userId",   Long.parseLong(claims.getSubject()));
        attributes.put("username", claims.get("username", String.class));
        attributes.put("role",     claims.get("role",     String.class));
        return true;
    }

    @Override
    public void afterHandshake(
            @org.springframework.lang.NonNull ServerHttpRequest request,
            @org.springframework.lang.NonNull ServerHttpResponse response,
            @org.springframework.lang.NonNull WebSocketHandler wsHandler,
            @org.springframework.lang.Nullable Exception exception) {
        // nothing needed
    }
}
