<template>
  <el-dialog
    v-model="visible"
    :title="$t('exportDialog.batchTitle')"
    width="520px"
    :close-on-click-modal="false"
    @closed="onClosed"
  >
    <!-- Confirm: show slide list before starting -->
    <div v-if="phase === 'confirm'" class="batch-body">
      <p class="batch-desc">
        {{ $t('exportDialog.batchDesc', { count: slides.length }) }}
      </p>

      <!-- Format selector -->
      <div class="format-section">
        <p class="format-label">{{ $t('exportDialog.selectFormat') }}</p>
        <el-radio-group v-model="selectedFormat" size="default">
          <el-radio-button v-for="f in formats" :key="f.value" :value="f.value">
            {{ f.label }}
          </el-radio-button>
        </el-radio-group>
        <p class="format-hint">{{ formatHint }}</p>
      </div>

      <!-- Patch size selector (PATCHES only) -->
      <div v-if="selectedFormat === 'PATCHES'" class="option-section">
        <p class="format-label">{{ $t('exportDialog.patchSize') }}</p>
        <el-radio-group v-model="patchSize" size="small">
          <el-radio-button :value="128">128×128</el-radio-button>
          <el-radio-button :value="256">256×256</el-radio-button>
          <el-radio-button :value="512">512×512</el-radio-button>
        </el-radio-group>
      </div>

      <!-- Mask options (MASK_ONLY only) -->
      <div v-if="selectedFormat === 'MASK_ONLY'" class="option-section">
        <p class="format-label">{{ $t('exportDialog.maskType') }}</p>
        <el-radio-group v-model="maskType" size="small">
          <el-radio-button value="SEMANTIC">{{ $t('exportDialog.maskSemantic') }}</el-radio-button>
          <el-radio-button value="COLOR">{{ $t('exportDialog.maskColor') }}</el-radio-button>
          <el-radio-button value="INSTANCE">{{ $t('exportDialog.maskInstance') }}</el-radio-button>
        </el-radio-group>
        <p class="format-label" style="margin-top: 8px">{{ $t('exportDialog.maskDownsample') }}</p>
        <el-radio-group v-model="maskScale" size="small">
          <el-radio-button :value="1">{{ $t('exportDialog.maskScaleOriginal') }}</el-radio-button>
          <el-radio-button :value="2">2×</el-radio-button>
          <el-radio-button :value="4">4×</el-radio-button>
          <el-radio-button :value="8">8×</el-radio-button>
        </el-radio-group>
      </div>

      <el-scrollbar max-height="220px">
        <div v-for="s in slides" :key="s.id" class="slide-row">
          <el-icon style="color:#909399; flex-shrink:0"><Document /></el-icon>
          <span class="slide-name">{{ s.fileName }}</span>
        </div>
      </el-scrollbar>
      <el-button type="primary" style="margin-top:16px" :loading="starting" @click="start">
        {{ $t('exportDialog.startBatchExport') }}
      </el-button>
    </div>

    <!-- Running -->
    <div v-else-if="phase === 'running'" class="batch-body centered">
      <el-icon class="spin big-icon" style="color:#409eff"><Loading /></el-icon>
      <p class="batch-desc">{{ $t('exportDialog.generatingZip') }}</p>
      <el-progress
        :percentage="progressPct"
        :striped="true"
        :striped-flow="true"
        :duration="8"
        style="width:300px; margin-top:8px"
      />
      <p class="hint-text">{{ $t('exportDialog.slidesInfo', { count: slides.length, format: selectedFormat }) }}</p>
    </div>

    <!-- Done -->
    <div v-else-if="phase === 'done'" class="batch-body centered">
      <el-icon style="font-size:48px; color:#67c23a"><CircleCheck /></el-icon>
      <p style="color:#67c23a; font-weight:500; margin:8px 0">{{ $t('exportDialog.exportDone') }}</p>
      <el-button type="primary" :icon="Download" @click="downloadResult">
        {{ $t('exportDialog.downloadZip') }}
      </el-button>
    </div>

    <!-- Error -->
    <div v-else-if="phase === 'error'" class="batch-body centered">
      <el-icon style="font-size:48px; color:#f56c6c"><CircleClose /></el-icon>
      <p style="color:#f56c6c; font-weight:500; margin:8px 0">{{ $t('exportDialog.exportFailed') }}</p>
      <p style="color:#909399; font-size:12px">{{ errorMsg }}</p>
      <el-button @click="reset">{{ $t('exportDialog.retry') }}</el-button>
    </div>

    <template #footer>
      <el-button @click="visible = false">
        {{ phase === 'done' ? $t('common.close') : $t('common.cancel') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Download, Loading, CircleCheck, CircleClose, Document } from '@element-plus/icons-vue'
import { exportApi } from '@/api/export'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  slides:     { type: Array, required: true }   // [{id, fileName, ...}]
})
const emit = defineEmits(['update:modelValue'])

const { t } = useI18n()

const visible  = ref(false)
const phase    = ref('confirm')   // confirm | running | done | error
const starting = ref(false)
const task     = ref(null)
const errorMsg = ref('')
const selectedFormat = ref('COCO')
const patchSize = ref(256)
const maskType = ref('SEMANTIC')
const maskScale = ref(1)

const formats = computed(() => [
  { value: 'COCO',      label: 'COCO' },
  { value: 'YOLO',      label: 'YOLO' },
  { value: 'VOC',       label: 'VOC' },
  { value: 'GEOJSON',   label: 'GeoJSON' },
  { value: 'MASK_ONLY', label: t('exportDialog.maskOnlyLabel') },
  { value: 'PATCHES',   label: 'Patches' }
])

const formatDescriptions = computed(() => ({
  COCO:      t('exportDialog.descCOCO'),
  YOLO:      t('exportDialog.descYOLO'),
  VOC:       t('exportDialog.descVOC'),
  GEOJSON:   t('exportDialog.descGeoJSON'),
  MASK_ONLY: t('exportDialog.descMask'),
  PATCHES:   t('exportDialog.descPatches')
}))

const formatHint = computed(() => formatDescriptions.value[selectedFormat.value] || '')

let pollTimer = null

watch(() => props.modelValue, v => { visible.value = v })
watch(visible, v => { emit('update:modelValue', v) })

// Fake progress: PENDING=20%, PROCESSING=60%, ramp up while waiting
const progressPct = computed(() => {
  if (!task.value) return 10
  if (task.value.status === 'PENDING') return 20
  if (task.value.status === 'PROCESSING') return 60
  return 100
})

async function start() {
  starting.value = true
  try {
    const res = await exportApi.startBatchExport(
      props.slides.map(s => s.id),
      {
        format: selectedFormat.value,
        patchSize: patchSize.value,
        maskType: maskType.value,
        maskScale: maskScale.value
      }
    )
    task.value = res.data
    phase.value = 'running'
    startPolling()
  } catch (e) {
    phase.value = 'error'
    errorMsg.value = e.message || t('exportDialog.requestFailed')
  } finally {
    starting.value = false
  }
}

function startPolling() {
  pollTimer = setInterval(async () => {
    if (!task.value?.id) return
    try {
      const res = await exportApi.getTask(task.value.id)
      task.value = res.data
    } catch {
      // transient — keep polling
    }
    if (task.value.status === 'DONE') {
      stopPolling()
      phase.value = 'done'
    } else if (task.value.status === 'ERROR') {
      stopPolling()
      phase.value = 'error'
      errorMsg.value = t('exportDialog.serverFailed')
    }
  }, 2000)
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

async function downloadResult() {
  if (!task.value) return
  try {
    const blob = await exportApi.downloadFile(task.value.id)
    const url  = URL.createObjectURL(blob)
    const a    = document.createElement('a')
    a.href     = url
    a.download = `batch_export_${task.value.id}.zip`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  } catch {
    // 错误已由 axios 拦截器统一弹出提示
  }
}

function reset() {
  stopPolling()
  task.value     = null
  errorMsg.value = ''
  phase.value    = 'confirm'
}

function onClosed() {
  stopPolling()
  if (phase.value !== 'running') reset()
}
</script>

<style scoped>
.batch-body {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.batch-body.centered {
  align-items: center;
  padding: 16px 0;
}

.batch-desc {
  font-size: 14px;
  color: #606266;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.format-section {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  margin: 4px 0 8px;
}

.format-label {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
  margin: 0;
}

.format-hint {
  font-size: 12px;
  color: #909399;
  margin: 0;
  min-height: 18px;
}

.option-section {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  margin: 0 0 8px;
}

.hint-text {
  font-size: 12px;
  color: #909399;
  margin: 4px 0 0;
}

.slide-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 4px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 13px;
  color: #303133;
}

.slide-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.big-icon {
  font-size: 48px;
}

.spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
</style>
