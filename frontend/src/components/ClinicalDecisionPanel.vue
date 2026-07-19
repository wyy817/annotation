<template>
  <section class="po-decision-panel" :aria-label="t('po.decision.title')">
    <header>
      <span class="po-panel-kicker">{{ t('po.decision.title') }}</span>
      <h2>{{ reviewerMode ? t('po.roles.seniorPathologist') : t('po.roles.pathologist') }}</h2>
      <p>{{ t(reviewerMode ? 'po.decision.reviewerPrompt' : 'po.decision.pathologistPrompt') }}</p>
    </header>

    <div v-if="caseItem.workflow.reviewTriggers.length" class="po-review-route">
      <strong>{{ t('po.queue.conditionalReason') }}</strong>
      <ul>
        <li v-for="reason in caseItem.workflow.reviewTriggers" :key="reason">
          <el-icon aria-hidden="true"><Flag /></el-icon>
          {{ t('po.reviewReasons.' + reason) }}
        </li>
      </ul>
    </div>

    <div v-if="currentDecision" class="po-current-decision">
      <span>{{ t('po.decision.currentDecision') }}</span>
      <strong>{{ t('po.decision.' + currentDecision.type) }}</strong>
      <small>
        {{ currentDecision.actor }} / {{ formatDateTime(currentDecision.at, locale) }}
      </small>
      <p v-if="currentDecision.note">
        {{ localizeNote(currentDecision.note) }}
      </p>
    </div>

    <div v-if="canStart" class="po-start-review po-decision-actions">
      <el-button type="primary" :icon="View" @click="$emit('start')">
        {{ t('po.case.startReview') }}
      </el-button>
    </div>

    <form
      v-else-if="canDecide"
      class="po-decision-form po-decision-actions"
      @submit.prevent="submitDecision"
    >
      <div class="po-decision-options" role="radiogroup" :aria-label="t('po.decision.title')">
        <button
          v-for="option in decisionOptions"
          :key="option.value"
          type="button"
          :class="{ selected: selectedDecision === option.value }"
          role="radio"
          :aria-checked="selectedDecision === option.value"
          @click="selectedDecision = option.value"
        >
          <span class="po-option-icon" aria-hidden="true">
            <el-icon><component :is="option.icon" /></el-icon>
          </span>
          <span class="po-option-copy">
            <strong>{{ t(option.labelKey) }}</strong>
            <small>{{ t(option.helpKey) }}</small>
          </span>
          <span class="po-radio-mark" aria-hidden="true"></span>
        </button>
      </div>

      <label class="po-note-field">
        <span>
          {{ t('po.decision.note') }}
          <small v-if="requiresNote">{{ t('po.form.required') }}</small>
        </span>
        <el-input
          v-model="note"
          type="textarea"
          :rows="3"
          resize="none"
          :placeholder="t('po.decision.notePlaceholder')"
          :aria-invalid="Boolean(validationError)"
        />
      </label>
      <p v-if="validationError" class="po-validation-error" role="alert">
        <el-icon aria-hidden="true"><WarningFilled /></el-icon>
        {{ validationError }}
      </p>

      <el-button
        type="primary"
        native-type="submit"
        :disabled="!selectedDecision"
        :icon="DocumentChecked"
      >
        {{ t(reviewerMode ? 'po.decision.submitReview' : 'po.decision.submit') }}
      </el-button>
    </form>

    <div v-else-if="readOnlyReason" class="po-readonly-state">
      <el-icon aria-hidden="true"><Lock /></el-icon>
      <div>
        <strong>{{ readOnlyReason.title }}</strong>
        <p>{{ readOnlyReason.body }}</p>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import {
  DocumentChecked,
  Flag,
  Lock,
  View,
  WarningFilled,
} from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { formatDateTime } from '@/utils/formatters'

const props = defineProps({
  caseItem: {
    type: Object,
    required: true,
  },
  role: {
    type: String,
    required: true,
  },
  analysisReady: {
    type: Boolean,
    default: true,
  },
})

const emit = defineEmits(['start', 'submit'])
const { t, locale, te } = useI18n()

const selectedDecision = ref('')
const note = ref('')
const validationError = ref('')

const reviewerMode = computed(() => props.role === 'REVIEWER')
const currentDecision = computed(() => props.caseItem.workflow.decision)
const canStart = computed(() => (
  props.role === 'ANNOTATOR'
  && props.analysisReady
  && ['ready', 'returned'].includes(props.caseItem.workflow.status)
))
const canPathologistDecide = computed(() => (
  props.role === 'ANNOTATOR' && props.caseItem.workflow.status === 'in_review'
))
const canReviewerDecide = computed(() => (
  props.role === 'REVIEWER' && props.caseItem.workflow.status === 'senior_review'
))
const canDecide = computed(() => canPathologistDecide.value || canReviewerDecide.value)

const decisionOptions = computed(() => {
  if (reviewerMode.value) {
    return [
      {
        value: 'approve',
        labelKey: 'po.decision.approve',
        helpKey: 'po.decision.approveHelp',
        icon: 'CircleCheck',
      },
      {
        value: 'return',
        labelKey: 'po.decision.return',
        helpKey: 'po.decision.returnHelp',
        icon: 'RefreshLeft',
      },
    ]
  }

  return [
    {
      value: 'accept',
      labelKey: 'po.decision.accept',
      helpKey: 'po.decision.acceptHelp',
      icon: 'Check',
    },
    {
      value: 'modify',
      labelKey: 'po.decision.modify',
      helpKey: 'po.decision.modifyHelp',
      icon: 'EditPen',
    },
    {
      value: 'reject',
      labelKey: 'po.decision.reject',
      helpKey: 'po.decision.rejectHelp',
      icon: 'CloseBold',
    },
    {
      value: 'escalate',
      labelKey: 'po.decision.escalate',
      helpKey: 'po.decision.escalateHelp',
      icon: 'TopRight',
    },
  ]
})

const requiresNote = computed(() => (
  ['modify', 'reject', 'escalate', 'return'].includes(selectedDecision.value)
))

const readOnlyReason = computed(() => {
  if (['AI_DATA_ADMIN', 'SYSTEM_ADMIN'].includes(props.role)) {
    return {
      title: t('po.case.readOnlyTitle'),
      body: t('po.case.readOnlyBody'),
    }
  }
  if (props.caseItem.workflow.status === 'completed') {
    return {
      title: t('po.status.completed'),
      body: t('po.feedback.provenanceNote'),
    }
  }
  if (props.role === 'ANNOTATOR' && !props.analysisReady) {
    return {
      title: t('po.analysis.noAnalysisTitle'),
      body: t('po.analysis.noAnalysisBody'),
    }
  }
  if (props.caseItem.workflow.status === 'senior_review' && props.role === 'ANNOTATOR') {
    return {
      title: t('po.status.senior_review'),
      body: t('po.decision.routedToReview'),
    }
  }
  if (props.role === 'REVIEWER') {
    return {
      title: t('po.queue.scopeReviewer'),
      body: t('po.queue.subtitleReviewer'),
    }
  }
  return null
})

function localizeNote(value) {
  return value.startsWith('po.') && te(value) ? t(value) : value
}

function submitDecision() {
  validationError.value = ''
  if (!selectedDecision.value) return
  if (requiresNote.value && !note.value.trim()) {
    validationError.value = t('po.decision.noteRequired')
    return
  }

  emit('submit', {
    type: selectedDecision.value,
    note: note.value.trim(),
  })
}

watch(
  () => [props.caseItem.id, props.caseItem.workflow.status],
  () => {
    selectedDecision.value = ''
    note.value = ''
    validationError.value = ''
  },
)
</script>

<style scoped>
.po-decision-panel {
  padding: 16px;
}

.po-decision-panel > header {
  padding-bottom: 13px;
  border-bottom: 1px solid var(--po-hairline);
}

.po-panel-kicker {
  color: var(--po-harbor);
  font-size: 9px;
  font-weight: 700;
}

.po-decision-panel h2 {
  margin: 3px 0 0;
  color: var(--po-ink);
  font-size: 16px;
  font-weight: 650;
  line-height: 1.35;
}

.po-decision-panel header p {
  margin: 5px 0 0;
  color: var(--po-ink-muted);
  font-size: 10px;
  line-height: 1.45;
}

.po-review-route,
.po-current-decision {
  margin-top: 12px;
  padding: 10px;
  border: 1px solid var(--po-hairline);
  border-radius: var(--po-radius-sm);
  background: var(--po-canvas);
}

.po-review-route > strong,
.po-current-decision > span {
  color: var(--po-ink-muted);
  font-size: 9px;
  font-weight: 700;
}

.po-review-route ul {
  display: grid;
  gap: 5px;
  margin: 7px 0 0;
  padding: 0;
  list-style: none;
}

.po-review-route li {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--po-attention);
  font-size: 10px;
  font-weight: 600;
}

.po-current-decision {
  display: grid;
}

.po-current-decision strong {
  margin-top: 3px;
  color: var(--po-ink);
  font-size: 12px;
}

.po-current-decision small {
  margin-top: 2px;
  color: var(--po-ink-muted);
  font-size: 9px;
}

.po-current-decision p {
  margin: 8px 0 0;
  padding-top: 8px;
  border-top: 1px solid var(--po-hairline);
  color: var(--po-ink);
  font-size: 10px;
  line-height: 1.5;
}

.po-start-review {
  margin-top: 14px;
}

.po-start-review .el-button {
  width: 100%;
}

.po-decision-form {
  display: grid;
  gap: 13px;
  margin-top: 14px;
}

.po-decision-options {
  display: grid;
  gap: 5px;
}

.po-decision-options button {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) 16px;
  align-items: center;
  gap: 8px;
  min-height: 52px;
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

.po-decision-options button:hover {
  border-color: oklch(0.75 0.015 230);
  background: var(--po-canvas);
}

.po-decision-options button:focus-visible {
  border-color: var(--po-harbor);
  outline: 3px solid var(--po-harbor-soft);
  outline-offset: 2px;
}

.po-decision-options button.selected {
  border-color: var(--po-harbor);
  background: var(--po-harbor-soft);
}

.po-option-icon {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: var(--po-radius-sm);
  background: var(--po-surface-subtle);
  color: var(--po-ink-muted);
}

.po-decision-options button.selected .po-option-icon {
  background: var(--po-harbor);
  color: var(--po-paper);
}

.po-option-copy {
  display: grid;
  min-width: 0;
  line-height: 1.25;
}

.po-option-copy strong {
  font-size: 11px;
  font-weight: 650;
}

.po-option-copy small {
  margin-top: 3px;
  color: var(--po-ink-muted);
  font-size: 9px;
}

.po-radio-mark {
  width: 14px;
  height: 14px;
  border: 1px solid oklch(0.7 0.02 230);
  border-radius: 999px;
}

.po-decision-options button.selected .po-radio-mark {
  border: 4px solid var(--po-harbor);
  background: var(--po-paper);
}

.po-note-field {
  display: grid;
  gap: 6px;
  color: var(--po-ink);
  font-size: 10px;
  font-weight: 650;
}

.po-note-field > span {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.po-note-field small {
  color: var(--po-risk);
  font-size: 9px;
}

.po-note-field :deep(.el-textarea__inner) {
  border-radius: var(--po-radius-sm);
  box-shadow: 0 0 0 1px var(--po-hairline) inset;
  color: var(--po-ink);
  font-size: 11px;
  line-height: 1.5;
}

.po-validation-error {
  display: flex;
  align-items: center;
  gap: 5px;
  margin: -5px 0 0;
  color: var(--po-risk);
  font-size: 10px;
  font-weight: 600;
}

.po-readonly-state {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin-top: 14px;
  padding: 10px;
  border: 1px solid var(--po-hairline);
  border-radius: var(--po-radius-sm);
  background: var(--po-canvas);
  color: var(--po-ink-muted);
}

.po-readonly-state .el-icon {
  flex: 0 0 auto;
  margin-top: 1px;
}

.po-readonly-state div {
  display: grid;
}

.po-readonly-state strong {
  color: var(--po-ink);
  font-size: 10px;
}

.po-readonly-state p {
  margin: 3px 0 0;
  color: var(--po-ink-muted);
  font-size: 9px;
  line-height: 1.45;
}

</style>
