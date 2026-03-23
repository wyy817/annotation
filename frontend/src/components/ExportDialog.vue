<template>
  <el-dialog
    v-model="visible"
    :title="$t('exportDialog.title')"
    width="480px"
    :close-on-click-modal="false"
    @closed="onClosed"
  >
    <!-- Idle: not yet started -->
    <div v-if="phase === 'idle'" class="export-body">
      <el-icon class="export-icon"><Download /></el-icon>

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

      <el-button type="primary" :loading="starting" @click="start">{{ $t('exportDialog.startExport') }}</el-button>
    </div>

    <!-- Running: PENDING or PROCESSING -->
    <div v-else-if="phase === 'running'" class="export-body">
      <el-icon class="export-icon spin"><Loading /></el-icon>
      <p class="export-status-text">
        {{ task?.status === 'PROCESSING' ? $t('exportDialog.generating') : $t('exportDialog.queuing') }}
      </p>
      <el-progress
        :percentage="task?.status === 'PROCESSING' ? 60 : 20"
        :striped="true"
        :striped-flow="true"
        :duration="10"
        status=""
        style="width: 100%; margin-top: 8px"
      />
      <p class="export-hint">{{ $t('exportDialog.timingHint') }}</p>
    </div>

    <!-- Done: DONE -->
    <div v-else-if="phase === 'done'" class="export-body">
      <el-icon class="export-icon success"><CircleCheck /></el-icon>
      <p class="export-status-text success">{{ $t('exportDialog.exportDone') }}</p>
      <el-button type="success" :icon="Download" @click="download">{{ $t('exportDialog.downloadZip') }}</el-button>
      <p class="export-hint" style="margin-top: 8px">{{ $t('exportDialog.downloadHint') }}</p>
    </div>

    <!-- Error -->
    <div v-else-if="phase === 'error'" class="export-body">
      <el-icon class="export-icon danger"><CircleClose /></el-icon>
      <p class="export-status-text danger">{{ $t('exportDialog.exportFailed') }}</p>
      <p class="export-hint">{{ errorMsg }}</p>
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
import { Download, Loading, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { exportApi } from '@/api/export'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  slideId:    { type: Number, required: true }
})
const emit = defineEmits(['update:modelValue'])

const { t } = useI18n()

const visible  = ref(false)
const phase    = ref('idle')   // idle | running | done | error
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

// Sync v-model
watch(() => props.modelValue, v => { visible.value = v })
watch(visible, v => { emit('update:modelValue', v) })

async function start() {
  starting.value = true
  try {
    const res = await exportApi.startExport(props.slideId, {
      format: selectedFormat.value,
      patchSize: patchSize.value,
      maskType: maskType.value,
      maskScale: maskScale.value
    })
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
      const status = task.value?.status
      if (status === 'DONE') {
        stopPolling()
        phase.value = 'done'
        download()   // auto-trigger
      } else if (status === 'ERROR') {
        stopPolling()
        phase.value = 'error'
        errorMsg.value = task.value?.errorMessage || t('exportDialog.serverGenFailed')
      }
    } catch {
      // transient network error — keep polling
    }
  }, 2000)
}

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

async function download() {
  try {
    const blob = await exportApi.downloadFile(task.value.id)
    const url  = URL.createObjectURL(blob)
    const a    = document.createElement('a')
    a.href     = url
    a.download = `export_${task.value.id}.zip`
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
  phase.value    = 'idle'
}

function onClosed() {
  stopPolling()
  // If still running, leave task in place so the user can re-open and see state
  // Only reset if idle or error
  if (phase.value === 'idle' || phase.value === 'error') reset()
}
</script>

<style scoped>
.export-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 0 4px;
  gap: 12px;
  text-align: center;
}

.export-icon {
  font-size: 48px;
  color: #409eff;
}
.export-icon.success { color: #67c23a; }
.export-icon.danger  { color: #f56c6c; }
.export-icon.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

.format-section {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.format-label {
  font-size: 14px;
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
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.export-status-text {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
  margin: 0;
}
.export-status-text.success { color: #67c23a; }
.export-status-text.danger  { color: #f56c6c; }

.export-hint {
  font-size: 12px;
  color: #909399;
  margin: 0;
}
</style>
