package com.wsi.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wsi.platform.common.MessageUtil;
import com.wsi.platform.entity.User;
import com.wsi.platform.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final MessageUtil messageUtil;

    public User findByUsername(String username) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    public User findById(Long id) {
        return userMapper.selectById(id);
    }

    public List<User> listAnnotators() {
        return userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, "ANNOTATOR")
                        .orderByAsc(User::getUsername));
    }

    public void updateRemark(User user) {
        userMapper.updateById(user);
    }

    /**
     * Verify password and return user if credentials match.
     * Returns null for wrong username/password.
     * Throws IllegalStateException if account is PENDING or DISABLED.
     */
    public User authenticate(String username, String rawPassword) {
        User user = findByUsername(username);
        if (user == null) return null;
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) return null;
        if ("PENDING".equals(user.getStatus())) {
            throw new IllegalStateException(messageUtil.msg("error.account.pending"));
        }
        if ("DISABLED".equals(user.getStatus())) {
            throw new IllegalStateException(messageUtil.msg("error.account.disabled"));
        }
        return user;
    }

    /**
     * Register a new user. Validates uniqueness, password match, strength, and role.
     * Returns the created user, or throws IllegalArgumentException on validation failure.
     */
    public User register(String username, String rawPassword, String confirmPassword, String role) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(messageUtil.msg("error.username.empty"));
        }
        if (username.length() < 3 || username.length() > 30) {
            throw new IllegalArgumentException(messageUtil.msg("error.username.length"));
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new IllegalArgumentException(messageUtil.msg("error.password.length"));
        }
        if (!rawPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException(messageUtil.msg("error.password.mismatch"));
        }
        if (!"ANNOTATOR".equals(role) && !"REVIEWER".equals(role)) {
            throw new IllegalArgumentException(messageUtil.msg("error.role.invalid"));
        }
        if (findByUsername(username) != null) {
            throw new IllegalArgumentException(messageUtil.msg("error.username.exists"));
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus("PENDING");
        userMapper.insert(user);
        return user;
    }

    /**
     * Create a user (used by DataInitializer). Seed accounts are always ACTIVE.
     */
    public User createIfAbsent(String username, String rawPassword, String role) {
        return createIfAbsent(username, rawPassword, role, username);
    }

    public User createIfAbsent(String username, String rawPassword, String role,
                               String displayName) {
        User existing = findByUsername(username);
        if (existing != null) {
            if (existing.getDisplayName() == null || existing.getDisplayName().isBlank()) {
                existing.setDisplayName(displayName);
                userMapper.updateById(existing);
            }
            return existing;
        }
        User user = new User();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setStatus("ACTIVE");
        userMapper.insert(user);
        return user;
    }

    public List<User> listPendingUsers() {
        return userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getStatus, "PENDING")
                        .orderByAsc(User::getCreatedAt));
    }

    public List<User> listAllUsers() {
        return userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .orderByAsc(User::getCreatedAt));
    }

    public void approveUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) throw new IllegalArgumentException(messageUtil.msg("error.user.notfound"));
        user.setStatus("ACTIVE");
        userMapper.updateById(user);
    }

    public void disableUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) throw new IllegalArgumentException(messageUtil.msg("error.user.notfound"));
        user.setStatus("DISABLED");
        userMapper.updateById(user);
    }
}
