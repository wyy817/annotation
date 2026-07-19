package com.wsi.platform.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.wsi.platform.config.StorageProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

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

    @Data
    public static class SamPredictRequest {
        @JsonProperty("slide_id")   private long slideId;
        @JsonProperty("file_path")  private String filePath;
        private int level = 0;
        private List<List<Double>> points = List.of();
        @JsonProperty("point_labels") private List<Integer> pointLabels = List.of();
        private List<Double> box;                // nullable: [x1,y1,x2,y2]
        @JsonProperty("patch_size") private int patchSize = 1024;
    }

    @Data
    public static class SamPredictResponse {
        private List<List<List<Double>>> polygons; // contours in level-0 WSI coords
        private double score;
        @JsonProperty("mask_b64") private String maskB64;
    }

    @Data
    public static class CreateAiAnalysisRequest {
        @JsonProperty("slide_id") private long slideId;
        @JsonProperty("file_path") private String filePath;
        @JsonProperty("case_id") private String caseId;
        @JsonProperty("annotation_xml") private String annotationXml;
        @JsonProperty("patch_h5") private String patchH5;
    }

    @Data
    public static class AiAnalysisJobResponse {
        @JsonProperty("job_id") private String jobId;
        @JsonProperty("slide_id") private long slideId;
        @JsonProperty("case_id") private String caseId;
        private String status;
        private String stage;
        @JsonProperty("stage_progress") private double stageProgress;
        private String message;
        @JsonProperty("created_at") private String createdAt;
        @JsonProperty("started_at") private String startedAt;
        @JsonProperty("completed_at") private String completedAt;
        private String error;
        @JsonProperty("status_url") private String statusUrl;
        @JsonProperty("result_url") private String resultUrl;
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

    /** Render a single OpenSlide DeepZoom tile. Python caches the result. */
    public ResponseEntity<byte[]> getSlideTile(long slideId,
                                               String filePath,
                                               int tileSize,
                                               int level,
                                               int col,
                                               int row) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(props.getPythonService().getBaseUrl())
                .pathSegment(
                        "internal", "slides", String.valueOf(slideId),
                        "tiles", String.valueOf(level), String.valueOf(col), String.valueOf(row)
                )
                .queryParam("file_path", filePath)
                .queryParam("tile_size", tileSize);
        return restTemplate.exchange(
                builder.build().encode().toUri(),
                HttpMethod.GET,
                null,
                byte[].class
        );
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

    /** Run SAM/MobileSAM inference on a WSI region. Blocking. */
    public SamPredictResponse samPredict(SamPredictRequest req) {
        String url = props.getPythonService().getBaseUrl() + "/internal/sam/predict";
        log.info("POST {} slideId={} points={} hasBox={}", url, req.getSlideId(),
                req.getPoints().size(), req.getBox() != null);
        return restTemplate.postForObject(url, req, SamPredictResponse.class);
    }

    public AiAnalysisJobResponse createAiAnalysis(long slideId,
                                                   String filePath,
                                                   String caseId,
                                                   String annotationXml,
                                                   String patchH5) {
        String url = props.getPythonService().getBaseUrl() + "/internal/ai/analyses";
        CreateAiAnalysisRequest req = new CreateAiAnalysisRequest();
        req.setSlideId(slideId);
        req.setFilePath(filePath);
        req.setCaseId(caseId);
        req.setAnnotationXml(annotationXml);
        req.setPatchH5(patchH5);
        log.info("POST {} slideId={}", url, slideId);
        return restTemplate.postForObject(url, req, AiAnalysisJobResponse.class);
    }

    public AiAnalysisJobResponse getAiAnalysisJob(String jobId) {
        String url = props.getPythonService().getBaseUrl()
                + "/internal/ai/analyses/" + jobId;
        return restTemplate.getForObject(url, AiAnalysisJobResponse.class);
    }

    public JsonNode getAiAnalysisResult(String jobId) {
        String url = props.getPythonService().getBaseUrl()
                + "/internal/ai/analyses/" + jobId + "/result";
        return restTemplate.getForObject(url, JsonNode.class);
    }

    public ResponseEntity<byte[]> getAiAnalysisArtifact(String jobId,
                                                        String relativePath) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(props.getPythonService().getBaseUrl())
                .pathSegment("internal", "ai", "analyses", jobId, "files");
        String normalized = relativePath.replace('\\', '/');
        for (String segment : normalized.split("/")) {
            if (!segment.isBlank()) builder.pathSegment(segment);
        }
        return restTemplate.exchange(
                builder.build().encode().toUri(),
                HttpMethod.GET,
                null,
                byte[].class
        );
    }
}
