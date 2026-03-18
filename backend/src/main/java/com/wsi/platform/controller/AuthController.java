package com.wsi.platform.controller;

import com.wsi.platform.common.Result;
import com.wsi.platform.entity.User;
import com.wsi.platform.security.JwtUtil;
import com.wsi.platform.security.UserContext;
import com.wsi.platform.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * POST /api/auth/login  — authenticate, return JWT token + user info
 * GET  /api/auth/me     — return current logged-in user (from token)
 */
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Data
    static class LoginRequest {
        private String username;
        private String password;
    }

    @PostMapping("/api/auth/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest req) {
        User user = userService.authenticate(req.getUsername(), req.getPassword());
        if (user == null) {
            return Result.error(401, "用户名或密码错误");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());
        return Result.success(Map.of(
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        "username", user.getUsername(),
                        "role", user.getRole()
                )
        ));
    }

    @GetMapping("/api/auth/me")
    public Result<Map<String, Object>> me() {
        UserContext.UserInfo info = UserContext.get();
        return Result.success(Map.of(
                "id", info.id(),
                "username", info.username(),
                "role", info.role()
        ));
    }
}
