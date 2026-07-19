<template>
  <div class="ws-card" :class="{ 'is-selected': selected }" @click="$emit('open', workspace)">
    <!-- Checkbox -->
    <div v-if="selectable" class="card-checkbox" @click.stop>
      <el-checkbox :model-value="selected" @change="$emit('toggle-select', workspace)" />
    </div>
    <!-- Icon area -->
    <div class="card-icon">
      <el-icon :size="32" color="var(--po-harbor)"><FolderOpened /></el-icon>
    </div>

    <!-- Info -->
    <div class="card-body">
      <div class="card-title" :title="workspace.name">{{ workspace.name }}</div>
      <div class="card-desc" :title="workspace.description || ''">
        {{ workspace.description || $t('workspace.noDescription') }}
      </div>
      <div class="card-meta">{{ $t('workspace.createdAt', { date: formatDate(workspace.createdAt) }) }}</div>
    </div>

    <!-- Actions (stop click propagation) -->
    <div class="card-actions" @click.stop>
      <el-dropdown trigger="click" @command="handleCommand">
        <el-button text :icon="MoreFilled" size="small" />
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="rename">
              <el-icon><Edit /></el-icon> {{ $t('workspace.renameWorkspace') }}
            </el-dropdown-item>
            <el-dropdown-item command="delete" class="danger-item">
              <el-icon><Delete /></el-icon> {{ $t('common.delete') }}
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
  workspace: { type: Object, required: true },
  selectable: { type: Boolean, default: false },
  selected: { type: Boolean, default: false }
})

const emit = defineEmits(['open', 'rename', 'delete', 'toggle-select'])

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
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 152px;
  padding: 20px;
  border: 1px solid var(--po-hairline);
  border-radius: var(--po-radius-md);
  background: var(--po-paper);
  cursor: pointer;
  transition:
    border-color var(--po-motion-fast) var(--po-ease-state),
    background-color var(--po-motion-fast) var(--po-ease-state);
}

.ws-card:hover {
  border-color: oklch(0.75 0.04 230);
  background: var(--po-harbor-soft);
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
  margin-bottom: 4px;
  overflow: hidden;
  color: var(--po-ink);
  font-size: 16px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-desc {
  margin-bottom: 8px;
  overflow: hidden;
  color: var(--po-ink-muted);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-meta {
  color: var(--po-ink-muted);
  font-size: 12px;
}

.card-checkbox {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 2;
}
.ws-card.is-selected {
  border-color: var(--po-harbor);
  outline: 2px solid var(--po-harbor-soft);
  background: var(--po-harbor-soft);
}

.card-actions {
  position: absolute;
  top: 12px;
  right: 12px;
}

:deep(.danger-item) {
  color: var(--po-risk) !important;
}
</style>
