package com.wsi.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsi.platform.dto.AiAnalysisView;
import com.wsi.platform.dto.AiReportView;
import com.wsi.platform.entity.AiReportDraft;
import com.wsi.platform.mapper.AiReportDraftMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiReportService {

    public static final String CLINICAL_NOTICE =
            "AI-assisted draft only. It is not a diagnosis and requires pathologist review and finalization.";

    private static final Set<String> DECISIONS = Set.of("accept", "modify", "reject", "escalate");
    private static final Set<String> NOTE_REQUIRED = Set.of("modify", "reject", "escalate");

    private final AiReportDraftMapper reportMapper;
    private final AiAnalysisService analysisService;
    private final AiReportGenerator reportGenerator;
    private final ObjectMapper objectMapper;

    public AiReportView create(Long analysisId,
                               String caseId,
                               String language,
                               String clinicianDecision,
                               String clinicianNote,
                               Long requestedBy) {
        return create(analysisId, caseId, language, clinicianDecision, clinicianNote,
                null, null, requestedBy);
    }

    public AiReportView create(Long analysisId,
                               String caseId,
                               String language,
                               String clinicianDecision,
                               String clinicianNote,
                               String seniorDecision,
                               String seniorNote,
                               Long requestedBy) {
        AiAnalysisView analysis = analysisService.getById(analysisId);
        if (analysis == null) throw new IllegalArgumentException("Analysis not found");
        if (!"SUCCEEDED".equals(analysis.status())) {
            throw new IllegalStateException("A completed analysis is required");
        }

        String normalizedCaseId = requireText(caseId, "caseId", 128);
        String normalizedDecision = requireText(clinicianDecision, "clinicianDecision", 32)
                .toLowerCase(Locale.ROOT);
        if (!DECISIONS.contains(normalizedDecision)) {
            throw new IllegalArgumentException("Unsupported clinician decision");
        }
        String normalizedNote = clinicianNote == null ? "" : clinicianNote.trim();
        if (normalizedNote.length() > 4_000) {
            throw new IllegalArgumentException("Clinician note exceeds 4000 characters");
        }
        if (NOTE_REQUIRED.contains(normalizedDecision) && normalizedNote.isBlank()) {
            throw new IllegalArgumentException("A clinician note is required for this decision");
        }

        String normalizedLanguage = normalizeLanguage(language);
        String normalizedSeniorDecision = seniorDecision == null ? "" : seniorDecision.trim();
        String normalizedSeniorNote = seniorNote == null ? "" : seniorNote.trim();
        AiReportDraft active = reportMapper.selectOne(
                new LambdaQueryWrapper<AiReportDraft>()
                        .eq(AiReportDraft::getAnalysisRunId, analysisId)
                        .eq(AiReportDraft::getCaseId, normalizedCaseId)
                        .in(AiReportDraft::getStatus, Set.of("QUEUED", "RUNNING"))
                        .orderByDesc(AiReportDraft::getCreatedAt)
                        .last("LIMIT 1")
        );
        if (active != null) {
            boolean sameRequest = normalizedLanguage.equals(active.getLanguage())
                    && normalizedDecision.equals(active.getClinicianDecision())
                    && normalizedNote.equals(active.getClinicianNote())
                    && normalizedSeniorDecision.equals(valueOrEmpty(active.getSeniorDecision()))
                    && normalizedSeniorNote.equals(valueOrEmpty(active.getSeniorNote()));
            if (sameRequest) return toView(active);
            throw new IllegalStateException("Another report is already being generated for this case");
        }

        AiReportDraft draft = new AiReportDraft();
        draft.setAnalysisRunId(analysisId);
        draft.setCaseId(normalizedCaseId);
        draft.setStatus("QUEUED");
        draft.setLanguage(normalizedLanguage);
        draft.setClinicianDecision(normalizedDecision);
        draft.setClinicianNote(normalizedNote);
        draft.setSeniorDecision(normalizedSeniorDecision);
        draft.setSeniorNote(normalizedSeniorNote);
        draft.setStale(false);
        draft.setRequestedBy(requestedBy);
        reportMapper.insert(draft);
        reportGenerator.generate(draft.getId());
        return toView(draft);
    }

    public AiReportView getById(Long reportId) {
        AiReportDraft draft = reportMapper.selectById(reportId);
        return draft == null ? null : toView(draft);
    }

    public AiReportView getLatest(Long analysisId, String caseId) {
        String normalizedCaseId = requireText(caseId, "caseId", 128);
        AiReportDraft draft = reportMapper.selectOne(
                new LambdaQueryWrapper<AiReportDraft>()
                        .eq(AiReportDraft::getAnalysisRunId, analysisId)
                        .eq(AiReportDraft::getCaseId, normalizedCaseId)
                        .orderByDesc(AiReportDraft::getCreatedAt)
                        .last("LIMIT 1")
        );
        return draft == null ? null : toView(draft);
    }

    public void markCaseReportsStale(String caseId) {
        reportMapper.update(null, new LambdaUpdateWrapper<AiReportDraft>()
                .eq(AiReportDraft::getCaseId, caseId)
                .eq(AiReportDraft::getStale, false)
                .set(AiReportDraft::getStale, true));
    }

    private AiReportView toView(AiReportDraft draft) {
        return new AiReportView(
                draft.getId(),
                draft.getAnalysisRunId(),
                draft.getCaseId(),
                draft.getStatus(),
                draft.getLanguage(),
                draft.getClinicianDecision(),
                draft.getClinicianNote(),
                draft.getSeniorDecision(),
                draft.getSeniorNote(),
                Boolean.TRUE.equals(draft.getStale()),
                draft.getGeneratorSource(),
                draft.getModelName(),
                parseJson(draft.getReportPayload()),
                parseErrors(draft.getValidationErrors()),
                draft.getErrorMessage(),
                draft.getStartedAt(),
                draft.getCompletedAt(),
                draft.getCreatedAt(),
                CLINICAL_NOTICE
        );
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored report contains invalid JSON", e);
        }
    }

    private List<String> parseErrors(String value) {
        JsonNode node = parseJson(value);
        if (node == null || !node.isArray()) return List.of();
        List<String> errors = new ArrayList<>();
        node.forEach(item -> errors.add(item.asText()));
        return List.copyOf(errors);
    }

    private static String normalizeLanguage(String language) {
        if (language != null && language.toLowerCase(Locale.ROOT).startsWith("zh")) return "zh-CN";
        return "en";
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
