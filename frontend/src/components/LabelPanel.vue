<template>
  <aside class="label-panel">
    <div class="panel-header">
      <span class="panel-title">标签</span>
    </div>

    <!-- Label list -->
    <div class="label-list">
      <div
        v-for="label in labels"
        :key="label.id"
        class="label-item"
        :class="{ active: label.id === activeId }"
        @click="$emit('select', label.id)"
      >
        <span class="color-dot" :style="{ background: label.color }" />
        <span class="label-name" :title="label.name">{{ label.name }}</span>
        <el-button
          class="del-btn"
          :icon="Delete"
          size="small"
          text
          type="danger"
          @click.stop="$emit('delete', label.id)"
        />
      </div>

      <div v-if="labels.length === 0" class="empty-hint">
        暂无标签，请先添加
      </div>
    </div>

    <!-- Add label form -->
    <div class="add-section">
      <el-divider style="margin: 8px 0" />
      <div class="add-row">
        <el-color-picker v-model="newColor" size="small" />
        <el-input
          v-model="newName"
          size="small"
          placeholder="标签名称"
          maxlength="32"
          @keydown.enter="doAdd"
          style="flex:1"
        />
        <el-button size="small" type="primary" :icon="Plus" @click="doAdd" />
      </div>
    </div>

    <!-- Brush size (shown when brush tool is active) -->
    <div class="brush-section" v-if="showBrushSize">
      <el-divider style="margin: 8px 0" />
      <div class="brush-label">
        <span>画笔大小</span>
        <strong>{{ brushSize }} px</strong>
      </div>
      <el-slider
        :model-value="brushSize"
        :min="1"
        :max="100"
        :step="1"
        size="small"
        @update:model-value="$emit('brush-size', $event)"
      />
    </div>
  </aside>
</template>

<script setup>
import { ref } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'

defineProps({
  labels:        { type: Array,   default: () => [] },
  activeId:      { type: Number,  default: null },
  brushSize:     { type: Number,  default: 20 },
  showBrushSize: { type: Boolean, default: false }
})

const emit = defineEmits(['select', 'add', 'delete', 'brush-size'])

const newName  = ref('')
const newColor = ref('#ff0000')

function doAdd() {
  const name = newName.value.trim()
  if (!name) return
  emit('add', { name, color: newColor.value })
  newName.value = ''
}
</script>

<style scoped>
.label-panel {
  width: 180px;
  flex-shrink: 0;
  background: rgba(20, 20, 20, 0.96);
  border-right: 1px solid #333;
  display: flex;
  flex-direction: column;
  padding: 8px 0;
  overflow: hidden;
}

.panel-header {
  padding: 4px 12px 8px;
}
.panel-title {
  font-size: 13px;
  font-weight: 600;
  color: #ccc;
  letter-spacing: 0.5px;
}

/* ── Label list ────────────────────────────────────────────────── */
.label-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 4px;
}

.label-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}
.label-item:hover { background: rgba(255,255,255,0.07); }
.label-item.active {
  background: rgba(64,158,255,0.2);
  outline: 1px solid rgba(64,158,255,0.5);
}

.color-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  flex-shrink: 0;
  border: 1px solid rgba(255,255,255,0.2);
}

.label-name {
  flex: 1;
  font-size: 12px;
  color: #ddd;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}

.del-btn {
  opacity: 0;
  transition: opacity 0.15s;
  padding: 2px !important;
}
.label-item:hover .del-btn { opacity: 1; }

.empty-hint {
  font-size: 11px;
  color: #555;
  text-align: center;
  padding: 16px 8px;
}

/* ── Add section ────────────────────────────────────────────────── */
.add-section { padding: 0 8px 4px; }
.add-row { display: flex; align-items: center; gap: 4px; }

/* ── Brush section ──────────────────────────────────────────────── */
.brush-section { padding: 0 12px 8px; }
.brush-label {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #888;
  margin-bottom: 4px;
}
.brush-label strong { color: #ccc; }

.label-list::-webkit-scrollbar { width: 4px; }
.label-list::-webkit-scrollbar-track { background: transparent; }
.label-list::-webkit-scrollbar-thumb { background: #444; border-radius: 2px; }
</style>
