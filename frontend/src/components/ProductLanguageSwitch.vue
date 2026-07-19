<template>
  <el-dropdown trigger="click" @command="setLocale">
    <button
      type="button"
      class="po-language-trigger"
      :aria-label="t('lang.switch')"
    >
      <el-icon aria-hidden="true"><Connection /></el-icon>
      <span>{{ currentLabel }}</span>
      <el-icon class="po-language-chevron" aria-hidden="true"><ArrowDown /></el-icon>
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="en-US" :disabled="locale === 'en-US'">
          English
        </el-dropdown-item>
        <el-dropdown-item command="zh-CN" :disabled="locale === 'zh-CN'">
          中文
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowDown, Connection } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { setLocale } from '@/i18n'

const { locale, t } = useI18n()

const currentLabel = computed(() => locale.value === 'zh-CN' ? '中文' : 'EN')
</script>

<style scoped>
.po-language-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--po-hairline);
  border-radius: var(--po-radius-sm);
  background: var(--po-paper);
  color: var(--po-ink-muted);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition:
    color var(--po-motion-fast) var(--po-ease-state),
    background-color var(--po-motion-fast) var(--po-ease-state),
    border-color var(--po-motion-fast) var(--po-ease-state);
}

.po-language-trigger:hover {
  border-color: oklch(0.75 0.015 230);
  background: var(--po-surface-subtle);
  color: var(--po-ink);
}

.po-language-trigger:focus-visible {
  border-color: var(--po-harbor);
  outline: 3px solid var(--po-harbor-soft);
  outline-offset: 2px;
}

.po-language-chevron {
  font-size: 11px;
}
</style>
