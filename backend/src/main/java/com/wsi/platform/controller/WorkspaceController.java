package com.wsi.platform.controller;

import com.wsi.platform.common.Result;
import com.wsi.platform.entity.Workspace;
import com.wsi.platform.service.WorkspaceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    /** GET /api/workspaces — list all workspaces ordered by creation time desc */
    @GetMapping
    public Result<List<Workspace>> list() {
        return Result.success(workspaceService.listAll());
    }

    /** GET /api/workspaces/{id} */
    @GetMapping("/{id}")
    public Result<Workspace> get(@PathVariable Long id) {
        Workspace ws = workspaceService.getById(id);
        if (ws == null) return Result.error(404, "Workspace not found");
        return Result.success(ws);
    }

    /** POST /api/workspaces — create workspace */
    @PostMapping
    public Result<Workspace> create(@RequestBody WorkspaceRequest req) {
        return Result.success(workspaceService.create(req.getName(), req.getDescription()));
    }

    /** PUT /api/workspaces/{id} — rename / update description */
    @PutMapping("/{id}")
    public Result<Workspace> update(@PathVariable Long id, @RequestBody WorkspaceRequest req) {
        Workspace ws = workspaceService.update(id, req.getName(), req.getDescription());
        if (ws == null) return Result.error(404, "Workspace not found");
        return Result.success(ws);
    }

    /** DELETE /api/workspaces/{id} — cascades to slides via FK */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        if (!workspaceService.delete(id)) return Result.error(404, "Workspace not found");
        return Result.success();
    }

    @Data
    public static class WorkspaceRequest {
        private String name;
        private String description;
    }
}
