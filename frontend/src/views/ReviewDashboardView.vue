<template>
  <div class="review-page">
    <!-- Header -->
    <header class="page-header">
      <div class="header-left">
        <h1 class="logo">WSI Platform</h1>
        <el-divider direction="vertical" />
        <span class="page-title">{{ $t('review.dashboard') }}</span>
      </div>
      <div class="header-right">
        <span class="user-info">
          <el-tag type="warning" size="small">{{ $t('review.reviewerTag') }}</el-tag>
          {{ userStore.user?.username }}
        </span>
        <NotificationBell />
        <LangSwitch />
        <el-button size="small" @click="$router.push({ name: 'admin' })">{{ $t('review.userManagementBtn') }}</el-button>
        <el-button size="small" @click="handleLogout">{{ $t('auth.logout') }}</el-button>
      </div>
    </header>

    <!-- Stats tabs -->
    <el-tabs v-model="activeTab" class="main-tabs" @tab-change="onTabChange">
      <!-- Pending Review Queue -->
      <el-tab-pane :label="$t('review.pendingQueue')" name="pending">
        <div class="toolbar">
          <el-button :icon="Refresh" @click="loadPending">{{ $t('common.refresh') }}</el-button>
        </div>

        <el-empty v-if="!pending.length" :description="$t('review.noPendingSlides')" class="empty-state" />

        <div v-else class="pending-list">
          <div v-for="s in pending" :key="s.id" class="pending-card">
            <div class="card-thumb">
              <img :src="`/api/slides/${s.id}/thumbnail`" class="thumb-img" loading="lazy" />
            </div>
            <div class="card-info">
              <p class="card-filename">{{ s.fileName }}</p>
              <p class="card-meta">
                {{ $t('review.submittedBy') }}<strong>{{ annotatorName(s.annotatorId) }}</strong>
              </p>
            </div>
            <div class="card-actions">
              <el-button
                type="success"
                size="small"
                :loading="approving === s.id"
                @click="handleApprove(s)"
              >{{ $t('review.approveBtn') }}</el-button>
              <el-button
                type="danger"
                size="small"
                :loading="rejecting === s.id"
                @click="openRejectDialog(s)"
              >{{ $t('review.rejectBtn') }}</el-button>
              <el-button
                size="small"
                @click="router.push({ name: 'slideViewer', params: { id: s.id } })"
              >{{ $t('review.viewAnnotations') }}</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Approved Slides (export) -->
      <el-tab-pane :label="$t('review.approvedSlides')" name="approved">
        <div class="toolbar">
          <el-button :icon="Refresh" @click="loadApproved">{{ $t('common.refresh') }}</el-button>
          <el-button
            type="primary"
            :icon="Download"
            :disabled="!approved.length"
            @click="showBatchExport = true"
          >{{ $t('review.batchExport') }}</el-button>
        </div>

        <el-empty v-if="!approved.length" :description="$t('review.noApprovedSlides')" class="empty-state" />

        <div v-else class="pending-list">
          <div v-for="s in approved" :key="s.id" class="pending-card">
            <div class="card-thumb">
              <img :src="`/api/slides/${s.id}/thumbnail`" class="thumb-img" loading="lazy" />
            </div>
            <div class="card-info">
              <p class="card-filename">{{ s.fileName }}</p>
              <p class="card-meta">
                {{ $t('review.annotatedBy') }}<strong>{{ annotatorName(s.annotatorId) }}</strong>
              </p>
            </div>
            <div class="card-actions">
              <el-button
                type="primary"
                size="small"
                :icon="Download"
                @click="openSingleExport(s)"
              >{{ $t('review.exportBtn') }}</el-button>
              <el-button
                size="small"
                @click="router.push({ name: 'slideViewer', params: { id: s.id } })"
              >{{ $t('review.viewAnnotations') }}</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- Audit Log -->
      <el-tab-pane :label="$t('review.auditLog')" name="auditLog">
        <div class="toolbar" style="display:flex;gap:12px;flex-wrap:wrap;align-items:center">
          <el-select v-model="auditFilter.action" :placeholder="$t('review.auditFilterAction')" clearable style="width:140px" @change="loadAuditLogs(true)">
            <el-option :label="$t('review.auditActionSave')" value="SAVE" />
            <el-option :label="$t('review.auditActionSubmit')" value="SUBMIT" />
            <el-option :label="$t('review.auditActionResubmit')" value="RESUBMIT" />
            <el-option :label="$t('review.auditActionApprove')" value="APPROVE" />
            <el-option :label="$t('review.auditActionReject')" value="REJECT" />
            <el-option :label="$t('review.auditActionImport')" value="IMPORT" />
          </el-select>
          <el-date-picker
            v-model="auditFilter.dateRange"
            type="datetimerange"
            :range-separator="$t('review.auditDateSep')"
            :start-placeholder="$t('review.auditDateFrom')"
            :end-placeholder="$t('review.auditDateTo')"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width:360px"
            @change="loadAuditLogs(true)"
          />
          <el-button :icon="Refresh" @click="loadAuditLogs(true)">{{ $t('common.refresh') }}</el-button>
        </div>

        <el-table
          :data="auditLogs"
          v-loading="auditLoading"
          border
          style="width:100%;margin-top:4px"
        >
          <el-table-column prop="createdAt" :label="$t('review.auditColTime')" width="170">
            <template #default="{ row }">
              {{ formatAuditTime(row.createdAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="username" :label="$t('review.auditColUser')" width="120" />
          <el-table-column prop="action" :label="$t('review.auditColAction')" width="110">
            <template #default="{ row }">
              <el-tag :type="auditActionType[row.action] || ''" size="small" effect="plain">
                {{ auditActionLabel(row.action) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="slideId" :label="$t('review.auditColSlideId')" width="90" align="center" />
          <el-table-column :label="$t('review.auditColDetail')" min-width="200">
            <template #default="{ row }">
              <span class="audit-detail">{{ formatAuditDetail(row.detail) }}</span>
            </template>
          </el-table-column>
        </el-table>

        <div style="margin-top:16px;display:flex;justify-content:flex-end">
          <el-pagination
            v-model:current-page="auditCurrentPage"
            v-model:page-size="auditPageSize"
            :total="auditTotal"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @size-change="loadAuditLogs(true)"
            @current-change="loadAuditLogs(false)"
          />
        </div>
      </el-tab-pane>

      <!-- Annotation Statistics -->
      <el-tab-pane :label="$t('review.statistics')" name="statistics">
        <AnnotationStatsPanel v-if="activeTab === 'statistics'" />
      </el-tab-pane>

      <!-- Annotator Progress -->
      <el-tab-pane :label="$t('review.annotatorProgress')" name="progress">
        <div class="toolbar">
          <el-button :icon="Refresh" @click="loadAnnotators">{{ $t('common.refresh') }}</el-button>
        </div>

        <el-table :data="annotators" border style="width:100%">
          <el-table-column prop="username" :label="$t('review.progressColAnnotator')" width="140" />
          <el-table-column prop="total" :label="$t('review.progressColTotal')" width="80" align="center" />
          <el-table-column prop="inProgress" :label="$t('review.progressColInProgress')" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ row.inProgress }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="pendingReview" :label="$t('review.progressColPending')" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" type="warning">{{ row.pendingReview }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="approved" :label="$t('review.progressColApproved')" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" type="success">{{ row.approved }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="rejected" :label="$t('review.progressColRejected')" width="80" align="center">
            <template #default="{ row }">
              <el-tag size="small" type="danger">{{ row.rejected }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="$t('review.progressColCompletion')" min-width="120">
            <template #default="{ row }">
              <el-progress
                v-if="row.total > 0"
                :percentage="Math.round((row.approved / row.total) * 100)"
                :stroke-width="10"
              />
              <span v-else style="color:#c0c4cc">—</span>
            </template>
          </el-table-column>
          <el-table-column :label="$t('review.progressColRemark')" min-width="160">
            <template #default="{ row }">
              <el-input
                v-model="row.remark"
                size="small"
                :placeholder="$t('review.progressRemarkPlaceholder')"
                @change="saveRemark(row)"
              />
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
    <el-dialog v-model="showReject" :title="$t('review.rejectDialogTitle')" width="420px">
      <p style="margin-bottom:12px; color:#606266">
        {{ $t('review.rejectDialogDesc', { name: rejectTarget?.fileName }) }}
      </p>
      <el-input
        v-model="rejectComment"
        type="textarea"
        :rows="4"
        :placeholder="$t('review.rejectPlaceholder')"
        maxlength="500"
        show-word-limit
      />
      <template #footer>
        <el-button @click="showReject = false">{{ $t('common.cancel') }}</el-button>
        <el-button
          type="danger"
          :loading="rejecting === rejectTarget?.id"
          :disabled="!rejectComment.trim()"
          @click="confirmReject"
        >{{ $t('review.confirmReject') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Refresh, Download } from '@element-plus/icons-vue'
import NotificationBell from '@/components/NotificationBell.vue'
import LangSwitch from '@/components/LangSwitch.vue'
import { reviewApi } from '@/api/review'
import { auditLogApi } from '@/api/auditLog'
import { useUserStore } from '@/stores/userStore'
import ExportDialog from '@/components/ExportDialog.vue'
import BatchExportDialog from '@/components/BatchExportDialog.vue'
import AnnotationStatsPanel from '@/components/AnnotationStatsPanel.vue'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()

const activeTab  = ref('pending')

function onTabChange(tab) {
  if (tab === 'auditLog' && auditLogs.value.length === 0) loadAuditLogs(true)
}
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

async function saveRemark(row) {
  try {
    await reviewApi.updateAnnotatorRemark(row.id, row.remark || '')
  } catch {
    ElMessage.error(t('review.remarkSaveFailed'))
  }
}

function annotatorName(id) {
  const a = annotators.value.find(u => u.id === id)
  return a ? a.username : `#${id}`
}

async function handleApprove(slide) {
  approving.value = slide.id
  try {
    await reviewApi.approve(slide.id)
    ElMessage.success(t('review.approvedMsg', { name: slide.fileName }))
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
    ElMessage.success(t('review.rejectedMsg', { name: rejectTarget.value.fileName }))
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

// ── Audit log ─────────────────────────────────────────────────────────────────
const auditLogs        = ref([])
const auditLoading     = ref(false)
const auditTotal       = ref(0)
const auditCurrentPage = ref(1)
const auditPageSize    = ref(20)
const auditFilter      = ref({ action: '', dateRange: null })

const auditActionKeys = {
  SAVE:     'review.auditActionSave',
  SUBMIT:   'review.auditActionSubmit',
  RESUBMIT: 'review.auditActionResubmit',
  APPROVE:  'review.auditActionApprove',
  REJECT:   'review.auditActionReject',
  IMPORT:   'review.auditActionImport',
}
const auditActionType = {
  SAVE:     '',
  SUBMIT:   'primary',
  RESUBMIT: 'warning',
  APPROVE:  'success',
  REJECT:   'danger',
  IMPORT:   'info'
}

function auditActionLabel(action) {
  const key = auditActionKeys[action]
  return key ? t(key) : action
}

function formatAuditTime(dt) {
  if (!dt) return ''
  return dt.replace('T', ' ').substring(0, 19)
}

function formatAuditDetail(detail) {
  if (!detail) return ''
  try {
    const obj = typeof detail === 'string' ? JSON.parse(detail) : detail
    return Object.entries(obj).map(([k, v]) => `${k}: ${v}`).join('  |  ')
  } catch {
    return detail
  }
}

async function loadAuditLogs(resetPage = true) {
  if (resetPage) auditCurrentPage.value = 1
  auditLoading.value = true
  try {
    const params = {
      page: auditCurrentPage.value - 1,
      size: auditPageSize.value
    }
    if (auditFilter.value.action) params.action = auditFilter.value.action
    if (auditFilter.value.dateRange?.[0]) params.from = auditFilter.value.dateRange[0]
    if (auditFilter.value.dateRange?.[1]) params.to   = auditFilter.value.dateRange[1]
    const res = await auditLogApi.list(params)
    auditLogs.value  = res.data.records ?? []
    auditTotal.value = res.data.total ?? 0
  } finally {
    auditLoading.value = false
  }
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

.audit-detail {
  font-size: 12px;
  color: #606266;
}
</style>
