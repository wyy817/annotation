package com.wsi.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wsi.platform.common.MessageUtil;
import com.wsi.platform.common.Result;
import com.wsi.platform.entity.Slide;
import com.wsi.platform.mapper.SlideMapper;
import com.wsi.platform.security.UserContext;
import com.wsi.platform.service.AnnotationVersionService;
import com.wsi.platform.service.AuditLogService;
import com.wsi.platform.websocket.NotificationPayload;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET   /api/reviews/pending         — list slides waiting for review (REVIEWER only)
 * GET   /api/reviews/approved        — list APPROVED slides (REVIEWER only)
 * PATCH /api/reviews/{slideId}/approve — approve a slide
 * PATCH /api/reviews/{slideId}/reject  — reject a slide with a comment
 */
@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final SlideMapper slideMapper;
    private final AnnotationVersionService annotationVersionService;
    private final AuditLogService auditLogService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageUtil messageUtil;

    @GetMapping("/api/reviews/approved")
    public Result<List<Slide>> approved() {
        List<Slide> slides = slideMapper.selectList(
                new LambdaQueryWrapper<Slide>()
                        .eq(Slide::getReviewStatus, "APPROVED")
                        .orderByDesc(Slide::getId));
        return Result.success(slides);
    }

    @GetMapping("/api/reviews/pending")
    public Result<List<Slide>> pending() {
        List<Slide> slides = slideMapper.selectList(
                new LambdaQueryWrapper<Slide>()
                        .eq(Slide::getReviewStatus, "PENDING_REVIEW")
                        .orderByAsc(Slide::getId));
        return Result.success(slides);
    }

    @PatchMapping("/api/reviews/{slideId}/approve")
    public Result<Void> approve(@PathVariable Long slideId) {
        Slide slide = slideMapper.selectById(slideId);
        if (slide == null) return Result.error(404, "Slide not found");
        if (!"PENDING_REVIEW".equals(slide.getReviewStatus())) {
            return Result.error(400, messageUtil.msg("error.review.notPending"));
        }
        UserContext.UserInfo reviewer = UserContext.get();
        slide.setReviewStatus("APPROVED");
        slide.setReviewerId(reviewer.id());
        slide.setReviewerComment(null);
        slide.setReviewedAt(LocalDateTime.now());
        slideMapper.updateById(slide);
        annotationVersionService.createVersion(slideId, reviewer.id(), "APPROVE", null);
        auditLogService.log("APPROVE", slideId, null);

        // Notify the annotator point-to-point
        if (slide.getAnnotatorId() != null) {
            messagingTemplate.convertAndSendToUser(
                    slide.getAnnotatorId().toString(),
                    "/queue/notifications",
                    new NotificationPayload(
                            "APPROVED",
                            slideId,
                            slide.getFileName(),
                            "切片 \"" + slide.getFileName() + "\" 已审核通过",
                            java.time.LocalDateTime.now()
                    ));
        }
        return Result.success(null);
    }

    @Data
    static class RejectRequest {
        private String comment;
    }

    @PatchMapping("/api/reviews/{slideId}/reject")
    public Result<Void> reject(@PathVariable Long slideId,
                               @RequestBody RejectRequest req) {
        Slide slide = slideMapper.selectById(slideId);
        if (slide == null) return Result.error(404, "Slide not found");
        if (!"PENDING_REVIEW".equals(slide.getReviewStatus())) {
            return Result.error(400, messageUtil.msg("error.review.notPending"));
        }
        UserContext.UserInfo reviewer = UserContext.get();
        slide.setReviewStatus("REJECTED");
        slide.setReviewerId(reviewer.id());
        slide.setReviewerComment(req.getComment());
        slide.setReviewedAt(LocalDateTime.now());
        // Reset annoStatus so annotator can re-edit
        slide.setAnnoStatus("IN_PROGRESS");
        slideMapper.updateById(slide);
        annotationVersionService.createVersion(slideId, reviewer.id(), "REJECT", req.getComment());
        auditLogService.log("REJECT", slideId,
                req.getComment() != null ? java.util.Map.of("comment", req.getComment()) : null);

        // Notify the annotator point-to-point
        if (slide.getAnnotatorId() != null) {
            String reason = req.getComment() != null ? req.getComment() : "";
            messagingTemplate.convertAndSendToUser(
                    slide.getAnnotatorId().toString(),
                    "/queue/notifications",
                    new NotificationPayload(
                            "REJECTED",
                            slideId,
                            slide.getFileName(),
                            "切片 \"" + slide.getFileName() + "\" 被拒绝：" + reason,
                            java.time.LocalDateTime.now()
                    ));
        }
        return Result.success(null);
    }
}
