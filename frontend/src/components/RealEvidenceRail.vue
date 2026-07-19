<template>
  <aside class="po-real-evidence" :aria-label="t('po.evidence.title')">
    <header class="po-real-evidence-heading">
      <div>
        <span>{{ t(analysis?.isPrecomputed ? 'po.mode.precomputedModelOutput' : 'po.mode.localModelOutput') }}</span>
        <h2>{{ t('po.evidence.title') }}</h2>
      </div>
      <span class="po-real-model-tag">CLAM-MB / 10 folds</span>
    </header>

    <template v-if="succeeded">
      <section class="po-real-evidence-section">
        <h3>{{ t('po.evidence.aiSummary') }}</h3>
        <div :class="['po-real-summary', analysis.tumorEvidenceScore >= 0.5 ? 'high' : 'low']">
          <el-icon aria-hidden="true">
            <WarningFilled v-if="analysis.tumorEvidenceScore >= 0.5" />
            <CircleCheck v-else />
          </el-icon>
          <p>
            {{ t(
              analysis.tumorEvidenceScore >= 0.5
                ? 'po.evidence.realSummaryHigh'
                : 'po.evidence.realSummaryLow',
              {
                score: formatPercent(analysis.tumorEvidenceScore, locale),
                count: analysis.candidateRegionCount || 0,
              },
            ) }}
          </p>
        </div>

        <dl class="po-real-metrics">
          <div>
            <dt>{{ t('po.evidence.modelScore') }}</dt>
            <dd class="po-tabular">{{ formatPercent(analysis.tumorEvidenceScore, locale) }}</dd>
          </div>
          <div>
            <dt>{{ t('po.evidence.foldDisagreement') }}</dt>
            <dd class="po-tabular">{{ formatPercent(analysis.ensembleDisagreement, locale) }}</dd>
          </div>
          <div>
            <dt>{{ t('po.evidence.candidateCount') }}</dt>
            <dd class="po-tabular">{{ analysis.candidateRegionCount || 0 }}</dd>
          </div>
          <div>
            <dt>{{ t('po.evidence.areaProxy') }}</dt>
            <dd class="po-tabular">{{ formatArea(analysis.candidateAreaProxyMm2) }}</dd>
          </div>
        </dl>
      </section>

      <section class="po-real-evidence-section">
        <h3>{{ t('po.evidence.regions') }}</h3>
        <div v-if="analysis.candidates?.length" class="po-real-region-list">
          <button
            v-for="candidate in analysis.candidates"
            :key="candidate.regionId"
            type="button"
            :class="['po-real-region', { selected: selectedRegion === candidate.regionId }]"
            :aria-pressed="selectedRegion === candidate.regionId"
            @click="$emit('select-region', candidate.regionId)"
          >
            <span class="po-real-region-number">{{ candidate.regionId.replace('R', '') }}</span>
            <span class="po-real-region-copy">
              <strong>{{ t('po.evidence.candidateRegion', { id: candidate.regionId }) }}</strong>
              <small>
                {{ t('po.evidence.evidenceIndex', {
                  score: formatPercent(candidate.maxEvidenceIndex, locale),
                }) }}
              </small>
            </span>
            <span class="po-real-region-area po-tabular">
              {{ formatArea(candidate.areaProxyMm2) }}
            </span>
          </button>
        </div>
        <div v-else class="po-no-real-regions">
          <el-icon aria-hidden="true"><CircleCheck /></el-icon>
          <span>{{ t('po.evidence.noCandidateRegions') }}</span>
        </div>
      </section>

      <section v-if="analysis.validation?.available" class="po-real-evidence-section">
        <h3>{{ t('po.evidence.publicCompatibility') }}</h3>
        <div class="po-overlap-result">
          <el-icon aria-hidden="true"><CircleCheckFilled /></el-icon>
          <p>{{ t('po.evidence.annotationOverlapConfirmed') }}</p>
        </div>
        <dl class="po-overlap-metrics">
          <div>
            <dt>{{ t('po.evidence.annotationCoverageProxy') }}</dt>
            <dd>{{ formatPercent(analysis.validation.annotationCoverageProxy, locale) }}</dd>
          </div>
          <div>
            <dt>{{ t('po.evidence.candidatePrecisionProxy') }}</dt>
            <dd>{{ formatPercent(analysis.validation.candidatePrecisionProxy, locale) }}</dd>
          </div>
        </dl>
        <p class="po-overlap-notice">{{ t('po.evidence.compatibilityNotice') }}</p>
      </section>

      <section class="po-real-evidence-section po-real-provenance">
        <h3>{{ t('po.evidence.provenance') }}</h3>
        <dl>
          <div>
            <dt>{{ t('po.case.model') }}</dt>
            <dd>{{ analysis.modelName }}</dd>
          </div>
          <div>
            <dt>{{ t('po.evidence.sourceSlide') }}</dt>
            <dd>{{ analysis.slide?.fileName || t('po.evidence.sourceDomainDemo') }}</dd>
          </div>
          <div>
            <dt>{{ t('po.evidence.evidenceBand') }}</dt>
            <dd>{{ t(`po.evidenceBands.${analysis.evidenceBand}`) }}</dd>
          </div>
          <div v-if="analysis.slide?.patchCount">
            <dt>{{ t('po.evidence.patchCount') }}</dt>
            <dd class="po-tabular">{{ formatInteger(analysis.slide.patchCount) }}</dd>
          </div>
          <div v-if="analysis.runtime?.device">
            <dt>{{ t('po.evidence.runtime') }}</dt>
            <dd>{{ analysis.runtime.device.toUpperCase() }} / {{ formatSeconds(analysis.runtime.totalSeconds) }}</dd>
          </div>
        </dl>
        <div class="po-real-limitation">
          <el-icon aria-hidden="true"><InfoFilled /></el-icon>
          <div>
            <strong>{{ t('po.case.limitations') }}</strong>
            <p>{{ t('po.evidence.realClinicalNotice') }}</p>
          </div>
        </div>
        <el-button
          v-if="canRun && !analysis.isPrecomputed"
          class="po-run-again"
          :icon="RefreshRight"
          :loading="starting"
          @click="$emit('run')"
        >
          {{ t('po.analysis.runAgain') }}
        </el-button>
      </section>
    </template>

    <section v-else class="po-analysis-rail-state" aria-live="polite">
      <el-icon aria-hidden="true"><component :is="stateIcon" /></el-icon>
      <div>
        <h3>{{ t(stateTitleKey) }}</h3>
        <p>{{ t(stateBodyKey) }}</p>
      </div>
      <div v-if="active" class="po-analysis-progress">
        <div>
          <span>{{ stageLabel }}</span>
          <strong class="po-tabular">{{ progress }}%</strong>
        </div>
        <el-progress :percentage="progress" :show-text="false" :stroke-width="5" />
      </div>
      <el-button
        v-if="showStateAction"
        type="primary"
        :icon="stateActionIcon"
        :loading="starting || loading"
        @click="$emit(error ? 'retry' : 'run')"
      >
        {{ t(error ? 'po.analysis.retry' : 'po.analysis.runAnalysis') }}
      </el-button>
    </section>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import {
  CircleCheck,
  CircleCheckFilled,
  Clock,
  InfoFilled,
  RefreshRight,
  VideoPlay,
  WarningFilled,
} from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { formatPercent } from '@/utils/formatters'

const props = defineProps({
  analysis: { type: Object, default: null },
  selectedRegion: { type: String, default: null },
  loading: { type: Boolean, default: false },
  starting: { type: Boolean, default: false },
  error: { type: Object, default: null },
  canRun: { type: Boolean, default: false },
})

defineEmits(['select-region', 'run', 'retry'])

const { t, te, locale } = useI18n()
const succeeded = computed(() => props.analysis?.status === 'SUCCEEDED')
const active = computed(() => ['QUEUED', 'RUNNING'].includes(props.analysis?.status))
const progress = computed(() => Math.round((props.analysis?.stageProgress || 0) * 100))
const stageLabel = computed(() => {
  const key = `po.analysis.stages.${props.analysis?.stage || 'queued'}`
  return te(key) ? t(key) : props.analysis?.stage || t('po.analysis.stages.queued')
})
const stateTitleKey = computed(() => {
  if (props.error) return 'po.analysis.unavailableTitle'
  if (props.loading && !props.analysis) return 'po.analysis.loadingTitle'
  if (!props.analysis) return 'po.analysis.noAnalysisTitle'
  if (active.value) return 'po.analysis.runningTitle'
  return 'po.analysis.failedTitle'
})
const stateBodyKey = computed(() => {
  if (props.error) return 'po.analysis.unavailableBody'
  if (props.loading && !props.analysis) return 'po.analysis.loadingBody'
  if (!props.analysis) return 'po.analysis.noAnalysisBody'
  if (active.value) return 'po.analysis.runningBody'
  return 'po.analysis.failedBody'
})
const stateIcon = computed(() => {
  if (props.error || (props.analysis && !active.value)) return WarningFilled
  if (active.value || props.loading) return Clock
  return VideoPlay
})
const showStateAction = computed(() => (
  props.canRun && !active.value && !props.loading
))
const stateActionIcon = computed(() => props.error ? RefreshRight : VideoPlay)

function formatArea(value) {
  if (value === null || value === undefined) return '-'
  return `${new Intl.NumberFormat(locale.value, {
    minimumFractionDigits: 3,
    maximumFractionDigits: 3,
  }).format(value)} mm²`
}

function formatInteger(value) {
  return new Intl.NumberFormat(locale.value).format(value)
}

function formatSeconds(value) {
  if (value === null || value === undefined) return '-'
  return `${new Intl.NumberFormat(locale.value, { maximumFractionDigits: 1 }).format(value)} s`
}
</script>

<style scoped>
.po-real-evidence {
  overflow: hidden;
  border: 1px solid var(--po-hairline);
  border-radius: var(--po-radius-md);
  background: var(--po-paper);
}

.po-real-evidence-heading,
.po-real-evidence-section,
.po-analysis-rail-state {
  padding: 15px;
}

.po-real-evidence-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid var(--po-hairline);
}

.po-real-evidence-heading > div > span {
  color: var(--po-verified);
  font-size: 9px;
  font-weight: 700;
}

.po-real-evidence-heading h2 {
  margin: 3px 0 0;
  color: var(--po-ink);
  font-size: 16px;
  font-weight: 650;
}

.po-real-model-tag {
  padding: 3px 5px;
  border: 1px solid var(--po-hairline);
  border-radius: 3px;
  color: var(--po-ink-muted);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 9px;
  white-space: nowrap;
}

.po-real-evidence-section + .po-real-evidence-section {
  border-top: 1px solid var(--po-hairline);
}

.po-real-evidence-section h3,
.po-analysis-rail-state h3 {
  margin: 0 0 9px;
  color: var(--po-ink-muted);
  font-size: 10px;
  font-weight: 700;
}

.po-real-summary {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 8px;
  border: 1px solid var(--po-hairline);
  border-radius: 4px;
  background: var(--po-surface-subtle);
}

.po-real-summary.high {
  border-color: oklch(0.85 0.06 25);
  background: var(--po-risk-soft);
  color: var(--po-risk);
}

.po-real-summary.low {
  border-color: oklch(0.88 0.05 155);
  background: var(--po-verified-soft);
  color: var(--po-verified);
}

.po-real-summary .el-icon {
  flex: 0 0 auto;
  margin-top: 2px;
}

.po-real-summary p,
.po-overlap-result p {
  margin: 0;
  color: var(--po-ink);
  font-size: 11px;
  line-height: 1.5;
}

.po-real-metrics,
.po-overlap-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin: 12px 0 0;
  border-top: 1px solid var(--po-hairline);
  border-left: 1px solid var(--po-hairline);
}

.po-real-metrics > div,
.po-overlap-metrics > div {
  display: grid;
  gap: 2px;
  min-width: 0;
  padding: 8px;
  border-right: 1px solid var(--po-hairline);
  border-bottom: 1px solid var(--po-hairline);
}

.po-real-metrics dt,
.po-overlap-metrics dt {
  color: var(--po-ink-muted);
  font-size: 8px;
}

.po-real-metrics dd,
.po-overlap-metrics dd {
  margin: 0;
  color: var(--po-ink);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 11px;
  font-weight: 650;
}

.po-real-region-list {
  display: grid;
  gap: 5px;
}

.po-real-region {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 48px;
  padding: 6px 8px;
  border: 1px solid var(--po-hairline);
  border-radius: var(--po-radius-sm);
  background: var(--po-paper);
  color: var(--po-ink);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--po-motion-fast) var(--po-ease-state),
    background-color var(--po-motion-fast) var(--po-ease-state);
}

.po-real-region:hover,
.po-real-region.selected {
  border-color: var(--po-eosin);
  background: var(--po-eosin-soft);
}

.po-real-region:focus-visible {
  outline: 3px solid var(--po-eosin-soft);
  outline-offset: 2px;
}

.po-real-region-number {
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border: 2px solid var(--po-eosin);
  border-radius: 999px;
  background: oklch(0.4 0.12 350);
  color: var(--po-paper);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
  font-weight: 700;
}

.po-real-region-copy {
  display: grid;
  min-width: 0;
  line-height: 1.25;
}

.po-real-region-copy strong {
  overflow: hidden;
  font-size: 11px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.po-real-region-copy small,
.po-real-region-area {
  color: var(--po-ink-muted);
  font-size: 9px;
}

.po-real-region-copy small {
  margin-top: 3px;
}

.po-no-real-regions,
.po-overlap-result {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 8px;
  border: 1px solid oklch(0.88 0.05 155);
  border-radius: 4px;
  background: var(--po-verified-soft);
  color: var(--po-verified);
  font-size: 10px;
  font-weight: 600;
}

.po-overlap-result .el-icon {
  flex: 0 0 auto;
  margin-top: 2px;
}

.po-overlap-notice {
  margin: 8px 0 0;
  color: var(--po-ink-muted);
  font-size: 8px;
  line-height: 1.45;
}

.po-real-provenance > dl {
  display: grid;
  gap: 8px;
  margin: 0;
}

.po-real-provenance > dl > div {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr);
  gap: 8px;
  font-size: 9px;
}

.po-real-provenance dt {
  color: var(--po-ink-muted);
}

.po-real-provenance dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--po-ink);
  font-weight: 600;
}

.po-real-limitation {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  margin-top: 12px;
  padding: 9px;
  border: 1px solid oklch(0.86 0.04 75);
  border-radius: 4px;
  background: var(--po-attention-soft);
  color: var(--po-attention);
}

.po-real-limitation .el-icon {
  flex: 0 0 auto;
  margin-top: 1px;
}

.po-real-limitation strong {
  font-size: 9px;
}

.po-real-limitation p {
  margin: 3px 0 0;
  color: var(--po-ink-muted);
  font-size: 9px;
  line-height: 1.5;
}

.po-run-again {
  width: 100%;
  margin-top: 10px;
}

.po-analysis-rail-state {
  display: grid;
  justify-items: start;
  gap: 12px;
  min-height: 260px;
  align-content: center;
}

.po-analysis-rail-state > .el-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: var(--po-radius-sm);
  background: var(--po-harbor-soft);
  color: var(--po-harbor);
  font-size: 17px;
}

.po-analysis-rail-state h3 {
  margin: 0;
  color: var(--po-ink);
  font-size: 13px;
}

.po-analysis-rail-state p {
  margin: 4px 0 0;
  color: var(--po-ink-muted);
  font-size: 10px;
  line-height: 1.5;
}

.po-analysis-progress {
  width: 100%;
}

.po-analysis-progress > div {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
  color: var(--po-ink-muted);
  font-size: 9px;
}
</style>
