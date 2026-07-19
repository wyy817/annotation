package com.wsi.platform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiAnalysisPoller {

    private final AiAnalysisService aiAnalysisService;
    private final ClinicalCaseService clinicalCaseService;

    @Scheduled(fixedDelayString = "${wsi.ai-analysis.poll-interval-ms:2000}")
    public void refreshActiveAnalyses() {
        for (Long runId : aiAnalysisService.listActiveRunIds()) {
            try {
                aiAnalysisService.refreshRun(runId);
                clinicalCaseService.synchronizeAnalysisTriggers(runId);
            } catch (Exception e) {
                log.warn("Could not refresh AI analysis run {}: {}", runId, e.getMessage());
            }
        }
    }
}
