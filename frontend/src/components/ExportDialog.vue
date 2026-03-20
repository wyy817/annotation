<template>
  <el-dialog
    v-model="visible"
    title="导出数据集"
    width="480px"
    :close-on-click-modal="false"
    @closed="onClosed"
  >
    <!-- Idle: not yet started -->
    <div v-if="phase === 'idle'" class="export-body">
      <el-icon class="export-icon"><Download /></el-icon>

      <!-- Format selector -->
      <div class="format-section">
        <p class="format-label">选择导出格式：</p>
        <el-radio-group v-model="selectedFormat" size="default">
          <el-radio-button v-for="f in formats" :key="f.value" :value="f.value">
            {{ f.label }}
          </el-radio-button>
        </el-radio-group>
        <p class="format-hint">{{ formatHint }}</p>
      </div>

      <!-- Patch size selector (PATCHES only) -->
      <div v-if="selectedFormat === 'PATCHES'" class="option-section">
        <p class="format-label">Patch 尺寸：</p>
        <el-radio-group v-model="patchSize" size="small">
          <el-radio-button :value="128">128×128</el-radio-button>
          <el-radio-button :value="256">256×256</el-radio-button>
          <el-radio-button :value="512">512×512</el-radio-button>
        </el-radio-group>
      </div>

      <!-- Mask options (MASK_ONLY only) -->
      <div v-if="selectedFormat === 'MASK_ONLY'" class="option-section">
        <p class="format-label">掩码类型：</p>
        <el-radio-group v-model="maskType" size="small">
          <el-radio-button value="SEMANTIC">语义掩码</el-radio-button>
          <el-radio-button value="COLOR">彩色 RGB</el-radio-button>
          <el-radio-button value="INSTANCE">实例掩码</el-radio-button>
        </el-radio-group>
        <p class="format-label" style="margin-top: 8px">降采样倍率：</p>
        <el-radio-group v-model="maskScale" size="small">
          <el-radio-button :value="1">1× (原始)</el-radio-button>
          <el-radio-button :value="2">2×</el-radio-button>
          <el-radio-button :value="4">4×</el-radio-button>
          <el-radio-button :value="8">8×</el-radio-button>
        </el-radio-group>
      </div>

      <el-button type="primary" :loading="starting" @click="start">开始导出</el-button>
    </div>

    <!-- Running: PENDING or PROCESSING -->
    <div v-else-if="phase === 'running'" class="export-body">
      <el-icon class="export-icon spin"><Loading /></el-icon>
      <p class="export-status-text">
        {{ task?.status === 'PROCESSING' ? '正在生成数据集...' : '任务排队中...' }}
      </p>
      <el-progress
        :percentage="task?.status === 'PROCESSING' ? 60 : 20"
        :striped="true"
        :striped-flow="true"
        :duration="10"
        status=""
        style="width: 100%; margin-top: 8px"
      />
      <p class="export-hint">这可能需要数秒至数分钟，取决于图像大小</p>
    </div>

    <!-- Done: DONE -->
    <div v-else-if="phase === 'done'" class="export-body">
      <el-icon class="export-icon success"><CircleCheck /></el-icon>
      <p class="export-status-text success">导出完成！</p>
      <el-button type="success" :icon="Download" @click="download">下载 ZIP</el-button>
      <p class="export-hint" style="margin-top: 8px">如未自动下载，请点击上方按钮</p>
    </div>

    <!-- Error -->
    <div v-else-if="phase === 'error'" class="export-body">
      <el-icon class="export-icon danger"><CircleClose /></el-icon>
      <p class="export-status-text danger">导出失败</p>
      <p class="export-hint">{{ errorMsg }}</p>
      <el-button @click="reset">重试</el-button>
    </div>

    <template #footer>
      <el-button @click="visible = false">
        {{ phase === 'done' ? '关闭' : '取消' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Download, Loading, CircleCheck, CircleClose } from '@element-plus/icons-vue'
import { exportApi } from '@/api/export'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  slideId:    { type: Number, required: true }
})
const emit = defineEmits(['update:modelValue'])

const visible  = ref(false)
const phase    = ref('idle')   // idle | running | done | error
const starting = ref(false)
const task     = ref(null)
const errorMsg = ref('')
const selectedFormat = ref('COCO')
const patchSize = ref(256)
const maskType = ref('SEMANTIC')
const maskScale = ref(1)

const formats = [
  { value: 'COCO',      label: 'COCO' },
  { value: 'YOLO',      label: 'YOLO' },
  { value: 'VOC',       label: 'VOC' },
  { value: 'GEOJSON',   label: 'GeoJSON' },
  { value: 'MASK_ONLY', label: '仅 Mask' },
  { value: 'PATCHES',   label: 'Patches' }
]

const formatDescriptions = {
  COCO:      '标准 COCO 格式：instances.json + 语义掩码 + 缩略图',
  YOLO:      'YOLO 检测/分割格式：images/ + labels/ + data.yaml',
  VOC:       'Pascal VOC XML 格式：每张图对应一个 XML 标注文件（bndbox 边界框）',
  GEOJSON:   'GeoJSON 矢量格式：FeatureCollection + Polygon/Point 几何体',
  MASK_ONLY: '语义/彩色/实例掩码 PNG，支持降采样',
  PATCHES:   '按标注区域裁切小图，按标签分类存储 + labels.csv'
}

const formatHint = computed(() => formatDescriptions[selectedFormat.value] || '')

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
    errorMsg.value = e.message || '请求失败'
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
        errorMsg.value = task.value?.errorMessage || '服务端生成失败'
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
