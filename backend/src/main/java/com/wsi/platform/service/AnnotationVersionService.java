package com.wsi.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsi.platform.entity.Annotation;
import com.wsi.platform.entity.AnnotationVersion;
import com.wsi.platform.entity.Label;
import com.wsi.platform.mapper.AnnotationMapper;
import com.wsi.platform.mapper.AnnotationVersionMapper;
import com.wsi.platform.mapper.LabelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnnotationVersionService {

    private final AnnotationVersionMapper versionMapper;
    private final AnnotationMapper annotationMapper;
    private final LabelMapper labelMapper;
    private final ObjectMapper objectMapper;

    /**
     * Snapshot the current annotations + labels for a slide and persist as a new version.
     *
     * @param slideId         target slide
     * @param createdBy       user who triggered the event
     * @param trigger         SUBMIT | RESUBMIT | REJECT | APPROVE
     * @param reviewerComment optional reviewer comment (only for REJECT)
     */
    @Transactional
    public void createVersion(Long slideId, Long createdBy, String trigger, String reviewerComment) {
        // Compute next version number for this slide
        Long count = versionMapper.selectCount(
                new LambdaQueryWrapper<AnnotationVersion>()
                        .eq(AnnotationVersion::getSlideId, slideId));
        int nextVersion = count.intValue() + 1;

        // Snapshot annotations
        List<Annotation> annotations = annotationMapper.selectList(
                new LambdaQueryWrapper<Annotation>()
                        .eq(Annotation::getSlideId, slideId)
                        .orderByAsc(Annotation::getId));

        // Snapshot labels
        List<Label> labels = labelMapper.selectList(
                new LambdaQueryWrapper<Label>()
                        .eq(Label::getSlideId, slideId)
                        .orderByAsc(Label::getId));

        String annotationsJson;
        String labelsJson;
        try {
            annotationsJson = objectMapper.writeValueAsString(annotations);
            labelsJson = objectMapper.writeValueAsString(labels);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize snapshot", e);
        }

        AnnotationVersion version = new AnnotationVersion();
        version.setSlideId(slideId);
        version.setVersionNumber(nextVersion);
        version.setCreatedBy(createdBy);
        version.setCreatedAt(LocalDateTime.now());
        version.setTrigger(trigger);
        version.setAnnotationsSnapshot(annotationsJson);
        version.setLabelsSnapshot(labelsJson);
        version.setReviewerComment(reviewerComment);

        versionMapper.insert(version);
    }

    /**
     * List version metadata for a slide (excludes large snapshot fields).
     */
    public List<Map<String, Object>> listVersions(Long slideId) {
        List<AnnotationVersion> versions = versionMapper.selectList(
                new LambdaQueryWrapper<AnnotationVersion>()
                        .eq(AnnotationVersion::getSlideId, slideId)
                        .orderByAsc(AnnotationVersion::getVersionNumber)
                        .select(AnnotationVersion::getId,
                                AnnotationVersion::getSlideId,
                                AnnotationVersion::getVersionNumber,
                                AnnotationVersion::getCreatedBy,
                                AnnotationVersion::getCreatedAt,
                                AnnotationVersion::getTrigger,
                                AnnotationVersion::getReviewerComment));
        return versions.stream()
                .map(v -> Map.<String, Object>of(
                        "id", v.getId(),
                        "slideId", v.getSlideId(),
                        "versionNumber", v.getVersionNumber(),
                        "createdBy", v.getCreatedBy() != null ? v.getCreatedBy() : "",
                        "createdAt", v.getCreatedAt(),
                        "trigger", v.getTrigger(),
                        "reviewerComment", v.getReviewerComment() != null ? v.getReviewerComment() : ""))
                .toList();
    }

    /**
     * Return the full annotations + labels snapshot for a specific version.
     */
    public Map<String, Object> getVersionAnnotations(Long slideId, Long versionId) {
        AnnotationVersion version = versionMapper.selectOne(
                new LambdaQueryWrapper<AnnotationVersion>()
                        .eq(AnnotationVersion::getId, versionId)
                        .eq(AnnotationVersion::getSlideId, slideId));
        if (version == null) {
            throw new IllegalArgumentException("Version not found: " + versionId);
        }
        try {
            Object annotations = objectMapper.readValue(version.getAnnotationsSnapshot(), Object.class);
            Object labels = objectMapper.readValue(version.getLabelsSnapshot(), Object.class);
            return Map.of("annotations", annotations, "labels", labels);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize snapshot", e);
        }
    }
}
