<template>
  <aside class="po-evidence-rail" :aria-label="t('po.evidence.title')">
    <header class="po-evidence-heading">
      <div>
        <span class="po-evidence-kicker">{{ t('po.mode.simulatedOutput') }}</span>
        <h2>{{ t('po.evidence.title') }}</h2>
      </div>
      <span class="po-model-version">{{ caseItem.ai.version }}</span>
    </header>

    <section class="po-evidence-section">
      <h3>{{ t('po.evidence.aiSummary') }}</h3>
      <p class="po-ai-summary">{{ t(caseItem.ai.summaryKey) }}</p>
      <div :class="['po-concordance', { discordant: isDiscordant }]">
        <el-icon aria-hidden="true">
          <WarningFilled v-if="isDiscordant" />
          <CircleCheck v-else />
        </el-icon>
        <span>{{ t(isDiscordant ? 'po.evidence.discordant' : 'po.evidence.concordant') }}</span>
      </div>
    </section>

    <section class="po-evidence-section">
      <h3>{{ t('po.evidence.regions') }}</h3>
      <div class="po-region-list">
        <button
          v-for="region in caseItem.ai.regions"
          :key="region.id"
          type="button"
          :class="['po-region-button', { selected: selectedRegion === region.id }]"
          :aria-pressed="selectedRegion === region.id"
          @click="$emit('select-region', region.id)"
        >
          <span class="po-region-number" aria-hidden="true">{{ region.id.replace('R', '') }}</span>
          <span class="po-region-copy">
            <strong>{{ t(region.labelKey) }}</strong>
            <small>{{ t('po.evidence.regionScore', { score: formatPercent(region.score, locale) }) }}</small>
          </span>
          <el-icon aria-hidden="true"><Aim /></el-icon>
        </button>
      </div>
    </section>

    <section class="po-evidence-section">
      <h3>{{ t('po.evidence.reportEvidence') }}</h3>
      <blockquote>{{ t(caseItem.ai.reportKey) }}</blockquote>
    </section>

    <section class="po-evidence-section po-provenance">
      <h3>{{ t('po.evidence.provenance') }}</h3>
      <dl>
        <div>
          <dt>{{ t('po.case.model') }}</dt>
          <dd>{{ caseItem.ai.name }}</dd>
        </div>
        <div>
          <dt>{{ t('po.case.modelProvenance') }}</dt>
          <dd>{{ caseItem.ai.outputType }}</dd>
        </div>
        <div>
          <dt>{{ t('po.confidence.label') }}</dt>
          <dd class="po-tabular">{{ formatPercent(caseItem.ai.confidence, locale) }}</dd>
        </div>
      </dl>
      <div class="po-limitation">
        <strong>{{ t('po.case.limitations') }}</strong>
        <p>{{ t('po.case.limitationText') }}</p>
      </div>
    </section>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { Aim, CircleCheck, WarningFilled } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { formatPercent } from '@/utils/formatters'

const props = defineProps({
  caseItem: {
    type: Object,
    required: true,
  },
  selectedRegion: {
    type: String,
    default: null,
  },
})

defineEmits(['select-region'])

const { t, locale } = useI18n()
const isDiscordant = computed(() => (
  props.caseItem.workflow.reviewTriggers.includes('image_text_discordance')
))
</script>

<style scoped>
.po-evidence-rail {
  overflow: hidden;
  border: 1px solid var(--po-hairline);
  border-radius: var(--po-radius-md);
  background: var(--po-paper);
}

.po-evidence-heading,
.po-evidence-section {
  padding: 15px;
}

.po-evidence-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid var(--po-hairline);
}

.po-evidence-kicker {
  color: oklch(0.4 0.12 350);
  font-size: 9px;
  font-weight: 700;
}

.po-evidence-heading h2 {
  margin: 3px 0 0;
  color: var(--po-ink);
  font-size: 16px;
  font-weight: 650;
  line-height: 1.35;
}

.po-model-version {
  padding: 3px 5px;
  border: 1px solid var(--po-hairline);
  border-radius: 3px;
  color: var(--po-ink-muted);
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 9px;
  white-space: nowrap;
}

.po-evidence-section + .po-evidence-section {
  border-top: 1px solid var(--po-hairline);
}

.po-evidence-section h3 {
  margin: 0 0 9px;
  color: var(--po-ink-muted);
  font-size: 10px;
  font-weight: 700;
}

.po-ai-summary {
  margin: 0;
  color: var(--po-ink);
  font-size: 12px;
  line-height: 1.55;
}

.po-concordance {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 11px;
  padding: 7px 8px;
  border: 1px solid oklch(0.88 0.05 155);
  border-radius: 4px;
  background: var(--po-verified-soft);
  color: var(--po-verified);
  font-size: 10px;
  font-weight: 600;
}

.po-concordance.discordant {
  border-color: oklch(0.85 0.06 25);
  background: var(--po-risk-soft);
  color: var(--po-risk);
}

.po-concordance .el-icon {
  flex: 0 0 auto;
  margin-top: 1px;
}

.po-region-list {
  display: grid;
  gap: 5px;
}

.po-region-button {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr) 16px;
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

.po-region-button:hover {
  border-color: oklch(0.79 0.11 350);
  background: var(--po-eosin-soft);
}

.po-region-button:focus-visible {
  border-color: var(--po-eosin);
  outline: 3px solid var(--po-eosin-soft);
  outline-offset: 2px;
}

.po-region-button.selected {
  border-color: var(--po-eosin);
  background: var(--po-eosin-soft);
}

.po-region-number {
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

.po-region-copy {
  display: grid;
  min-width: 0;
  line-height: 1.25;
}

.po-region-copy strong {
  overflow: hidden;
  font-size: 11px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.po-region-copy small {
  margin-top: 3px;
  color: var(--po-ink-muted);
  font-size: 9px;
}

.po-region-button > .el-icon {
  color: var(--po-ink-muted);
}

blockquote {
  margin: 0;
  padding: 1px 0 1px 10px;
  border-left: 2px solid var(--po-eosin);
  color: var(--po-ink);
  font-size: 11px;
  font-style: normal;
  line-height: 1.55;
}

.po-provenance dl {
  display: grid;
  gap: 7px;
  margin: 0;
}

.po-provenance dl > div {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 8px;
  font-size: 9px;
}

.po-provenance dt {
  color: var(--po-ink-muted);
}

.po-provenance dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--po-ink);
  font-weight: 600;
}

.po-limitation {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--po-hairline);
}

.po-limitation strong {
  color: var(--po-attention);
  font-size: 9px;
}

.po-limitation p {
  margin: 3px 0 0;
  color: var(--po-ink-muted);
  font-size: 9px;
  line-height: 1.5;
}
</style>
