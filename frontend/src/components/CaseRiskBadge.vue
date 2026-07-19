<template>
  <span :class="['po-risk-badge', 'po-risk-' + band]">
    <el-icon aria-hidden="true">
      <WarningFilled v-if="band === 'high'" />
      <Clock v-else-if="band === 'intermediate' || band === 'pending'" />
      <CircleCheck v-else />
    </el-icon>
    <span>{{ t('po.risk.' + band) }}</span>
    <span v-if="hasScore" class="po-risk-value">{{ formattedScore }}</span>
  </span>
</template>

<script setup>
import { computed } from 'vue'
import { CircleCheck, Clock, WarningFilled } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { formatPercent, getRiskBand } from '@/utils/formatters'

const props = defineProps({
  score: {
    type: Number,
    default: null,
  },
})

const { t, locale } = useI18n()
const hasScore = computed(() => Number.isFinite(props.score))
const band = computed(() => getRiskBand(props.score))
const formattedScore = computed(() => formatPercent(props.score, locale.value))
</script>

<style scoped>
.po-risk-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 24px;
  padding: 0 7px;
  border: 1px solid;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 650;
  white-space: nowrap;
}

.po-risk-badge .el-icon {
  font-size: 12px;
}

.po-risk-value {
  padding-left: 6px;
  border-left: 1px solid currentColor;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-variant-numeric: tabular-nums;
  opacity: 0.9;
}

.po-risk-high {
  border-color: oklch(0.85 0.06 25);
  background: var(--po-risk-soft);
  color: var(--po-risk);
}

.po-risk-intermediate {
  border-color: oklch(0.87 0.065 80);
  background: var(--po-attention-soft);
  color: var(--po-attention);
}

.po-risk-low {
  border-color: oklch(0.88 0.05 155);
  background: var(--po-verified-soft);
  color: var(--po-verified);
}

.po-risk-pending {
  border-color: var(--po-hairline-strong);
  background: var(--po-surface-subtle);
  color: var(--po-ink-muted);
}
</style>
