package com.wsi.platform.controller;

import com.wsi.platform.common.Result;
import com.wsi.platform.dto.AiAnalysisView;
import com.wsi.platform.security.UserContext;
import com.wsi.platform.service.AiAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    public record StartAnalysisRequest(
            String caseId,
            String annotationXml,
            String patchH5
    ) {
    }

    @PostMapping("/slides/{slideId}/ai-analyses")
    public Result<AiAnalysisView> start(
            @PathVariable Long slideId,
            @RequestBody(required = false) StartAnalysisRequest request) {
        StartAnalysisRequest body = request == null
                ? new StartAnalysisRequest(null, null, null)
                : request;
        try {
            UserContext.UserInfo user = UserContext.get();
            return Result.success(aiAnalysisService.startAnalysis(
                    slideId,
                    body.caseId(),
                    body.annotationXml(),
                    body.patchH5(),
                    user == null ? null : user.id()
            ));
        } catch (IllegalArgumentException e) {
            return Result.error(404, e.getMessage());
        } catch (Exception e) {
            log.error("Could not start AI analysis for slide {}", slideId, e);
            return Result.error(503, "AI analysis service is unavailable");
        }
    }

    @GetMapping("/ai-analyses/{analysisId}")
    public Result<AiAnalysisView> get(@PathVariable Long analysisId) {
        AiAnalysisView view = aiAnalysisService.getById(analysisId);
        return view == null
                ? Result.error(404, "Analysis not found")
                : Result.success(view);
    }

    @GetMapping("/slides/{slideId}/ai-analyses/latest")
    public Result<AiAnalysisView> latest(@PathVariable Long slideId) {
        AiAnalysisView view = aiAnalysisService.getLatestForSlide(slideId);
        return Result.success(view);
    }

    @GetMapping("/ai-analyses/{analysisId}/artifacts/{artifactKey}")
    public ResponseEntity<byte[]> artifact(
            @PathVariable Long analysisId,
            @PathVariable String artifactKey) {
        try {
            AiAnalysisService.ArtifactPayload artifact =
                    aiAnalysisService.getArtifact(analysisId, artifactKey);
            return ResponseEntity.ok()
                    .contentType(artifact.contentType())
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                    .body(artifact.body());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).build();
        }
    }
}
