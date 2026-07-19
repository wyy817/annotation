package com.wsi.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsi.platform.dto.AiAnalysisView;
import com.wsi.platform.entity.AiAnalysisRun;
import com.wsi.platform.entity.AiCandidateRegion;
import com.wsi.platform.entity.Slide;
import com.wsi.platform.mapper.AiAnalysisRunMapper;
import com.wsi.platform.mapper.AiCandidateRegionMapper;
import com.wsi.platform.mapper.SlideMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisService {

    public static final String CLINICAL_NOTICE =
            "Research prototype only. Model evidence and candidate areas are not "
                    + "calibrated clinical probabilities or tumor segmentation. "
                    + "A pathologist must make the final decision.";

    private static final Set<String> ACTIVE_STATUSES = Set.of("QUEUED", "RUNNING");

    private final AiAnalysisRunMapper analysisRunMapper;
    private final AiCandidateRegionMapper candidateRegionMapper;
    private final SlideMapper slideMapper;
    private final PythonServiceClient pythonServiceClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public AiAnalysisView startAnalysis(Long slideId,
                                        String caseId,
                                        String annotationXml,
                                        String patchH5,
                                        Long startedBy) {
        Slide slide = slideMapper.selectById(slideId);
        if (slide == null) {
            throw new IllegalArgumentException("Slide not found: " + slideId);
        }
        if (slide.getOriginalPath() == null || slide.getOriginalPath().isBlank()) {
            throw new IllegalStateException("Slide has no readable source path");
        }

        PythonServiceClient.AiAnalysisJobResponse job =
                pythonServiceClient.createAiAnalysis(
                        slideId,
                        slide.getOriginalPath(),
                        caseId,
                        annotationXml,
                        patchH5
                );
        if (job == null || job.getJobId() == null || job.getJobId().isBlank()) {
            throw new IllegalStateException("Python service returned no analysis job ID");
        }

        AiAnalysisRun run = new AiAnalysisRun();
        run.setSlideId(slideId);
        run.setCaseReference(caseId);
        run.setPythonJobId(job.getJobId());
        run.setStatus(normalizeStatus(job.getStatus()));
        run.setStage(job.getStage());
        run.setStageProgress(job.getStageProgress());
        run.setMessage(job.getMessage());
        run.setStartedBy(startedBy);
        run.setStartedAt(parseTimestamp(job.getStartedAt()));
        analysisRunMapper.insert(run);
        return toView(run);
    }

    public AiAnalysisView getById(Long analysisId) {
        AiAnalysisRun run = analysisRunMapper.selectById(analysisId);
        return run == null ? null : toView(run);
    }

    public AiAnalysisView getLatestForSlide(Long slideId) {
        AiAnalysisRun run = analysisRunMapper.selectOne(
                new LambdaQueryWrapper<AiAnalysisRun>()
                        .eq(AiAnalysisRun::getSlideId, slideId)
                        .orderByDesc(AiAnalysisRun::getCreatedAt)
                        .last("LIMIT 1")
        );
        return run == null ? null : toView(run);
    }

    public AiAnalysisView getLatestSuccessfulForCase(String caseReference) {
        AiAnalysisRun run = analysisRunMapper.selectOne(
                new LambdaQueryWrapper<AiAnalysisRun>()
                        .eq(AiAnalysisRun::getCaseReference, caseReference)
                        .eq(AiAnalysisRun::getStatus, "SUCCEEDED")
                        .orderByDesc(AiAnalysisRun::getCreatedAt)
                        .last("LIMIT 1")
        );
        return run == null ? null : toView(run);
    }

    public List<Long> listActiveRunIds() {
        return analysisRunMapper.selectList(
                        new LambdaQueryWrapper<AiAnalysisRun>()
                                .in(AiAnalysisRun::getStatus, ACTIVE_STATUSES)
                                .orderByAsc(AiAnalysisRun::getCreatedAt)
                                .last("LIMIT 20")
                ).stream()
                .map(AiAnalysisRun::getId)
                .toList();
    }

    @Transactional
    public void refreshRun(Long analysisId) {
        AiAnalysisRun run = analysisRunMapper.selectById(analysisId);
        if (run == null || !ACTIVE_STATUSES.contains(run.getStatus())) return;

        PythonServiceClient.AiAnalysisJobResponse job =
                pythonServiceClient.getAiAnalysisJob(run.getPythonJobId());
        if (job == null) return;

        run.setStatus(normalizeStatus(job.getStatus()));
        run.setStage(job.getStage());
        run.setStageProgress(job.getStageProgress());
        run.setMessage(job.getMessage());
        run.setStartedAt(firstNonNull(run.getStartedAt(), parseTimestamp(job.getStartedAt())));
        run.setCompletedAt(parseTimestamp(job.getCompletedAt()));
        run.setErrorMessage(job.getError());

        if ("SUCCEEDED".equals(run.getStatus())) {
            JsonNode result = pythonServiceClient.getAiAnalysisResult(run.getPythonJobId());
            if (result == null || !"succeeded".equals(result.path("status").asText())) {
                throw new IllegalStateException("Python analysis result is incomplete");
            }
            persistCompletedResult(run, result);
        }
        analysisRunMapper.updateById(run);
    }

    public ArtifactPayload getArtifact(Long analysisId, String artifactKey) {
        AiAnalysisRun run = analysisRunMapper.selectById(analysisId);
        if (run == null) throw new IllegalArgumentException("Analysis not found");
        if (!"SUCCEEDED".equals(run.getStatus()) || run.getArtifacts() == null) {
            throw new IllegalStateException("Analysis artifacts are not available");
        }
        String relativePath = resolveArtifactPath(run.getArtifacts(), artifactKey);
        ResponseEntity<byte[]> response = pythonServiceClient.getAiAnalysisArtifact(
                run.getPythonJobId(), relativePath
        );
        MediaType contentType = response.getHeaders().getContentType();
        return new ArtifactPayload(
                response.getBody() == null ? new byte[0] : response.getBody(),
                contentType == null ? MediaType.APPLICATION_OCTET_STREAM : contentType
        );
    }

    private void persistCompletedResult(AiAnalysisRun run, JsonNode result) {
        JsonNode inference = result.path("inference");
        JsonNode metrics = result.path("region_metrics");
        JsonNode model = result.path("model");
        JsonNode artifacts = result.path("artifacts");

        run.setModelName(textOrNull(model, "name"));
        run.setPredictedClass(textOrNull(inference, "predicted_class"));
        run.setTumorEvidenceScore(doubleOrNull(inference, "tumor_evidence_score"));
        run.setEvidenceBand(textOrNull(inference, "evidence_band"));
        run.setEnsembleDisagreement(doubleOrNull(inference, "ensemble_disagreement"));
        run.setCandidateRegionCount(integerOrNull(metrics, "candidate_region_count"));
        run.setHighRiskRegionCount(integerOrNull(metrics, "high_risk_region_count"));
        run.setCandidateAreaProxyMm2(doubleOrNull(metrics, "candidate_area_proxy_mm2"));
        run.setResultPayload(result.toString());
        run.setArtifacts(artifacts.toString());

        candidateRegionMapper.delete(
                new LambdaQueryWrapper<AiCandidateRegion>()
                        .eq(AiCandidateRegion::getAnalysisRunId, run.getId())
        );
        JsonNode candidates = metrics.path("candidates");
        JsonNode evidencePatches = result.path("evidence_patches");
        if (!candidates.isArray()) return;
        for (int index = 0; index < candidates.size(); index++) {
            JsonNode candidateNode = candidates.get(index);
            AiCandidateRegion candidate = new AiCandidateRegion();
            candidate.setAnalysisRunId(run.getId());
            candidate.setRegionKey(textOrNull(candidateNode, "region_id"));
            candidate.setEvidenceTier(textOrNull(candidateNode, "evidence_tier"));
            candidate.setPatchCount(integerOrNull(candidateNode, "patch_count"));
            candidate.setAreaProxyMm2(doubleOrNull(candidateNode, "area_proxy_mm2"));
            candidate.setMeanAttentionPercentile(
                    doubleOrNull(candidateNode, "mean_attention_percentile")
            );
            candidate.setMaxAttentionPercentile(
                    doubleOrNull(candidateNode, "max_attention_percentile")
            );
            candidate.setMeanEvidenceIndex(
                    doubleOrNull(candidateNode, "mean_evidence_index")
            );
            candidate.setMaxEvidenceIndex(
                    doubleOrNull(candidateNode, "max_evidence_index")
            );
            candidate.setBboxLevel0(candidateNode.path("bbox_level0").toString());
            candidate.setCentroidLevel0(candidateNode.path("centroid_level0").toString());
            if (index < evidencePatches.size()) {
                candidate.setRepresentativePatchPath(
                        textOrNull(evidencePatches.get(index), "path")
                );
            }
            candidateRegionMapper.insert(candidate);
        }
    }

    private AiAnalysisView toView(AiAnalysisRun run) {
        JsonNode result = parseJson(run.getResultPayload());
        List<AiCandidateRegion> candidates = candidateRegionMapper.selectList(
                new LambdaQueryWrapper<AiCandidateRegion>()
                        .eq(AiCandidateRegion::getAnalysisRunId, run.getId())
                        .orderByAsc(AiCandidateRegion::getRegionKey)
        );
        List<AiAnalysisView.CandidateRegion> candidateViews = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            AiCandidateRegion candidate = candidates.get(index);
            String patchUrl = candidate.getRepresentativePatchPath() == null
                    ? null
                    : artifactUrl(run.getId(), "evidence-patch-" + (index + 1));
            candidateViews.add(new AiAnalysisView.CandidateRegion(
                    candidate.getId(),
                    candidate.getRegionKey(),
                    candidate.getEvidenceTier(),
                    candidate.getPatchCount(),
                    candidate.getAreaProxyMm2(),
                    candidate.getMeanAttentionPercentile(),
                    candidate.getMaxAttentionPercentile(),
                    candidate.getMeanEvidenceIndex(),
                    candidate.getMaxEvidenceIndex(),
                    parseJson(candidate.getBboxLevel0()),
                    parseJson(candidate.getCentroidLevel0()),
                    patchUrl
            ));
        }

        return new AiAnalysisView(
                run.getId(),
                run.getSlideId(),
                run.getStatus(),
                run.getStage(),
                run.getStageProgress(),
                run.getMessage(),
                run.getModelName(),
                run.getPredictedClass(),
                run.getTumorEvidenceScore(),
                run.getEvidenceBand(),
                run.getEnsembleDisagreement(),
                run.getCandidateRegionCount(),
                run.getHighRiskRegionCount(),
                run.getCandidateAreaProxyMm2(),
                run.getErrorMessage(),
                run.getStartedAt(),
                run.getCompletedAt(),
                run.getCreatedAt(),
                candidateViews,
                artifactUrls(run),
                slideSummary(result.path("slide")),
                validationSummary(result.path("annotation_overlap")),
                runtimeSummary(result.path("runtime")),
                CLINICAL_NOTICE
        );
    }

    private AiAnalysisView.SlideSummary slideSummary(JsonNode slide) {
        if (slide.isMissingNode() || slide.isEmpty()) return null;
        JsonNode dimensions = slide.path("dimensions_level0");
        JsonNode mpp = slide.path("mpp");
        return new AiAnalysisView.SlideSummary(
                textOrNull(slide, "file_name"),
                arrayIntegerOrNull(dimensions, 0),
                arrayIntegerOrNull(dimensions, 1),
                integerOrNull(slide, "patch_count"),
                arrayDoubleOrNull(mpp, 0),
                arrayDoubleOrNull(mpp, 1)
        );
    }

    private AiAnalysisView.ValidationSummary validationSummary(JsonNode validation) {
        if (validation.isMissingNode() || validation.isEmpty()) return null;
        return new AiAnalysisView.ValidationSummary(
                booleanOrNull(validation, "available"),
                booleanOrNull(validation, "high_attention_overlaps_annotation"),
                booleanOrNull(validation, "candidate_regions_overlap_annotation"),
                doubleOrNull(validation, "high_attention_precision_proxy"),
                doubleOrNull(validation, "annotation_coverage_proxy"),
                doubleOrNull(validation, "candidate_precision_proxy"),
                textOrNull(validation, "notice")
        );
    }

    private AiAnalysisView.RuntimeSummary runtimeSummary(JsonNode runtime) {
        if (runtime.isMissingNode() || runtime.isEmpty()) return null;
        return new AiAnalysisView.RuntimeSummary(
                textOrNull(runtime, "device"),
                doubleOrNull(runtime.path("timings_seconds"), "total")
        );
    }

    private Map<String, String> artifactUrls(AiAnalysisRun run) {
        Map<String, String> urls = new LinkedHashMap<>();
        if (!"SUCCEEDED".equals(run.getStatus()) || run.getArtifacts() == null) {
            return urls;
        }
        JsonNode artifacts = parseJson(run.getArtifacts());
        if (artifacts.hasNonNull("model_evidence_overview")) {
            urls.put("modelEvidenceOverview", artifactUrl(run.getId(), "model-evidence-overview"));
        }
        if (artifacts.hasNonNull("relative_attention_overview")) {
            urls.put("relativeAttentionOverview", artifactUrl(run.getId(), "relative-attention-overview"));
        }
        JsonNode patches = artifacts.path("evidence_patches");
        for (int index = 0; index < patches.size(); index++) {
            urls.put(
                    "evidencePatch" + (index + 1),
                    artifactUrl(run.getId(), "evidence-patch-" + (index + 1))
            );
        }
        return urls;
    }

    private String resolveArtifactPath(String artifactJson, String artifactKey) {
        JsonNode artifacts = parseJson(artifactJson);
        return switch (artifactKey) {
            case "model-evidence-overview" -> requiredText(
                    artifacts, "model_evidence_overview"
            );
            case "relative-attention-overview" -> requiredText(
                    artifacts, "relative_attention_overview"
            );
            default -> {
                if (!artifactKey.matches("evidence-patch-[1-3]")) {
                    throw new IllegalArgumentException("Unknown artifact key");
                }
                int index = Integer.parseInt(artifactKey.substring(15)) - 1;
                JsonNode patches = artifacts.path("evidence_patches");
                if (!patches.isArray() || index >= patches.size()) {
                    throw new IllegalArgumentException("Artifact not found");
                }
                yield patches.get(index).asText();
            }
        };
    }

    private String artifactUrl(Long runId, String key) {
        return "/api/ai-analyses/" + runId + "/artifacts/" + key;
    }

    private JsonNode parseJson(String value) {
        if (value == null || value.isBlank()) return objectMapper.createObjectNode();
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored AI result contains invalid JSON", e);
        }
    }

    private static String normalizeStatus(String status) {
        return status == null ? "QUEUED" : status.toUpperCase(Locale.ROOT);
    }

    private static LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) return null;
        return OffsetDateTime.parse(timestamp).toLocalDateTime();
    }

    private static LocalDateTime firstNonNull(LocalDateTime first, LocalDateTime second) {
        return first != null ? first : second;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String requiredText(JsonNode node, String field) {
        String value = textOrNull(node, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Artifact not found");
        }
        return value;
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asDouble();
    }

    private static Integer integerOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private static Boolean booleanOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    private static Integer arrayIntegerOrNull(JsonNode node, int index) {
        return node.isArray() && index < node.size() && !node.get(index).isNull()
                ? node.get(index).asInt()
                : null;
    }

    private static Double arrayDoubleOrNull(JsonNode node, int index) {
        return node.isArray() && index < node.size() && !node.get(index).isNull()
                ? node.get(index).asDouble()
                : null;
    }

    public record ArtifactPayload(byte[] body, MediaType contentType) {
    }
}
