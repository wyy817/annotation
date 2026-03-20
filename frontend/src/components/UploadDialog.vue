<template>
  <el-dialog
    v-model="visible"
    title="上传图像"
    width="500px"
    :close-on-click-modal="!uploading"
    :close-on-press-escape="!uploading"
    @close="handleClose"
  >
    <!-- File picker area -->
    <div
      v-if="!uploading && !done"
      class="drop-zone"
      :class="{ dragover }"
      @dragover.prevent="dragover = true"
      @dragleave="dragover = false"
      @drop.prevent="onDrop"
      @click="fileInputRef.click()"
    >
      <el-icon class="drop-icon"><Upload /></el-icon>
      <p class="drop-text">点击或拖拽文件到此区域</p>
      <p class="drop-hint">支持 PNG、JPG、SVS、NDPI、TIFF、MRXS、SCN 等，或 .zip / .tar.gz 压缩包</p>
      <input
        ref="fileInputRef"
        type="file"
        multiple
        accept=".png,.jpg,.jpeg,.svs,.ndpi,.tiff,.tif,.mrxs,.scn,.bif,.vms,.vmu,.zip,.tar.gz,.tgz"
        style="display:none"
        @change="onFileChange"
      />
    </div>

    <!-- Selected files list -->
    <div v-if="selectedFiles.length && !uploading && !done" class="file-list">
      <div v-for="(f, i) in selectedFiles" :key="i" class="file-item">
        <el-icon><Document /></el-icon>
        <span class="f-name">{{ f.name }}</span>
        <span class="f-size">{{ formatSize(f.size) }}</span>
        <el-icon class="f-remove" @click="removeFile(i)"><Close /></el-icon>
      </div>
    </div>

    <!-- Upload progress -->
    <div v-if="uploading" class="progress-wrap">
      <p class="progress-label">上传中，请勿关闭窗口…</p>
      <el-progress :percentage="uploadPercent" :stroke-width="12" status="striped" striped-flow />
      <p class="progress-hint">{{ uploadPercent }}%</p>
    </div>

    <!-- Polling phase -->
    <div v-if="polling" class="progress-wrap">
      <p class="progress-label">等待服务器处理（瓦片生成）…</p>
      <el-progress :percentage="pollPercent" :stroke-width="10" />
      <p class="progress-hint">{{ readyCount }} / {{ pendingSlides.length }} 就绪</p>
    </div>

    <!-- Done -->
    <div v-if="done" class="done-wrap">
      <el-icon class="done-icon" style="color:#67c23a"><CircleCheckFilled /></el-icon>
      <p>已上传 {{ pendingSlides.length }} 个文件</p>
      <p v-if="errorCount" class="error-hint">{{ errorCount }} 个文件处理失败</p>
    </div>

    <template #footer>
      <el-button v-if="!uploading && !done" @click="visible = false">取消</el-button>
      <el-button
        v-if="!uploading && !done"
        type="primary"
        :disabled="!selectedFiles.length"
        @click="startUpload"
      >
        开始上传
      </el-button>
      <el-button v-if="done" type="primary" @click="visible = false">完成</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Upload, Document, Close, CircleCheckFilled } from '@element-plus/icons-vue'
import { slideApi } from '@/api/slide'
import { useSlideStore } from '@/stores/slideStore'

const props = defineProps({
  modelValue: Boolean,
  workspaceId: { type: [Number, String], required: true }
})
const emit = defineEmits(['update:modelValue', 'uploaded'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const store = useSlideStore()
const fileInputRef = ref(null)
const dragover = ref(false)
const selectedFiles = ref([])

const uploading = ref(false)
const uploadPercent = ref(0)

const polling = ref(false)
const pendingSlides = ref([]) // slides returned from upload (PENDING)
const readyCount = ref(0)
const errorCount = ref(0)
const done = ref(false)

let pollTimer = null

watch(visible, (v) => {
  if (!v) reset()
})

function reset() {
  selectedFiles.value = []
  uploading.value = false
  uploadPercent.value = 0
  polling.value = false
  pendingSlides.value = []
  readyCount.value = 0
  errorCount.value = 0
  done.value = false
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

function handleClose() {
  if (!uploading.value) reset()
}

const pollPercent = computed(() => {
  if (!pendingSlides.value.length) return 0
  return Math.round(((readyCount.value + errorCount.value) / pendingSlides.value.length) * 100)
})

function onFileChange(e) {
  addFiles(Array.from(e.target.files))
  e.target.value = ''
}

function onDrop(e) {
  dragover.value = false
  addFiles(Array.from(e.dataTransfer.files))
}

function addFiles(files) {
  files.forEach((f) => {
    if (!selectedFiles.value.find((x) => x.name === f.name && x.size === f.size)) {
      selectedFiles.value.push(f)
    }
  })
}

function removeFile(i) {
  selectedFiles.value.splice(i, 1)
}

async function startUpload() {
  if (!selectedFiles.value.length) return
  uploading.value = true
  uploadPercent.value = 0
  try {
    const res = await slideApi.upload(
      props.workspaceId,
      selectedFiles.value,
      (e) => {
        if (e.total) uploadPercent.value = Math.round((e.loaded / e.total) * 100)
      }
    )
    const newSlides = res.data ?? []
    uploading.value = false
    store.addSlides(newSlides)
    emit('uploaded', newSlides)

    if (newSlides.length === 0) { done.value = true; return }

    pendingSlides.value = newSlides
    polling.value = true
    startPolling()
  } catch {
    uploading.value = false
    done.value = true
  }
}

function startPolling() {
  const remaining = new Set(pendingSlides.value.map((s) => s.id))
  readyCount.value = 0
  errorCount.value = 0

  pollTimer = setInterval(async () => {
    const ids = [...remaining]
    await Promise.allSettled(
      ids.map(async (id) => {
        try {
          const r = await slideApi.get(id)
          const slide = r.data
          store.updateSlide(slide)
          if (slide.status === 'READY') {
            readyCount.value++
            remaining.delete(id)
          } else if (slide.status === 'ERROR') {
            errorCount.value++
            remaining.delete(id)
          }
        } catch { /* ignore */ }
      })
    )
    if (remaining.size === 0) {
      clearInterval(pollTimer)
      pollTimer = null
      polling.value = false
      done.value = true
    }
  }, 2000)
}

function formatSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}
</script>

<style scoped>
.drop-zone {
  border: 2px dashed #dcdfe6;
  border-radius: 8px;
  padding: 36px 20px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}
.drop-zone:hover,
.drop-zone.dragover {
  border-color: #409eff;
  background: #ecf5ff;
}
.drop-icon {
  font-size: 40px;
  color: #c0c4cc;
  margin-bottom: 8px;
}
.drop-text {
  font-size: 14px;
  color: #606266;
  margin-bottom: 4px;
}
.drop-hint {
  font-size: 12px;
  color: #909399;
}

.file-list {
  margin-top: 12px;
  max-height: 180px;
  overflow-y: auto;
}
.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 4px;
  border-bottom: 1px solid #f0f0f0;
  font-size: 13px;
}
.f-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #303133;
}
.f-size {
  color: #909399;
  flex-shrink: 0;
}
.f-remove {
  cursor: pointer;
  color: #c0c4cc;
  flex-shrink: 0;
}
.f-remove:hover {
  color: #f56c6c;
}

.progress-wrap {
  padding: 16px 0;
  text-align: center;
}
.progress-label {
  font-size: 14px;
  color: #303133;
  margin-bottom: 12px;
}
.progress-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
}

.done-wrap {
  text-align: center;
  padding: 24px 0;
  font-size: 14px;
  color: #303133;
}
.done-icon {
  font-size: 48px;
  margin-bottom: 10px;
}
.error-hint {
  color: #f56c6c;
  font-size: 13px;
  margin-top: 4px;
}
</style>
