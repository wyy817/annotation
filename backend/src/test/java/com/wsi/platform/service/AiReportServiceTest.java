package com.wsi.platform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsi.platform.dto.AiAnalysisView;
import com.wsi.platform.dto.AiReportView;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiReportServiceTest {

    @Mock
    private AiReportDraftMapper reportMapper;

    @Mock
    private AiAnalysisService analysisService;

    @Mock
    private AiReportGenerator reportGenerator;

    private AiReportService service;

    @BeforeEach
    void setUp() {
        service = new AiReportService(
                reportMapper, analysisService, reportGenerator, new ObjectMapper()
        );
    }

    @Test
    void queuesReportForCompletedAnalysis() {
        when(analysisService.getById(9L)).thenReturn(completedAnalysis());
        when(reportMapper.insert(any(AiReportDraft.class))).thenAnswer(invocation -> {
            AiReportDraft draft = invocation.getArgument(0);
            draft.setId(44L);
            return 1;
        });

        AiReportView view = service.create(
                9L, "PO-CASE-9", "en", "accept", "Reviewed on WSI", 2L
        );

        assertThat(view.id()).isEqualTo(44L);
        assertThat(view.status()).isEqualTo("QUEUED");
        assertThat(view.clinicianDecision()).isEqualTo("accept");
        verify(reportGenerator).generate(44L);
    }

    @Test
    void requiresClinicalReasonForRejectDecision() {
        when(analysisService.getById(9L)).thenReturn(completedAnalysis());

        assertThatThrownBy(() -> service.create(
                9L, "PO-CASE-9", "en", "reject", "", 2L
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("note is required");
    }

    @Test
    void reusesMatchingActiveReportInsteadOfSubmittingDuplicate() {
        when(analysisService.getById(9L)).thenReturn(completedAnalysis());
        AiReportDraft active = new AiReportDraft();
        active.setId(45L);
        active.setAnalysisRunId(9L);
        active.setCaseId("PO-CASE-9");
        active.setStatus("RUNNING");
        active.setLanguage("en");
        active.setClinicianDecision("accept");
        active.setClinicianNote("Reviewed on WSI");
        when(reportMapper.selectOne(any())).thenReturn(active);

        AiReportView view = service.create(
                9L, "PO-CASE-9", "en", "accept", "Reviewed on WSI", 2L
        );

        assertThat(view.id()).isEqualTo(45L);
        assertThat(view.status()).isEqualTo("RUNNING");
    }

    private AiAnalysisView completedAnalysis() {
        return new AiAnalysisView(
                9L, 5L, "SUCCEEDED", "complete", 1.0, "Complete",
                "CLAM ensemble", "tumor_associated_evidence", 0.97,
                "high_evidence", 0.02, 5, 5, 0.399,
                null, null, null, null, List.of(), Map.of(),
                null, null, null, AiAnalysisService.CLINICAL_NOTICE
        );
    }
}
