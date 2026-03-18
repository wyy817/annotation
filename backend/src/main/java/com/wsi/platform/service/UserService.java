package com.wsi.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    /**
     * Verify password and return user if credentials match, null otherwise.
     */
    public User authenticate(String username, String rawPassword) {
        User user = findByUsername(username);
        if (user == null) return null;
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) return null;
        return user;
    }

    /**
     * Create a user (used by DataInitializer).
     */
    public User createIfAbsent(String username, String rawPassword, String role) {
        User existing = findByUsername(username);
        if (existing != null) return existing;
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        userMapper.insert(user);
        return user;
    }
}
