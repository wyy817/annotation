package com.wsi.platform.service;

import com.wsi.platform.entity.Slide;
import com.wsi.platform.mapper.SlideMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Async tile-generation trigger.
 * Kept separate from SlideService so that @Async proxying works correctly
 * (Spring cannot proxy self-invocation within the same bean).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlideProcessingService {

    private final SlideMapper slideMapper;
    private final PythonServiceClient pythonClient;

    /**
     * Called after a Slide record is created (status=PENDING).
     * Runs in a separate thread via Spring's task executor.
     */
    @Async
    public void triggerProcessing(Long slideId) {
        Slide slide = slideMapper.selectById(slideId);
        if (slide == null) {
            log.warn("triggerProcessing: slide {} not found", slideId);
            return;
        }

        slide.setStatus("PROCESSING");
        slideMapper.updateById(slide);

        try {
            PythonServiceClient.ProcessSlideResponse resp =
                    pythonClient.processSlide(slideId, slide.getOriginalPath(), 256);

            slide.setStatus("READY");
            slide.setTileDir(resp.getTileDir());
            slide.setThumbnailPath(resp.getThumbnailPath());
            slide.setWidth(resp.getWidth());
            slide.setHeight(resp.getHeight());
            slide.setMaxZoom(resp.getMaxZoom());
            slide.setTileSize(256);
            slideMapper.updateById(slide);
            log.info("Slide {} READY — {}×{} maxZoom={}", slideId,
                    resp.getWidth(), resp.getHeight(), resp.getMaxZoom());

        } catch (Exception e) {
            log.error("Slide {} processing failed: {}", slideId, e.getMessage(), e);
            slide.setStatus("ERROR");
            slideMapper.updateById(slide);
        }
    }
}
