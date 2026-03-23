package com.wsi.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wsi.platform.common.MessageUtil;
import com.wsi.platform.common.Result;
import com.wsi.platform.entity.Slide;
import com.wsi.platform.entity.User;
import com.wsi.platform.mapper.SlideMapper;
import com.wsi.platform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GET /api/users/annotators — list all annotators with their progress stats
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final SlideMapper slideMapper;
    private final MessageUtil messageUtil;

    @GetMapping("/api/users/annotators")
    public Result<List<Map<String, Object>>> annotators() {
        List<User> users = userService.listAnnotators();

        List<Map<String, Object>> result = users.stream().map(u -> {
            // Count slides by review status for this annotator
            List<Slide> slides = slideMapper.selectList(
                    new LambdaQueryWrapper<Slide>().eq(Slide::getAnnotatorId, u.getId()));

            long total       = slides.size();
            long pending     = slides.stream().filter(s -> "PENDING_REVIEW".equals(s.getReviewStatus())).count();
            long approved    = slides.stream().filter(s -> "APPROVED".equals(s.getReviewStatus())).count();
            long rejected    = slides.stream().filter(s -> "REJECTED".equals(s.getReviewStatus())).count();
            long inProgress  = slides.stream().filter(s -> "NOT_SUBMITTED".equals(s.getReviewStatus())).count();

            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("username", u.getUsername());
            map.put("total", total);
            map.put("inProgress", inProgress);
            map.put("pendingReview", pending);
            map.put("approved", approved);
            map.put("rejected", rejected);
            map.put("remark", u.getRemark());
            return map;
        }).collect(Collectors.toList());

        return Result.success(result);
    }

    @PutMapping("/api/users/{id}/remark")
    public Result<Void> updateRemark(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User user = userService.findById(id);
        if (user == null) {
            return Result.error(messageUtil.msg("error.user.notfound"));
        }
        user.setRemark(body.get("remark"));
        userService.updateRemark(user);
        return Result.success(null);
    }
}
