package com.wsi.platform.controller;

import com.wsi.platform.common.Result;
import com.wsi.platform.entity.User;
import com.wsi.platform.security.UserContext;
import com.wsi.platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only endpoints for user management.
 * All endpoints require REVIEWER role.
 *
 * GET  /api/admin/users/pending      — list users with PENDING status
 * GET  /api/admin/users              — list all users
 * PUT  /api/admin/users/{id}/approve — set status to ACTIVE
 * PUT  /api/admin/users/{id}/disable — set status to DISABLED
 */
@RestController
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;

    @GetMapping("/api/admin/users/pending")
    public Result<List<User>> listPending() {
        requireReviewer();
        return Result.success(userService.listPendingUsers());
    }

    @GetMapping("/api/admin/users")
    public Result<List<User>> listAll() {
        requireReviewer();
        return Result.success(userService.listAllUsers());
    }

    @PutMapping("/api/admin/users/{id}/approve")
    public Result<String> approve(@PathVariable Long id) {
        requireReviewer();
        try {
            userService.approveUser(id);
            return Result.success("已通过审批");
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }

    @PutMapping("/api/admin/users/{id}/disable")
    public Result<String> disable(@PathVariable Long id) {
        requireReviewer();
        try {
            userService.disableUser(id);
            return Result.success("已禁用");
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }

    private void requireReviewer() {
        UserContext.UserInfo info = UserContext.get();
        if (!"REVIEWER".equals(info.role())) {
            throw new SecurityException("无权限");
        }
    }
}
