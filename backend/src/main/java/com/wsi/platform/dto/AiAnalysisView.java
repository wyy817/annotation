package com.wsi.platform.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AiAnalysisView(
        Long id,
        Long slideId,
        String status,
        String stage,
        Double stageProgress,
        String message,
        String modelName,
        String predictedClass,
        Double tumorEvidenceScore,
        String evidenceBand,
        Double ensembleDisagreement,
        Integer candidateRegionCount,
        Integer highRiskRegionCount,
        Double candidateAreaProxyMm2,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        List<CandidateRegion> candidates,
        Map<String, String> artifactUrls,
        SlideSummary slide,
        ValidationSummary validation,
        RuntimeSummary runtime,
        String clinicalNotice
) {
    public record CandidateRegion(
            Long id,
            String regionId,
            String evidenceTier,
            Integer patchCount,
            Double areaProxyMm2,
            Double meanAttentionPercentile,
            Double maxAttentionPercentile,
            Double meanEvidenceIndex,
            Double maxEvidenceIndex,
            JsonNode bboxLevel0,
            JsonNode centroidLevel0,
            String evidencePatchUrl
    ) {
    }

    public record SlideSummary(
            String fileName,
            Integer width,
            Integer height,
            Integer patchCount,
            Double mppX,
            Double mppY
    ) {
    }

    public record ValidationSummary(
            Boolean available,
            Boolean highAttentionOverlapsAnnotation,
            Boolean candidateRegionsOverlapAnnotation,
            Double attentionPrecisionProxy,
            Double annotationCoverageProxy,
            Double candidatePrecisionProxy,
            String notice
    ) {
    }

    public record RuntimeSummary(
            String device,
            Double totalSeconds
    ) {
    }
}
