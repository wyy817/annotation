package com.wsi.platform.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsi.platform.dto.AiAnalysisView;
import com.wsi.platform.entity.AiAnalysisRun;
import com.wsi.platform.entity.AiCandidateRegion;
import com.wsi.platform.entity.Slide;
import com.wsi.platform.mapper.AiAnalysisRunMapper;
import com.wsi.platform.mapper.AiCandidateRegionMapper;
import com.wsi.platform.mapper.SlideMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAnalysisServiceTest {

    @Mock
    private AiAnalysisRunMapper analysisRunMapper;

    @Mock
    private AiCandidateRegionMapper candidateRegionMapper;

    @Mock
    private SlideMapper slideMapper;

    @Mock
    private PythonServiceClient pythonServiceClient;

    private ObjectMapper objectMapper;
    private AiAnalysisService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new AiAnalysisService(
                analysisRunMapper,
                candidateRegionMapper,
                slideMapper,
                pythonServiceClient,
                objectMapper
        );
    }

    @Test
    void startsPythonJobAndPersistsRunIdentity() {
        Slide slide = new Slide();
        slide.setId(42L);
        slide.setOriginalPath("D:/data/tumor_001.tif");
        when(slideMapper.selectById(42L)).thenReturn(slide);

        PythonServiceClient.AiAnalysisJobResponse job =
                new PythonServiceClient.AiAnalysisJobResponse();
        job.setJobId("a".repeat(32));
        job.setStatus("queued");
        job.setStage("queued");
        job.setMessage("Waiting for GPU");
        when(pythonServiceClient.createAiAnalysis(
                eq(42L),
                eq("D:/data/tumor_001.tif"),
                eq("CASE-42"),
                eq("D:/data/tumor_001.xml"),
                eq(null)
        )).thenReturn(job);
        when(analysisRunMapper.insert(any(AiAnalysisRun.class))).thenAnswer(invocation -> {
            AiAnalysisRun run = invocation.getArgument(0);
            run.setId(100L);
            return 1;
        });
        when(candidateRegionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        AiAnalysisView view = service.startAnalysis(
                42L,
                "CASE-42",
                "D:/data/tumor_001.xml",
                null,
                7L
        );

        assertThat(view.id()).isEqualTo(100L);
        assertThat(view.status()).isEqualTo("QUEUED");
        ArgumentCaptor<AiAnalysisRun> runCaptor =
                ArgumentCaptor.forClass(AiAnalysisRun.class);
        verify(analysisRunMapper).insert(runCaptor.capture());
        assertThat(runCaptor.getValue().getPythonJobId()).isEqualTo("a".repeat(32));
        assertThat(runCaptor.getValue().getStartedBy()).isEqualTo(7L);
    }

    @Test
    void completedJobPersistsMetricsSnapshotAndCandidateRegions() throws Exception {
        AiAnalysisRun run = new AiAnalysisRun();
        run.setId(101L);
        run.setSlideId(42L);
        run.setPythonJobId("b".repeat(32));
        run.setStatus("RUNNING");
        when(analysisRunMapper.selectById(101L)).thenReturn(run);

        PythonServiceClient.AiAnalysisJobResponse job =
                new PythonServiceClient.AiAnalysisJobResponse();
        job.setJobId(run.getPythonJobId());
        job.setStatus("succeeded");
        job.setStage("complete");
        job.setStageProgress(1.0);
        job.setMessage("Analysis completed");
        job.setStartedAt("2026-07-18T08:00:00+00:00");
        job.setCompletedAt("2026-07-18T08:04:00+00:00");
        when(pythonServiceClient.getAiAnalysisJob(run.getPythonJobId()))
                .thenReturn(job);

        JsonNode result = objectMapper.readTree("""
                {
                  "status": "succeeded",
                  "model": {"name": "CAMELYON 40x CLAM 10-fold ensemble"},
                  "inference": {
                    "predicted_class": "tumor_associated_evidence",
                    "tumor_evidence_score": 0.970272,
                    "evidence_band": "high_evidence",
                    "ensemble_disagreement": 0.020626
                  },
                  "region_metrics": {
                    "candidate_region_count": 2,
                    "high_risk_region_count": 2,
                    "candidate_area_proxy_mm2": 0.096821,
                    "candidates": [
                      {
                        "region_id": "R01",
                        "evidence_tier": "high",
                        "patch_count": 19,
                        "area_proxy_mm2": 0.073584,
                        "mean_attention_percentile": 0.991,
                        "max_attention_percentile": 1.0,
                        "mean_evidence_index": 0.961648,
                        "max_evidence_index": 0.970258,
                        "bbox_level0": [70016, 131904, 72320, 134464],
                        "centroid_level0": [71168, 133184]
                      },
                      {
                        "region_id": "R02",
                        "evidence_tier": "high",
                        "patch_count": 6,
                        "area_proxy_mm2": 0.023237,
                        "mean_attention_percentile": 0.985,
                        "max_attention_percentile": 0.999,
                        "mean_evidence_index": 0.956199,
                        "max_evidence_index": 0.970208,
                        "bbox_level0": [72320, 123712, 73088, 124480],
                        "centroid_level0": [72704, 124096]
                      }
                    ]
                  },
                  "evidence_patches": [
                    {"path": "artifacts/evidence_patch_01.png"},
                    {"path": "artifacts/evidence_patch_02.png"}
                  ],
                  "artifacts": {
                    "model_evidence_overview": "artifacts/model_evidence_overview.png",
                    "relative_attention_overview": "artifacts/relative_attention_overview.png",
                    "evidence_patches": [
                      "artifacts/evidence_patch_01.png",
                      "artifacts/evidence_patch_02.png"
                    ]
                  }
                }
                """);
        when(pythonServiceClient.getAiAnalysisResult(run.getPythonJobId()))
                .thenReturn(result);

        service.refreshRun(101L);

        assertThat(run.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(run.getTumorEvidenceScore()).isEqualTo(0.970272);
        assertThat(run.getCandidateRegionCount()).isEqualTo(2);
        assertThat(run.getHighRiskRegionCount()).isEqualTo(2);
        assertThat(run.getResultPayload()).contains("tumor_associated_evidence");
        assertThat(run.getArtifacts()).contains("model_evidence_overview");
        verify(analysisRunMapper).updateById(run);

        ArgumentCaptor<AiCandidateRegion> candidateCaptor =
                ArgumentCaptor.forClass(AiCandidateRegion.class);
        verify(candidateRegionMapper, org.mockito.Mockito.times(2))
                .insert(candidateCaptor.capture());
        assertThat(candidateCaptor.getAllValues())
                .extracting(AiCandidateRegion::getRegionKey)
                .containsExactly("R01", "R02");
        assertThat(candidateCaptor.getAllValues().getFirst().getBboxLevel0())
                .isEqualTo("[70016,131904,72320,134464]");
        assertThat(candidateCaptor.getAllValues().getFirst().getRepresentativePatchPath())
                .isEqualTo("artifacts/evidence_patch_01.png");
    }
}
