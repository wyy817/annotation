package com.wsi.platform.controller;

import com.wsi.platform.common.Result;
import com.wsi.platform.entity.Label;
import com.wsi.platform.service.LabelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * GET    /api/slides/{slideId}/labels    — list labels for a slide
 * POST   /api/slides/{slideId}/labels    — create label  { name, color }
 * PUT    /api/labels/{id}                — update name/color
 * DELETE /api/labels/{id}               — delete label + cascade annotations
 */
@RestController
@RequiredArgsConstructor
public class LabelController {

    private final LabelService labelService;

    @GetMapping("/api/slides/{slideId}/labels")
    public Result<List<Label>> list(@PathVariable Long slideId) {
        return Result.success(labelService.listBySlide(slideId));
    }

    @PostMapping("/api/slides/{slideId}/labels")
    public Result<Label> create(@PathVariable Long slideId,
                                @RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) return Result.error(400, "name is required");
        String color = body.getOrDefault("color", "#FF0000");
        return Result.success(labelService.create(slideId, name, color));
    }

    @PutMapping("/api/labels/{id}")
    public Result<Label> update(@PathVariable Long id,
                                @RequestBody Map<String, String> body) {
        Label updated = labelService.update(id, body.get("name"), body.get("color"));
        if (updated == null) return Result.error(404, "Label not found");
        return Result.success(updated);
    }

    @DeleteMapping("/api/labels/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        if (!labelService.delete(id)) return Result.error(404, "Label not found");
        return Result.success();
    }
}
