package com.wsi.platform.service;

import com.wsi.platform.config.CaseWorkflowProperties;
import com.wsi.platform.dto.ClinicalCaseView;
import com.wsi.platform.entity.AiAnalysisRun;
import com.wsi.platform.entity.ClinicalCase;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the clinical case workflow: role and assignment boundaries,
 * WSI-format and duplicate-binding validation, the CLAM review gate, senior-review
 * trigger thresholds, direct vs. conditional completion, report staleness, and the
 * feedback-validation restrictions.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClinicalCaseServiceTest {

    private static final String CASE_REF = "PO-26-0001-001";
    private static final Long CASE_ID = 100L;
    private static final Long SLIDE_ID = 5L;
    private static final Long PATH_ID = 10L;
    private static final Long OTHER_PATH_ID = 11L;
    private static final Long REVIEWER_ID = 20L;
    private static final Long ADMIN_ID = 30L;

    @Mock private ClinicalCaseMapper caseMapper;
    @Mock private ClinicalCaseSlideMapper caseSlideMapper;
    @Mock private ClinicalCaseDecisionMapper decisionMapper;
    @Mock private ClinicalCaseReviewTriggerMapper triggerMapper;
    @Mock private ClinicalCaseAuditEventMapper auditMapper;
    @Mock private SlideMapper slideMapper;
    @Mock private UserMapper userMapper;
    @Mock private AiAnalysisRunMapper analysisRunMapper;
    @Mock private AiAnalysisService analysisService;
    @Mock private AiReportService reportService;

    private CaseWorkflowProperties workflowProperties;
    private ClinicalCaseService service;

    @BeforeEach
    void setUp() {
        workflowProperties = new CaseWorkflowProperties();
        service = new ClinicalCaseService(
                caseMapper, caseSlideMapper, decisionMapper, triggerMapper, auditMapper,
                slideMapper, userMapper, analysisRunMapper, analysisService, reportService,
                workflowProperties);

        // Common stubs so toView() and audit helpers never NPE. Individual tests
        // override triggerMapper.selectList / analysisRunMapper.selectOne as needed.
        when(caseSlideMapper.selectOne(any())).thenReturn(binding());
        when(slideMapper.selectById(any())).thenReturn(slide("TIF", "READY"));
        when(userMapper.selectById(any())).thenReturn(user(PATH_ID, "ANNOTATOR", "ACTIVE"));
        when(triggerMapper.selectList(any())).thenReturn(List.of());
        when(triggerMapper.selectCount(any())).thenReturn(0L);
        when(caseSlideMapper.selectCount(any())).thenReturn(0L);
        when(decisionMapper.selectOne(any())).thenReturn(null);
        when(analysisRunMapper.selectOne(any())).thenReturn(null);
        when(auditMapper.selectList(any())).thenReturn(List.of());
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    // ---------------------------------------------------------------- Auth / role boundaries

    @Test
    void requiresAuthenticationForAnyRead() {
        assertThatThrownBy(() -> service.listForCurrentUser())
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Authentication is required");
    }

    @Test
    void rejectsUnsupportedRoleFromCaseList() {
        authenticate(99L, "GUEST");
        assertThatThrownBy(() -> service.listForCurrentUser())
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void adminCanListCases() {
        authenticate(ADMIN_ID, "AI_DATA_ADMIN");
        when(caseMapper.selectList(any())).thenReturn(List.of(caseItem("READY", PATH_ID)));

        List<ClinicalCaseView> cases = service.listForCurrentUser();

        assertThat(cases).hasSize(1);
        assertThat(cases.get(0).id()).isEqualTo(CASE_REF);
    }

    @Test
    void annotatorCannotReadCaseAssignedToAnother() {
        authenticate(OTHER_PATH_ID, "ANNOTATOR");
        when(caseMapper.selectOne(any())).thenReturn(caseItem("READY", PATH_ID));

        assertThatThrownBy(() -> service.getForCurrentUser(CASE_REF))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("another pathologist");
    }

    @Test
    void onlyAdminRolesCanCreateCases() {
        authenticate(PATH_ID, "ANNOTATOR");
        assertThatThrownBy(() -> service.create(validCommand()))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void onlyAnnotatorCanStartAnalysis() {
        authenticate(REVIEWER_ID, "REVIEWER");
        when(caseMapper.selectOne(any())).thenReturn(caseItem("READY", PATH_ID));

        assertThatThrownBy(() -> service.startAnalysis(CASE_REF))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void unassignedAnnotatorCannotStartAnalysis() {
        authenticate(OTHER_PATH_ID, "ANNOTATOR");
        when(caseMapper.selectOne(any())).thenReturn(caseItem("READY", PATH_ID));

        assertThatThrownBy(() -> service.startAnalysis(CASE_REF))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("assigned pathologist");
    }

    @Test
    void onlyReviewerCanRecordSeniorReview() {
        authenticate(PATH_ID, "ANNOTATOR");
        when(caseMapper.selectOne(any())).thenReturn(caseItem("SENIOR_REVIEW", PATH_ID));

        assertThatThrownBy(() -> service.recordSeniorReview(CASE_REF, "approve", ""))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void onlyDataAdminCanValidateFeedback() {
        authenticate(REVIEWER_ID, "REVIEWER");
        when(caseMapper.selectOne(any())).thenReturn(completedQueuedCase());

        assertThatThrownBy(() -> service.validateFeedback(CASE_REF))
                .isInstanceOf(SecurityException.class);
    }

    // ---------------------------------------------------------------- Create validation

    @Test
    void rejectsUnsupportedWsiFormatOnCreate() {
        authenticate(ADMIN_ID, "AI_DATA_ADMIN");
        when(slideMapper.selectById(any())).thenReturn(slide("JPG", "READY"));

        assertThatThrownBy(() -> service.create(validCommand()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("supported WSI format");
    }

    @Test
    void rejectsDuplicatePrimaryBindingOnCreate() {
        authenticate(ADMIN_ID, "AI_DATA_ADMIN");
        when(slideMapper.selectById(any())).thenReturn(slide("TIF", "READY"));
        when(caseSlideMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(validCommand()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already bound");
    }

    @Test
    void rejectsPatientAgeOutOfRange() {
        authenticate(ADMIN_ID, "AI_DATA_ADMIN");
        ClinicalCaseService.CreateCommand command = new ClinicalCaseService.CreateCommand(
                "ACC-1", SLIDE_ID, ClinicalCaseService.DISEASE_PROFILE, "Axillary node",
                130, LocalDateTime.now(), LocalDateTime.now().plusDays(1), PATH_ID);

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("age");
    }

    @Test
    void rejectsDueTimeBeforeReceivedTime() {
        authenticate(ADMIN_ID, "AI_DATA_ADMIN");
        LocalDateTime received = LocalDateTime.now();
        ClinicalCaseService.CreateCommand command = new ClinicalCaseService.CreateCommand(
                "ACC-1", SLIDE_ID, ClinicalCaseService.DISEASE_PROFILE, "Axillary node",
                57, received, received.minusHours(1), PATH_ID);

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Due time");
    }

    @Test
    void createsReadyCaseForValidCommand() {
        authenticate(ADMIN_ID, "AI_DATA_ADMIN");
        when(slideMapper.selectById(any())).thenReturn(slide("TIF", "READY"));
        when(caseMapper.nextReferenceSequence()).thenReturn(1L);

        ClinicalCaseView view = service.create(validCommand());

        assertThat(view.workflow().status()).isEqualTo("ready");
        verify(caseMapper).insert(any(ClinicalCase.class));
        verify(caseSlideMapper).insert(any(ClinicalCaseSlide.class));
    }

    @Test
    void adminCanDeleteReadyCaseWithoutAnalysisHistory() {
        authenticate(ADMIN_ID, "AI_DATA_ADMIN");
        when(caseMapper.selectOne(any())).thenReturn(caseItem("READY", PATH_ID));
        when(analysisRunMapper.selectCount(any())).thenReturn(0L);
        when(decisionMapper.selectCount(any())).thenReturn(0L);

        service.delete(CASE_REF);

        verify(caseMapper).deleteById(CASE_ID);
    }

    @Test
    void deletionRejectsCasesWithAnalysisHistory() {
        authenticate(ADMIN_ID, "AI_DATA_ADMIN");
        when(caseMapper.selectOne(any())).thenReturn(caseItem("READY", PATH_ID));
        when(analysisRunMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(CASE_REF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("analysis history");
        verify(caseMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    @Test
    void onlyAdminRolesCanDeleteCases() {
        authenticate(PATH_ID, "ANNOTATOR");
        when(caseMapper.selectOne(any())).thenReturn(caseItem("READY", PATH_ID));

        assertThatThrownBy(() -> service.delete(CASE_REF))
                .isInstanceOf(SecurityException.class);
        verify(caseMapper, never()).deleteById(any(java.io.Serializable.class));
    }

    // ---------------------------------------------------------------- CLAM review gate

    @Test
    void startReviewRequiresSucceededAnalysis() {
        authenticate(PATH_ID, "ANNOTATOR");
        when(caseMapper.selectOne(any())).thenReturn(caseItem("READY", PATH_ID));
        when(analysisRunMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.startReview(CASE_REF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("completed CLAM analysis is required");
    }

    @Test
    void startReviewRejectsPendingAnalysis() {
        authenticate(PATH_ID, "ANNOTATOR");
        when(caseMapper.selectOne(any())).thenReturn(caseItem("READY", PATH_ID));
        when(analysisRunMapper.selectOne(any())).thenReturn(run("RUNNING", null, null));

        assertThatThrownBy(() -> service.startReview(CASE_REF))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startReviewMovesCaseIntoReviewWhenAnalysisSucceeded() {
        authenticate(PATH_ID, "ANNOTATOR");
        ClinicalCase item = caseItem("READY", PATH_ID);
        when(caseMapper.selectOne(any())).thenReturn(item);
        when(analysisRunMapper.selectOne(any())).thenReturn(run("SUCCEEDED", 0.02, 0.9));

        service.startReview(CASE_REF);

        assertThat(item.getStatus()).isEqualTo("IN_REVIEW");
        assertThat(item.getFeedbackStatus()).isEqualTo("NOT_ELIGIBLE");
    }

    // ---------------------------------------------------------------- Trigger thresholds

    @Test
    void addsLowConfidenceTriggerAtDisagreementThreshold() {
        ClinicalCase item = caseItem("READY", PATH_ID);
        when(analysisRunMapper.selectById(4L)).thenReturn(run("SUCCEEDED", 0.15, null));
        when(caseMapper.selectOne(any())).thenReturn(item);

        service.synchronizeAnalysisTriggers(4L);

        assertThat(insertedTriggerCodes()).contains("low_confidence");
    }

    @Test
    void doesNotAddLowConfidenceTriggerBelowThreshold() {
        ClinicalCase item = caseItem("READY", PATH_ID);
        when(analysisRunMapper.selectById(4L)).thenReturn(run("SUCCEEDED", 0.149, null));
        when(caseMapper.selectOne(any())).thenReturn(item);

        service.synchronizeAnalysisTriggers(4L);

        verify(triggerMapper, never()).insert(any(ClinicalCaseReviewTrigger.class));
    }

    @Test
    void addsBoundaryTriggerWithinEvidenceBand() {
        ClinicalCase item = caseItem("READY", PATH_ID);
        when(analysisRunMapper.selectById(4L)).thenReturn(run("SUCCEEDED", 0.02, 0.35));
        when(caseMapper.selectOne(any())).thenReturn(item);

        service.synchronizeAnalysisTriggers(4L);

        assertThat(insertedTriggerCodes()).contains("boundary_result");
    }

    @Test
    void doesNotAddBoundaryTriggerAboveBand() {
        ClinicalCase item = caseItem("READY", PATH_ID);
        when(analysisRunMapper.selectById(4L)).thenReturn(run("SUCCEEDED", 0.02, 0.66));
        when(caseMapper.selectOne(any())).thenReturn(item);

        service.synchronizeAnalysisTriggers(4L);

        verify(triggerMapper, never()).insert(any(ClinicalCaseReviewTrigger.class));
    }

    // ---------------------------------------------------------------- Pathologist decisions

    @Test
    void acceptWithoutTriggersCompletesDirectly() {
        authenticate(PATH_ID, "ANNOTATOR");
        ClinicalCase item = caseItem("IN_REVIEW", PATH_ID);
        when(caseMapper.selectOne(any())).thenReturn(item);

        ClinicalCaseView view = service.recordPathologistDecision(CASE_REF, "accept", "");

        assertThat(item.getStatus()).isEqualTo("COMPLETED");
        assertThat(item.getFeedbackStatus()).isEqualTo("QUEUED");
        assertThat(item.getFeedbackVersion()).isEqualTo(1);
        assertThat(view.workflow().status()).isEqualTo("completed");
        verify(reportService).markCaseReportsStale(CASE_REF);
    }

    @Test
    void modifyAloneDoesNotTriggerSeniorReview() {
        authenticate(PATH_ID, "ANNOTATOR");
        ClinicalCase item = caseItem("IN_REVIEW", PATH_ID);
        when(caseMapper.selectOne(any())).thenReturn(item);

        service.recordPathologistDecision(CASE_REF, "modify", "Adjusted the tumor margin");

        assertThat(item.getStatus()).isEqualTo("COMPLETED");
        assertThat(item.getFeedbackStatus()).isEqualTo("QUEUED");
        verify(triggerMapper, never()).insert(any(ClinicalCaseReviewTrigger.class));
    }

    @Test
    void rejectRoutesToSeniorReviewAndAddsDiscordanceTrigger() {
        authenticate(PATH_ID, "ANNOTATOR");
        ClinicalCase item = caseItem("IN_REVIEW", PATH_ID);
        when(caseMapper.selectOne(any())).thenReturn(item);
        // Simulate the trigger being present after the insert.
        when(triggerMapper.selectList(any())).thenReturn(List.of(trigger("image_text_discordance")));

        ClinicalCaseView view = service.recordPathologistDecision(
                CASE_REF, "reject", "Report describes no metastasis");

        assertThat(item.getStatus()).isEqualTo("SENIOR_REVIEW");
        assertThat(item.getFeedbackStatus()).isEqualTo("AWAITING_REVIEW");
        assertThat(view.workflow().status()).isEqualTo("senior_review");
        assertThat(insertedTriggerCodes()).contains("image_text_discordance");
    }

    @Test
    void escalateRoutesToSeniorReviewAndAddsEscalationTrigger() {
        authenticate(PATH_ID, "ANNOTATOR");
        ClinicalCase item = caseItem("IN_REVIEW", PATH_ID);
        when(caseMapper.selectOne(any())).thenReturn(item);
        when(triggerMapper.selectList(any())).thenReturn(List.of(trigger("explicit_escalation")));

        service.recordPathologistDecision(CASE_REF, "escalate", "Requesting a second opinion");

        assertThat(item.getStatus()).isEqualTo("SENIOR_REVIEW");
        assertThat(item.getFeedbackStatus()).isEqualTo("AWAITING_REVIEW");
        assertThat(insertedTriggerCodes()).contains("explicit_escalation");
    }

    @Test
    void rejectRequiresClinicalNote() {
        authenticate(PATH_ID, "ANNOTATOR");
        when(caseMapper.selectOne(any())).thenReturn(caseItem("IN_REVIEW", PATH_ID));

        assertThatThrownBy(() -> service.recordPathologistDecision(CASE_REF, "reject", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("note is required");
    }

    @Test
    void pathologistDecisionRequiresCaseInReview() {
        authenticate(PATH_ID, "ANNOTATOR");
        when(caseMapper.selectOne(any())).thenReturn(caseItem("READY", PATH_ID));

        assertThatThrownBy(() -> service.recordPathologistDecision(CASE_REF, "accept", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not in pathologist review");
    }

    // ---------------------------------------------------------------- Senior review

    @Test
    void seniorApprovalCompletesAndQueuesFeedback() {
        authenticate(REVIEWER_ID, "REVIEWER");
        ClinicalCase item = caseItem("SENIOR_REVIEW", PATH_ID);
        when(caseMapper.selectOne(any())).thenReturn(item);

        ClinicalCaseView view = service.recordSeniorReview(CASE_REF, "approve", "");

        assertThat(item.getStatus()).isEqualTo("COMPLETED");
        assertThat(item.getFeedbackStatus()).isEqualTo("QUEUED");
        assertThat(view.workflow().status()).isEqualTo("completed");
        verify(reportService).markCaseReportsStale(CASE_REF);
    }

    @Test
    void seniorReturnDoesNotCompleteOrQueueFeedback() {
        authenticate(REVIEWER_ID, "REVIEWER");
        ClinicalCase item = caseItem("SENIOR_REVIEW", PATH_ID);
        when(caseMapper.selectOne(any())).thenReturn(item);

        ClinicalCaseView view = service.recordSeniorReview(
                CASE_REF, "return", "Please resample the node block");

        assertThat(item.getStatus()).isEqualTo("RETURNED");
        assertThat(item.getFeedbackStatus()).isEqualTo("NOT_ELIGIBLE");
        assertThat(view.workflow().status()).isEqualTo("returned");
    }

    @Test
    void seniorReturnRequiresNote() {
        authenticate(REVIEWER_ID, "REVIEWER");
        when(caseMapper.selectOne(any())).thenReturn(caseItem("SENIOR_REVIEW", PATH_ID));

        assertThatThrownBy(() -> service.recordSeniorReview(CASE_REF, "return", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("note is required");
    }

    @Test
    void seniorReviewRejectedWhenCaseNotAwaitingReview() {
        authenticate(REVIEWER_ID, "REVIEWER");
        when(caseMapper.selectOne(any())).thenReturn(caseItem("IN_REVIEW", PATH_ID));

        assertThatThrownBy(() -> service.recordSeniorReview(CASE_REF, "approve", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not awaiting senior review");
    }

    // ---------------------------------------------------------------- Feedback validation

    @Test
    void feedbackValidationRequiresQueuedStatus() {
        authenticate(ADMIN_ID, "AI_DATA_ADMIN");
        ClinicalCase item = caseItem("COMPLETED", PATH_ID);
        item.setFeedbackStatus("NOT_ELIGIBLE");
        when(caseMapper.selectOne(any())).thenReturn(item);

        assertThatThrownBy(() -> service.validateFeedback(CASE_REF))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not queued");
    }

    @Test
    void feedbackValidationMarksRecordValidated() {
        authenticate(ADMIN_ID, "AI_DATA_ADMIN");
        ClinicalCase item = completedQueuedCase();
        when(caseMapper.selectOne(any())).thenReturn(item);

        service.validateFeedback(CASE_REF);

        assertThat(item.getFeedbackStatus()).isEqualTo("VALIDATED");
    }

    // ---------------------------------------------------------------- helpers

    private void authenticate(Long id, String role) {
        UserContext.set(new UserContext.UserInfo(id, "user" + id, role));
    }

    private List<String> insertedTriggerCodes() {
        ArgumentCaptor<ClinicalCaseReviewTrigger> captor =
                ArgumentCaptor.forClass(ClinicalCaseReviewTrigger.class);
        verify(triggerMapper, atLeast(0)).insert(captor.capture());
        return captor.getAllValues().stream()
                .map(ClinicalCaseReviewTrigger::getTriggerCode)
                .toList();
    }

    private ClinicalCaseService.CreateCommand validCommand() {
        return new ClinicalCaseService.CreateCommand(
                "ACC-1", SLIDE_ID, ClinicalCaseService.DISEASE_PROFILE, "Axillary node",
                57, LocalDateTime.now(), LocalDateTime.now().plusDays(1), PATH_ID);
    }

    private ClinicalCase caseItem(String status, Long pathologistId) {
        ClinicalCase item = new ClinicalCase();
        item.setId(CASE_ID);
        item.setCaseReference(CASE_REF);
        item.setAccessionReference("ACC-1");
        item.setDiseaseProfileId(ClinicalCaseService.DISEASE_PROFILE);
        item.setSpecimenName("Axillary node");
        item.setSpecimenSiteKey(ClinicalCaseService.SPECIMEN_SITE_KEY);
        item.setStain(ClinicalCaseService.STAIN);
        item.setPatientAge(57);
        item.setReceivedAt(LocalDateTime.now().minusDays(1));
        item.setDueAt(LocalDateTime.now().plusDays(1));
        item.setAssignedPathologistId(pathologistId);
        item.setStatus(status);
        item.setReviewRequired(false);
        item.setQcSample(false);
        item.setFeedbackStatus("NOT_ELIGIBLE");
        item.setFeedbackVersion(0);
        return item;
    }

    private ClinicalCase completedQueuedCase() {
        ClinicalCase item = caseItem("COMPLETED", PATH_ID);
        item.setFeedbackStatus("QUEUED");
        item.setFeedbackVersion(1);
        return item;
    }

    private Slide slide(String format, String status) {
        Slide slide = new Slide();
        slide.setId(SLIDE_ID);
        slide.setFileName("tumor_001.tif");
        slide.setFormat(format);
        slide.setStatus(status);
        slide.setFileSize(1024L);
        slide.setWidth(1000);
        slide.setHeight(1000);
        return slide;
    }

    private User user(Long id, String role, String status) {
        User user = new User();
        user.setId(id);
        user.setUsername("user" + id);
        user.setDisplayName("User " + id);
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    private AiAnalysisRun run(String status, Double disagreement, Double score) {
        AiAnalysisRun run = new AiAnalysisRun();
        run.setId(4L);
        run.setCaseReference(CASE_REF);
        run.setStatus(status);
        run.setEnsembleDisagreement(disagreement);
        run.setTumorEvidenceScore(score);
        return run;
    }

    private ClinicalCaseSlide binding() {
        ClinicalCaseSlide binding = new ClinicalCaseSlide();
        binding.setCaseId(CASE_ID);
        binding.setSlideId(SLIDE_ID);
        binding.setIsPrimary(true);
        return binding;
    }

    private ClinicalCaseReviewTrigger trigger(String code) {
        ClinicalCaseReviewTrigger trigger = new ClinicalCaseReviewTrigger();
        trigger.setCaseId(CASE_ID);
        trigger.setTriggerCode(code);
        trigger.setCreatedAt(LocalDateTime.now());
        return trigger;
    }
}
