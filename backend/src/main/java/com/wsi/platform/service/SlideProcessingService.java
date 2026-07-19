package com.wsi.platform.service;

import com.wsi.platform.entity.Slide;
import com.wsi.platform.mapper.SlideMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Coordinates initial slide preparation and repair of missing viewer artifacts. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlideProcessingService {

    private final SlideMapper slideMapper;
    private final PythonServiceClient pythonClient;
    private final Map<Long, Object> artifactLocks = new ConcurrentHashMap<>();

    /** Called after upload; processing runs on Spring's async executor. */
    @Async
    public void triggerProcessing(Long slideId) {
        Object lock = artifactLocks.computeIfAbsent(slideId, ignored -> new Object());
        synchronized (lock) {
            Slide slide = slideMapper.selectById(slideId);
            if (slide == null) {
                log.warn("triggerProcessing: slide {} not found", slideId);
                return;
            }

            slide.setStatus("PROCESSING");
            slideMapper.updateById(slide);
            try {
                processAndPersist(slide);
            } catch (Exception e) {
                log.error("Slide {} processing failed: {}", slideId, e.getMessage(), e);
                slide.setStatus("ERROR");
                slideMapper.updateById(slide);
            }
        }
    }

    /**
     * Repair a READY/imported slide whose DZI descriptor or thumbnail is absent.
     * Concurrent thumbnail and DZI requests for the same slide share one repair.
     */
    public Slide ensureArtifacts(Long slideId) {
        Object lock = artifactLocks.computeIfAbsent(slideId, ignored -> new Object());
        synchronized (lock) {
            Slide slide = slideMapper.selectById(slideId);
            if (slide == null) {
                throw new IllegalArgumentException("Slide not found: " + slideId);
            }
            if (hasViewerArtifacts(slide)) return slide;
            if (slide.getOriginalPath() == null || slide.getOriginalPath().isBlank()) {
                throw new IllegalStateException("Slide source path is unavailable");
            }

            log.info("Repairing viewer artifacts for slide {}", slideId);
            try {
                return processAndPersist(slide);
            } catch (Exception e) {
                log.error("Could not repair viewer artifacts for slide {}: {}",
                        slideId, e.getMessage(), e);
                throw new IllegalStateException("Slide artifacts are unavailable", e);
            }
        }
    }

    static boolean hasViewerArtifacts(Slide slide) {
        if (slide.getTileDir() == null || slide.getTileDir().isBlank()
                || slide.getThumbnailPath() == null || slide.getThumbnailPath().isBlank()) {
            return false;
        }
        try {
            return Files.isRegularFile(Paths.get(slide.getTileDir(), "slide.dzi"))
                    && Files.isRegularFile(Paths.get(slide.getThumbnailPath()));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    private Slide processAndPersist(Slide slide) {
        PythonServiceClient.ProcessSlideResponse response =
                pythonClient.processSlide(slide.getId(), slide.getOriginalPath(), 256);
        if (response == null) {
            throw new IllegalStateException("Python service returned no slide metadata");
        }
        if (response.getTileDir() == null || response.getTileDir().isBlank()
                || response.getThumbnailPath() == null || response.getThumbnailPath().isBlank()) {
            throw new IllegalStateException("Python service returned incomplete slide artifacts");
        }

        slide.setStatus("READY");
        slide.setTileDir(response.getTileDir());
        slide.setThumbnailPath(response.getThumbnailPath());
        slide.setWidth(response.getWidth());
        slide.setHeight(response.getHeight());
        slide.setMaxZoom(response.getMaxZoom());
        slide.setTileSize(256);
        slideMapper.updateById(slide);
        log.info("Slide {} READY - {}x{} maxZoom={}", slide.getId(),
                response.getWidth(), response.getHeight(), response.getMaxZoom());
        return slide;
    }
}
