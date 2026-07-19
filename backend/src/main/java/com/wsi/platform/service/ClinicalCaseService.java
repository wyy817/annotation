package com.wsi.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wsi.platform.config.CaseWorkflowProperties;
import com.wsi.platform.dto.AiAnalysisView;
import com.wsi.platform.dto.CaseCreationOptionsView;
import com.wsi.platform.dto.ClinicalCaseView;
import com.wsi.platform.entity.AiAnalysisRun;
import com.wsi.platform.entity.ClinicalCase;
import com.wsi.platform.entity.ClinicalCaseAuditEvent;
import com.wsi.platform.entity.ClinicalCaseDecision;
import com.wsi.platform.entity.ClinicalCaseReviewTrigger;
import com.wsi.platform.entity.ClinicalCaseSlide;
import com.wsi.platform.entity.Slide;
import com.wsi.platform.entity.User;
import com.wsi.platform.mapper.AiAnalysisRunMapper;
import com.wsi.platform.mapper.ClinicalCaseAuditEventMapper;
import com.wsi.platform.mapper.ClinicalCaseDecisionMapper;
import com.wsi.platform.mapper.ClinicalCaseMapper;
import com.wsi.platform.mapper.ClinicalCaseReviewTriggerMapper;
import com.wsi.platform.mapper.ClinicalCaseSlideMapper;
import com.wsi.platform.mapper.SlideMapper;
import com.wsi.platform.mapper.UserMapper;
import com.wsi.platform.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ClinicalCaseService {

    public static final String DISEASE_PROFILE = "BREAST_LYMPH_NODE_HE";
    public static final String SPECIMEN_SITE_KEY = "po.disease.axillaryNode";
    public static final String STAIN = "H&E";

    private static final Set<String> PATHOLOGIST_DECISIONS =
            Set.of("accept", "modify", "reject", "escalate");
    private static final Set<String> PATHOLOGIST_NOTE_REQUIRED =
            Set.of("modify", "reject", "escalate");
    private static final Set<String> SENIOR_DECISIONS = Set.of("approve", "return");
    private static final Set<String> SUPPORTED_WSI_FORMATS = Set.of(
            "SVS", "NDPI", "TIFF", "TIF", "MRXS", "SCN", "BIF", "VMS", "VMU");

    private final ClinicalCaseMapper caseMapper;
    private final ClinicalCaseSlideMapper caseSlideMapper;
    private final ClinicalCaseDecisionMapper decisionMapper;
    private final ClinicalCaseReviewTriggerMapper triggerMapper;
    private final ClinicalCaseAuditEventMapper auditMapper;
    private final SlideMapper slideMapper;
    private final UserMapper userMapper;
    private final AiAnalysisRunMapper analysisRunMapper;
    private final AiAnalysisService analysisService;
    private final AiReportService reportService;
    private final CaseWorkflowProperties workflowProperties;

    public record CreateCommand(
            String accessionReference,
            Long primarySlideId,
            String diseaseProfileId,
            String specimenName,
            Integer patientAge,
            LocalDateTime receivedAt,
            LocalDateTime dueAt,
            Long assignedPathologistId
    ) {}

    public List<ClinicalCaseView> listForCurrentUser() {
        UserContext.UserInfo user = requireUser();
        LambdaQueryWrapper<ClinicalCase> query = new LambdaQueryWrapper<ClinicalCase>()
                .orderByAsc(ClinicalCase::getDueAt);
        if ("ANNOTATOR".equals(user.role())) {
            query.eq(ClinicalCase::getAssignedPathologistId, user.id());
        } else if ("REVIEWER".equals(user.role())) {
            query.eq(ClinicalCase::getStatus, "SENIOR_REVIEW");
        } else {
            requireAnyRole(user, "AI_DATA_ADMIN", "SYSTEM_ADMIN");
        }
        return caseMapper.selectList(query).stream().map(this::toView).toList();
    }

    public ClinicalCaseView getForCurrentUser(String reference) {
        ClinicalCase item = requireCase(reference);
        UserContext.UserInfo user = requireUser();
        if ("ANNOTATOR".equals(user.role())
                && !user.id().equals(item.getAssignedPathologistId())) {
            throw new SecurityException("Case is assigned to another pathologist");
        }
        if ("REVIEWER".equals(user.role()) && !isReviewerVisible(item, user.id())) {
            throw new SecurityException("Case is not in this reviewer's scope");
        }
        return toView(item);
    }

    public CaseCreationOptionsView creationOptions() {
        requireAnyRole(requireUser(), "AI_DATA_ADMIN", "SYSTEM_ADMIN");
        List<CaseCreationOptionsView.SlideOption> slides = slideMapper.selectList(
                new LambdaQueryWrapper<Slide>()
                        .eq(Slide::getStatus, "READY")
                        .in(Slide::getFormat, SUPPORTED_WSI_FORMATS)
                        .notInSql(Slide::getId, "SELECT slide_id FROM clinical_case_slides")
                        .orderByDesc(Slide::getCreatedAt)
        ).stream().map(slide -> new CaseCreationOptionsView.SlideOption(
                slide.getId(), slide.getFileName(), slide.getFormat(), slide.getFileSize(),
                slide.getWidth(), slide.getHeight(), slide.getStatus()
        )).toList();
        List<CaseCreationOptionsView.UserOption> pathologists = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, "ANNOTATOR")
                        .eq(User::getStatus, "ACTIVE")
                        .orderByAsc(User::getUsername)
        ).stream().map(user -> new CaseCreationOptionsView.UserOption(
                user.getId(), user.getUsername(), displayName(user)
        )).toList();
        return new CaseCreationOptionsView(
                DISEASE_PROFILE, SPECIMEN_SITE_KEY, STAIN, slides, pathologists
        );
    }

    @Transactional
    public ClinicalCaseView create(CreateCommand command) {
        UserContext.UserInfo actor = requireUser();
        requireAnyRole(actor, "AI_DATA_ADMIN", "SYSTEM_ADMIN");
        ClinicalCase item = createInternal(command, nextReference(), actor.id(), actorName(actor));
        return toView(item);
    }

    @Transactional
    public ClinicalCaseView createSeedIfAbsent(
            String caseReference,
            String accession,
            String specimen,
            int patientAge,
            Long slideId,
            Long pathologistId,
            LocalDateTime receivedAt,
            LocalDateTime dueAt) {
        ClinicalCase existing = findCase(caseReference);
        if (existing != null) return toView(existing);
        CreateCommand command = new CreateCommand(
                accession, slideId, DISEASE_PROFILE, specimen, patientAge,
                receivedAt, dueAt, pathologistId
        );
        return toView(createInternal(command, caseReference, null, "System"));
    }

    @Transactional
    public AiAnalysisView startAnalysis(String reference) {
        UserContext.UserInfo actor = requireUser();
        requireAnyRole(actor, "ANNOTATOR");
        ClinicalCase item = requireCase(reference);
        requireAssignedPathologist(item, actor);
        if (Set.of("SENIOR_REVIEW", "COMPLETED").contains(item.getStatus())) {
            throw new IllegalStateException("The case is not eligible for a new analysis");
        }
        AiAnalysisRun active = latestAnalysisEntity(reference);
        if (active != null && Set.of("QUEUED", "RUNNING").contains(active.getStatus())) {
            return analysisService.getById(active.getId());
        }
        Slide slide = requirePrimarySlide(item.getId());
        if (!"READY".equals(slide.getStatus())) {
            throw new IllegalStateException("Primary WSI must be READY before analysis");
        }
        AiAnalysisView analysis = analysisService.startAnalysis(
                slide.getId(), reference, null, null, actor.id()
        );
        audit(item.getId(), "analysis_started", actor, "po.audit.aiAnalysisStarted");
        return analysis;
    }

    public AiAnalysisView latestAnalysis(String reference) {
        ClinicalCase item = requireCase(reference);
        assertReadable(item);
        AiAnalysisRun run = latestAnalysisEntity(reference);
        return run == null ? null : analysisService.getById(run.getId());
    }

    @Transactional
    public void synchronizeAnalysisTriggers(Long runId) {
        AiAnalysisRun run = analysisRunMapper.selectById(runId);
        if (run == null || !"SUCCEEDED".equals(run.getStatus())
                || run.getCaseReference() == null) return;
        ClinicalCase item = findCase(run.getCaseReference());
        if (item == null) return;

        triggerMapper.delete(new LambdaQueryWrapper<ClinicalCaseReviewTrigger>()
                .eq(ClinicalCaseReviewTrigger::getCaseId, item.getId())
                .in(ClinicalCaseReviewTrigger::getTriggerCode,
                        List.of("low_confidence", "boundary_result")));
        Double disagreement = run.getEnsembleDisagreement();
        if (disagreement != null
                && disagreement >= workflowProperties.getLowConfidenceDisagreement()) {
            addTrigger(item.getId(), "low_confidence");
        }
        Double score = run.getTumorEvidenceScore();
        if (score != null
                && score >= workflowProperties.getBoundaryEvidenceMin()
                && score <= workflowProperties.getBoundaryEvidenceMax()) {
            addTrigger(item.getId(), "boundary_result");
        }
        item.setReviewRequired(!triggers(item.getId()).isEmpty());
        caseMapper.updateById(item);
    }

    @Transactional
    public ClinicalCaseView startReview(String reference) {
        UserContext.UserInfo actor = requireUser();
        requireAnyRole(actor, "ANNOTATOR");
        ClinicalCase item = requireCase(reference);
        requireAssignedPathologist(item, actor);
        if (!Set.of("READY", "RETURNED").contains(item.getStatus())) {
            return toView(item);
        }
        AiAnalysisRun run = latestAnalysisEntity(reference);
        if (run == null || !"SUCCEEDED".equals(run.getStatus())) {
            throw new IllegalStateException("A completed CLAM analysis is required");
        }
        item.setStatus("IN_REVIEW");
        item.setFeedbackStatus("NOT_ELIGIBLE");
        caseMapper.updateById(item);
        audit(item.getId(), "review_started", actor, "po.audit.reviewStarted");
        return toView(item);
    }

    @Transactional
    public ClinicalCaseView recordPathologistDecision(
            String reference, String decisionType, String note) {
        UserContext.UserInfo actor = requireUser();
        requireAnyRole(actor, "ANNOTATOR");
        ClinicalCase item = requireCase(reference);
        requireAssignedPathologist(item, actor);
        if (!"IN_REVIEW".equals(item.getStatus())) {
            throw new IllegalStateException("Case is not in pathologist review");
        }
        String decision = normalizeDecision(decisionType, PATHOLOGIST_DECISIONS);
        String normalizedNote = normalizeNote(note, PATHOLOGIST_NOTE_REQUIRED.contains(decision));
        reportService.markCaseReportsStale(reference);
        removeDecisionTriggers(item.getId());
        addDecision(item, "PATHOLOGIST", decision, normalizedNote, actor);
        if ("reject".equals(decision)) addTrigger(item.getId(), "image_text_discordance");
        if ("escalate".equals(decision)) addTrigger(item.getId(), "explicit_escalation");

        List<String> currentTriggers = triggers(item.getId());
        item.setReviewRequired(!currentTriggers.isEmpty());
        audit(item.getId(), "decision_recorded", actor, decisionDetailKey(decision));
        if (currentTriggers.isEmpty()) {
            completeCase(item, actor);
        } else {
            item.setStatus("SENIOR_REVIEW");
            item.setFeedbackStatus("AWAITING_REVIEW");
            audit(item.getId(), "senior_review_queued", null, "po.audit.seniorReviewQueued");
        }
        caseMapper.updateById(item);
        return toView(item);
    }

    @Transactional
    public ClinicalCaseView recordSeniorReview(
            String reference, String reviewType, String note) {
        UserContext.UserInfo actor = requireUser();
        requireAnyRole(actor, "REVIEWER");
        ClinicalCase item = requireCase(reference);
        if (!"SENIOR_REVIEW".equals(item.getStatus())) {
            throw new IllegalStateException("Case is not awaiting senior review");
        }
        String decision = normalizeDecision(reviewType, SENIOR_DECISIONS);
        String normalizedNote = normalizeNote(note, "return".equals(decision));
        reportService.markCaseReportsStale(reference);
        addDecision(item, "SENIOR", decision, normalizedNote, actor);
        if ("approve".equals(decision)) {
            completeCase(item, actor);
            audit(item.getId(), "senior_review_approved", actor,
                    "po.audit.seniorReviewApproved");
        } else {
            item.setStatus("RETURNED");
            item.setFeedbackStatus("NOT_ELIGIBLE");
            audit(item.getId(), "senior_review_returned", actor,
                    "po.audit.seniorReviewReturned");
        }
        caseMapper.updateById(item);
        return toView(item);
    }

    @Transactional
    public ClinicalCaseView validateFeedback(String reference) {
        UserContext.UserInfo actor = requireUser();
        requireAnyRole(actor, "AI_DATA_ADMIN");
        ClinicalCase item = requireCase(reference);
        if (!"QUEUED".equals(item.getFeedbackStatus())) {
            throw new IllegalStateException("Feedback is not queued for validation");
        }
        item.setFeedbackStatus("VALIDATED");
        caseMapper.updateById(item);
        audit(item.getId(), "feedback_validated", actor, "po.audit.feedbackValidated");
        return toView(item);
    }

    public ClinicalCaseView getCompletedForReport(String reference) {
        ClinicalCase item = requireCase(reference);
        assertReadable(item);
        if (!"COMPLETED".equals(item.getStatus())) {
            throw new IllegalStateException("Case must be completed before report generation");
        }
        return toView(item);
    }

    private ClinicalCase createInternal(
            CreateCommand command, String reference, Long createdBy, String actorName) {
        validateCreate(command);
        Slide slide = slideMapper.selectById(command.primarySlideId());
        if (slide == null || !"READY".equals(slide.getStatus())) {
            throw new IllegalArgumentException("Primary WSI must exist and be READY");
        }
        if (slide.getFormat() == null
                || !SUPPORTED_WSI_FORMATS.contains(slide.getFormat().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Primary slide must use a supported WSI format");
        }
        if (caseSlideMapper.selectCount(new LambdaQueryWrapper<ClinicalCaseSlide>()
                .eq(ClinicalCaseSlide::getSlideId, slide.getId())) > 0) {
            throw new IllegalStateException("Primary WSI is already bound to a case");
        }
        User pathologist = userMapper.selectById(command.assignedPathologistId());
        if (pathologist == null || !"ANNOTATOR".equals(pathologist.getRole())
                || !"ACTIVE".equals(pathologist.getStatus())) {
            throw new IllegalArgumentException("Assigned pathologist must be an active pathologist");
        }

        ClinicalCase item = new ClinicalCase();
        item.setCaseReference(reference);
        item.setAccessionReference(command.accessionReference().trim());
        item.setDiseaseProfileId(DISEASE_PROFILE);
        item.setSpecimenName(command.specimenName().trim());
        item.setSpecimenSiteKey(SPECIMEN_SITE_KEY);
        item.setStain(STAIN);
        item.setPatientAge(command.patientAge());
        item.setReceivedAt(command.receivedAt());
        item.setDueAt(command.dueAt());
        item.setAssignedPathologistId(pathologist.getId());
        item.setStatus("READY");
        item.setReviewRequired(false);
        item.setQcSample(isQcSample(reference));
        item.setFeedbackStatus("NOT_ELIGIBLE");
        item.setFeedbackVersion(0);
        item.setCreatedBy(createdBy);
        caseMapper.insert(item);

        ClinicalCaseSlide binding = new ClinicalCaseSlide();
        binding.setCaseId(item.getId());
        binding.setSlideId(slide.getId());
        binding.setIsPrimary(true);
        caseSlideMapper.insert(binding);
        if (Boolean.TRUE.equals(item.getQcSample())) {
            addTrigger(item.getId(), "random_qc");
            item.setReviewRequired(true);
            caseMapper.updateById(item);
        }
        audit(item.getId(), "case_received", createdBy, actorName, "po.audit.caseReceived");
        audit(item.getId(), "slide_bound", createdBy, actorName, "po.audit.primarySlideBound");
        return item;
    }

    private void validateCreate(CreateCommand command) {
        if (command == null) throw new IllegalArgumentException("Case details are required");
        if (command.accessionReference() == null || command.accessionReference().isBlank()) {
            throw new IllegalArgumentException("Accession reference is required");
        }
        if (command.specimenName() == null || command.specimenName().isBlank()) {
            throw new IllegalArgumentException("Specimen name is required");
        }
        if (command.patientAge() == null || command.patientAge() < 0
                || command.patientAge() > 120) {
            throw new IllegalArgumentException("Patient age must be between 0 and 120");
        }
        if (command.receivedAt() == null || command.dueAt() == null
                || !command.dueAt().isAfter(command.receivedAt())) {
            throw new IllegalArgumentException("Due time must be after received time");
        }
        if (command.primarySlideId() == null || command.assignedPathologistId() == null) {
            throw new IllegalArgumentException("Primary WSI and pathologist are required");
        }
        if (command.diseaseProfileId() != null
                && !DISEASE_PROFILE.equals(command.diseaseProfileId())) {
            throw new IllegalArgumentException("Unsupported disease profile");
        }
    }

    private ClinicalCaseView toView(ClinicalCase item) {
        Slide slide = requirePrimarySlide(item.getId());
        User owner = userMapper.selectById(item.getAssignedPathologistId());
        List<String> reviewTriggers = triggers(item.getId());
        ClinicalCaseDecision pathologistDecision = latestDecision(item.getId(), "PATHOLOGIST");
        ClinicalCaseDecision seniorDecision = latestDecision(item.getId(), "SENIOR");
        AiAnalysisRun analysis = latestAnalysisEntity(item.getCaseReference());
        Double risk = analysis == null ? null : analysis.getTumorEvidenceScore();
        Double confidence = analysis == null || analysis.getEnsembleDisagreement() == null
                ? null : Math.max(0.0, Math.min(1.0, 1.0 - analysis.getEnsembleDisagreement()));
        List<String> flags = new ArrayList<>(reviewTriggers);

        ClinicalCaseView.Decision decisionView = pathologistDecision == null ? null
                : new ClinicalCaseView.Decision(
                        pathologistDecision.getDecisionType(), pathologistDecision.getNote(),
                        pathologistDecision.getActorName(), pathologistDecision.getActorRole(),
                        pathologistDecision.getCreatedAt());
        ClinicalCaseView.SeniorReview seniorView = seniorDecision == null
                ? ("SENIOR_REVIEW".equals(item.getStatus())
                    ? new ClinicalCaseView.SeniorReview("pending", null, null, "") : null)
                : new ClinicalCaseView.SeniorReview(
                        "approve".equals(seniorDecision.getDecisionType()) ? "approved" : "returned",
                        seniorDecision.getActorName(), seniorDecision.getCreatedAt(),
                        seniorDecision.getNote());
        List<ClinicalCaseView.AuditEvent> audits = auditMapper.selectList(
                new LambdaQueryWrapper<ClinicalCaseAuditEvent>()
                        .eq(ClinicalCaseAuditEvent::getCaseId, item.getId())
                        .orderByAsc(ClinicalCaseAuditEvent::getCreatedAt)
        ).stream().map(event -> new ClinicalCaseView.AuditEvent(
                event.getCreatedAt(), event.getEventType(), event.getActorName(), event.getDetailKey()
        )).toList();

        return new ClinicalCaseView(
                item.getId(), item.getCaseReference(), item.getAccessionReference(),
                item.getDiseaseProfileId(), item.getPatientAge(),
                new ClinicalCaseView.Specimen(
                        item.getSpecimenName(), item.getSpecimenSiteKey(), item.getStain(), 1),
                item.getReceivedAt(), item.getDueAt(), displayName(owner), owner.getId(),
                slide.getId(), slide.getFileName(), slide.getStatus(),
                new ClinicalCaseView.AiSummary(
                        risk, confidence, flags, "backend", analysis == null ? null : analysis.getId(),
                        analysis == null ? null : analysis.getStatus(),
                        analysis == null ? null : analysis.getCompletedAt()),
                new ClinicalCaseView.Workflow(
                        item.getStatus().toLowerCase(Locale.ROOT), item.getReviewRequired(),
                        reviewTriggers, item.getQcSample(), decisionView, seniorView,
                        item.getUpdatedAt()),
                new ClinicalCaseView.Feedback(
                        Set.of("QUEUED", "VALIDATED").contains(item.getFeedbackStatus()),
                        item.getFeedbackStatus().toLowerCase(Locale.ROOT),
                        item.getFeedbackVersion() > 0 ? "v" + item.getFeedbackVersion() : null),
                audits
        );
    }

    private void completeCase(ClinicalCase item, UserContext.UserInfo actor) {
        item.setStatus("COMPLETED");
        item.setFeedbackStatus("QUEUED");
        item.setFeedbackVersion(item.getFeedbackVersion() + 1);
        audit(item.getId(), "feedback_queued", null, "po.audit.feedbackQueued");
    }

    private void addDecision(ClinicalCase item, String stage, String type, String note,
                             UserContext.UserInfo actor) {
        ClinicalCaseDecision decision = new ClinicalCaseDecision();
        decision.setCaseId(item.getId());
        decision.setDecisionStage(stage);
        decision.setDecisionType(type);
        decision.setNote(note);
        decision.setActorId(actor.id());
        decision.setActorName(actorName(actor));
        decision.setActorRole(actor.role());
        decision.setCreatedAt(LocalDateTime.now());
        decisionMapper.insert(decision);
    }

    private void addTrigger(Long caseId, String code) {
        if (triggerMapper.selectCount(new LambdaQueryWrapper<ClinicalCaseReviewTrigger>()
                .eq(ClinicalCaseReviewTrigger::getCaseId, caseId)
                .eq(ClinicalCaseReviewTrigger::getTriggerCode, code)) > 0) return;
        ClinicalCaseReviewTrigger trigger = new ClinicalCaseReviewTrigger();
        trigger.setCaseId(caseId);
        trigger.setTriggerCode(code);
        trigger.setCreatedAt(LocalDateTime.now());
        triggerMapper.insert(trigger);
    }

    private void removeDecisionTriggers(Long caseId) {
        triggerMapper.delete(new LambdaQueryWrapper<ClinicalCaseReviewTrigger>()
                .eq(ClinicalCaseReviewTrigger::getCaseId, caseId)
                .in(ClinicalCaseReviewTrigger::getTriggerCode,
                        List.of("image_text_discordance", "explicit_escalation")));
    }

    private List<String> triggers(Long caseId) {
        return triggerMapper.selectList(new LambdaQueryWrapper<ClinicalCaseReviewTrigger>()
                .eq(ClinicalCaseReviewTrigger::getCaseId, caseId)
                .orderByAsc(ClinicalCaseReviewTrigger::getTriggerCode))
                .stream().map(ClinicalCaseReviewTrigger::getTriggerCode).toList();
    }

    private ClinicalCaseDecision latestDecision(Long caseId, String stage) {
        return decisionMapper.selectOne(new LambdaQueryWrapper<ClinicalCaseDecision>()
                .eq(ClinicalCaseDecision::getCaseId, caseId)
                .eq(ClinicalCaseDecision::getDecisionStage, stage)
                .orderByDesc(ClinicalCaseDecision::getCreatedAt)
                .last("LIMIT 1"));
    }

    private AiAnalysisRun latestAnalysisEntity(String reference) {
        return analysisRunMapper.selectOne(new LambdaQueryWrapper<AiAnalysisRun>()
                .eq(AiAnalysisRun::getCaseReference, reference)
                .orderByDesc(AiAnalysisRun::getCreatedAt)
                .last("LIMIT 1"));
    }

    private Slide requirePrimarySlide(Long caseId) {
        ClinicalCaseSlide binding = caseSlideMapper.selectOne(
                new LambdaQueryWrapper<ClinicalCaseSlide>()
                        .eq(ClinicalCaseSlide::getCaseId, caseId)
                        .eq(ClinicalCaseSlide::getIsPrimary, true));
        if (binding == null) throw new IllegalStateException("Case has no primary WSI");
        Slide slide = slideMapper.selectById(binding.getSlideId());
        if (slide == null) throw new IllegalStateException("Primary WSI no longer exists");
        return slide;
    }

    private ClinicalCase requireCase(String reference) {
        ClinicalCase item = findCase(reference);
        if (item == null) throw new IllegalArgumentException("Case not found: " + reference);
        return item;
    }

    private ClinicalCase findCase(String reference) {
        if (reference == null || reference.isBlank()) return null;
        return caseMapper.selectOne(new LambdaQueryWrapper<ClinicalCase>()
                .eq(ClinicalCase::getCaseReference, reference));
    }

    private void assertReadable(ClinicalCase item) {
        UserContext.UserInfo user = requireUser();
        if ("ANNOTATOR".equals(user.role())) requireAssignedPathologist(item, user);
        else if ("REVIEWER".equals(user.role()) && !isReviewerVisible(item, user.id())) {
            throw new SecurityException("Case is not in this reviewer's scope");
        } else if (!Set.of("REVIEWER", "AI_DATA_ADMIN", "SYSTEM_ADMIN").contains(user.role())) {
            throw new SecurityException("Role is not allowed to read cases");
        }
    }

    private boolean isReviewerVisible(ClinicalCase item, Long reviewerId) {
        if ("SENIOR_REVIEW".equals(item.getStatus())) return true;
        return decisionMapper.selectCount(new LambdaQueryWrapper<ClinicalCaseDecision>()
                .eq(ClinicalCaseDecision::getCaseId, item.getId())
                .eq(ClinicalCaseDecision::getDecisionStage, "SENIOR")
                .eq(ClinicalCaseDecision::getActorId, reviewerId)) > 0;
    }

    private void requireAssignedPathologist(ClinicalCase item, UserContext.UserInfo actor) {
        if (!actor.id().equals(item.getAssignedPathologistId())) {
            throw new SecurityException("Only the assigned pathologist can perform this action");
        }
    }

    private String nextReference() {
        LocalDate today = LocalDate.now();
        long sequence = caseMapper.nextReferenceSequence();
        return String.format(Locale.ROOT, "PO-%02d-%02d%02d-%03d",
                today.getYear() % 100, today.getMonthValue(), today.getDayOfMonth(), sequence);
    }

    private boolean isQcSample(String reference) {
        return Math.floorMod(reference.hashCode(), 100)
                < workflowProperties.getRandomQcPercentage();
    }

    private String normalizeDecision(String value, Set<String> allowed) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException("Unsupported decision");
        return normalized;
    }

    private String normalizeNote(String note, boolean required) {
        String normalized = note == null ? "" : note.trim();
        if (required && normalized.isBlank()) {
            throw new IllegalArgumentException("A note is required for this decision");
        }
        if (normalized.length() > 4_000) throw new IllegalArgumentException("Note exceeds 4000 characters");
        return normalized;
    }

    private String decisionDetailKey(String decision) {
        return switch (decision) {
            case "accept" -> "po.audit.decisionAccepted";
            case "modify" -> "po.audit.decisionModified";
            case "reject" -> "po.audit.decisionRejected";
            default -> "po.audit.decisionEscalated";
        };
    }

    private void audit(Long caseId, String type, UserContext.UserInfo actor, String detailKey) {
        audit(caseId, type, actor == null ? null : actor.id(),
                actor == null ? "System" : actorName(actor), detailKey);
    }

    private void audit(Long caseId, String type, Long actorId, String actorName, String detailKey) {
        ClinicalCaseAuditEvent event = new ClinicalCaseAuditEvent();
        event.setCaseId(caseId);
        event.setEventType(type);
        event.setActorId(actorId);
        event.setActorName(actorName);
        event.setDetailKey(detailKey);
        event.setCreatedAt(LocalDateTime.now());
        auditMapper.insert(event);
    }

    private UserContext.UserInfo requireUser() {
        UserContext.UserInfo user = UserContext.get();
        if (user == null) throw new SecurityException("Authentication is required");
        return user;
    }

    private void requireAnyRole(UserContext.UserInfo user, String... roles) {
        for (String role : roles) if (role.equals(user.role())) return;
        throw new SecurityException("Role is not allowed to perform this action");
    }

    private String actorName(UserContext.UserInfo actor) {
        User user = userMapper.selectById(actor.id());
        return user == null ? actor.username() : displayName(user);
    }

    private String displayName(User user) {
        if (user == null) return "Unassigned";
        return user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername() : user.getDisplayName();
    }
}
