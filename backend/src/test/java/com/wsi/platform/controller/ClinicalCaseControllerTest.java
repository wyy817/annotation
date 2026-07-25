package com.wsi.platform.controller;

import com.wsi.platform.common.Result;
import com.wsi.platform.dto.ClinicalCaseView;
import com.wsi.platform.service.CaseReportCoordinator;
import com.wsi.platform.service.ClinicalCaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The controller only asks for an AI report once the workflow actually reaches the
 * completed state, and maps the service's typed failures onto stable HTTP codes.
 */
@ExtendWith(MockitoExtension.class)
class ClinicalCaseControllerTest {

    private static final String CASE_REF = "PO-26-0001-001";

    @Mock private ClinicalCaseService caseService;
    @Mock private CaseReportCoordinator reportCoordinator;

    private ClinicalCaseController controller() {
        return new ClinicalCaseController(caseService, reportCoordinator);
    }

    @Test
    void pathologistDecisionCompletingCaseGeneratesReport() {
        when(caseService.recordPathologistDecision(CASE_REF, "accept", "Confirmed on WSI"))
                .thenReturn(viewWithStatus("completed"));

        Result<ClinicalCaseView> result = controller().decision(
                CASE_REF, new ClinicalCaseController.DecisionRequest("accept", "Confirmed on WSI", "en"));

        assertThat(result.getCode()).isEqualTo(200);
        verify(reportCoordinator).generate(CASE_REF, "en");
    }

    @Test
    void pathologistDecisionRoutingToSeniorReviewDoesNotGenerateReport() {
        when(caseService.recordPathologistDecision(CASE_REF, "escalate", "Second opinion"))
                .thenReturn(viewWithStatus("senior_review"));

        controller().decision(
                CASE_REF, new ClinicalCaseController.DecisionRequest("escalate", "Second opinion", "en"));

        verify(reportCoordinator, never()).generate(any(), any());
    }

    @Test
    void seniorApprovalGeneratesReport() {
        when(caseService.recordSeniorReview(CASE_REF, "approve", "Concur"))
                .thenReturn(viewWithStatus("completed"));

        controller().seniorReview(
                CASE_REF, new ClinicalCaseController.DecisionRequest("approve", "Concur", "en"));

        verify(reportCoordinator).generate(CASE_REF, "en");
    }

    @Test
    void seniorReturnDoesNotGenerateReport() {
        when(caseService.recordSeniorReview(CASE_REF, "return", "Resample"))
                .thenReturn(viewWithStatus("returned"));

        controller().seniorReview(
                CASE_REF, new ClinicalCaseController.DecisionRequest("return", "Resample", "en"));

        verify(reportCoordinator, never()).generate(any(), any());
    }

    @Test
    void adminCanDeleteCase() {
        Result<Void> result = controller().delete(CASE_REF);

        assertThat(result.getCode()).isEqualTo(200);
        verify(caseService).delete(CASE_REF);
    }

    @Test
    void securityExceptionMapsToForbidden() {
        when(caseService.getForCurrentUser(CASE_REF))
                .thenThrow(new SecurityException("Case is assigned to another pathologist"));

        assertThat(controller().get(CASE_REF).getCode()).isEqualTo(403);
    }

    @Test
    void illegalArgumentMapsToUnprocessable() {
        when(caseService.getForCurrentUser(CASE_REF))
                .thenThrow(new IllegalArgumentException("Case not found"));

        assertThat(controller().get(CASE_REF).getCode()).isEqualTo(422);
    }

    @Test
    void illegalStateMapsToConflict() {
        when(caseService.getForCurrentUser(CASE_REF))
                .thenThrow(new IllegalStateException("Case must be completed before report generation"));

        assertThat(controller().get(CASE_REF).getCode()).isEqualTo(409);
    }

    private ClinicalCaseView viewWithStatus(String status) {
        return new ClinicalCaseView(
                100L, CASE_REF, "ACC-1", "BREAST_LYMPH_NODE_HE", 57,
                new ClinicalCaseView.Specimen("Axillary node", "po.disease.axillaryNode", "H&E", 1),
                null, null, "Dr Owner", 10L, 5L, "tumor_001.tif", "READY",
                new ClinicalCaseView.AiSummary(0.4, 0.98, List.of(), "backend", 4L, "SUCCEEDED", null),
                new ClinicalCaseView.Workflow(status, true, List.of(), false, null, null, null),
                new ClinicalCaseView.Feedback(true, "queued", "v1"),
                List.of());
    }
}
