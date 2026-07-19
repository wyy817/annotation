package com.wsi.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsi.platform.common.MessageUtil;
import com.wsi.platform.common.Result;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final MessageUtil messageUtil;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler)
            throws Exception {
        // Only credential exchange endpoints are public. /api/auth/me requires a JWT.
        String path = req.getRequestURI();
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) return true;
        if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) return true;
        if (path.matches("/api/slides/\\d+/thumbnail")) return true;

        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeUnauthorized(res, messageUtil.msg("error.auth.unauthorized"));
            return false;
        }

        String token = header.substring(7);
        if (!jwtUtil.isValid(token)) {
            writeUnauthorized(res, messageUtil.msg("error.auth.tokenExpired"));
            return false;
        }

        Claims claims = jwtUtil.parse(token);
        UserContext.set(new UserContext.UserInfo(
                Long.parseLong(claims.getSubject()),
                claims.get("username", String.class),
                claims.get("role", String.class)
        ));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse res,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private void writeUnauthorized(HttpServletResponse res, String msg) throws Exception {
        res.setStatus(401);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(objectMapper.writeValueAsString(Result.error(401, msg)));
    }
}
