package com.wsi.platform.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wsi.platform.config.StorageProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * HTTP client for the Python FastAPI microservice (localhost:8001).
 * Covers all three internal endpoints used across Phase 3 and Phase 4.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PythonServiceClient {

    private final RestTemplate restTemplate;
    private final StorageProperties props;

    // ── Request / Response POJOs ─────────────────────────────────────────────

    @Data
    public static class ProcessSlideRequest {
        @JsonProperty("slide_id")   private long slideId;
        @JsonProperty("file_path")  private String filePath;
        @JsonProperty("tile_size")  private int tileSize;
    }

    @Data
    public static class ProcessSlideResponse {
        @JsonProperty("slide_id")       private long slideId;
        @JsonProperty("tile_dir")       private String tileDir;
        private int width;
        private int height;
        @JsonProperty("max_zoom")       private int maxZoom;
        @JsonProperty("thumbnail_path") private String thumbnailPath;
    }

    @Data
    public static class GenerateMaskRequest {
        @JsonProperty("slide_id")   private long slideId;
        private int width;
        private int height;
        private List<Map<String, Object>> annotations;
        @JsonProperty("mask_type")  private String maskType;   // SEMANTIC | COLOR | INSTANCE
        @JsonProperty("mask_scale") private int maskScale;     // downsample factor (1/2/4/8)
    }

    @Data
    public static class GenerateMaskResponse {
        @JsonProperty("slide_id")   private long slideId;
        @JsonProperty("mask_path")  private String maskPath;
    }

    @Data
    public static class ExtractPatchesRequest {
        @JsonProperty("slide_id")   private long slideId;
        @JsonProperty("file_path")  private String filePath;
        private List<Map<String, Object>> annotations;
        @JsonProperty("patch_size") private int patchSize;
    }

    @Data
    public static class ExtractPatchesResponse {
        @JsonProperty("slide_id")   private long slideId;
        @JsonProperty("patch_dir")  private String patchDir;
        @JsonProperty("patch_count") private int patchCount;
    }

    // ── API calls ────────────────────────────────────────────────────────────

    /** Generate DeepZoom tiles + thumbnail for a slide. Blocking. */
    public ProcessSlideResponse processSlide(long slideId, String filePath, int tileSize) {
        String url = props.getPythonService().getBaseUrl() + "/internal/process-slide";
        ProcessSlideRequest req = new ProcessSlideRequest();
        req.setSlideId(slideId);
        req.setFilePath(filePath);
        req.setTileSize(tileSize);
        log.info("POST {} slideId={}", url, slideId);
        return restTemplate.postForObject(url, req, ProcessSlideResponse.class);
    }

    /** Render vector annotations into a semantic mask PNG. Blocking. */
    public GenerateMaskResponse generateMask(long slideId, int width, int height,
                                             List<Map<String, Object>> annotations,
                                             String maskType, int maskScale) {
        String url = props.getPythonService().getBaseUrl() + "/internal/generate-mask";
        GenerateMaskRequest req = new GenerateMaskRequest();
        req.setSlideId(slideId);
        req.setWidth(width);
        req.setHeight(height);
        req.setAnnotations(annotations);
        req.setMaskType(maskType != null ? maskType : "SEMANTIC");
        req.setMaskScale(maskScale > 0 ? maskScale : 1);
        log.info("POST {} slideId={} annCount={} maskType={} maskScale={}", url, slideId, annotations.size(), maskType, maskScale);
        return restTemplate.postForObject(url, req, GenerateMaskResponse.class);
    }

    /** Crop patches from the original image for each annotation ROI. Blocking. */
    public ExtractPatchesResponse extractPatches(long slideId, String filePath,
                                                 List<Map<String, Object>> annotations,
                                                 int patchSize) {
        String url = props.getPythonService().getBaseUrl() + "/internal/extract-patches";
        ExtractPatchesRequest req = new ExtractPatchesRequest();
        req.setSlideId(slideId);
        req.setFilePath(filePath);
        req.setAnnotations(annotations);
        req.setPatchSize(patchSize);
        log.info("POST {} slideId={} annCount={}", url, slideId, annotations.size());
        return restTemplate.postForObject(url, req, ExtractPatchesResponse.class);
    }
}
