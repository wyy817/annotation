package com.wsi.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wsi.platform.entity.Annotation;
import com.wsi.platform.entity.Slide;
import com.wsi.platform.mapper.AnnotationMapper;
import com.wsi.platform.mapper.SlideMapper;
import com.wsi.platform.websocket.NotificationPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnnotationService {

    private final AnnotationMapper annotationMapper;
    private final SlideMapper slideMapper;
    private final AnnotationVersionService annotationVersionService;
    private final AuditLogService auditLogService;
    private final SimpMessagingTemplate messagingTemplate;

    public List<Annotation> listBySlide(Long slideId) {
        return annotationMapper.selectList(
                new LambdaQueryWrapper<Annotation>()
                        .eq(Annotation::getSlideId, slideId)
                        .orderByAsc(Annotation::getId));
    }

    /**
     * Replace all annotations for a slide with the given list.
     * Updates slide.annoStatus: NOT_STARTED if empty, IN_PROGRESS otherwise.
     */
    @Transactional
    public List<Annotation> saveAll(Long slideId, List<Annotation> annotations) {
        long beforeCount = annotationMapper.selectCount(
                new LambdaQueryWrapper<Annotation>().eq(Annotation::getSlideId, slideId));

        annotationMapper.delete(new LambdaQueryWrapper<Annotation>()
                .eq(Annotation::getSlideId, slideId));

        for (Annotation ann : annotations) {
            ann.setId(null);
            ann.setSlideId(slideId);
            annotationMapper.insert(ann);
        }

        String newStatus = annotations.isEmpty() ? "NOT_STARTED" : "IN_PROGRESS";
        Slide slide = slideMapper.selectById(slideId);
        if (slide != null) {
            slide.setAnnoStatus(newStatus);
            slideMapper.updateById(slide);
        }

        long afterCount = annotations.size();
        auditLogService.log("SAVE", slideId,
                Map.of("before", beforeCount, "after", afterCount));

        return annotations;
    }

    /**
     * Submit a slide for review (called by ANNOTATOR).
     * Sets annoStatus=DONE, reviewStatus=PENDING_REVIEW, annotatorId from current user.
     * Also handles re-submission after REJECTED.
     */
    @Transactional
    public void markDone(Long slideId, Long annotatorId) {
        Slide slide = slideMapper.selectById(slideId);
        if (slide == null) {
            throw new IllegalArgumentException("Slide not found: " + slideId);
        }
        if ("NOT_STARTED".equals(slide.getAnnoStatus())) {
            throw new IllegalStateException("Cannot submit a slide with no annotations.");
        }
        String trigger = "REJECTED".equals(slide.getReviewStatus()) ? "RESUBMIT" : "SUBMIT";
        slide.setAnnoStatus("DONE");
        slide.setReviewStatus("PENDING_REVIEW");
        slide.setAnnotatorId(annotatorId);
        // Clear previous rejection
        slide.setReviewerComment(null);
        slide.setReviewedAt(null);
        slideMapper.updateById(slide);
        annotationVersionService.createVersion(slideId, annotatorId, trigger, null);
        auditLogService.log(trigger, slideId, null);

        // Notify all reviewers (broadcast); front-end filters by role
        messagingTemplate.convertAndSend("/topic/review-notification",
                new NotificationPayload(
                        "REVIEW_PENDING",
                        slideId,
                        slide.getFileName(),
                        "切片 \"" + slide.getFileName() + "\" 已提交，待审核",
                        LocalDateTime.now()
                ));
    }
}
