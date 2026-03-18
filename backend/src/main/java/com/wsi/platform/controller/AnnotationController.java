package com.wsi.platform.controller;

import com.wsi.platform.common.Result;
import com.wsi.platform.entity.Annotation;
import com.wsi.platform.service.AnnotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * GET  /api/slides/{slideId}/annotations         — list annotations for a slide
 * POST /api/slides/{slideId}/annotations/batch   — replace all annotations (body: JSON array)
 */
@RestController
@RequiredArgsConstructor
public class AnnotationController {

    private final AnnotationService annotationService;

    @GetMapping("/api/slides/{slideId}/annotations")
    public Result<List<Annotation>> list(@PathVariable Long slideId) {
        return Result.success(annotationService.listBySlide(slideId));
    }

    /**
     * Batch-save: replaces ALL existing annotations for the slide.
     * Front-end should POST the full current annotation list every auto-save cycle.
     */
    @PostMapping("/api/slides/{slideId}/annotations/batch")
    public Result<List<Annotation>> saveAll(@PathVariable Long slideId,
                                            @RequestBody List<Annotation> annotations) {
        return Result.success(annotationService.saveAll(slideId, annotations));
    }

    /**
     * PATCH /api/slides/{slideId}/annotations/complete
     * Mark the slide's annotation status as DONE (user-initiated).
     */
    @PatchMapping("/api/slides/{slideId}/annotations/complete")
    public Result<Void> markDone(@PathVariable Long slideId) {
        annotationService.markDone(slideId);
        return Result.success(null);
    }
}
