<template>
  <div class="workspace-page">
    <!-- Header -->
    <header class="page-header">
      <div class="header-content">
        <h1 class="logo">WSI Platform</h1>
        <p class="subtitle">全切片图像标注平台</p>
      </div>
      <div class="header-right">
        <span class="user-info">
          <el-tag type="success" size="small">标注者</el-tag>
          {{ userStore.user?.username }}
        </span>
        <el-button size="small" @click="handleLogout">退出登录</el-button>
        <el-button type="primary" :icon="Plus" @click="showCreate = true">
          新建工作空间
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
      description="还没有工作空间，点击右上角新建一个吧"
      class="empty-state"
    />

    <!-- Workspace cards -->
    <div v-else class="card-grid">
      <WorkspaceCard
        v-for="ws in store.workspaces"
        :key="ws.id"
        :workspace="ws"
        @open="handleOpen"
        @rename="handleRenameClick"
        @delete="handleDeleteClick"
      />
    </div>

    <!-- Dialogs -->
    <CreateWorkspaceDialog v-model="showCreate" @created="onCreated" />

    <RenameWorkspaceDialog
      v-model="showRename"
      :workspace="renameTarget"
      @renamed="onRenamed"
    />

    <el-dialog v-model="showDelete" title="删除工作空间" width="400px">
      <p>
        确定要删除工作空间
        <strong>「{{ deleteTarget?.name }}」</strong>
        吗？<br />
        <span class="warn-text">工作空间内所有图像和标注将被永久删除，不可恢复。</span>
      </p>
      <template #footer>
        <el-button @click="showDelete = false">取消</el-button>
        <el-button type="danger" :loading="deleting" @click="confirmDelete">确认删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

import { useWorkspaceStore } from '@/stores/workspaceStore'
import { useUserStore } from '@/stores/userStore'
import WorkspaceCard from '@/components/WorkspaceCard.vue'
import CreateWorkspaceDialog from '@/components/CreateWorkspaceDialog.vue'
import RenameWorkspaceDialog from '@/components/RenameWorkspaceDialog.vue'

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
  ElMessage.success(`工作空间「${ws.name}」已创建`)
}

function onRenamed(ws) {
  ElMessage.success(`已重命名为「${ws.name}」`)
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await store.remove(deleteTarget.value.id)
    ElMessage.success('工作空间已删除')
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
