<template>
  <div class="browser-page">
    <!-- Header -->
    <header class="page-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" text @click="router.push('/')">工作空间</el-button>
        <el-divider direction="vertical" />
        <span class="ws-name">{{ workspace?.name ?? '...' }}</span>
      </div>
      <el-button type="primary" :icon="Upload" @click="showUpload = true">上传图像</el-button>
    </header>

    <!-- Toolbar -->
    <div class="toolbar">
      <el-input
        v-model="searchText"
        placeholder="搜索文件名..."
        clearable
        :prefix-icon="Search"
        style="width:220px"
      />
      <el-select v-model="sortKey" style="width:130px">
        <el-option label="上传时间↓" value="createdAt_desc" />
        <el-option label="上传时间↑" value="createdAt_asc" />
        <el-option label="文件名 A→Z" value="name_asc" />
        <el-option label="文件名 Z→A" value="name_desc" />
        <el-option label="文件大小↓" value="size_desc" />
      </el-select>

      <!-- Status filter -->
      <el-select v-model="filterStatus" clearable placeholder="标注状态" style="width:110px">
        <el-option label="未标注" value="NOT_STARTED" />
        <el-option label="标注中" value="IN_PROGRESS" />
        <el-option label="已完成" value="DONE" />
      </el-select>

      <!-- Review status filter -->
      <el-select v-model="filterReview" clearable placeholder="审核状态" style="width:110px">
        <el-option label="未提交" value="NOT_SUBMITTED" />
        <el-option label="待审核" value="PENDING_REVIEW" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="已拒绝" value="REJECTED" />
      </el-select>

      <div style="flex:1" />

      <!-- Batch export -->
      <el-tooltip content="导出所有已通过审核的切片" placement="bottom">
        <el-button
          :icon="Download"
          :disabled="!approvedSlides.length"
          @click="showBatchExport = true"
        >批量导出 ({{ approvedSlides.length }})</el-button>
      </el-tooltip>

      <!-- View toggle -->
      <el-button-group>
        <el-button
          :type="viewMode === 'grid' ? 'primary' : ''"
          :icon="Grid"
          @click="viewMode = 'grid'"
        />
        <el-button
          :type="viewMode === 'list' ? 'primary' : ''"
          :icon="List"
          @click="viewMode = 'list'"
        />
      </el-button-group>
    </div>

    <!-- Loading -->
    <div v-if="store.loading" class="grid-wrap">
      <el-skeleton v-for="n in 6" :key="n" class="skel" :rows="3" animated />
    </div>

    <!-- Empty -->
    <el-empty
      v-else-if="!filteredSlides.length"
      :description="store.slides.length ? '没有匹配的文件' : '还没有图像，点击右上角上传'"
      class="empty-state"
    />

    <!-- Grid -->
    <div v-else-if="viewMode === 'grid'" class="grid-wrap">
      <SlideCard
        v-for="s in filteredSlides"
        :key="s.id"
        :slide="s"
        view-mode="grid"
        @open="handleOpen"
        @move="handleMoveClick"
        @delete="handleDeleteClick"
      />
    </div>

    <!-- List -->
    <div v-else class="list-wrap">
      <div class="list-header">
        <div style="width:56px"></div>
        <div style="flex:1">文件名</div>
        <div style="width:60px">格式</div>
        <div style="width:80px">大小</div>
        <div style="width:90px">处理状态</div>
        <div style="width:90px">标注状态</div>
        <div style="width:90px">审核状态</div>
        <div style="width:40px"></div>
      </div>
      <SlideCard
        v-for="s in filteredSlides"
        :key="s.id"
        :slide="s"
        view-mode="list"
        @open="handleOpen"
        @move="handleMoveClick"
        @delete="handleDeleteClick"
      />
    </div>

    <!-- Upload dialog -->
    <UploadDialog
      v-model="showUpload"
      :workspace-id="workspaceId"
      @uploaded="onUploaded"
    />

    <!-- Move dialog -->
    <MoveSlideDialog
      v-model="showMove"
      :slide="moveTarget"
      @moved="onMoved"
    />

    <!-- Batch export dialog -->
    <BatchExportDialog v-model="showBatchExport" :slides="approvedSlides" />

    <!-- Delete confirm -->
    <el-dialog v-model="showDelete" title="删除图像" width="400px">
      <p>
        确定要删除
        <strong>「{{ deleteTarget?.fileName }}」</strong>
        吗？<br />
        <span class="warn-text">标注数据将同步删除，不可恢复。</span>
      </p>
      <template #footer>
        <el-button @click="showDelete = false">取消</el-button>
        <el-button type="danger" :loading="deleting" @click="confirmDelete">确认删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Upload, Search, Grid, List, Download } from '@element-plus/icons-vue'

import { useSlideStore } from '@/stores/slideStore'
import { useWorkspaceStore } from '@/stores/workspaceStore'

import SlideCard from '@/components/SlideCard.vue'
import UploadDialog from '@/components/UploadDialog.vue'
import MoveSlideDialog from '@/components/MoveSlideDialog.vue'
import BatchExportDialog from '@/components/BatchExportDialog.vue'

const route = useRoute()
const router = useRouter()
const workspaceId = Number(route.params.id)

const store = useSlideStore()
const wsStore = useWorkspaceStore()

const workspace = computed(() => wsStore.workspaces.find((w) => w.id === workspaceId) ?? null)

// ── toolbar state ──────────────────────────────────────────────────────────
const searchText = ref('')
const sortKey = ref('createdAt_desc')
const filterStatus = ref('')
const filterReview = ref('')
const viewMode = ref('grid')

// ── dialog state ──────────────────────────────────────────────────────────
const showUpload = ref(false)
const showMove = ref(false)
const moveTarget = ref(null)
const showDelete = ref(false)
const deleteTarget = ref(null)
const deleting = ref(false)
const showBatchExport = ref(false)

// ── computed ──────────────────────────────────────────────────────────────
const approvedSlides = computed(() =>
  store.slides.filter(s => s.reviewStatus === 'APPROVED' && s.status === 'READY')
)

const filteredSlides = computed(() => {
  let list = [...store.slides]

  // search
  const q = searchText.value.trim().toLowerCase()
  if (q) list = list.filter((s) => s.fileName.toLowerCase().includes(q))

  // filter by anno status
  if (filterStatus.value) list = list.filter((s) => s.annoStatus === filterStatus.value)

  // filter by review status
  if (filterReview.value) list = list.filter((s) => (s.reviewStatus ?? 'NOT_SUBMITTED') === filterReview.value)

  // sort
  list.sort((a, b) => {
    switch (sortKey.value) {
      case 'createdAt_asc':  return new Date(a.createdAt) - new Date(b.createdAt)
      case 'createdAt_desc': return new Date(b.createdAt) - new Date(a.createdAt)
      case 'name_asc':       return a.fileName.localeCompare(b.fileName)
      case 'name_desc':      return b.fileName.localeCompare(a.fileName)
      case 'size_desc':      return (b.fileSize ?? 0) - (a.fileSize ?? 0)
      default:               return 0
    }
  })
  return list
})

// ── lifecycle ─────────────────────────────────────────────────────────────
onMounted(async () => {
  // Ensure workspace list is loaded (for move dialog and header name)
  if (!wsStore.workspaces.length) await wsStore.fetchAll()
  store.fetchAll(workspaceId)
})

// ── handlers ──────────────────────────────────────────────────────────────
function handleOpen(slide) {
  if (slide.status !== 'READY') {
    ElMessage.warning('图像尚未处理完成，请稍候')
    return
  }
  router.push({ name: 'slideViewer', params: { id: slide.id } })
}

function handleMoveClick(slide) {
  moveTarget.value = slide
  showMove.value = true
}

function handleDeleteClick(slide) {
  deleteTarget.value = slide
  showDelete.value = true
}

function onUploaded(newSlides) {
  ElMessage.success(`已上传 ${newSlides.length} 个文件，等待处理...`)
}

function onMoved() {
  // slide is already removed from store by slideStore.move()
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await store.remove(deleteTarget.value.id)
    ElMessage.success('已删除')
    showDelete.value = false
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
.browser-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 32px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ws-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 32px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}

.grid-wrap {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
  padding: 24px 32px;
}

.skel {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
}

.list-wrap {
  margin: 24px 32px;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.list-header {
  display: grid;
  grid-template-columns: 56px 1fr 60px 80px 90px 90px 40px;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
  font-size: 12px;
  color: #909399;
  font-weight: 600;
}

.empty-state {
  margin-top: 100px;
}

.warn-text {
  color: #f56c6c;
  font-size: 13px;
}
</style>
