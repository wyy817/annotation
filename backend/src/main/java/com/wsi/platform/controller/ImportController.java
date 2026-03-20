package com.wsi.platform.controller;

import com.wsi.platform.common.Result;
import com.wsi.platform.service.ImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * POST /api/slides/{slideId}/annotations/import
 *   — Upload annotation file(s) to import for the given slide.
 *
 * Params:
 *   file      : multipart annotation file (JSON for COCO/CUSTOM_JSON, TXT for YOLO)
 *   classFile : (YOLO only) classes.txt file listing class names, one per line
 *   format    : CUSTOM_JSON | COCO | YOLO | VOC | GEOJSON  (default: COCO)
 *   mode      : REPLACE | MERGE            (default: REPLACE)
 *
 * Returns ImportResult { annotationCount, labelCount, mode }
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;

    @PostMapping("/api/slides/{slideId}/annotations/import")
    public Result<ImportService.ImportResult> importAnnotations(
            @PathVariable Long slideId,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) MultipartFile classFile,
            @RequestParam(defaultValue = "COCO") String format,
            @RequestParam(defaultValue = "REPLACE") String mode) {
        try {
            java.io.InputStream classesStream = (classFile != null && !classFile.isEmpty())
                    ? classFile.getInputStream() : null;
            ImportService.ImportResult result = importService.importAnnotations(
                    slideId, file.getInputStream(), classesStream, format, mode);
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("Import failed for slide {}: {}", slideId, e.getMessage(), e);
            return Result.error(500, "导入失败: " + e.getMessage());
        }
    }
}
