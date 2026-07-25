package com.wsi.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wsi.platform.dto.AiAnalysisView;
import com.wsi.platform.entity.AiReportDraft;
import com.wsi.platform.mapper.AiReportDraftMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiReportGenerator {

    private final AiReportDraftMapper reportMapper;
    private final AiAnalysisService analysisService;
    private final LlmReportClient llmReportClient;
    private final AiReportContentValidator contentValidator;
    private final ObjectMapper objectMapper;

    @Async("reportGenerationExecutor")
    public void generate(Long reportId) {
        AiReportDraft draft = reportMapper.selectById(reportId);
        if (draft == null || !"QUEUED".equals(draft.getStatus())) return;

        draft.setStatus("RUNNING");
        draft.setStartedAt(LocalDateTime.now());
        reportMapper.updateById(draft);

        AiAnalysisView analysis = analysisService.getById(draft.getAnalysisRunId());
        try {
            if (analysis == null || !"SUCCEEDED".equals(analysis.status())) {
                throw new IllegalStateException("Completed model evidence is unavailable");
            }

            JsonNode context = buildContext(draft, analysis);
            draft.setRequestPayload(context.toString());
            reportMapper.updateById(draft);

            LlmReportClient.GenerationResult generated = llmReportClient.generate(
                    context, loadImages(analysis)
            );
            AiReportContentValidator.ValidationResult validation =
                    contentValidator.validateAndNormalize(generated.report(), draft.getLanguage());
            if (!validation.errors().isEmpty()) {
                draft.setValidationErrors(objectMapper.valueToTree(validation.errors()).toString());
                throw new InvalidReportException("LLM response failed report schema validation");
            }

            draft.setStatus("SUCCEEDED");
            draft.setGeneratorSource("LIVE_LLM");
            draft.setModelName(generated.model());
            draft.setResponsePayload(generated.rawResponse().toString());
            draft.setReportPayload(validation.report().toString());
            draft.setErrorMessage(null);
        } catch (Exception error) {
            log.warn("Report {} used deterministic fallback: {}", reportId, safeMessage(error));
            JsonNode fallback = deterministicFallback(draft, analysis);
            draft.setStatus("FALLBACK");
            draft.setGeneratorSource("DETERMINISTIC_FALLBACK");
            draft.setModelName("parvisight-deterministic-v1");
            draft.setReportPayload(fallback.toString());
            draft.setErrorMessage(safeMessage(error));
        } finally {
            draft.setCompletedAt(LocalDateTime.now());
            reportMapper.updateById(draft);
        }
    }

    private JsonNode buildContext(AiReportDraft draft, AiAnalysisView analysis) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("requested_language", draft.getLanguage());
        root.put("case_reference", draft.getCaseId());

        ObjectNode clinician = root.putObject("clinician_review");
        clinician.put("decision", draft.getClinicianDecision());
        clinician.put("note", draft.getClinicianNote() == null ? "" : draft.getClinicianNote());

        if (draft.getSeniorDecision() != null && !draft.getSeniorDecision().isBlank()) {
            ObjectNode senior = root.putObject("senior_review");
            senior.put("decision", draft.getSeniorDecision());
            senior.put("note", draft.getSeniorNote() == null ? "" : draft.getSeniorNote());
        }

        ObjectNode model = root.putObject("model_evidence");
        model.put("model_name", analysis.modelName());
        model.put("predicted_class", analysis.predictedClass());
        putNullable(model, "slide_level_tumor_evidence", analysis.tumorEvidenceScore());
        model.put("evidence_band", analysis.evidenceBand());
        putNullable(model, "ensemble_disagreement", analysis.ensembleDisagreement());
        model.put("candidate_region_count", valueOrZero(analysis.candidateRegionCount()));
        model.put("high_evidence_region_count", valueOrZero(analysis.highRiskRegionCount()));
        putNullable(model, "candidate_area_proxy_mm2", analysis.candidateAreaProxyMm2());

        ArrayNode regions = root.putArray("candidate_regions");
        if (analysis.candidates() != null) {
            for (AiAnalysisView.CandidateRegion candidate : analysis.candidates()) {
                ObjectNode region = regions.addObject();
                region.put("region_id", candidate.regionId());
                region.put("evidence_tier", candidate.evidenceTier());
                region.put("patch_count", valueOrZero(candidate.patchCount()));
                putNullable(region, "area_proxy_mm2", candidate.areaProxyMm2());
                putNullable(region, "mean_attention_percentile", candidate.meanAttentionPercentile());
                putNullable(region, "max_attention_percentile", candidate.maxAttentionPercentile());
                putNullable(region, "mean_evidence_index", candidate.meanEvidenceIndex());
                putNullable(region, "max_evidence_index", candidate.maxEvidenceIndex());
            }
        }

        if (analysis.validation() != null && Boolean.TRUE.equals(analysis.validation().available())) {
            ObjectNode validation = root.putObject("public_annotation_compatibility");
            validation.put(
                    "high_attention_overlaps_annotation",
                    Boolean.TRUE.equals(analysis.validation().highAttentionOverlapsAnnotation())
            );
            validation.put(
                    "candidate_regions_overlap_annotation",
                    Boolean.TRUE.equals(analysis.validation().candidateRegionsOverlapAnnotation())
            );
            putNullable(validation, "annotation_coverage_proxy", analysis.validation().annotationCoverageProxy());
            putNullable(validation, "candidate_precision_proxy", analysis.validation().candidatePrecisionProxy());
            validation.put(
                    "notice",
                    "Source-domain compatibility only; not segmentation accuracy or clinical validation."
            );
        }

        ArrayNode boundaries = root.putArray("non_negotiable_boundaries");
        boundaries.add("Model evidence is not calibrated lesion probability.");
        boundaries.add("Attention is not tumor segmentation.");
        boundaries.add("Candidate area is an approximation.");
        boundaries.add("The clinician decision is authoritative and the report remains a draft.");
        return root;
    }

    private List<LlmReportClient.ImageAttachment> loadImages(AiAnalysisView analysis) {
        List<LlmReportClient.ImageAttachment> images = new ArrayList<>();
        addImage(images, analysis.id(), "model-evidence-overview", "Whole-slide model evidence overview", 1_800);
        int patchCount = Math.min(3, analysis.candidates() == null ? 0 : analysis.candidates().size());
        for (int index = 0; index < patchCount; index++) {
            AiAnalysisView.CandidateRegion candidate = analysis.candidates().get(index);
            if (candidate.evidencePatchUrl() == null) continue;
            addImage(
                    images,
                    analysis.id(),
                    "evidence-patch-" + (index + 1),
                    "Representative patch for " + candidate.regionId(),
                    768
            );
        }
        return images;
    }

    private void addImage(List<LlmReportClient.ImageAttachment> images,
                          Long analysisId,
                          String key,
                          String label,
                          int maxDimension) {
        try {
            AiAnalysisService.ArtifactPayload artifact = analysisService.getArtifact(analysisId, key);
            if (artifact.body().length > 0) {
                images.add(new LlmReportClient.ImageAttachment(label, artifact.body(), maxDimension));
            }
        } catch (RuntimeException error) {
            log.debug("Report image {} is unavailable for analysis {}", key, analysisId);
        }
    }

    private JsonNode deterministicFallback(AiReportDraft draft, AiAnalysisView analysis) {
        boolean chinese = draft.getLanguage() != null
                && draft.getLanguage().toLowerCase(Locale.ROOT).startsWith("zh");
        double score = analysis == null || analysis.tumorEvidenceScore() == null
                ? 0.0 : analysis.tumorEvidenceScore();
        int candidates = analysis == null ? 0 : valueOrZero(analysis.candidateRegionCount());
        double area = analysis == null || analysis.candidateAreaProxyMm2() == null
                ? 0.0 : analysis.candidateAreaProxyMm2();
        String decision = draft.getClinicianDecision();
        String note = draft.getClinicianNote() == null ? "" : draft.getClinicianNote().trim();

        ObjectNode report = objectMapper.createObjectNode();
        ArrayNode findings = report.putArray("key_findings");
        ArrayNode reviewSteps = report.putArray("suggested_review_steps");
        ArrayNode limitations = report.putArray("limitations");
        if (chinese) {
            report.put("impression", score >= 0.5
                    ? "全切片模型证据升高，需结合医生对候选区域的复核结论形成最终判断。"
                    : "全切片模型证据较低，当前门控下未形成可信候选病灶。"
            );
            report.put(
                    "microscopic_description",
                    String.format(Locale.ROOT,
                            "CLAM 集成模型证据为 %.1f%%，识别 %d 个候选区域，候选面积近似值合计 %.3f mm²。",
                            score * 100, candidates, area)
            );
            findings.add(String.format(Locale.ROOT, "切片级模型证据：%.1f%%。", score * 100));
            findings.add("候选区域数量：" + candidates + "。");
            if (!note.isBlank()) findings.add("医生说明：" + note);
            report.put("draft_assessment", "医生记录的处理决定为“" + decision + "”；该决定优先于模型证据。"
            );
            reviewSteps.add("在原始 WSI 上复核候选区域及其邻近组织。")
                    .add("结合临床资料和必要的辅助检查完成正式报告。")
                    .add("由病理医生审核并签发最终结论。");
            limitations.add("CLAM 注意力属于研究用途的模型证据，不是校准后的病变概率或肿瘤分割。")
                    .add("候选面积为近似值，本草稿不得替代医生诊断。");
        } else {
            report.put("impression", score >= 0.5
                    ? "Elevated whole-slide model evidence requires correlation with the clinician-reviewed candidate regions."
                    : "Whole-slide model evidence is low and no convincing candidate focus passed the configured gate."
            );
            report.put(
                    "microscopic_description",
                    String.format(Locale.ROOT,
                            "The CLAM ensemble produced %.1f%% slide-level model evidence, %d candidate region(s), and %.3f mm² total candidate area proxy.",
                            score * 100, candidates, area)
            );
            findings.add(String.format(Locale.ROOT, "Slide-level model evidence: %.1f%%.", score * 100));
            findings.add("Candidate region count: " + candidates + ".");
            if (!note.isBlank()) findings.add("Clinician note: " + note);
            report.put(
                    "draft_assessment",
                    "The recorded clinician decision is " + decision + "; that decision takes precedence over model evidence."
            );
            reviewSteps.add("Review candidate regions and adjacent tissue on the source WSI.")
                    .add("Correlate with available clinical context and ancillary studies as appropriate.")
                    .add("Have a pathologist review and finalize the diagnostic report.");
            limitations.add("CLAM attention is research-use model evidence, not calibrated lesion probability or tumor segmentation.")
                    .add("Candidate area is approximate and this draft cannot replace pathologist diagnosis.");
        }
        report.put("clinicopathologic_concordance", concordanceFor(decision));
        report.put("report_status", "DRAFT");
        report.put("clinician_review_required", true);
        return report;
    }

    private static String concordanceFor(String decision) {
        if ("reject".equals(decision)) return "discordant";
        if ("accept".equals(decision)) return "concordant";
        return "indeterminate";
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static void putNullable(ObjectNode node, String key, Double value) {
        if (value == null) node.putNull(key);
        else node.put(key, value);
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private static class InvalidReportException extends RuntimeException {
        InvalidReportException(String message) {
            super(message);
        }
    }
}
