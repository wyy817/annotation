package com.wsi.platform.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ClinicalCaseView(
        Long databaseId,
        String id,
        String accession,
        String diseaseProfileId,
        Integer patientAge,
        Specimen specimen,
        LocalDateTime receivedAt,
        LocalDateTime dueAt,
        String owner,
        Long assignedPathologistId,
        Long localSlideId,
        String primarySlideFileName,
        String primarySlideStatus,
        AiSummary ai,
        Workflow workflow,
        Feedback feedback,
        List<AuditEvent> auditTrail
) {
    public record Specimen(String node, String siteKey, String stain, Integer slideCount) {}

    public record AiSummary(
            Double risk,
            Double confidence,
            List<String> flags,
            String analysisSource,
            Long latestAnalysisId,
            String latestAnalysisStatus,
            LocalDateTime generatedAt
    ) {}

    public record Workflow(
            String status,
            Boolean reviewRequired,
            List<String> reviewTriggers,
            Boolean qcSample,
            Decision decision,
            SeniorReview seniorReview,
            LocalDateTime lastUpdatedAt
    ) {}

    public record Decision(
            String type,
            String note,
            String actor,
            String actorRole,
            LocalDateTime at
    ) {}

    public record SeniorReview(
            String status,
            String reviewer,
            LocalDateTime at,
            String note
    ) {}

    public record Feedback(Boolean eligible, String status, String version) {}

    public record AuditEvent(
            LocalDateTime at,
            String type,
            String actor,
            String detailKey
    ) {}
}
