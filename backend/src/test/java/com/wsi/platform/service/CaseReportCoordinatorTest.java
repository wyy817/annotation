package com.wsi.platform.service;

import com.wsi.platform.dto.AiAnalysisView;
import com.wsi.platform.dto.ClinicalCaseView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The report coordinator must only generate after a case is completed, and it must
 * carry both the pathologist decision and any senior-review context into the report.
 */
@ExtendWith(MockitoExtension.class)
class CaseReportCoordinatorTest {

    private static final String CASE_REF = "PO-26-0001-001";

    @Mock private ClinicalCaseService caseService;
    @Mock private AiAnalysisService analysisService;
    @Mock private AiReportService reportService;

    private CaseReportCoordinator coordinator() {
        return new CaseReportCoordinator(caseService, analysisService, reportService);
    }

    @Test
    void reportCarriesPathologistAndSeniorContext() {
        ClinicalCaseView.Decision decision = new ClinicalCaseView.Decision(
                "escalate", "Requesting a second opinion", "Dr Path", "ANNOTATOR", null);
        ClinicalCaseView.SeniorReview senior = new ClinicalCaseView.SeniorReview(
                "approved", "Dr Senior", null, "Concur with metastasis");
        when(caseService.getCompletedForReport(CASE_REF)).thenReturn(view(decision, senior));
        when(analysisService.getLatestSuccessfulForCase(CASE_REF)).thenReturn(analysis());

        coordinator().generate(CASE_REF, "en");

        verify(reportService).create(
                eq(4L), eq(CASE_REF), eq("en"),
                eq("escalate"), eq("Requesting a second opinion"),
                eq("approved"), eq("Concur with metastasis"),
                isNull());
    }

    @Test
    void reportOmitsSeniorContextForDirectCompletion() {
        ClinicalCaseView.Decision decision = new ClinicalCaseView.Decision(
                "accept", "Confirmed on WSI", "Dr Path", "ANNOTATOR", null);
        when(caseService.getCompletedForReport(CASE_REF)).thenReturn(view(decision, null));
        when(analysisService.getLatestSuccessfulForCase(CASE_REF)).thenReturn(analysis());

        coordinator().generate(CASE_REF, "en");

        verify(reportService).create(
                eq(4L), eq(CASE_REF), eq("en"),
                eq("accept"), eq("Confirmed on WSI"),
                isNull(), isNull(), isNull());
    }

    @Test
    void generationDelegatesCompletionGateToCaseService() {
        when(caseService.getCompletedForReport(CASE_REF))
                .thenThrow(new IllegalStateException("Case must be completed before report generation"));

        assertThatThrownBy(() -> coordinator().generate(CASE_REF, "en"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completed");
    }

    @Test
    void generationFailsWhenModelEvidenceMissing() {
        ClinicalCaseView.Decision decision = new ClinicalCaseView.Decision(
                "accept", "Confirmed", "Dr Path", "ANNOTATOR", null);
        when(caseService.getCompletedForReport(CASE_REF)).thenReturn(view(decision, null));
        when(analysisService.getLatestSuccessfulForCase(CASE_REF)).thenReturn(null);

        assertThatThrownBy(() -> coordinator().generate(CASE_REF, "en"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model evidence");
    }

    @Test
    void generationFailsWhenPathologistDecisionMissing() {
        when(caseService.getCompletedForReport(CASE_REF)).thenReturn(view(null, null));
        when(analysisService.getLatestSuccessfulForCase(CASE_REF)).thenReturn(analysis());

        assertThatThrownBy(() -> coordinator().generate(CASE_REF, "en"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decision");
    }

    private ClinicalCaseView view(
            ClinicalCaseView.Decision decision, ClinicalCaseView.SeniorReview senior) {
        return new ClinicalCaseView(
                100L, CASE_REF, "ACC-1", "BREAST_LYMPH_NODE_HE", 57,
                new ClinicalCaseView.Specimen("Axillary node", "po.disease.axillaryNode", "H&E", 1),
                null, null, "Dr Owner", 10L, 5L, "tumor_001.tif", "READY",
                new ClinicalCaseView.AiSummary(0.4, 0.98, List.of(), "backend", 4L, "SUCCEEDED", null),
                new ClinicalCaseView.Workflow(
                        "completed", true, List.of(), false, decision, senior, null),
                new ClinicalCaseView.Feedback(true, "queued", "v1"),
                List.of());
    }

    private AiAnalysisView analysis() {
        return new AiAnalysisView(
                4L, 5L, "SUCCEEDED", "complete", 1.0, "Complete",
                "CLAM ensemble", "tumor_associated_evidence", 0.97,
                "high_evidence", 0.02, 5, 5, 0.399,
                null, null, null, null, List.of(), Map.of(),
                null, null, null, AiAnalysisService.CLINICAL_NOTICE);
    }
}
