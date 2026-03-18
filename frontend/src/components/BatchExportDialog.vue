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
      <p class="batch-desc hint">
        ZIP 结构：<code>image/</code>（缩略图）、<code>mask/</code>（语义掩码）、<code>annotations.json</code>
      </p>
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
      <p class="hint-text">包含 {{ slides.length }} 个切片的 image/ 和 mask/</p>
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
    const res = await exportApi.startBatchExport(props.slides.map(s => s.id))
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

.batch-desc.hint {
  font-size: 12px;
  color: #909399;
}

.batch-desc.hint code {
  background: #f5f7fa;
  padding: 1px 4px;
  border-radius: 3px;
  font-family: monospace;
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
