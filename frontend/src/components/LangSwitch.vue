<template>
  <el-button size="small" @click="toggle">
    {{ currentLabel }}
  </el-button>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { setLocale } from '@/i18n'

const { locale } = useI18n()

const currentLabel = computed(() => locale.value === 'zh-CN' ? 'EN' : '中文')

function toggle() {
  setLocale(locale.value === 'zh-CN' ? 'en-US' : 'zh-CN')
}
</script>
