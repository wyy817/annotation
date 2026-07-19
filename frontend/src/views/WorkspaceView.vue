<template>
  <ProductShell>
    <div class="workspace-page">
    <header class="library-header">
      <div>
        <span class="library-eyebrow">{{ t('po.library.eyebrow') }}</span>
        <h1>{{ t('po.library.title') }}</h1>
        <p>{{ t('po.library.subtitle') }}</p>
      </div>
      <div class="library-actions">
        <el-button
          :type="selectionMode ? 'warning' : ''"
          :icon="Operation"
          @click="toggleSelectionMode"
        >{{ selectionMode ? $t('common.exitSelection') : $t('common.batchSelect') }}</el-button>
        <el-button type="primary" :icon="Plus" @click="showCreate = true">
          {{ $t('workspace.newWorkspace') }}
        </el-button>
      </div>
    </header>

    <div v-if="store.loading" class="card-grid">
      <el-skeleton v-for="n in 4" :key="n" class="skeleton-card" :rows="3" animated />
    </div>

    <el-empty
      v-else-if="store.workspaces.length === 0"
      :description="$t('workspace.empty')"
      class="empty-state"
    />

    <template v-else>
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

    <CreateWorkspaceDialog v-model="showCreate" @created="onCreated" />

    <RenameWorkspaceDialog
      v-model="showRename"
      :workspace="renameTarget"
      @renamed="onRenamed"
    />

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
  </ProductShell>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Plus, Delete, Operation } from '@element-plus/icons-vue'
import ProductShell from '@/components/ProductShell.vue'

import { useWorkspaceStore } from '@/stores/workspaceStore'
import WorkspaceCard from '@/components/WorkspaceCard.vue'
import CreateWorkspaceDialog from '@/components/CreateWorkspaceDialog.vue'
import RenameWorkspaceDialog from '@/components/RenameWorkspaceDialog.vue'

const { t } = useI18n()
const router = useRouter()
const store = useWorkspaceStore()

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
  padding-bottom: 20px;
}

.library-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 26px;
  padding-bottom: 22px;
  border-bottom: 1px solid var(--po-hairline);
}

.library-eyebrow {
  color: var(--po-harbor);
  font-size: 11px;
  font-weight: 700;
}

.library-header h1 {
  margin: 6px 0 4px;
  color: var(--po-ink);
  font-size: 28px;
  font-weight: 680;
  letter-spacing: 0;
  line-height: 1.2;
}

.library-header p {
  margin: 0;
  color: var(--po-ink-muted);
  font-size: 13px;
}

.library-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.batch-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 48px;
  padding: 6px 12px;
  border: 1px solid oklch(0.86 0.04 230);
  border-radius: var(--po-radius-sm);
  background: var(--po-harbor-soft);
  margin-bottom: 20px;
}

.batch-info {
  color: var(--po-harbor);
  font-size: 12px;
  font-weight: 500;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.skeleton-card {
  padding: 20px;
  border: 1px solid var(--po-hairline);
  border-radius: var(--po-radius-md);
  background: var(--po-paper);
}

.empty-state {
  min-height: 320px;
}

.warn-text {
  color: var(--po-risk);
  font-size: 13px;
}

@media (max-width: 700px) {
  .library-header {
    align-items: stretch;
    flex-direction: column;
    gap: 16px;
  }

  .library-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .library-actions :deep(.el-button) {
    margin: 0;
  }

  .card-grid {
    grid-template-columns: 1fr;
  }

  .batch-bar {
    flex-wrap: wrap;
  }
}
</style>
