<template>
  <el-dialog
    v-model="visible"
    title="批量导出数据集"
    width="520px"
    :close-on-click-modal="false"
    @closed="onClosed"
  >
    <!-- Confirm: show slide list before starting -->
    <div v-if="phase === 'confirm'" class="batch-body">
      <p class="batch-desc">
        以下 <strong>{{ slides.length }}</strong> 个已通过审核的切片将合并导出为一个 ZIP 文件：
      </p>

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

      <el-scrollbar max-height="220px">
        <div v-for="s in slides" :key="s.id" class="slide-row">
          <el-icon style="color:#909399; flex-shrink:0"><Document /></el-icon>
          <span class="slide-name">{{ s.fileName }}</span>
        </div>
      </el-scrollbar>
      <el-button type="primary" style="margin-top:16px" :loading="starting" @click="start">
        开始批量导出
      </el-button>
    </div>

    <!-- Running -->
    <div v-else-if="phase === 'running'" class="batch-body centered">
      <el-icon class="spin big-icon" style="color:#409eff"><Loading /></el-icon>
      <p class="batch-desc">正在生成数据集 ZIP，请稍候…</p>
      <el-progress
        :percentage="progressPct"
        :striped="true"
        :striped-flow="true"
        :duration="8"
        style="width:300px; margin-top:8px"
      />
      <p class="hint-text">包含 {{ slides.length }} 个切片，格式：{{ selectedFormat }}</p>
    </div>

    <!-- Done -->
    <div v-else-if="phase === 'done'" class="batch-body centered">
      <el-icon style="font-size:48px; color:#67c23a"><CircleCheck /></el-icon>
      <p style="color:#67c23a; font-weight:500; margin:8px 0">导出完成！</p>
      <el-button type="primary" :icon="Download" @click="downloadResult">
        下载 ZIP
      </el-button>
    </div>

    <!-- Error -->
    <div v-else-if="phase === 'error'" class="batch-body centered">
      <el-icon style="font-size:48px; color:#f56c6c"><CircleClose /></el-icon>
      <p style="color:#f56c6c; font-weight:500; margin:8px 0">导出失败</p>
      <p style="color:#909399; font-size:12px">{{ errorMsg }}</p>
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
import { Download, Loading, CircleCheck, CircleClose, Document } from '@element-plus/icons-vue'
import { exportApi } from '@/api/export'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  slides:     { type: Array, required: true }   // [{id, fileName, ...}]
})
const emit = defineEmits(['update:modelValue'])

const visible  = ref(false)
const phase    = ref('confirm')   // confirm | running | done | error
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
    } catch {
      // transient — keep polling
    }
    if (task.value.status === 'DONE') {
      stopPolling()
      phase.value = 'done'
    } else if (task.value.status === 'ERROR') {
      stopPolling()
      phase.value = 'error'
      errorMsg.value = '服务器处理失败，请重试'
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
