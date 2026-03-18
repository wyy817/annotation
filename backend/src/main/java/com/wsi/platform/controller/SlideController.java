package com.wsi.platform.controller;

import com.wsi.platform.common.Result;
import com.wsi.platform.entity.Slide;
import com.wsi.platform.service.SlideProcessingService;
import com.wsi.platform.service.SlideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SlideController {

    private final SlideService slideService;
    private final SlideProcessingService processingService;

    /**
     * POST /api/workspaces/{workspaceId}/slides/upload
     * Accept: multipart/form-data, field name "files"
     * Supports: single image, multiple images (folder upload), .zip, .tar.gz
     * Returns immediately with PENDING slides; processing happens async.
     */
    @PostMapping("/api/workspaces/{workspaceId}/slides/upload")
    public Result<List<Slide>> upload(
            @PathVariable Long workspaceId,
            @RequestPart("files") List<MultipartFile> files) {
        try {
            List<Slide> slides = slideService.uploadFiles(workspaceId, files);
            slides.forEach(s -> processingService.triggerProcessing(s.getId()));
            return Result.success(slides);
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        } catch (IOException e) {
            log.error("Upload failed for workspace {}: {}", workspaceId, e.getMessage(), e);
            return Result.error(500, "Upload failed: " + e.getMessage());
        }
    }

    /**
     * GET /api/workspaces/{workspaceId}/slides
     * List all slides in a workspace (for file browser).
     */
    @GetMapping("/api/workspaces/{workspaceId}/slides")
    public Result<List<Slide>> listByWorkspace(@PathVariable Long workspaceId) {
        return Result.success(slideService.listByWorkspace(workspaceId));
    }

    /**
     * GET /api/slides/{id}
     * Poll a single slide's status (PENDING → PROCESSING → READY / ERROR).
     */
    @GetMapping("/api/slides/{id}")
    public Result<Slide> get(@PathVariable Long id) {
        Slide slide = slideService.getById(id);
        if (slide == null) return Result.error(404, "Slide not found");
        return Result.success(slide);
    }

    /** DELETE /api/slides/{id} */
    @DeleteMapping("/api/slides/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        if (!slideService.delete(id)) return Result.error(404, "Slide not found");
        return Result.success();
    }

    /** PATCH /api/slides/{id}/move  body: { "workspaceId": 123 } */
    @PatchMapping("/api/slides/{id}/move")
    public Result<Void> move(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        Long targetWorkspaceId = body.get("workspaceId");
        if (targetWorkspaceId == null) return Result.error(400, "workspaceId required");
        if (!slideService.move(id, targetWorkspaceId)) return Result.error(404, "Slide or target workspace not found");
        return Result.success();
    }
}
