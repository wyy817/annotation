package com.wsi.platform.controller;

import com.wsi.platform.common.Result;
import com.wsi.platform.entity.ExportTask;
import com.wsi.platform.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.*;

/**
 * POST /api/slides/{slideId}/export          — trigger export, returns ExportTask (PENDING)
 * GET  /api/export-tasks/{taskId}            — poll status (PENDING→PROCESSING→DONE/ERROR)
 * GET  /api/export-tasks/{taskId}/download   — stream ZIP when DONE
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @PostMapping("/api/slides/{slideId}/export")
    public Result<ExportTask> startExport(@PathVariable Long slideId) {
        try {
            return Result.success(exportService.startExport(slideId));
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(409, e.getMessage());
        }
    }

    @GetMapping("/api/export-tasks/{taskId}")
    public Result<ExportTask> getTask(@PathVariable Long taskId) {
        ExportTask task = exportService.getTask(taskId);
        if (task == null) return Result.error(404, "Export task not found");
        return Result.success(task);
    }

    @GetMapping("/api/export-tasks/{taskId}/download")
    public void download(@PathVariable Long taskId,
                         HttpServletResponse response) throws IOException {
        String zipPath = exportService.getZipPath(taskId);
        if (zipPath == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "Export not ready or task not found");
            return;
        }
        Path zip = Paths.get(zipPath);
        if (!Files.exists(zip)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "ZIP file missing");
            return;
        }
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"export_" + taskId + ".zip\"");
        response.setContentLengthLong(Files.size(zip));
        try (InputStream in = new BufferedInputStream(Files.newInputStream(zip))) {
            in.transferTo(response.getOutputStream());
        }
    }
}
