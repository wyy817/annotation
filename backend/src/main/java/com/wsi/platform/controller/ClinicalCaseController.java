package com.wsi.platform.controller;

import com.wsi.platform.common.Result;
import com.wsi.platform.dto.AiAnalysisView;
import com.wsi.platform.dto.AiReportView;
import com.wsi.platform.dto.CaseCreationOptionsView;
import com.wsi.platform.dto.ClinicalCaseView;
import com.wsi.platform.service.CaseReportCoordinator;
import com.wsi.platform.service.ClinicalCaseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cases")
public class ClinicalCaseController {

    private final ClinicalCaseService caseService;
    private final CaseReportCoordinator reportCoordinator;

    public record CreateRequest(
            @NotBlank @Size(max = 128) String accessionReference,
            @NotNull Long primarySlideId,
            @Size(max = 64) String diseaseProfileId,
            @NotBlank @Size(max = 128) String specimenName,
            @NotNull @Min(0) @Max(120) Integer patientAge,
            @NotNull LocalDateTime receivedAt,
            @NotNull LocalDateTime dueAt,
            @NotNull Long assignedPathologistId
    ) {}

    public record DecisionRequest(
            @NotBlank @Size(max = 32) String type,
            @Size(max = 4_000) String note,
            @Size(max = 16) String language
    ) {}

    public record ReportRequest(@Size(max = 16) String language) {}

    @GetMapping
    public Result<List<ClinicalCaseView>> list() {
        return execute(caseService::listForCurrentUser);
    }

    @GetMapping("/{reference}")
    public Result<ClinicalCaseView> get(@PathVariable String reference) {
        return execute(() -> caseService.getForCurrentUser(reference));
    }

    @GetMapping("/creation/options")
    public Result<CaseCreationOptionsView> creationOptions() {
        return execute(caseService::creationOptions);
    }

    @PostMapping
    public Result<ClinicalCaseView> create(@Valid @RequestBody CreateRequest request) {
        return execute(() -> caseService.create(new ClinicalCaseService.CreateCommand(
                request.accessionReference(), request.primarySlideId(), request.diseaseProfileId(),
                request.specimenName(), request.patientAge(), request.receivedAt(), request.dueAt(),
                request.assignedPathologistId()
        )));
    }

    @PostMapping("/{reference}/ai-analysis")
    public Result<AiAnalysisView> startAnalysis(@PathVariable String reference) {
        return execute(() -> caseService.startAnalysis(reference));
    }

    @GetMapping("/{reference}/ai-analysis/latest")
    public Result<AiAnalysisView> latestAnalysis(@PathVariable String reference) {
        return execute(() -> caseService.latestAnalysis(reference));
    }

    @PostMapping("/{reference}/review/start")
    public Result<ClinicalCaseView> startReview(@PathVariable String reference) {
        return execute(() -> caseService.startReview(reference));
    }

    @PostMapping("/{reference}/decision")
    public Result<ClinicalCaseView> decision(
            @PathVariable String reference,
            @Valid @RequestBody DecisionRequest request) {
        return execute(() -> {
            ClinicalCaseView item = caseService.recordPathologistDecision(
                    reference, request.type(), request.note());
            if ("completed".equals(item.workflow().status())) {
                reportCoordinator.generate(reference, request.language());
            }
            return item;
        });
    }

    @PostMapping("/{reference}/senior-review")
    public Result<ClinicalCaseView> seniorReview(
            @PathVariable String reference,
            @Valid @RequestBody DecisionRequest request) {
        return execute(() -> {
            ClinicalCaseView item = caseService.recordSeniorReview(
                    reference, request.type(), request.note());
            if ("completed".equals(item.workflow().status())) {
                reportCoordinator.generate(reference, request.language());
            }
            return item;
        });
    }

    @PostMapping("/{reference}/report")
    public Result<AiReportView> report(
            @PathVariable String reference,
            @RequestBody(required = false) ReportRequest request) {
        return execute(() -> reportCoordinator.generate(
                reference, request == null ? null : request.language()));
    }

    @PostMapping("/{reference}/feedback/validate")
    public Result<ClinicalCaseView> validateFeedback(@PathVariable String reference) {
        return execute(() -> caseService.validateFeedback(reference));
    }

    private <T> Result<T> execute(Operation<T> operation) {
        try {
            return Result.success(operation.run());
        } catch (SecurityException e) {
            return Result.error(403, e.getMessage());
        } catch (IllegalArgumentException e) {
            return Result.error(422, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.error(409, e.getMessage());
        }
    }

    @FunctionalInterface
    private interface Operation<T> {
        T run();
    }
}
