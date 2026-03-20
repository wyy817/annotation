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
        <el-color-picker
          v-model="editColorMap[label.id]"
          size="small"
          class="inline-color-picker"
          @click.stop
          @change="onColorChange(label.id, $event)"
        />

        <!-- Inline editing mode -->
        <el-input
          v-if="editingId === label.id"
          ref="editInputRef"
          v-model="editingName"
          size="small"
          class="inline-edit-input"
          maxlength="32"
          placeholder="输入标签名称"
          @keydown.enter="confirmEdit"
          @keydown.escape="cancelEdit"
          @blur="confirmEdit"
          @click.stop
        />
        <!-- Normal display -->
        <span v-else class="label-name" :title="label.name" @dblclick.stop="startEdit(label)">
          {{ label.name }}
        </span>

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
        暂无标签，点击绘图工具直接开始标注
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
        <el-input-number
          :model-value="brushSize"
          :min="1"
          :max="100"
          :step="1"
          size="small"
          controls-position="right"
          class="param-input"
          @update:model-value="$emit('brush-size', $event)"
        />
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

    <!-- Eraser settings (shown when eraser tool is active) -->
    <div class="brush-section" v-if="showEraserSize">
      <el-divider style="margin: 8px 0" />
      <div class="brush-label" style="margin-bottom: 8px">
        <span>擦除模式</span>
      </div>
      <el-radio-group
        :model-value="eraserMode"
        size="small"
        class="eraser-mode-group"
        @update:model-value="$emit('eraser-mode', $event)"
      >
        <el-radio-button value="pixel">擦像素</el-radio-button>
        <el-radio-button value="stroke">擦笔划</el-radio-button>
      </el-radio-group>
      <div class="brush-label" style="margin-top: 8px">
        <span>橡皮擦大小</span>
        <el-input-number
          :model-value="eraserSize"
          :min="1"
          :max="200"
          :step="1"
          size="small"
          controls-position="right"
          class="param-input"
          @update:model-value="$emit('eraser-size', $event)"
        />
      </div>
      <el-slider
        :model-value="eraserSize"
        :min="1"
        :max="200"
        :step="1"
        size="small"
        @update:model-value="$emit('eraser-size', $event)"
      />
    </div>

    <!-- Nudge size (shown when nudge tool is active) -->
    <div class="brush-section" v-if="showNudgeSize">
      <el-divider style="margin: 8px 0" />
      <div class="brush-label">
        <span>推拉半径</span>
        <el-input-number
          :model-value="nudgeSize"
          :min="5"
          :max="200"
          :step="1"
          size="small"
          controls-position="right"
          class="param-input"
          @update:model-value="$emit('nudge-size', $event)"
        />
      </div>
      <el-slider
        :model-value="nudgeSize"
        :min="5"
        :max="200"
        :step="1"
        size="small"
        @update:model-value="$emit('nudge-size', $event)"
      />
      <div class="brush-label" style="margin-top: 4px">
        <span>推拉强度</span>
        <el-input-number
          :model-value="Math.round(nudgeStrength * 100)"
          :min="5"
          :max="100"
          :step="5"
          size="small"
          controls-position="right"
          class="param-input"
          @update:model-value="$emit('nudge-strength', $event / 100)"
        />
      </div>
      <el-slider
        :model-value="nudgeStrength"
        :min="0.05"
        :max="1"
        :step="0.05"
        size="small"
        @update:model-value="$emit('nudge-strength', $event)"
      />
    </div>

    <!-- Point size (shown when point tool is active) -->
    <div class="brush-section" v-if="showPointSize">
      <el-divider style="margin: 8px 0" />
      <div class="brush-label">
        <span>点大小</span>
        <el-input-number
          :model-value="pointSize"
          :min="3"
          :max="30"
          :step="1"
          size="small"
          controls-position="right"
          class="param-input"
          @update:model-value="$emit('point-size', $event)"
        />
      </div>
      <el-slider
        :model-value="pointSize"
        :min="3"
        :max="30"
        :step="1"
        size="small"
        @update:model-value="$emit('point-size', $event)"
      />
    </div>
  </aside>
</template>

<script setup>
import { ref, nextTick, watch } from 'vue'
import { Delete, Plus } from '@element-plus/icons-vue'

const props = defineProps({
  labels:        { type: Array,   default: () => [] },
  activeId:      { type: Number,  default: null },
  brushSize:     { type: Number,  default: 20 },
  showBrushSize: { type: Boolean, default: false },
  nudgeSize:      { type: Number,  default: 30 },
  nudgeStrength:  { type: Number,  default: 0.35 },
  showNudgeSize:  { type: Boolean, default: false },
  eraserSize:     { type: Number,  default: 20 },
  eraserMode:     { type: String,  default: 'pixel' },
  showEraserSize: { type: Boolean, default: false },
  pointSize:      { type: Number,  default: 7 },
  showPointSize:  { type: Boolean, default: false },
  editingId:      { type: Number,  default: null }   // label id to enter edit mode
})

const emit = defineEmits(['select', 'add', 'delete', 'brush-size', 'nudge-size', 'nudge-strength', 'eraser-size', 'eraser-mode', 'point-size', 'rename', 'color-change'])

const newName  = ref('')
const newColor = ref('#ff0000')

// ── Inline color editing ────────────────────────────────────
const editColorMap = ref({})

// Initialize color map from labels
watch(() => props.labels, (list) => {
  for (const l of list) {
    if (editColorMap.value[l.id] !== l.color) {
      editColorMap.value[l.id] = l.color
    }
  }
}, { immediate: true, deep: true })

function onColorChange(id, color) {
  if (color) {
    emit('color-change', { id, color })
  }
}

// ── Inline editing state ─────────────────────────────────────
const editingName    = ref('')
const editInputRef   = ref(null)

const editingId = ref(null)

// Watch parent-triggered editing
watch(() => props.editingId, (id) => {
  if (id != null) {
    const label = props.labels.find(l => l.id === id)
    if (label) startEdit(label)
  }
})

function startEdit(label) {
  editingId.value   = label.id
  editingName.value = label.name.startsWith('未命名') ? '' : label.name
  nextTick(() => {
    const inputEl = editInputRef.value
    // el-input may be an array when used in v-for with same ref
    const el = Array.isArray(inputEl) ? inputEl[0] : inputEl
    el?.focus?.()
  })
}

function confirmEdit() {
  if (editingId.value == null) return
  const name = editingName.value.trim()
  if (name) {
    emit('rename', { id: editingId.value, name })
  }
  editingId.value = null
  editingName.value = ''
}

function cancelEdit() {
  editingId.value = null
  editingName.value = ''
}

function doAdd() {
  const name = newName.value.trim()
  if (!name) return
  emit('add', { name, color: newColor.value })
  newName.value = ''
}

defineExpose({ newColor })
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

/* ── Param input number ────────────────────────────────────────── */
.param-input {
  width: 72px;
  flex-shrink: 0;
}
.param-input :deep(.el-input-number__decrease),
.param-input :deep(.el-input-number__increase) {
  background: transparent;
  border-color: #555;
  color: #aaa;
}
.param-input :deep(.el-input__wrapper) {
  background: rgba(255,255,255,0.08);
  box-shadow: none;
  padding: 0 2px;
}
.param-input :deep(.el-input__inner) {
  font-size: 11px;
  color: #ddd;
  text-align: center;
}

/* ── Inline edit ───────────────────────────────────────────────── */
.inline-edit-input {
  flex: 1;
  min-width: 0;
}
.inline-edit-input :deep(.el-input__wrapper) {
  background: rgba(255,255,255,0.1);
  box-shadow: none;
  padding: 0 4px;
}
.inline-edit-input :deep(.el-input__inner) {
  font-size: 12px;
  color: #fff;
  height: 22px;
  line-height: 22px;
}

/* ── Inline color picker (looks like a dot) ───────────────────── */
.inline-color-picker {
  flex-shrink: 0;
}
.inline-color-picker :deep(.el-color-picker__trigger) {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  padding: 0;
  border: 1px solid rgba(255,255,255,0.2);
}
.inline-color-picker :deep(.el-color-picker__color) {
  border: none;
  border-radius: 50%;
}
.inline-color-picker :deep(.el-color-picker__color-inner) {
  border-radius: 50%;
}
.inline-color-picker :deep(.el-color-picker__icon) {
  display: none;
}

/* ── Eraser mode toggle ────────────────────────────────────────── */
.eraser-mode-group {
  width: 100%;
  display: flex;
}
.eraser-mode-group :deep(.el-radio-button) {
  flex: 1;
}
.eraser-mode-group :deep(.el-radio-button__inner) {
  width: 100%;
  font-size: 11px;
  padding: 4px 0;
  background: rgba(255,255,255,0.06);
  border-color: #555;
  color: #aaa;
}
.eraser-mode-group :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: rgba(255,68,68,0.25);
  border-color: #ff4444;
  color: #ff8888;
}

.label-list::-webkit-scrollbar { width: 4px; }
.label-list::-webkit-scrollbar-track { background: transparent; }
.label-list::-webkit-scrollbar-thumb { background: #444; border-radius: 2px; }
</style>
