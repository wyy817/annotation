<template>
  <div class="ws-card" @click="$emit('open', workspace)">
    <!-- Icon area -->
    <div class="card-icon">
      <el-icon :size="36" color="#409eff"><FolderOpened /></el-icon>
    </div>

    <!-- Info -->
    <div class="card-body">
      <div class="card-title" :title="workspace.name">{{ workspace.name }}</div>
      <div class="card-desc" :title="workspace.description || ''">
        {{ workspace.description || '无描述' }}
      </div>
      <div class="card-meta">创建于 {{ formatDate(workspace.createdAt) }}</div>
    </div>

    <!-- Actions (stop click propagation) -->
    <div class="card-actions" @click.stop>
      <el-dropdown trigger="click" @command="handleCommand">
        <el-button text :icon="MoreFilled" size="small" />
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="rename">
              <el-icon><Edit /></el-icon> 重命名
            </el-dropdown-item>
            <el-dropdown-item command="delete" class="danger-item">
              <el-icon><Delete /></el-icon> 删除
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { MoreFilled, Edit, Delete, FolderOpened } from '@element-plus/icons-vue'

const props = defineProps({
  workspace: { type: Object, required: true }
})

const emit = defineEmits(['open', 'rename', 'delete'])

function handleCommand(cmd) {
  if (cmd === 'rename') emit('rename', props.workspace)
  else if (cmd === 'delete') emit('delete', props.workspace)
}

function formatDate(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
</script>

<style scoped>
.ws-card {
  position: relative;
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: box-shadow 0.2s, transform 0.15s;
}

.ws-card:hover {
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.15);
  transform: translateY(-2px);
}

.card-icon {
  display: flex;
  align-items: center;
}

.card-body {
  flex: 1;
  min-width: 0;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}

.card-desc {
  font-size: 13px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 8px;
}

.card-meta {
  font-size: 12px;
  color: #c0c4cc;
}

.card-actions {
  position: absolute;
  top: 12px;
  right: 12px;
}

:deep(.danger-item) {
  color: #f56c6c !important;
}
</style>
