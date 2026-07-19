<template>
  <section class="po-audit-panel" :aria-label="t('po.audit.title')">
    <header>
      <h2>{{ t('po.audit.title') }}</h2>
      <span class="po-tabular">{{ entries.length }}</span>
    </header>
    <ol>
      <li v-for="(entry, index) in entries" :key="entry.at + entry.type + index">
        <span class="po-audit-marker" aria-hidden="true"></span>
        <div>
          <strong>{{ t(entry.detailKey) }}</strong>
          <span>{{ entry.actor }}</span>
          <time :datetime="entry.at">{{ formatDateTime(entry.at, locale) }}</time>
        </div>
      </li>
    </ol>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { formatDateTime } from '@/utils/formatters'

const props = defineProps({
  auditTrail: {
    type: Array,
    required: true,
  },
})

const { t, locale } = useI18n()
const entries = computed(() => [...props.auditTrail].reverse())
</script>

<style scoped>
.po-audit-panel {
  border-top: 1px solid var(--po-hairline);
  padding: 14px 16px 16px;
}

.po-audit-panel header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.po-audit-panel h2 {
  margin: 0;
  color: var(--po-ink);
  font-size: 12px;
  font-weight: 650;
}

.po-audit-panel header span {
  display: grid;
  place-items: center;
  min-width: 22px;
  height: 20px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--po-surface-subtle);
  color: var(--po-ink-muted);
  font-size: 9px;
}

.po-audit-panel ol {
  display: grid;
  gap: 0;
  max-height: 300px;
  margin: 11px 0 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
}

.po-audit-panel li {
  position: relative;
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr);
  gap: 8px;
  min-height: 50px;
}

.po-audit-panel li:not(:last-child)::before {
  position: absolute;
  top: 12px;
  bottom: -1px;
  left: 4px;
  width: 1px;
  background: var(--po-hairline);
  content: "";
}

.po-audit-marker {
  position: relative;
  z-index: 1;
  width: 9px;
  height: 9px;
  margin-top: 3px;
  border: 2px solid var(--po-harbor);
  border-radius: 999px;
  background: var(--po-paper);
}

.po-audit-panel li:first-child .po-audit-marker {
  background: var(--po-harbor);
}

.po-audit-panel li div {
  display: grid;
  align-content: start;
  line-height: 1.25;
}

.po-audit-panel strong {
  color: var(--po-ink);
  font-size: 10px;
  font-weight: 600;
}

.po-audit-panel li span:not(.po-audit-marker),
.po-audit-panel time {
  margin-top: 3px;
  color: var(--po-ink-muted);
  font-size: 9px;
}
</style>
