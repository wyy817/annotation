<template>
  <!-- Grid card -->
  <div v-if="viewMode === 'grid'" class="slide-card" :class="{ 'is-selected': selected }" @click="$emit('open', slide)">
    <!-- Checkbox -->
    <div v-if="selectable" class="card-checkbox" @click.stop>
      <el-checkbox :model-value="selected" @change="$emit('toggle-select', slide)" />
    </div>
    <!-- Thumbnail -->
    <div class="thumb-wrap">
      <img
        v-if="slide.status === 'READY'"
        :src="`/api/slides/${slide.id}/thumbnail`"
        class="thumb-img"
        loading="lazy"
        @error="imgError = true"
      />
      <div v-else class="thumb-placeholder">
        <el-icon v-if="slide.status === 'PENDING' || slide.status === 'PROCESSING'" class="spin">
          <Loading />
        </el-icon>
        <el-icon v-else-if="slide.status === 'ERROR'" style="color:#f56c6c">
          <WarningFilled />
        </el-icon>
        <el-icon v-else><Picture /></el-icon>
      </div>
    </div>

    <!-- Info -->
    <div class="card-body">
      <p class="file-name" :title="slide.fileName">{{ slide.fileName }}</p>
      <div class="badge-row">
        <el-tag size="small" type="info">{{ slide.format }}</el-tag>
        <el-tag size="small" :type="statusType(slide.status)">{{ statusLabel(slide.status) }}</el-tag>
        <el-tag size="small" :type="annoType(slide.annoStatus)">{{ annoLabel(slide.annoStatus) }}</el-tag>
        <el-tag v-if="slide.reviewStatus && slide.reviewStatus !== 'NOT_SUBMITTED'" size="small" :type="reviewTagType(slide.reviewStatus)">{{ reviewLabel(slide.reviewStatus) }}</el-tag>
      </div>
      <p class="meta">{{ formatSize(slide.fileSize) }}</p>
    </div>

    <!-- Actions dropdown -->
    <el-dropdown class="card-actions" trigger="click" @click.stop @command="handleCommand">
      <el-icon class="action-icon" @click.stop><MoreFilled /></el-icon>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="open" :disabled="slide.status !== 'READY'">打开查看</el-dropdown-item>
          <el-dropdown-item command="move">移动到...</el-dropdown-item>
          <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>

  <!-- List row -->
  <div v-else class="slide-row" :class="{ 'is-selected': selected, 'has-checkbox': selectable }" @click="$emit('open', slide)">
    <div v-if="selectable" class="row-checkbox" @click.stop>
      <el-checkbox :model-value="selected" @change="$emit('toggle-select', slide)" />
    </div>
    <div class="row-thumb">
      <img
        v-if="slide.status === 'READY'"
        :src="`/api/slides/${slide.id}/thumbnail`"
        class="row-thumb-img"
        loading="lazy"
      />
      <el-icon v-else style="font-size:24px; color:#c0c4cc"><Picture /></el-icon>
    </div>
    <div class="row-name">
      <span :title="slide.fileName">{{ slide.fileName }}</span>
    </div>
    <div class="row-meta">{{ slide.format }}</div>
    <div class="row-meta">{{ formatSize(slide.fileSize) }}</div>
    <div class="row-meta">
      <el-tag size="small" :type="statusType(slide.status)">{{ statusLabel(slide.status) }}</el-tag>
    </div>
    <div class="row-meta">
      <el-tag size="small" :type="annoType(slide.annoStatus)">{{ annoLabel(slide.annoStatus) }}</el-tag>
    </div>
    <div class="row-meta">
      <el-tag v-if="slide.reviewStatus && slide.reviewStatus !== 'NOT_SUBMITTED'" size="small" :type="reviewTagType(slide.reviewStatus)">{{ reviewLabel(slide.reviewStatus) }}</el-tag>
    </div>
    <div class="row-actions" @click.stop>
      <el-dropdown trigger="click" @command="handleCommand">
        <el-button link :icon="MoreFilled" />
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="open" :disabled="slide.status !== 'READY'">打开查看</el-dropdown-item>
            <el-dropdown-item command="move">移动到...</el-dropdown-item>
            <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { Loading, WarningFilled, Picture, MoreFilled } from '@element-plus/icons-vue'

const props = defineProps({
  slide: { type: Object, required: true },
  viewMode: { type: String, default: 'grid' }, // 'grid' | 'list'
  selectable: { type: Boolean, default: false },
  selected: { type: Boolean, default: false }
})

const emit = defineEmits(['open', 'move', 'delete', 'toggle-select'])

function handleCommand(cmd) {
  if (cmd === 'open') emit('open', props.slide)
  else if (cmd === 'move') emit('move', props.slide)
  else if (cmd === 'delete') emit('delete', props.slide)
}

function statusType(s) {
  return { PENDING: 'info', PROCESSING: 'warning', READY: 'success', ERROR: 'danger' }[s] ?? 'info'
}
function statusLabel(s) {
  return { PENDING: '等待处理', PROCESSING: '处理中', READY: '就绪', ERROR: '错误' }[s] ?? s
}
function annoType(s) {
  return { NOT_STARTED: '', IN_PROGRESS: 'warning', DONE: 'success' }[s] ?? ''
}
function annoLabel(s) {
  return { NOT_STARTED: '未标注', IN_PROGRESS: '标注中', DONE: '已完成' }[s] ?? s
}
function reviewTagType(s) {
  return { PENDING_REVIEW: 'warning', APPROVED: 'success', REJECTED: 'danger' }[s] ?? 'info'
}
function reviewLabel(s) {
  return { PENDING_REVIEW: '待审核', APPROVED: '已通过', REJECTED: '已拒绝' }[s] ?? s
}
function formatSize(bytes) {
  if (!bytes) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}
</script>

<style scoped>
/* ── Grid card ── */
.slide-card {
  position: relative;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  transition: box-shadow 0.2s;
  overflow: hidden;
}
.slide-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.14);
}

.thumb-wrap {
  width: 100%;
  aspect-ratio: 4/3;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.thumb-placeholder {
  font-size: 40px;
  color: #c0c4cc;
}
.spin {
  animation: rotate 1.2s linear infinite;
}
@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.card-body {
  padding: 10px 12px 12px;
}
.file-name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 6px;
}
.badge-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 4px;
}
.meta {
  font-size: 12px;
  color: #909399;
}

.card-checkbox {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 2;
  background: rgba(255,255,255,0.85);
  border-radius: 4px;
  padding: 2px;
}
.slide-card.is-selected {
  outline: 2px solid #409eff;
}

.card-actions {
  position: absolute;
  top: 8px;
  right: 8px;
}
.action-icon {
  font-size: 18px;
  color: #606266;
  background: rgba(255,255,255,0.85);
  border-radius: 4px;
  padding: 2px;
  cursor: pointer;
}
.action-icon:hover {
  color: #409eff;
}

/* ── List row ── */
.row-checkbox {
  display: flex;
  align-items: center;
  justify-content: center;
}
.slide-row.is-selected {
  background: #ecf5ff;
}
.slide-row {
  display: grid;
  grid-template-columns: 56px 1fr 60px 80px 90px 90px 90px 40px;
  align-items: center;
  gap: 12px;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: background 0.15s;
}
.slide-row.has-checkbox {
  grid-template-columns: 32px 56px 1fr 60px 80px 90px 90px 90px 40px;
}
.slide-row:hover {
  background: #f5f7fa;
}
.row-thumb {
  width: 56px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  overflow: hidden;
  background: #f5f7fa;
  flex-shrink: 0;
}
.row-thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.row-name {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.row-meta {
  font-size: 12px;
  color: #606266;
}
.row-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
