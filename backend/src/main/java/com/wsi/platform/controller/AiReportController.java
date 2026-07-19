package com.wsi.platform.controller;

import com.wsi.platform.common.Result;
import com.wsi.platform.dto.AiReportView;
import com.wsi.platform.security.UserContext;
import com.wsi.platform.service.AiReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class AiReportController {

    private final AiReportService reportService;

    public record GenerateReportRequest(
            @NotBlank @Size(max = 128) String caseId,
            @Size(max = 16) String language,
            @NotBlank @Size(max = 32) String clinicianDecision,
            @Size(max = 4_000) String clinicianNote
    ) {
    }

    @PostMapping("/ai-analyses/{analysisId}/reports")
    public Result<AiReportView> create(
            @PathVariable Long analysisId,
            @Valid @RequestBody GenerateReportRequest request) {
        try {
            UserContext.UserInfo user = UserContext.get();
            return Result.success(reportService.create(
                    analysisId,
                    request.caseId(),
                    request.language(),
                    request.clinicianDecision(),
                    request.clinicianNote(),
                    user == null ? null : user.id()
            ));
        } catch (IllegalArgumentException e) {
            return Result.error(422, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(409, e.getMessage());
        } catch (Exception e) {
            log.error("Could not create report draft for analysis {}", analysisId, e);
            return Result.error(503, "Report generation is unavailable");
        }
    }

    @GetMapping("/ai-reports/{reportId}")
    public Result<AiReportView> get(@PathVariable Long reportId) {
        AiReportView report = reportService.getById(reportId);
        return report == null ? Result.error(404, "Report not found") : Result.success(report);
    }

    @GetMapping("/ai-analyses/{analysisId}/reports/latest")
    public Result<AiReportView> latest(
            @PathVariable Long analysisId,
            @RequestParam String caseId) {
        try {
            return Result.success(reportService.getLatest(analysisId, caseId));
        } catch (IllegalArgumentException e) {
            return Result.error(422, e.getMessage());
        }
    }
}
