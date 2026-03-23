<template>
  <div class="admin-page">
    <!-- Header -->
    <header class="page-header">
      <div class="header-left">
        <h1 class="logo">WSI Platform</h1>
        <el-divider direction="vertical" />
        <span class="page-title">{{ $t('admin.userManagement') }}</span>
      </div>
      <div class="header-right">
        <span class="user-info">
          <el-tag type="warning" size="small">{{ $t('admin.reviewerTag') }}</el-tag>
          {{ userStore.user?.username }}
        </span>
        <LangSwitch />
        <el-button size="small" @click="$router.push({ name: 'reviewDashboard' })">{{ $t('admin.backToReview') }}</el-button>
        <el-button size="small" @click="handleLogout">{{ $t('auth.logout') }}</el-button>
      </div>
    </header>

    <div class="main-content">
      <el-tabs v-model="activeTab" class="main-tabs">
        <!-- Pending approval tab -->
        <el-tab-pane name="pending">
          <template #label>
            {{ $t('admin.pendingTab') }}
            <el-badge v-if="pendingUsers.length" :value="pendingUsers.length" type="danger" class="tab-badge" />
          </template>

          <div class="toolbar">
            <el-button :icon="Refresh" @click="loadPending" :loading="loadingPending">{{ $t('common.refresh') }}</el-button>
          </div>

          <el-empty v-if="!loadingPending && !pendingUsers.length" :description="$t('admin.noPendingUsers')" class="empty-state" />

          <el-table v-else :data="pendingUsers" v-loading="loadingPending" class="user-table">
            <el-table-column :label="$t('admin.username')" prop="username" />
            <el-table-column :label="$t('admin.role')">
              <template #default="{ row }">
                <el-tag :type="row.role === 'REVIEWER' ? 'warning' : 'success'" size="small">
                  {{ row.role === 'REVIEWER' ? $t('admin.reviewerLabel') : $t('admin.annotatorLabel') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('admin.registerTime')">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column :label="$t('admin.actions')" width="180">
              <template #default="{ row }">
                <el-button
                  type="success"
                  size="small"
                  :loading="approvingId === row.id"
                  @click="handleApprove(row)"
                >{{ $t('admin.approveBtn') }}</el-button>
                <el-button
                  type="danger"
                  size="small"
                  :loading="disablingId === row.id"
                  @click="handleDisable(row)"
                >{{ $t('admin.rejectBtn') }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- All users tab -->
        <el-tab-pane :label="$t('admin.allUsers')" name="all">
          <div class="toolbar">
            <el-button :icon="Refresh" @click="loadAll" :loading="loadingAll">{{ $t('common.refresh') }}</el-button>
          </div>

          <el-table :data="allUsers" v-loading="loadingAll" class="user-table">
            <el-table-column :label="$t('admin.username')" prop="username" />
            <el-table-column :label="$t('admin.role')">
              <template #default="{ row }">
                <el-tag :type="row.role === 'REVIEWER' ? 'warning' : 'success'" size="small">
                  {{ row.role === 'REVIEWER' ? $t('admin.reviewerLabel') : $t('admin.annotatorLabel') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('admin.status')">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="$t('admin.registerTime')">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column :label="$t('admin.actions')" width="180">
              <template #default="{ row }">
                <el-button
                  v-if="row.status !== 'ACTIVE'"
                  type="success"
                  size="small"
                  :loading="approvingId === row.id"
                  @click="handleApprove(row)"
                >{{ $t('admin.activateBtn') }}</el-button>
                <el-button
                  v-if="row.status !== 'DISABLED'"
                  type="danger"
                  size="small"
                  :loading="disablingId === row.id"
                  @click="handleDisable(row)"
                >{{ $t('admin.disableBtn') }}</el-button>
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
import { useI18n } from 'vue-i18n'
import { Refresh } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/userStore'
import { adminApi } from '@/api/auth'
import LangSwitch from '@/components/LangSwitch.vue'

const { t, locale } = useI18n()
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
    ElMessage.success(t('admin.activateSuccess', { name: user.username }))
    await loadPending()
    if (activeTab.value === 'all') await loadAll()
  } finally {
    approvingId.value = null
  }
}

async function handleDisable(user) {
  try {
    await ElMessageBox.confirm(
      t('admin.disableConfirm', { name: user.username }),
      t('admin.disableTitle'),
      { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' }
    )
  } catch {
    return
  }
  disablingId.value = user.id
  try {
    await adminApi.disable(user.id)
    ElMessage.success(t('admin.disableSuccess', { name: user.username }))
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
  return new Date(dt).toLocaleString(locale.value === 'en-US' ? 'en-US' : 'zh-CN', { hour12: false })
}

function statusLabel(status) {
  const map = {
    PENDING: t('admin.statusPending'),
    ACTIVE: t('admin.statusActive'),
    DISABLED: t('admin.statusDisabled'),
  }
  return map[status] ?? status
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
