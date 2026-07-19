package com.wsi.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsi.platform.dto.AiAnalysisView;
import com.wsi.platform.entity.AiReportDraft;
import com.wsi.platform.mapper.AiReportDraftMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiReportGeneratorTest {

    @Mock
    private AiReportDraftMapper reportMapper;

    @Mock
    private AiAnalysisService analysisService;

    @Mock
    private LlmReportClient llmReportClient;

    private AiReportGenerator generator;
    private AiReportDraft draft;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        generator = new AiReportGenerator(
                reportMapper,
                analysisService,
                llmReportClient,
                new AiReportContentValidator(objectMapper),
                objectMapper
        );
        draft = new AiReportDraft();
        draft.setId(55L);
        draft.setAnalysisRunId(9L);
        draft.setCaseId("PO-CASE-9");
        draft.setStatus("QUEUED");
        draft.setLanguage("en");
        draft.setClinicianDecision("accept");
        draft.setClinicianNote("Reviewed on source WSI");
    }

    @Test
    void persistsDeterministicFallbackWhenRelayFails() {
        when(reportMapper.selectById(55L)).thenReturn(draft);
        when(analysisService.getById(9L)).thenReturn(completedAnalysis());
        when(llmReportClient.generate(any(), any()))
                .thenThrow(new LlmReportClient.LlmUnavailableException("LLM relay timed out"));

        generator.generate(55L);

        assertThat(draft.getStatus()).isEqualTo("FALLBACK");
        assertThat(draft.getGeneratorSource()).isEqualTo("DETERMINISTIC_FALLBACK");
        assertThat(draft.getReportPayload())
                .contains("DRAFT")
                .contains("CLAM attention")
                .contains("Reviewed on source WSI");
        assertThat(draft.getErrorMessage()).isEqualTo("LLM relay timed out");
        verify(reportMapper, atLeast(2)).updateById(draft);
    }

    private AiAnalysisView completedAnalysis() {
        return new AiAnalysisView(
                9L, 5L, "SUCCEEDED", "complete", 1.0, "Complete",
                "CLAM ensemble", "tumor_associated_evidence", 0.970272,
                "high_evidence", 0.020626, 5, 5, 0.398901,
                null, null, null, null, List.of(), Map.of(),
                null, null, null, AiAnalysisService.CLINICAL_NOTICE
        );
    }
}
