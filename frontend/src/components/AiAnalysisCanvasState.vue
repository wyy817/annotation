<template>
  <section class="po-analysis-canvas-state" aria-live="polite">
    <div class="po-analysis-state-mark" aria-hidden="true">
      <el-icon><component :is="stateIcon" /></el-icon>
    </div>
    <div class="po-analysis-state-copy">
      <span>{{ stageLabel }}</span>
      <h2>{{ t(stateTitleKey) }}</h2>
      <p>{{ t(stateBodyKey) }}</p>
    </div>
    <div v-if="active" class="po-analysis-state-progress">
      <el-progress :percentage="progress" :show-text="false" :stroke-width="6" />
      <strong class="po-tabular">{{ t('po.analysis.progress', { value: progress }) }}</strong>
    </div>
    <el-button
      v-if="showAction"
      type="primary"
      :icon="actionIcon"
      :loading="loading || starting"
      @click="$emit(error || artifactError ? 'retry' : 'run')"
    >
      {{ t(error || artifactError ? 'po.analysis.retry' : 'po.analysis.runAnalysis') }}
    </el-button>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import {
  Clock,
  PictureRounded,
  RefreshRight,
  VideoPlay,
  WarningFilled,
} from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  analysis: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  starting: { type: Boolean, default: false },
  error: { type: Object, default: null },
  artifactError: { type: Object, default: null },
  canRun: { type: Boolean, default: false },
})

defineEmits(['run', 'retry'])

const { t, te } = useI18n()
const active = computed(() => ['QUEUED', 'RUNNING'].includes(props.analysis?.status))
const progress = computed(() => Math.round((props.analysis?.stageProgress || 0) * 100))
const stageLabel = computed(() => {
  if (props.artifactError) return t('po.analysis.artifactStage')
  const key = `po.analysis.stages.${props.analysis?.stage || 'queued'}`
  return te(key) ? t(key) : props.analysis?.stage || t('po.analysis.stages.queued')
})
const stateTitleKey = computed(() => {
  if (props.artifactError) return 'po.analysis.artifactUnavailableTitle'
  if (props.error) return 'po.analysis.unavailableTitle'
  if (props.loading && !props.analysis) return 'po.analysis.loadingTitle'
  if (!props.analysis) return 'po.analysis.noAnalysisTitle'
  if (active.value) return 'po.analysis.runningTitle'
  return 'po.analysis.failedTitle'
})
const stateBodyKey = computed(() => {
  if (props.artifactError) return 'po.analysis.artifactUnavailableBody'
  if (props.error) return 'po.analysis.unavailableBody'
  if (props.loading && !props.analysis) return 'po.analysis.loadingBody'
  if (!props.analysis) return 'po.analysis.noAnalysisBody'
  if (active.value) return 'po.analysis.runningBody'
  return 'po.analysis.failedBody'
})
const stateIcon = computed(() => {
  if (props.artifactError) return PictureRounded
  if (props.error || (props.analysis && !active.value)) return WarningFilled
  if (active.value || props.loading) return Clock
  return VideoPlay
})
const actionIcon = computed(() => props.error || props.artifactError ? RefreshRight : VideoPlay)
const showAction = computed(() => (
  props.canRun && !active.value && !props.loading && !props.starting
))
</script>

<style scoped>
.po-analysis-canvas-state {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 14px;
  width: 100%;
  height: clamp(500px, calc(100vh - 230px), 760px);
  min-height: 500px;
  padding: 36px;
  border: 1px solid oklch(0.28 0.016 230);
  border-radius: var(--po-radius-sm);
  background: var(--po-viewer);
  color: var(--po-viewer-ink);
  text-align: center;
  box-sizing: border-box;
}

.po-analysis-state-mark {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border: 1px solid oklch(0.37 0.05 230);
  border-radius: var(--po-radius-sm);
  background: var(--po-viewer-panel);
  color: oklch(0.76 0.09 230);
  font-size: 22px;
}

.po-analysis-state-copy {
  max-width: 430px;
}

.po-analysis-state-copy > span {
  color: oklch(0.72 0.018 230);
  font-size: 9px;
  font-weight: 650;
}

.po-analysis-state-copy h2 {
  margin: 5px 0 0;
  color: var(--po-viewer-ink);
  font-size: 17px;
  font-weight: 650;
}

.po-analysis-state-copy p {
  margin: 7px 0 0;
  color: oklch(0.72 0.018 230);
  font-size: 11px;
  line-height: 1.55;
}

.po-analysis-state-progress {
  width: min(360px, 80%);
}

.po-analysis-state-progress strong {
  display: block;
  margin-top: 7px;
  color: oklch(0.76 0.018 230);
  font-size: 9px;
}

.po-analysis-state-progress :deep(.el-progress-bar__outer) {
  background: oklch(0.24 0.012 230);
}

@media (max-width: 760px) {
  .po-analysis-canvas-state {
    height: 360px;
    min-height: 360px;
    padding: 24px;
  }
}
</style>
