package com.wsi.platform.service;

import com.wsi.platform.dto.AiAnalysisView;
import com.wsi.platform.dto.AiReportView;
import com.wsi.platform.dto.ClinicalCaseView;
import com.wsi.platform.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CaseReportCoordinator {

    private final ClinicalCaseService caseService;
    private final AiAnalysisService analysisService;
    private final AiReportService reportService;

    public AiReportView generate(String caseReference, String language) {
        ClinicalCaseView item = caseService.getCompletedForReport(caseReference);
        AiAnalysisView analysis = analysisService.getLatestSuccessfulForCase(caseReference);
        if (analysis == null) throw new IllegalStateException("Completed model evidence is unavailable");
        ClinicalCaseView.Decision decision = item.workflow().decision();
        if (decision == null) throw new IllegalStateException("Pathologist decision is unavailable");
        ClinicalCaseView.SeniorReview senior = item.workflow().seniorReview();
        UserContext.UserInfo user = UserContext.get();
        return reportService.create(
                analysis.id(), caseReference, language,
                decision.type(), decision.note(),
                senior == null ? null : senior.status(),
                senior == null ? null : senior.note(),
                user == null ? null : user.id()
        );
    }
}
