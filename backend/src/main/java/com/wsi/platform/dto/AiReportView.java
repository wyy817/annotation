package com.wsi.platform.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

public record AiReportView(
        Long id,
        Long analysisId,
        String caseId,
        String status,
        String language,
        String clinicianDecision,
        String clinicianNote,
        String seniorDecision,
        String seniorNote,
        Boolean stale,
        String generatorSource,
        String modelName,
        JsonNode report,
        List<String> validationErrors,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        String clinicalNotice
) {
}
