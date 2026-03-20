<template>
  <div class="admin-page">
    <!-- Header -->
    <header class="page-header">
      <div class="header-left">
        <h1 class="logo">WSI Platform</h1>
        <el-divider direction="vertical" />
        <span class="page-title">用户管理</span>
      </div>
      <div class="header-right">
        <span class="user-info">
          <el-tag type="warning" size="small">审核者</el-tag>
          {{ userStore.user?.username }}
        </span>
        <el-button size="small" @click="$router.push({ name: 'reviewDashboard' })">返回审核</el-button>
        <el-button size="small" @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <div class="main-content">
      <el-tabs v-model="activeTab" class="main-tabs">
        <!-- Pending approval tab -->
        <el-tab-pane name="pending">
          <template #label>
            待审批
            <el-badge v-if="pendingUsers.length" :value="pendingUsers.length" type="danger" class="tab-badge" />
          </template>

          <div class="toolbar">
            <el-button :icon="Refresh" @click="loadPending" :loading="loadingPending">刷新</el-button>
          </div>

          <el-empty v-if="!loadingPending && !pendingUsers.length" description="暂无待审批用户" class="empty-state" />

          <el-table v-else :data="pendingUsers" v-loading="loadingPending" class="user-table">
            <el-table-column label="用户名" prop="username" />
            <el-table-column label="角色">
              <template #default="{ row }">
                <el-tag :type="row.role === 'REVIEWER' ? 'warning' : 'success'" size="small">
                  {{ row.role === 'REVIEWER' ? '审阅者' : '标注员' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="注册时间">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button
                  type="success"
                  size="small"
                  :loading="approvingId === row.id"
                  @click="handleApprove(row)"
                >通过</el-button>
                <el-button
                  type="danger"
                  size="small"
                  :loading="disablingId === row.id"
                  @click="handleDisable(row)"
                >拒绝</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- All users tab -->
        <el-tab-pane label="所有用户" name="all">
          <div class="toolbar">
            <el-button :icon="Refresh" @click="loadAll" :loading="loadingAll">刷新</el-button>
          </div>

          <el-table :data="allUsers" v-loading="loadingAll" class="user-table">
            <el-table-column label="用户名" prop="username" />
            <el-table-column label="角色">
              <template #default="{ row }">
                <el-tag :type="row.role === 'REVIEWER' ? 'warning' : 'success'" size="small">
                  {{ row.role === 'REVIEWER' ? '审阅者' : '标注员' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="注册时间">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button
                  v-if="row.status !== 'ACTIVE'"
                  type="success"
                  size="small"
                  :loading="approvingId === row.id"
                  @click="handleApprove(row)"
                >激活</el-button>
                <el-button
                  v-if="row.status !== 'DISABLED'"
                  type="danger"
                  size="small"
                  :loading="disablingId === row.id"
                  @click="handleDisable(row)"
                >禁用</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/userStore'
import { adminApi } from '@/api/auth'

const router = useRouter()
const userStore = useUserStore()

const activeTab = ref('pending')
const pendingUsers = ref([])
const allUsers = ref([])
const loadingPending = ref(false)
const loadingAll = ref(false)
const approvingId = ref(null)
const disablingId = ref(null)

async function loadPending() {
  loadingPending.value = true
  try {
    const res = await adminApi.listPending()
    pendingUsers.value = res.data.data ?? res.data
  } finally {
    loadingPending.value = false
  }
}

async function loadAll() {
  loadingAll.value = true
  try {
    const res = await adminApi.listAll()
    allUsers.value = res.data.data ?? res.data
  } finally {
    loadingAll.value = false
  }
}

async function handleApprove(user) {
  approvingId.value = user.id
  try {
    await adminApi.approve(user.id)
    ElMessage.success(`已激活用户 ${user.username}`)
    await loadPending()
    if (activeTab.value === 'all') await loadAll()
  } finally {
    approvingId.value = null
  }
}

async function handleDisable(user) {
  try {
    await ElMessageBox.confirm(
      `确定要禁用用户 "${user.username}" 吗？`,
      '禁用确认',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  disablingId.value = user.id
  try {
    await adminApi.disable(user.id)
    ElMessage.success(`已禁用用户 ${user.username}`)
    await loadPending()
    if (activeTab.value === 'all') await loadAll()
  } finally {
    disablingId.value = null
  }
}

function handleLogout() {
  userStore.logout()
  router.push({ name: 'login' })
}

function formatDate(dt) {
  if (!dt) return '-'
  return new Date(dt).toLocaleString('zh-CN', { hour12: false })
}

function statusLabel(status) {
  return { PENDING: '待审批', ACTIVE: '正常', DISABLED: '已禁用' }[status] ?? status
}

function statusTagType(status) {
  return { PENDING: 'warning', ACTIVE: 'success', DISABLED: 'danger' }[status] ?? 'info'
}

watch(activeTab, (tab) => {
  if (tab === 'all' && !allUsers.value.length) loadAll()
})

onMounted(() => {
  loadPending()
})
</script>

<style scoped>
.admin-page {
  min-height: 100vh;
  background: #f5f7fa;
  display: flex;
  flex-direction: column;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  font-size: 20px;
  font-weight: 700;
  color: #409eff;
  margin: 0;
}

.page-title {
  font-size: 15px;
  color: #303133;
  font-weight: 500;
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
  font-size: 13px;
  color: #606266;
}

.main-content {
  flex: 1;
  padding: 24px;
}

.main-tabs {
  background: #fff;
  border-radius: 8px;
  padding: 0 16px 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.toolbar {
  margin: 12px 0;
}

.user-table {
  margin-top: 8px;
}

.empty-state {
  padding: 48px 0;
}

.tab-badge {
  margin-left: 6px;
}
</style>
