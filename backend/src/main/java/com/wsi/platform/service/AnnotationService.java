package com.wsi.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wsi.platform.entity.Annotation;
import com.wsi.platform.entity.Slide;
import com.wsi.platform.mapper.AnnotationMapper;
import com.wsi.platform.mapper.SlideMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnotationService {

    private final AnnotationMapper annotationMapper;
    private final SlideMapper slideMapper;

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
        return annotations;
    }

    /**
     * Mark a slide's annotation status as DONE (completed by user).
     * Throws if the slide does not exist or has no annotations yet.
     */
    @Transactional
    public void markDone(Long slideId) {
        Slide slide = slideMapper.selectById(slideId);
        if (slide == null) {
            throw new IllegalArgumentException("Slide not found: " + slideId);
        }
        if ("NOT_STARTED".equals(slide.getAnnoStatus())) {
            throw new IllegalStateException("Cannot complete a slide with no annotations.");
        }
        slide.setAnnoStatus("DONE");
        slideMapper.updateById(slide);
    }
}
