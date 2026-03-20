package com.wsi.platform.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wsi.platform.common.Result;
import com.wsi.platform.entity.Slide;
import com.wsi.platform.mapper.SlideMapper;
import com.wsi.platform.service.PythonServiceClient;
import com.wsi.platform.service.PythonServiceClient.SamPredictRequest;
import com.wsi.platform.service.PythonServiceClient.SamPredictResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * POST /api/slides/{slideId}/sam/predict
 *
 * JWT-authenticated proxy to the Python SAM endpoint.
 * Validates the slide exists and is READY, then forwards the request.
 */
@RestController
@RequiredArgsConstructor
public class SamController {

    private final SlideMapper slideMapper;
    private final PythonServiceClient pythonClient;

    // ── Request / Response DTOs ──────────────────────────────────────────────

    @Data
    public static class SamRequest {
        /** WSI pyramid level (0 = full resolution). */
        private int level = 0;

        /** Point prompts in level-0 WSI global pixel coordinates: [[x,y], ...] */
        private List<List<Double>> points = List.of();

        /** Per-point label: 1 = foreground, 0 = background. */
        @JsonProperty("point_labels")
        private List<Integer> pointLabels = List.of();

        /** Optional bounding-box prompt [x1, y1, x2, y2] in level-0 WSI coords. */
        private List<Double> box;

        /** Size (px) of the image patch cropped around the prompt and fed to SAM. */
        @JsonProperty("patch_size")
        private int patchSize = 1024;
    }

    @Data
    public static class SamResponse {
        /** Polygon contours in level-0 WSI coords: [ [[x,y],...], ... ] */
        private List<List<List<Double>>> polygons;
        /** Confidence score of the best mask. */
        private double score;
        /** Base64-encoded PNG of the best binary mask (patch coordinate space). */
        @JsonProperty("mask_b64")
        private String maskB64;
    }

    // ── Endpoint ─────────────────────────────────────────────────────────────

    /**
     * Run SAM/MobileSAM inference on a WSI slide region.
     *
     * <p>The slide must be in READY status. At least one point or a bounding box
     * must be supplied. The request is forwarded to the Python microservice and
     * the result (polygons in WSI coordinates + mask PNG) is returned as-is.
     */
    @PostMapping("/api/slides/{slideId}/sam/predict")
    public Result<SamResponse> predict(@PathVariable Long slideId,
                                       @RequestBody SamRequest body) {
        // 1. Validate slide exists and is ready
        Slide slide = slideMapper.selectById(slideId);
        if (slide == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "切片不存在");
        }
        if (!"READY".equals(slide.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "切片尚未就绪（当前状态：" + slide.getStatus() + "）");
        }

        // 2. Validate at least one prompt is supplied
        boolean hasPoints = body.getPoints() != null && !body.getPoints().isEmpty();
        boolean hasBox = body.getBox() != null && body.getBox().size() == 4;
        if (!hasPoints && !hasBox) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "必须提供至少一个点提示（points）或框提示（box）");
        }

        // 3. Validate point / label length consistency
        if (hasPoints) {
            if (body.getPointLabels() == null ||
                    body.getPointLabels().size() != body.getPoints().size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "point_labels 长度必须与 points 相同");
            }
            for (List<Double> pt : body.getPoints()) {
                if (pt == null || pt.size() != 2) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "每个 point 必须是 [x, y] 格式");
                }
            }
        }

        // 4. Build Python-service request and forward
        SamPredictRequest pyReq = new SamPredictRequest();
        pyReq.setSlideId(slideId);
        pyReq.setFilePath(slide.getOriginalPath());
        pyReq.setLevel(body.getLevel());
        pyReq.setPoints(body.getPoints() != null ? body.getPoints() : List.of());
        pyReq.setPointLabels(body.getPointLabels() != null ? body.getPointLabels() : List.of());
        pyReq.setBox(body.getBox());
        pyReq.setPatchSize(body.getPatchSize() > 0 ? body.getPatchSize() : 1024);

        SamPredictResponse pyRes = pythonClient.samPredict(pyReq);

        // 5. Map to API response
        SamResponse resp = new SamResponse();
        resp.setPolygons(pyRes.getPolygons());
        resp.setScore(pyRes.getScore());
        resp.setMaskB64(pyRes.getMaskB64());
        return Result.success(resp);
    }
}
