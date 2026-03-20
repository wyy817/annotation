package com.wsi.platform.controller;

import com.wsi.platform.common.Result;
import com.wsi.platform.service.AnnotationVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * GET /api/slides/{slideId}/versions               — version metadata list (no snapshot bodies)
 * GET /api/slides/{slideId}/versions/{versionId}/annotations — full snapshot for a specific version
 */
@RestController
@RequiredArgsConstructor
public class AnnotationVersionController {

    private final AnnotationVersionService versionService;

    @GetMapping("/api/slides/{slideId}/versions")
    public Result<List<Map<String, Object>>> listVersions(@PathVariable Long slideId) {
        return Result.success(versionService.listVersions(slideId));
    }

    @GetMapping("/api/slides/{slideId}/versions/{versionId}/annotations")
    public Result<Map<String, Object>> getVersionAnnotations(
            @PathVariable Long slideId,
            @PathVariable Long versionId) {
        try {
            return Result.success(versionService.getVersionAnnotations(slideId, versionId));
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        }
    }
}
