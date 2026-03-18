<template>
  <el-dialog
    v-model="visible"
    title="导出数据集"
    width="440px"
    :close-on-click-modal="false"
    @closed="onClosed"
  >
    <!-- Idle: not yet started -->
    <div v-if="phase === 'idle'" class="export-body">
      <el-icon class="export-icon"><Download /></el-icon>
      <p class="export-desc">
        将生成包含以下内容的 ZIP 文件：<br />
        缩略图 · 语义掩码 · Patches · COCO JSON · 统计报告
      </p>
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
import { ref, watch } from 'vue'
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

let pollTimer = null

// Sync v-model
watch(() => props.modelValue, v => { visible.value = v })
watch(visible, v => { emit('update:modelValue', v) })

async function start() {
  starting.value = true
  try {
    const res = await exportApi.startExport(props.slideId)
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

function download() {
  const url = exportApi.downloadUrl(task.value.id)
  const a   = document.createElement('a')
  a.href     = url
  a.download = `export_${task.value.id}.zip`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
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

.export-desc {
  color: #606266;
  font-size: 14px;
  line-height: 1.7;
  margin: 0;
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
