<template>
  <div class="workspace-page">
    <!-- Header -->
    <header class="page-header">
      <div class="header-content">
        <h1 class="logo">WSI Platform</h1>
        <p class="subtitle">{{ $t('app.subtitle') }}</p>
      </div>
      <div class="header-right">
        <span class="user-info">
          <el-tag type="success" size="small">{{ $t('workspace.annotatorTag') }}</el-tag>
          {{ userStore.user?.username }}
        </span>
        <NotificationBell />
        <LangSwitch />
        <el-button size="small" @click="handleLogout">{{ $t('auth.logout') }}</el-button>
        <el-button
          :type="selectionMode ? 'warning' : ''"
          @click="toggleSelectionMode"
        >{{ selectionMode ? $t('common.exitSelection') : $t('common.batchSelect') }}</el-button>
        <el-button type="primary" :icon="Plus" @click="showCreate = true">
          {{ $t('workspace.newWorkspace') }}
        </el-button>
      </div>
    </header>

    <!-- Loading skeleton -->
    <div v-if="store.loading" class="card-grid">
      <el-skeleton v-for="n in 4" :key="n" class="skeleton-card" :rows="3" animated />
    </div>

    <!-- Empty state -->
    <el-empty
      v-else-if="store.workspaces.length === 0"
      :description="$t('workspace.empty')"
      class="empty-state"
    />

    <!-- Workspace cards (with optional batch bar) -->
    <template v-else>
      <!-- Batch action bar -->
      <div v-if="selectionMode" class="batch-bar">
        <el-checkbox
          :model-value="isAllSelected"
          :indeterminate="isIndeterminate"
          @change="toggleSelectAll"
        >{{ $t('common.selectAll') }}</el-checkbox>
        <span class="batch-info">{{ $t('common.selectedCount', { count: selectedIds.size }) }}</span>
        <el-button
          type="danger"
          :icon="Delete"
          :disabled="!selectedIds.size"
          @click="showBatchDelete = true"
        >{{ $t('common.delete') }}</el-button>
      </div>

      <div class="card-grid">
      <WorkspaceCard
        v-for="ws in store.workspaces"
        :key="ws.id"
        :workspace="ws"
        :selectable="selectionMode"
        :selected="selectedIds.has(ws.id)"
        @open="handleOpen"
        @rename="handleRenameClick"
        @delete="handleDeleteClick"
        @toggle-select="toggleSelect"
      />
      </div>
    </template>

    <!-- Dialogs -->
    <CreateWorkspaceDialog v-model="showCreate" @created="onCreated" />

    <RenameWorkspaceDialog
      v-model="showRename"
      :workspace="renameTarget"
      @renamed="onRenamed"
    />

    <!-- Batch delete confirm -->
    <el-dialog v-model="showBatchDelete" :title="$t('workspace.batchDeleteTitle')" width="420px">
      <p>
        {{ $t('workspace.batchDeleteConfirm', { count: selectedIds.size }) }}<br />
        <span class="warn-text">{{ $t('workspace.allDataWarn') }}</span>
      </p>
      <template #footer>
        <el-button @click="showBatchDelete = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="danger" :loading="batchDeleting" @click="confirmBatchDelete">{{ $t('common.confirmDelete') }}</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDelete" :title="$t('workspace.deleteTitle')" width="400px">
      <p>
        {{ $t('workspace.deleteContentConfirm', { name: deleteTarget?.name }) }}<br />
        <span class="warn-text">{{ $t('workspace.allDataWarn') }}</span>
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
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Plus, Delete } from '@element-plus/icons-vue'
import NotificationBell from '@/components/NotificationBell.vue'
import LangSwitch from '@/components/LangSwitch.vue'

import { useWorkspaceStore } from '@/stores/workspaceStore'
import { useUserStore } from '@/stores/userStore'
import WorkspaceCard from '@/components/WorkspaceCard.vue'
import CreateWorkspaceDialog from '@/components/CreateWorkspaceDialog.vue'
import RenameWorkspaceDialog from '@/components/RenameWorkspaceDialog.vue'

const { t } = useI18n()
const router = useRouter()
const store = useWorkspaceStore()
const userStore = useUserStore()

function handleLogout() {
  userStore.logout()
  router.replace({ name: 'login' })
}

const showCreate = ref(false)
const showRename = ref(false)
const renameTarget = ref(null)
const showDelete = ref(false)
const deleteTarget = ref(null)
const deleting = ref(false)
const showBatchDelete = ref(false)
const batchDeleting = ref(false)

// ── selection state ──────────────────────────────────────────────────────
const selectionMode = ref(false)
const selectedIds = ref(new Set())

const isAllSelected = computed(() =>
  store.workspaces.length > 0 && selectedIds.value.size === store.workspaces.length
)
const isIndeterminate = computed(() =>
  selectedIds.value.size > 0 && selectedIds.value.size < store.workspaces.length
)

function toggleSelectionMode() {
  selectionMode.value = !selectionMode.value
  selectedIds.value = new Set()
}

function toggleSelect(ws) {
  const s = new Set(selectedIds.value)
  if (s.has(ws.id)) s.delete(ws.id)
  else s.add(ws.id)
  selectedIds.value = s
}

function toggleSelectAll(val) {
  if (val) {
    selectedIds.value = new Set(store.workspaces.map(ws => ws.id))
  } else {
    selectedIds.value = new Set()
  }
}

async function confirmBatchDelete() {
  batchDeleting.value = true
  try {
    const ids = [...selectedIds.value]
    for (const id of ids) {
      await store.remove(id)
    }
    ElMessage.success(t('workspace.batchDeleteSuccess', { count: ids.length }))
    selectedIds.value = new Set()
    showBatchDelete.value = false
  } finally {
    batchDeleting.value = false
  }
}

onMounted(() => store.fetchAll())

function handleOpen(ws) {
  router.push({ name: 'fileBrowser', params: { id: ws.id } })
}

function handleRenameClick(ws) {
  renameTarget.value = ws
  showRename.value = true
}

function handleDeleteClick(ws) {
  deleteTarget.value = ws
  showDelete.value = true
}

function onCreated(ws) {
  ElMessage.success(t('workspace.createdMsg', { name: ws.name }))
}

function onRenamed(ws) {
  ElMessage.success(t('workspace.renamedMsg', { name: ws.name }))
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await store.remove(deleteTarget.value.id)
    ElMessage.success(t('workspace.deleteSuccess'))
    showDelete.value = false
  } finally {
    deleting.value = false
  }
}
</script>

<style scoped>
.workspace-page {
  min-height: 100vh;
  padding: 0 0 48px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 40px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 32px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #606266;
}

.logo {
  font-size: 22px;
  font-weight: 700;
  color: #409eff;
  margin-bottom: 2px;
}

.subtitle {
  font-size: 13px;
  color: #909399;
}

.batch-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 40px;
  background: #ecf5ff;
  border-bottom: 1px solid #d9ecff;
  margin-bottom: 20px;
}
.batch-info {
  font-size: 13px;
  color: #409eff;
  font-weight: 500;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  padding: 0 40px;
}

.skeleton-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.empty-state {
  margin-top: 120px;
}

.warn-text {
  color: #f56c6c;
  font-size: 13px;
}
</style>
