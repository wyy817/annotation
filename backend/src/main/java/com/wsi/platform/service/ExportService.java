package com.wsi.platform.service;

import com.wsi.platform.entity.ExportTask;
import com.wsi.platform.entity.Slide;
import com.wsi.platform.mapper.ExportTaskMapper;
import com.wsi.platform.mapper.SlideMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Public API for export tasks.
 * Heavy lifting (Python calls + ZIP) is delegated to ExportAsyncService (@Async bean).
 */
@Service
@RequiredArgsConstructor
public class ExportService {

    private final SlideMapper slideMapper;
    private final ExportTaskMapper exportTaskMapper;
    private final ExportAsyncService exportAsyncService;

    /**
     * Create an ExportTask (PENDING) and trigger async processing.
     * Returns immediately; poll via getTask().
     */
    public ExportTask startExport(Long slideId) {
        Slide slide = slideMapper.selectById(slideId);
        if (slide == null) throw new IllegalArgumentException("Slide not found: " + slideId);
        if (!"READY".equals(slide.getStatus()))
            throw new IllegalStateException("Slide not READY (status=" + slide.getStatus() + ")");

        ExportTask task = new ExportTask();
        task.setSlideId(slideId);
        task.setStatus("PENDING");
        exportTaskMapper.insert(task);

        exportAsyncService.doExport(task.getId());
        return task;
    }

    public ExportTask getTask(Long taskId) {
        return exportTaskMapper.selectById(taskId);
    }

    /** Returns ZIP path only when task is DONE, null otherwise. */
    public String getZipPath(Long taskId) {
        ExportTask task = exportTaskMapper.selectById(taskId);
        if (task == null || !"DONE".equals(task.getStatus())) return null;
        return task.getZipPath();
    }
}
