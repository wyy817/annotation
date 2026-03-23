<template>
  <div class="browser-page">
    <!-- Header -->
    <header class="page-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" text @click="router.push('/')">{{ $t('workspace.title') }}</el-button>
        <el-divider direction="vertical" />
        <span class="ws-name">{{ workspace?.name ?? '...' }}</span>
      </div>
      <div class="header-right">
        <NotificationBell />
        <LangSwitch />
        <el-button type="primary" :icon="Upload" @click="showUpload = true">{{ $t('fileBrowser.uploadImage') }}</el-button>
      </div>
    </header>

    <!-- Toolbar -->
    <div class="toolbar">
      <el-input
        v-model="searchText"
        :placeholder="$t('fileBrowser.searchPlaceholder')"
        clearable
        :prefix-icon="Search"
        style="width:220px"
      />
      <el-select v-model="sortKey" style="width:140px">
        <el-option :label="$t('fileBrowser.sortTimeDesc')" value="createdAt_desc" />
        <el-option :label="$t('fileBrowser.sortTimeAsc')" value="createdAt_asc" />
        <el-option :label="$t('fileBrowser.sortNameAsc')" value="name_asc" />
        <el-option :label="$t('fileBrowser.sortNameDesc')" value="name_desc" />
        <el-option :label="$t('fileBrowser.sortSizeDesc')" value="size_desc" />
      </el-select>

      <!-- Status filter -->
      <el-select v-model="filterStatus" clearable :placeholder="$t('fileBrowser.filterAnnoStatus')" style="width:120px">
        <el-option :label="$t('fileBrowser.annoNotStarted')" value="NOT_STARTED" />
        <el-option :label="$t('fileBrowser.annoInProgress')" value="IN_PROGRESS" />
        <el-option :label="$t('fileBrowser.annoDone')" value="DONE" />
      </el-select>

      <!-- Review status filter -->
      <el-select v-model="filterReview" clearable :placeholder="$t('fileBrowser.filterReviewStatus')" style="width:120px">
        <el-option :label="$t('fileBrowser.reviewNotSubmitted')" value="NOT_SUBMITTED" />
        <el-option :label="$t('fileBrowser.reviewPending')" value="PENDING_REVIEW" />
        <el-option :label="$t('fileBrowser.reviewApproved')" value="APPROVED" />
        <el-option :label="$t('fileBrowser.reviewRejected')" value="REJECTED" />
      </el-select>

      <div style="flex:1" />

      <!-- Selection mode toggle -->
      <el-button
        :type="selectionMode ? 'warning' : ''"
        @click="toggleSelectionMode"
      >{{ selectionMode ? $t('common.exitSelection') : $t('common.batchSelect') }}</el-button>

      <!-- Batch export -->
      <el-tooltip :content="$t('fileBrowser.batchExportTooltip')" placement="bottom">
        <el-button
          :icon="Download"
          :disabled="!approvedSlides.length"
          @click="showBatchExport = true"
        >{{ $t('fileBrowser.batchExportBtn', { count: approvedSlides.length }) }}</el-button>
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

    <!-- Batch action bar -->
    <div v-if="selectionMode" class="batch-bar">
      <el-checkbox
        :model-value="isAllSelected"
        :indeterminate="isIndeterminate"
        @change="toggleSelectAll"
      >{{ $t('common.selectAll') }}</el-checkbox>
      <span class="batch-info">{{ $t('common.selectedCount', { count: selectedIds.size }) }}</span>
      <el-button
        type="primary"
        :icon="Download"
        :disabled="!selectedIds.size"
        @click="handleBatchExportSelected"
      >{{ $t('fileBrowser.download') }}</el-button>
      <el-button
        type="danger"
        :icon="DeleteIcon"
        :disabled="!selectedIds.size"
        @click="handleBatchDeleteClick"
      >{{ $t('common.delete') }}</el-button>
    </div>

    <!-- Loading -->
    <div v-if="store.loading" class="grid-wrap">
      <el-skeleton v-for="n in 6" :key="n" class="skel" :rows="3" animated />
    </div>

    <!-- Empty -->
    <el-empty
      v-else-if="!filteredSlides.length"
      :description="store.slides.length ? $t('fileBrowser.emptySearch') : $t('fileBrowser.empty')"
      class="empty-state"
    />

    <!-- Grid -->
    <div v-else-if="viewMode === 'grid'" class="grid-wrap">
      <SlideCard
        v-for="s in filteredSlides"
        :key="s.id"
        :slide="s"
        view-mode="grid"
        :selectable="selectionMode"
        :selected="selectedIds.has(s.id)"
        @open="handleOpen"
        @move="handleMoveClick"
        @delete="handleDeleteClick"
        @toggle-select="toggleSelect"
      />
    </div>

    <!-- List -->
    <div v-else class="list-wrap">
      <div class="list-header" :class="{ 'has-checkbox': selectionMode }">
        <div v-if="selectionMode" style="width:32px"></div>
        <div style="width:56px"></div>
        <div style="flex:1">{{ $t('fileBrowser.colFileName') }}</div>
        <div style="width:60px">{{ $t('fileBrowser.colFormat') }}</div>
        <div style="width:80px">{{ $t('fileBrowser.colSize') }}</div>
        <div style="width:90px">{{ $t('fileBrowser.colProcessStatus') }}</div>
        <div style="width:90px">{{ $t('fileBrowser.colAnnoStatus') }}</div>
        <div style="width:90px">{{ $t('fileBrowser.colReviewStatus') }}</div>
        <div style="width:40px"></div>
      </div>
      <SlideCard
        v-for="s in filteredSlides"
        :key="s.id"
        :slide="s"
        view-mode="list"
        :selectable="selectionMode"
        :selected="selectedIds.has(s.id)"
        @open="handleOpen"
        @move="handleMoveClick"
        @delete="handleDeleteClick"
        @toggle-select="toggleSelect"
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
    <BatchExportDialog v-model="showBatchExport" :slides="batchExportSlides" />

    <!-- Batch delete confirm -->
    <el-dialog v-model="showBatchDelete" :title="$t('fileBrowser.batchDeleteTitle')" width="420px">
      <p>
        {{ $t('fileBrowser.batchDeleteConfirm', { count: selectedIds.size }) }}<br />
        <span class="warn-text">{{ $t('fileBrowser.annoDataWarn') }}</span>
      </p>
      <template #footer>
        <el-button @click="showBatchDelete = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="danger" :loading="batchDeleting" @click="confirmBatchDelete">{{ $t('common.confirmDelete') }}</el-button>
      </template>
    </el-dialog>

    <!-- Delete confirm -->
    <el-dialog v-model="showDelete" :title="$t('fileBrowser.deleteImageTitle')" width="400px">
      <p>
        {{ $t('fileBrowser.deleteImageConfirm', { name: deleteTarget?.fileName }) }}<br />
        <span class="warn-text">{{ $t('fileBrowser.annoDataWarn') }}</span>
      </p>
      <template #footer>
        <el-button @click="showDelete = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="danger" :loading="deleting" @click="confirmDelete">{{ $t('common.confirmDelete') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, Upload, Search, Grid, List, Download, Delete as DeleteIcon } from '@element-plus/icons-vue'

import { useSlideStore } from '@/stores/slideStore'
import { useWorkspaceStore } from '@/stores/workspaceStore'

import SlideCard from '@/components/SlideCard.vue'
import UploadDialog from '@/components/UploadDialog.vue'
import MoveSlideDialog from '@/components/MoveSlideDialog.vue'
import BatchExportDialog from '@/components/BatchExportDialog.vue'
import NotificationBell from '@/components/NotificationBell.vue'
import LangSwitch from '@/components/LangSwitch.vue'

const { t } = useI18n()
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
const showBatchDelete = ref(false)
const batchDeleting = ref(false)

// ── selection state ──────────────────────────────────────────────────────
const selectionMode = ref(false)
const selectedIds = ref(new Set())

const isAllSelected = computed(() =>
  filteredSlides.value.length > 0 && selectedIds.value.size === filteredSlides.value.length
)
const isIndeterminate = computed(() =>
  selectedIds.value.size > 0 && selectedIds.value.size < filteredSlides.value.length
)

function toggleSelectionMode() {
  selectionMode.value = !selectionMode.value
  selectedIds.value = new Set()
}

function toggleSelect(slide) {
  const s = new Set(selectedIds.value)
  if (s.has(slide.id)) s.delete(slide.id)
  else s.add(slide.id)
  selectedIds.value = s
}

function toggleSelectAll(val) {
  if (val) {
    selectedIds.value = new Set(filteredSlides.value.map(s => s.id))
  } else {
    selectedIds.value = new Set()
  }
}

function handleBatchExportSelected() {
  showBatchExport.value = true
}

function handleBatchDeleteClick() {
  showBatchDelete.value = true
}

async function confirmBatchDelete() {
  batchDeleting.value = true
  try {
    const ids = [...selectedIds.value]
    for (const id of ids) {
      await store.remove(id)
    }
    ElMessage.success(t('fileBrowser.batchDeleteSuccess', { count: ids.length }))
    selectedIds.value = new Set()
    showBatchDelete.value = false
  } finally {
    batchDeleting.value = false
  }
}

// ── computed ──────────────────────────────────────────────────────────────
const approvedSlides = computed(() =>
  store.slides.filter(s => s.reviewStatus === 'APPROVED' && s.status === 'READY')
)

const batchExportSlides = computed(() => {
  if (selectionMode.value && selectedIds.value.size > 0) {
    return store.slides.filter(s => selectedIds.value.has(s.id) && s.status === 'READY' && s.reviewStatus === 'APPROVED')
  }
  return approvedSlides.value
})

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
  if (!wsStore.workspaces.length) await wsStore.fetchAll()
  store.fetchAll(workspaceId)
})

// ── handlers ──────────────────────────────────────────────────────────────
function handleOpen(slide) {
  if (slide.status !== 'READY') {
    ElMessage.warning(t('fileBrowser.imageNotReady'))
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
  ElMessage.success(t('fileBrowser.uploadedMsg', { count: newSlides.length }))
}

function onMoved() {
  // slide is already removed from store by slideStore.move()
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await store.remove(deleteTarget.value.id)
    ElMessage.success(t('fileBrowser.deletedMsg'))
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

.batch-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 32px;
  background: #ecf5ff;
  border-bottom: 1px solid #d9ecff;
}
.batch-info {
  font-size: 13px;
  color: #409eff;
  font-weight: 500;
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
  grid-template-columns: 56px 1fr 60px 80px 90px 90px 90px 40px;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: #fafafa;
  border-bottom: 1px solid #ebeef5;
  font-size: 12px;
  color: #909399;
  font-weight: 600;
}
.list-header.has-checkbox {
  grid-template-columns: 32px 56px 1fr 60px 80px 90px 90px 90px 40px;
}

.empty-state {
  margin-top: 100px;
}

.warn-text {
  color: #f56c6c;
  font-size: 13px;
}
</style>
