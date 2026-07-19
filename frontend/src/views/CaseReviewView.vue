<template>
  <ProductShell wide>
    <section v-if="caseItem" class="po-review-page">
      <header class="po-case-header">
        <div class="po-case-title-block">
          <router-link class="po-back-link" :to="{ name: 'cases' }">
            <el-icon aria-hidden="true"><ArrowLeft /></el-icon>
            {{ t('po.case.backToQueue') }}
          </router-link>
          <div class="po-case-title-row">
            <div>
              <h1>{{ caseItem.id }}</h1>
              <p>{{ caseItem.accession }} / {{ caseItem.specimen.node }}</p>
            </div>
            <CaseRiskBadge :score="displayRisk" />
            <CaseStatusBadge :status="caseItem.workflow.status" />
          </div>
        </div>

        <dl class="po-case-metadata">
          <div>
            <dt>{{ t('po.case.received') }}</dt>
            <dd>{{ formatDateTime(caseItem.receivedAt, locale) }}</dd>
          </div>
          <div>
            <dt>{{ t('po.case.due') }}</dt>
            <dd>{{ formatDateTime(caseItem.dueAt, locale) }}</dd>
          </div>
          <div>
            <dt>{{ t('po.case.owner') }}</dt>
            <dd>{{ caseItem.owner || t('po.queue.unassigned') }}</dd>
          </div>
          <div>
            <dt>{{ t('po.case.stain') }}</dt>
            <dd>{{ caseItem.specimen.stain }}</dd>
          </div>
        </dl>
      </header>

      <div class="po-review-workspace">
        <RealEvidenceRail
          v-if="isRealAiCase"
          class="po-review-evidence"
          :analysis="analysis"
          :selected-region="selectedRegion"
          :loading="analysisLoading"
          :starting="analysisStarting"
          :error="analysisError"
          :can-run="canRunAnalysis"
          @select-region="selectedRegion = $event"
          @run="runAiAnalysis"
          @retry="reloadAiAnalysis"
        />

        <CaseEvidenceRail
          v-else
          class="po-review-evidence"
          :case-item="caseItem"
          :selected-region="selectedRegion"
          @select-region="selectedRegion = $event"
        />

        <div class="po-review-canvas">
          <div class="po-viewer-heading">
            <div>
              <span>{{ t('po.viewer.title') }}</span>
              <strong>{{ selectedRegionTitle }}</strong>
            </div>
            <span v-if="selectedRegionMetric" class="po-tabular">
              {{ selectedRegionMetric }}
            </span>
          </div>
          <RealWsiEvidenceViewer
            v-if="showRealViewer"
            :analysis="analysis"
            :artifact-urls="artifactUrls"
            :selected-region="selectedRegion"
          />
          <AiAnalysisCanvasState
            v-else-if="isRealAiCase"
            :analysis="analysis"
            :loading="analysisLoading || artifactsLoading"
            :starting="analysisStarting"
            :error="analysisError"
            :artifact-error="primaryArtifactError"
            :can-run="canRunAnalysis"
            @run="runAiAnalysis"
            @retry="reloadAiAnalysis"
          />
          <DemoWsiCanvas
            v-else
            :case-item="caseItem"
            :selected-region="selectedRegion"
            @select-region="selectedRegion = $event"
          />
        </div>

        <aside class="po-review-sidebar">
          <ClinicalDecisionPanel
            :case-item="caseItem"
            :role="productStore.effectiveRole"
            :analysis-ready="!isRealAiCase || realAnalysisReady"
            @start="startReview"
            @submit="recordDecision"
          />
          <AiReportPanel
            v-if="isRealAiCase"
            :report="aiReport"
            :loading="reportLoading"
            :starting="reportStarting"
            :error="reportError"
            :has-decision="Boolean(reportDecision)"
            :can-generate="canGenerateReport"
            @generate="generateReportDraft"
            @retry="reloadReport"
          />
          <CaseAuditTimeline :audit-trail="caseItem.auditTrail" />
        </aside>
      </div>
    </section>

    <section v-else-if="caseStore.loading" class="po-case-loading">
      <el-skeleton :rows="10" animated />
    </section>

    <section v-else class="po-case-missing">
      <el-icon aria-hidden="true"><DocumentDelete /></el-icon>
      <h1>{{ t('po.case.caseNotFound') }}</h1>
      <el-button type="primary" @click="router.replace({ name: 'cases' })">
        {{ t('po.case.returnToQueue') }}
      </el-button>
    </section>
  </ProductShell>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowLeft, DocumentDelete } from '@element-plus/icons-vue'
import ProductShell from '@/components/ProductShell.vue'
import AiAnalysisCanvasState from '@/components/AiAnalysisCanvasState.vue'
import AiReportPanel from '@/components/AiReportPanel.vue'
import CaseRiskBadge from '@/components/CaseRiskBadge.vue'
import CaseStatusBadge from '@/components/CaseStatusBadge.vue'
import CaseEvidenceRail from '@/components/CaseEvidenceRail.vue'
import DemoWsiCanvas from '@/components/DemoWsiCanvas.vue'
import RealEvidenceRail from '@/components/RealEvidenceRail.vue'
import RealWsiEvidenceViewer from '@/components/RealWsiEvidenceViewer.vue'
import ClinicalDecisionPanel from '@/components/ClinicalDecisionPanel.vue'
import CaseAuditTimeline from '@/components/CaseAuditTimeline.vue'
import { useAiAnalysis } from '@/composables/useAiAnalysis'
import { useAiReport } from '@/composables/useAiReport'
import { getPrecomputedAnalysis } from '@/data/precomputedAiAnalyses'
import { createPrecomputedReport } from '@/data/precomputedAiReports'
import { useCaseStore } from '@/stores/caseStore'
import { useProductStore } from '@/stores/productStore'
import { formatDateTime, formatPercent } from '@/utils/formatters'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const caseStore = useCaseStore()
const productStore = useProductStore()
const {
  analysis,
  artifactUrls,
  loading: analysisLoading,
  starting: analysisStarting,
  error: analysisError,
  artifactError,
  artifactsLoading,
  loadLatest,
  loadPrecomputed,
  reset: resetAnalysis,
  start: startAnalysis,
} = useAiAnalysis()
const {
  report: aiReport,
  loading: reportLoading,
  starting: reportStarting,
  error: reportError,
  loadLatest: loadLatestReport,
  loadPrecomputed: loadPrecomputedReport,
  reset: resetReport,
  startForCase: startCaseReport,
} = useAiReport()

const caseItem = computed(() => caseStore.getCaseById(route.params.caseId))
const selectedRegion = ref(null)
const synchronizedAnalysisId = ref(null)
const isRealAiCase = computed(() => (
  Boolean(caseItem.value?.ai.analysisSource)
))
const canRunAnalysis = computed(() => (
  !productStore.isDemoMode
  && productStore.effectiveRole === 'ANNOTATOR'
  && Boolean(caseItem.value?.localSlideId)
  && ['ready', 'returned'].includes(caseItem.value?.workflow.status)
  && !['QUEUED', 'RUNNING', 'SUCCEEDED'].includes(analysis.value?.status)
))
const realAnalysisReady = computed(() => (
  analysis.value?.status === 'SUCCEEDED'
))
const showRealViewer = computed(() => (
  realAnalysisReady.value
  && (Boolean(artifactUrls.value.modelEvidenceOverview) || artifactsLoading.value)
))
const primaryArtifactError = computed(() => (
  realAnalysisReady.value
  && !artifactsLoading.value
  && !artifactUrls.value.modelEvidenceOverview
    ? artifactError.value
    : null
))
const displayRisk = computed(() => (
  realAnalysisReady.value
    ? analysis.value.tumorEvidenceScore
    : caseItem.value?.ai.risk ?? null
))
const reportDecision = computed(() => {
  if (caseItem.value?.workflow.status !== 'completed') return null
  return caseItem.value?.workflow.decision || null
})
const reportRoleAllowed = computed(() => (
  ['ANNOTATOR', 'REVIEWER'].includes(productStore.effectiveRole)
))
const canGenerateReport = computed(() => (
  realAnalysisReady.value && Boolean(reportDecision.value) && reportRoleAllowed.value
))

const selectedRegionObject = computed(() => {
  if (isRealAiCase.value) {
    if (!realAnalysisReady.value) return null
    return analysis.value.candidates?.find(
      (candidate) => candidate.regionId === selectedRegion.value,
    ) || analysis.value.candidates?.[0] || null
  }

  return caseItem.value?.ai.regions.find((region) => region.id === selectedRegion.value)
    || caseItem.value?.ai.regions[0]
    || { id: 'R1', labelKey: 'po.regions.focalAtypia', score: 0 }
})
const selectedRegionTitle = computed(() => {
  if (isRealAiCase.value && realAnalysisReady.value) {
    return selectedRegionObject.value
      ? t('po.evidence.candidateRegion', { id: selectedRegionObject.value.regionId })
      : t('po.evidence.noCandidateRegions')
  }
  if (isRealAiCase.value && ['QUEUED', 'RUNNING'].includes(analysis.value?.status)) {
    return t('po.analysis.runningTitle')
  }
  if (isRealAiCase.value) {
    return t(analysisLoading.value ? 'po.analysis.loadingTitle' : 'po.analysis.noAnalysisTitle')
  }
  return t(selectedRegionObject.value.labelKey)
})
const selectedRegionMetric = computed(() => {
  if (isRealAiCase.value) {
    if (!realAnalysisReady.value) return ''
    const score = selectedRegionObject.value?.maxEvidenceIndex
      ?? analysis.value.tumorEvidenceScore
    const identifier = selectedRegionObject.value?.regionId || 'AI'
    return `${identifier} / ${formatPercent(score, locale.value)}`
  }
  return `${selectedRegionObject.value.id} / ${formatPercent(selectedRegionObject.value.score, locale.value)}`
})

async function reloadAiAnalysis() {
  if (!isRealAiCase.value) return
  if (productStore.isDemoMode) {
    const precomputed = getPrecomputedAnalysis(caseItem.value.ai.analysisSource)
    loadPrecomputed(precomputed)
    selectedRegion.value = precomputed?.candidates?.[0]?.regionId || null
    return
  }
  if (caseItem.value.localSlideId) {
    await loadLatest(caseItem.value.id)
    selectedRegion.value = analysis.value?.candidates?.[0]?.regionId || null
  }
}

async function runAiAnalysis() {
  if (!canRunAnalysis.value) return
  await startAnalysis(caseItem.value.id)
}

async function reloadReport() {
  if (!realAnalysisReady.value || !isRealAiCase.value || !reportDecision.value) {
    resetReport()
    return
  }
  if (productStore.isDemoMode) {
    loadPrecomputedReport(createPrecomputedReport(
      analysis.value,
      caseItem.value,
      locale.value,
    ))
    return
  }
  await loadLatestReport(analysis.value.id, caseItem.value.id)
}

async function generateReportDraft() {
  if (!canGenerateReport.value) return
  if (productStore.isDemoMode) {
    loadPrecomputedReport(createPrecomputedReport(
      analysis.value,
      caseItem.value,
      locale.value,
    ))
    return
  }
  await startCaseReport(caseItem.value.id, {
    language: locale.value,
  })
}

async function startReview() {
  if (!caseItem.value) return
  await caseStore.startReview(caseItem.value.id, productStore.identity)
  ElMessage.success(t('po.case.reviewStarted'))
}

async function recordDecision(payload) {
  if (!caseItem.value) return

  const result = productStore.effectiveRole === 'REVIEWER'
    ? await caseStore.recordSeniorReview(
      caseItem.value.id, payload, productStore.identity, locale.value,
    )
    : await caseStore.recordPathologistDecision(
      caseItem.value.id, payload, productStore.identity, locale.value,
    )

  if (!result.ok) return

  if (result.status === 'senior_review') {
    ElMessage.success(t('po.decision.routedToReview'))
  } else if (result.status === 'returned') {
    ElMessage.success(t('po.decision.returned'))
  } else {
    ElMessage.success(t('po.decision.sentToFeedback'))
  }

  if (result.status === 'completed' && isRealAiCase.value) {
    await reloadReport()
  }
}

onMounted(async () => {
  if (!productStore.isDemoMode) await caseStore.loadCase(route.params.caseId)
})

watch(
  [
    () => caseItem.value?.id,
    () => caseItem.value?.ai.analysisSource,
    () => caseItem.value?.localSlideId,
    () => productStore.isDemoMode,
  ],
  () => {
    selectedRegion.value = caseItem.value?.ai?.regions?.[0]?.id || null
    if (isRealAiCase.value) reloadAiAnalysis()
    else resetAnalysis()
  },
  { immediate: true },
)

watch(
  [() => analysis.value?.id, () => analysis.value?.status],
  async ([analysisId, status]) => {
    if (
      status === 'SUCCEEDED'
      && analysisId
      && synchronizedAnalysisId.value !== analysisId
      && !productStore.isDemoMode
      && caseItem.value
    ) {
      synchronizedAnalysisId.value = analysisId
      await caseStore.loadCase(caseItem.value.id)
    }
  },
)

watch(
  [
    () => analysis.value?.id,
    () => analysis.value?.status,
    () => caseItem.value?.workflow.status,
  ],
  () => {
    if (!realAnalysisReady.value) {
      resetReport()
      return
    }
    selectedRegion.value = analysis.value.candidates?.[0]?.regionId || null
    reloadReport()
  },
)

watch(
  () => locale.value,
  () => {
    if (productStore.isDemoMode && realAnalysisReady.value) reloadReport()
  },
)
</script>

<style scoped>
.po-review-page {
  display: grid;
  gap: 14px;
}

.po-case-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 0 2px 12px;
  border-bottom: 1px solid var(--po-hairline);
}

.po-case-title-block {
  min-width: 0;
}

.po-back-link {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-bottom: 8px;
  color: var(--po-ink-muted);
  font-size: 10px;
  font-weight: 600;
  text-decoration: none;
}

.po-back-link:hover {
  color: var(--po-harbor);
}

.po-back-link:focus-visible {
  border-radius: 2px;
  outline: 3px solid var(--po-harbor-soft);
  outline-offset: 2px;
}

.po-case-title-row {
  display: flex;
  align-items: center;
  gap: 9px;
}

.po-case-title-row > div {
  display: grid;
  margin-right: 5px;
}

.po-case-title-row h1 {
  margin: 0;
  color: var(--po-ink);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 22px;
  font-weight: 650;
  line-height: 1.25;
}

.po-case-title-row p {
  margin: 3px 0 0;
  color: var(--po-ink-muted);
  font-size: 10px;
}

.po-case-metadata {
  display: flex;
  align-items: flex-end;
  margin: 0;
}

.po-case-metadata > div {
  min-width: 112px;
  padding: 0 14px;
  border-left: 1px solid var(--po-hairline);
}

.po-case-metadata dt {
  color: var(--po-ink-muted);
  font-size: 9px;
  font-weight: 600;
}

.po-case-metadata dd {
  margin: 3px 0 0;
  color: var(--po-ink);
  font-size: 10px;
  font-weight: 600;
  white-space: nowrap;
}

.po-review-workspace {
  display: grid;
  grid-template-columns: 286px minmax(480px, 1fr) 320px;
  align-items: start;
  gap: 12px;
}

.po-review-evidence,
.po-review-sidebar {
  max-height: clamp(550px, calc(100vh - 170px), 820px);
  overflow-y: auto;
  scrollbar-width: thin;
}

.po-review-sidebar {
  overflow: hidden auto;
  border: 1px solid var(--po-hairline);
  border-radius: var(--po-radius-md);
  background: var(--po-paper);
}

.po-review-canvas {
  min-width: 0;
}

.po-viewer-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  min-height: 40px;
  padding: 0 2px 8px;
}

.po-viewer-heading > div {
  display: grid;
}

.po-viewer-heading span {
  color: var(--po-ink-muted);
  font-size: 9px;
  font-weight: 600;
}

.po-viewer-heading strong {
  margin-top: 2px;
  color: var(--po-ink);
  font-size: 12px;
  font-weight: 650;
}

.po-viewer-heading > .po-tabular {
  color: oklch(0.4 0.12 350);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
}

.po-case-missing {
  display: grid;
  place-items: center;
  gap: 12px;
  min-height: 60vh;
  text-align: center;
}

.po-case-loading {
  max-width: 920px;
  padding-top: 32px;
}

.po-case-missing > .el-icon {
  color: var(--po-ink-muted);
  font-size: 32px;
}

.po-case-missing h1 {
  margin: 0;
  font-size: 18px;
}

@media (max-width: 1280px) {
  .po-case-header {
    align-items: flex-start;
  }

  .po-case-metadata {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 7px 0;
  }

  .po-review-workspace {
    grid-template-columns: 270px minmax(480px, 1fr);
  }

  .po-review-sidebar {
    display: grid;
    grid-column: 1 / -1;
    grid-template-columns: minmax(320px, 1fr) minmax(280px, 0.8fr);
    max-height: none;
  }

  .po-review-sidebar :deep(.po-audit-panel) {
    grid-column: 1 / -1;
    border-top: 1px solid var(--po-hairline);
    border-left: 0;
  }

  .po-review-sidebar :deep(.po-report-panel) {
    border-top: 0;
    border-left: 1px solid var(--po-hairline);
  }
}

@media (max-width: 900px) {
  .po-case-header {
    display: grid;
  }

  .po-case-metadata {
    width: 100%;
    grid-template-columns: repeat(4, 1fr);
  }

  .po-review-workspace {
    grid-template-columns: 1fr;
  }

  .po-review-canvas {
    grid-row: 1;
  }

  .po-review-evidence {
    grid-row: 2;
    max-height: none;
  }

  .po-review-sidebar {
    grid-row: 3;
  }
}

@media (max-width: 760px) {
  .po-case-header {
    padding-bottom: 10px;
  }

  .po-case-title-row {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .po-case-title-row > div {
    width: 100%;
  }

  .po-case-title-row h1 {
    font-size: 18px;
  }

  .po-case-metadata {
    grid-template-columns: repeat(2, 1fr);
  }

  .po-case-metadata > div {
    min-width: 0;
    padding: 0 10px;
  }

  .po-case-metadata > div:nth-child(odd) {
    border-left: 0;
    padding-left: 0;
  }

  .po-review-sidebar {
    grid-template-columns: 1fr;
  }

  .po-review-sidebar :deep(.po-audit-panel) {
    grid-column: auto;
    border-top: 1px solid var(--po-hairline);
    border-left: 0;
  }

  .po-review-sidebar :deep(.po-report-panel) {
    border-top: 1px solid var(--po-hairline);
    border-left: 0;
  }

}
</style>
