<template>
  <section class="po-report-panel" :aria-label="t('po.report.title')">
    <header>
      <div>
        <span>{{ t('po.report.kicker') }}</span>
        <h2>{{ t('po.report.title') }}</h2>
      </div>
      <span v-if="report" :class="['po-report-status', statusClass]">
        {{ statusLabel }}
      </span>
    </header>

    <div v-if="loading" class="po-report-state" aria-live="polite">
      <el-skeleton animated :rows="3" />
    </div>

    <div v-else-if="active" class="po-report-state" aria-live="polite">
      <el-icon class="is-loading" aria-hidden="true"><Loading /></el-icon>
      <strong>{{ t('po.report.generatingTitle') }}</strong>
      <p>{{ t('po.report.generatingBody') }}</p>
    </div>

    <div v-else-if="report?.report" class="po-report-content">
      <div class="po-report-source">
        <el-icon aria-hidden="true"><Stamp /></el-icon>
        <div>
          <strong>{{ sourceLabel }}</strong>
          <span>{{ report.modelName }}</span>
        </div>
      </div>

      <section>
        <h3>{{ t('po.report.impression') }}</h3>
        <p>{{ report.report.impression }}</p>
      </section>
      <section>
        <h3>{{ t('po.report.draftAssessment') }}</h3>
        <p>{{ report.report.draft_assessment }}</p>
      </section>
      <section>
        <h3>{{ t('po.report.keyFindings') }}</h3>
        <ul>
          <li v-for="finding in report.report.key_findings" :key="finding">{{ finding }}</li>
        </ul>
      </section>
      <section>
        <h3>{{ t('po.report.concordance') }}</h3>
        <p>{{ t(`po.report.concordanceValues.${report.report.clinicopathologic_concordance}`) }}</p>
      </section>
      <section v-if="report.report.suggested_review_steps?.length">
        <h3>{{ t('po.report.reviewSteps') }}</h3>
        <ol>
          <li v-for="step in report.report.suggested_review_steps" :key="step">{{ step }}</li>
        </ol>
      </section>

      <div :class="['po-report-notice', { fallback: report.status === 'FALLBACK' }]">
        <el-icon aria-hidden="true"><WarningFilled /></el-icon>
        <div>
          <strong>{{ report.status === 'FALLBACK' ? t('po.report.fallbackTitle') : t('po.report.draftNoticeTitle') }}</strong>
          <p>{{ report.status === 'FALLBACK' ? t('po.report.fallbackBody') : t('po.report.draftNoticeBody') }}</p>
        </div>
      </div>

      <el-button v-if="canGenerate" :icon="RefreshRight" :loading="starting" @click="$emit('generate')">
        {{ t('po.report.regenerate') }}
      </el-button>
    </div>

    <div v-else-if="error" class="po-report-state" role="alert">
      <el-icon aria-hidden="true"><WarningFilled /></el-icon>
      <strong>{{ t('po.report.unavailableTitle') }}</strong>
      <p>{{ t('po.report.unavailableBody') }}</p>
      <el-button :icon="RefreshRight" @click="$emit('retry')">{{ t('po.analysis.retry') }}</el-button>
    </div>

    <div v-else class="po-report-state">
      <el-icon aria-hidden="true"><Document /></el-icon>
      <strong>{{ hasDecision ? t('po.report.readyTitle') : t('po.report.waitingTitle') }}</strong>
      <p>{{ hasDecision ? t('po.report.readyBody') : t('po.report.waitingBody') }}</p>
      <el-button
        v-if="hasDecision && canGenerate"
        type="primary"
        :icon="MagicStick"
        :loading="starting"
        @click="$emit('generate')"
      >
        {{ t('po.report.generate') }}
      </el-button>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import {
  Document,
  Loading,
  MagicStick,
  RefreshRight,
  Stamp,
  WarningFilled,
} from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  report: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  starting: { type: Boolean, default: false },
  error: { type: Object, default: null },
  hasDecision: { type: Boolean, default: false },
  canGenerate: { type: Boolean, default: false },
})

defineEmits(['generate', 'retry'])
const { t } = useI18n()

const active = computed(() => ['QUEUED', 'RUNNING'].includes(props.report?.status))
const statusClass = computed(() => String(props.report?.status || '').toLowerCase())
const statusLabel = computed(() => {
  if (active.value) return t('po.report.statusGenerating')
  if (props.report?.status === 'FALLBACK') return t('po.report.statusFallback')
  return t('po.report.statusDraft')
})
const sourceLabel = computed(() => {
  const key = `po.report.sources.${props.report?.generatorSource}`
  return t(key)
})
</script>

<style scoped>
.po-report-panel {
  padding: 16px;
  border-top: 1px solid var(--po-hairline);
}

.po-report-panel > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--po-hairline);
}

.po-report-panel > header > div {
  display: grid;
}

.po-report-panel > header span:first-child {
  color: var(--po-harbor);
  font-size: 9px;
  font-weight: 700;
}

.po-report-panel h2 {
  margin: 3px 0 0;
  color: var(--po-ink);
  font-size: 15px;
  font-weight: 650;
}

.po-report-status {
  padding: 3px 5px;
  border: 1px solid var(--po-hairline);
  border-radius: 3px;
  color: var(--po-ink-muted);
  font-size: 8px;
  font-weight: 700;
  white-space: nowrap;
}

.po-report-status.succeeded {
  border-color: oklch(0.82 0.06 155);
  color: var(--po-verified);
}

.po-report-status.fallback {
  border-color: oklch(0.82 0.06 75);
  color: var(--po-attention);
}

.po-report-state {
  display: grid;
  justify-items: start;
  gap: 8px;
  padding: 16px 0 2px;
}

.po-report-state > .el-icon {
  color: var(--po-harbor);
  font-size: 20px;
}

.po-report-state strong {
  color: var(--po-ink);
  font-size: 11px;
}

.po-report-state p {
  margin: 0;
  color: var(--po-ink-muted);
  font-size: 10px;
  line-height: 1.5;
}

.po-report-content {
  display: grid;
  gap: 13px;
  padding-top: 13px;
}

.po-report-source {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px;
  border: 1px solid var(--po-hairline);
  border-radius: var(--po-radius-sm);
  background: var(--po-canvas);
  color: var(--po-verified);
}

.po-report-source > div {
  display: grid;
  min-width: 0;
}

.po-report-source strong {
  color: var(--po-ink);
  font-size: 9px;
}

.po-report-source span {
  margin-top: 2px;
  overflow-wrap: anywhere;
  color: var(--po-ink-muted);
  font-size: 8px;
}

.po-report-content section {
  padding-top: 10px;
  border-top: 1px solid var(--po-hairline);
}

.po-report-content h3 {
  margin: 0 0 5px;
  color: var(--po-ink-muted);
  font-size: 9px;
  font-weight: 700;
}

.po-report-content p,
.po-report-content li {
  color: var(--po-ink);
  font-size: 10px;
  line-height: 1.5;
}

.po-report-content section > p {
  margin: 0;
}

.po-report-content ul,
.po-report-content ol {
  display: grid;
  gap: 4px;
  margin: 0;
  padding-left: 17px;
}

.po-report-notice {
  display: flex;
  align-items: flex-start;
  gap: 7px;
  padding: 9px;
  border: 1px solid oklch(0.86 0.04 75);
  border-radius: 4px;
  background: var(--po-attention-soft);
  color: var(--po-attention);
}

.po-report-notice > div {
  display: grid;
}

.po-report-notice strong {
  font-size: 9px;
}

.po-report-notice p {
  margin: 3px 0 0;
  color: var(--po-ink-muted);
  font-size: 9px;
}

.po-report-content > .el-button {
  width: 100%;
}
</style>
