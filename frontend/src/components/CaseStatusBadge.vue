<template>
  <span :class="['po-status-badge', 'po-status-' + status]">
    <span class="po-status-mark" aria-hidden="true"></span>
    {{ t('po.status.' + status) }}
  </span>
</template>

<script setup>
import { useI18n } from 'vue-i18n'

defineProps({
  status: {
    type: String,
    required: true,
  },
})

const { t } = useI18n()
</script>

<style scoped>
.po-status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 24px;
  padding: 0 8px;
  border: 1px solid var(--po-hairline);
  border-radius: 999px;
  background: var(--po-paper);
  color: var(--po-ink-muted);
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.po-status-mark {
  width: 7px;
  height: 7px;
  border: 2px solid currentColor;
  border-radius: 2px;
}

.po-status-ready {
  background: var(--po-harbor-soft);
  color: var(--po-harbor);
}

.po-status-in_review,
.po-status-senior_review {
  background: var(--po-attention-soft);
  color: var(--po-attention);
}

.po-status-senior_review .po-status-mark {
  border-radius: 999px;
}

.po-status-returned {
  background: var(--po-risk-soft);
  color: var(--po-risk);
}

.po-status-completed {
  background: var(--po-verified-soft);
  color: var(--po-verified);
}

.po-status-completed .po-status-mark {
  border-radius: 999px;
  background: currentColor;
}
</style>
