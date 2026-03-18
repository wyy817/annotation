<template>
  <div class="review-page">
    <!-- Header -->
    <header class="page-header">
      <div class="header-left">
        <h1 class="logo">WSI Platform</h1>
        <el-divider direction="vertical" />
        <span class="page-title">审核仪表板</span>
      </div>
      <div class="header-right">
        <span class="user-info">
          <el-tag type="warning" size="small">审核者</el-tag>
          {{ userStore.user?.username }}
        </span>
        <el-button size="small" @click="handleLogout">退出登录</el-button>
      </div>
    </header>

    <!-- Stats tabs -->
    <el-tabs v-model="activeTab" class="main-tabs">
      <!-- Pending Review Queue -->
      <el-tab-pane label="待审核队列" name="pending">
        <div class="toolbar">
          <el-button :icon="Refresh" @click="loadPending">刷新</el-button>
        </div>

        <el-empty v-if="!pending.length" description="暂无待审核切片" class="empty-state" />

        <div v-else class="pending-list">
          <div v-for="s in pending" :key="s.id" class="pending-card">
            <div class="card-thumb">
              <img :src="`/api/slides/${s.id}/thumbnail`" class="thumb-img" loading="lazy" />
            </div>
            <div class="card-info">
              <p class="card-filename">{{ s.fileName }}</p>
              <p class="card-meta">
                提交者：<strong>{{ annotatorName(s.annotatorId) }}</strong>
              </p>
            </div>
            <div class="card-actions">
              <el-button
                type="success"
                size="small"
                :loading="approving === s.id"
                @click="handleApprove(s)"
              >通过</el-button>
              <el-button
                type="danger"
                size="small"
                :loading="rejecting === s.id"
                @click="openRejectDialog(s)"
              >拒绝</el-button>
              <el-button
                size="small"
                @click="router.push({ name: 'slideViewer', params: { id: s.id } })"
              >查看标注</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Approved Slides (export) -->
      <el-tab-pane label="已通过切片" name="approved">
        <div class="toolbar">
          <el-button :icon="Refresh" @click="loadApproved">刷新</el-button>
          <el-button
            type="primary"
            :icon="Download"
            :disabled="!approved.length"
            @click="showBatchExport = true"
          >批量导出</el-button>
        </div>

        <el-empty v-if="!approved.length" description="暂无已通过切片" class="empty-state" />

        <div v-else class="pending-list">
          <div v-for="s in approved" :key="s.id" class="pending-card">
            <div class="card-thumb">
              <img :src="`/api/slides/${s.id}/thumbnail`" class="thumb-img" loading="lazy" />
            </div>
            <div class="card-info">
              <p class="card-filename">{{ s.fileName }}</p>
              <p class="card-meta">
                标注者：<strong>{{ annotatorName(s.annotatorId) }}</strong>
              </p>
            </div>
            <div class="card-actions">
              <el-button
                type="primary"
                size="small"
                :icon="Download"
                @click="openSingleExport(s)"
              >导出</el-button>
              <el-button
                size="small"
                @click="router.push({ name: 'slideViewer', params: { id: s.id } })"
              >查看标注</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Annotator Progress -->
      <el-tab-pane label="标注者进度" name="progress">
        <div class="toolbar">
          <el-button :icon="Refresh" @click="loadAnnotators">刷新</el-button>
        </div>

        <el-table :data="annotators" border style="width:100%">
          <el-table-column prop="username" label="标注者" width="140" />
          <el-table-column prop="total" label="总切片" width="80" align="center" />
          <el-table-column prop="inProgress" label="标注中" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ row.inProgress }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="pendingReview" label="待审核" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" type="warning">{{ row.pendingReview }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="approved" label="已通过" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" type="success">{{ row.approved }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="rejected" label="已拒绝" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" type="danger">{{ row.rejected }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="完成率" min-width="120">
            <template #default="{ row }">
              <el-progress
                v-if="row.total > 0"
                :percentage="Math.round((row.approved / row.total) * 100)"
                :stroke-width="10"
              />
              <span v-else style="color:#c0c4cc">—</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- Single export dialog (approved tab) -->
    <ExportDialog v-model="showSingleExport" :slide-id="singleExportSlideId" />

    <!-- Batch export dialog (approved tab) -->
    <BatchExportDialog v-model="showBatchExport" :slides="approved" />

    <!-- Reject dialog -->
    <el-dialog v-model="showReject" title="拒绝标注" width="420px">
      <p style="margin-bottom:12px; color:#606266">
        拒绝切片 <strong>{{ rejectTarget?.fileName }}</strong>，请填写拒绝意见：
      </p>
      <el-input
        v-model="rejectComment"
        type="textarea"
        :rows="4"
        placeholder="请输入拒绝原因，标注者将看到该意见..."
        maxlength="500"
        show-word-limit
      />
      <template #footer>
        <el-button @click="showReject = false">取消</el-button>
        <el-button
          type="danger"
          :loading="rejecting === rejectTarget?.id"
          :disabled="!rejectComment.trim()"
          @click="confirmReject"
        >确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Download } from '@element-plus/icons-vue'
import { reviewApi } from '@/api/review'
import { useUserStore } from '@/stores/userStore'
import ExportDialog from '@/components/ExportDialog.vue'
import BatchExportDialog from '@/components/BatchExportDialog.vue'

const router = useRouter()
const userStore = useUserStore()

const activeTab  = ref('pending')
const pending    = ref([])
const approved   = ref([])
const annotators = ref([])
const approving  = ref(null)
const rejecting  = ref(null)

const showReject    = ref(false)
const rejectTarget  = ref(null)
const rejectComment = ref('')

// Export state
const showSingleExport    = ref(false)
const singleExportSlideId = ref(0)
const showBatchExport     = ref(false)

onMounted(() => {
  loadPending()
  loadApproved()
  loadAnnotators()
})

async function loadPending() {
  const res = await reviewApi.getPending()
  pending.value = res.data
}

async function loadApproved() {
  const res = await reviewApi.getApproved()
  approved.value = res.data
}

function openSingleExport(slide) {
  singleExportSlideId.value = slide.id
  showSingleExport.value = true
}

async function loadAnnotators() {
  const res = await reviewApi.getAnnotators()
  annotators.value = res.data
}

function annotatorName(id) {
  const a = annotators.value.find(u => u.id === id)
  return a ? a.username : `#${id}`
}

async function handleApprove(slide) {
  approving.value = slide.id
  try {
    await reviewApi.approve(slide.id)
    ElMessage.success(`「${slide.fileName}」已通过`)
    pending.value = pending.value.filter(s => s.id !== slide.id)
    loadApproved()
    loadAnnotators()
  } finally {
    approving.value = null
  }
}

function openRejectDialog(slide) {
  rejectTarget.value = slide
  rejectComment.value = ''
  showReject.value = true
}

async function confirmReject() {
  if (!rejectTarget.value) return
  rejecting.value = rejectTarget.value.id
  try {
    await reviewApi.reject(rejectTarget.value.id, rejectComment.value)
    ElMessage.success(`「${rejectTarget.value.fileName}」已拒绝`)
    pending.value = pending.value.filter(s => s.id !== rejectTarget.value.id)
    showReject.value = false
    loadAnnotators()
  } finally {
    rejecting.value = null
  }
}

function handleLogout() {
  userStore.logout()
  router.replace({ name: 'login' })
}
</script>

<style scoped>
.review-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 40px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
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
}

.page-title {
  font-size: 16px;
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
  font-size: 14px;
  color: #606266;
}

.main-tabs {
  padding: 24px 40px;
}

.toolbar {
  margin-bottom: 16px;
}

.empty-state {
  margin-top: 60px;
}

/* Pending list */
.pending-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.pending-card {
  display: flex;
  align-items: center;
  gap: 16px;
  background: #fff;
  border-radius: 8px;
  padding: 12px 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.08);
}

.card-thumb {
  width: 80px;
  height: 60px;
  border-radius: 4px;
  overflow: hidden;
  flex-shrink: 0;
  background: #f5f7fa;
}

.thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card-info {
  flex: 1;
  min-width: 0;
}

.card-filename {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 4px;
}

.card-meta {
  font-size: 13px;
  color: #909399;
}

.card-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
</style>
