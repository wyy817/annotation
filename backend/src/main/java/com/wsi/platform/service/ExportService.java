package com.wsi.platform.service;

import com.wsi.platform.common.MessageUtil;
import com.wsi.platform.entity.ExportTask;
import com.wsi.platform.entity.Slide;
import com.wsi.platform.enums.ExportFormat;
import com.wsi.platform.mapper.ExportTaskMapper;
import com.wsi.platform.mapper.SlideMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Public API for export tasks.
 * Heavy lifting (Python calls + ZIP) is delegated to ExportAsyncService (@Async bean).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final SlideMapper slideMapper;
    private final ExportTaskMapper exportTaskMapper;
    private final ExportAsyncService exportAsyncService;
    private final MessageUtil messageUtil;

    /**
     * Create an ExportTask (PENDING) and trigger async processing.
     * Returns immediately; poll via getTask().
     */
    public ExportTask startExport(Long slideId, String format,
                                   Integer patchSize, String maskType, Integer maskScale) {
        Slide slide = slideMapper.selectById(slideId);
        if (slide == null) throw new IllegalArgumentException("Slide not found: " + slideId);
        if (!"READY".equals(slide.getStatus()))
            throw new IllegalStateException("Slide not READY (status=" + slide.getStatus() + ")");
        if (!"APPROVED".equals(slide.getReviewStatus()))
            throw new IllegalStateException(messageUtil.msg("error.export.notApproved"));

        ExportFormat fmt = ExportFormat.fromString(format);
        ExportTask task = new ExportTask();
        task.setSlideId(slideId);
        task.setFormat(fmt.name());
        task.setStatus("PENDING");
        task.setPatchSize(patchSize != null ? patchSize : 256);
        task.setMaskType(maskType != null ? maskType : "SEMANTIC");
        task.setMaskScale(maskScale != null ? maskScale : 1);
        exportTaskMapper.insert(task);

        exportAsyncService.doExport(task.getId());
        return task;
    }

    public ExportTask getTask(Long taskId) {
        return exportTaskMapper.selectById(taskId);
    }

    /**
     * Start a single combined batch export for all given slideIds.
     * Validates each slide before starting; throws if none are eligible.
     * Returns one ExportTask covering all slides.
     */
    public ExportTask startBatchExport(List<Long> slideIds, String format,
                                        Integer patchSize, String maskType, Integer maskScale) {
        // Validate — collect eligible slide IDs
        List<Long> eligible = slideIds.stream().filter(slideId -> {
            Slide slide = slideMapper.selectById(slideId);
            if (slide == null) {
                log.warn("Batch export: slide {} not found, skipping", slideId);
                return false;
            }
            if (!"READY".equals(slide.getStatus())) {
                log.warn("Batch export: slide {} not READY, skipping", slideId);
                return false;
            }
            if (!"APPROVED".equals(slide.getReviewStatus())) {
                log.warn("Batch export: slide {} not APPROVED, skipping", slideId);
                return false;
            }
            return true;
        }).toList();

        if (eligible.isEmpty())
            throw new IllegalStateException(messageUtil.msg("error.export.noApprovedSlides"));

        ExportFormat fmt = ExportFormat.fromString(format);
        ExportTask task = new ExportTask();
        // slideId is null for batch tasks
        task.setFormat(fmt.name());
        task.setStatus("PENDING");
        task.setPatchSize(patchSize != null ? patchSize : 256);
        task.setMaskType(maskType != null ? maskType : "SEMANTIC");
        task.setMaskScale(maskScale != null ? maskScale : 1);
        exportTaskMapper.insert(task);

        exportAsyncService.doBatchExport(task.getId(), eligible);
        return task;
    }

    /** Returns ZIP path only when task is DONE, null otherwise. */
    public String getZipPath(Long taskId) {
        ExportTask task = exportTaskMapper.selectById(taskId);
        if (task == null || !"DONE".equals(task.getStatus())) return null;
        return task.getZipPath();
    }
}
