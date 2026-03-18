<template>
  <el-dialog v-model="visible" title="移动图像到..." width="400px">
    <p class="slide-name">{{ slide?.fileName }}</p>
    <el-select
      v-model="targetId"
      placeholder="选择目标工作空间"
      style="width:100%"
    >
      <el-option
        v-for="ws in otherWorkspaces"
        :key="ws.id"
        :label="ws.name"
        :value="ws.id"
      />
    </el-select>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!targetId" :loading="moving" @click="confirm">
        移动
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useWorkspaceStore } from '@/stores/workspaceStore'
import { useSlideStore } from '@/stores/slideStore'

const props = defineProps({
  modelValue: Boolean,
  slide: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'moved'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const wsStore = useWorkspaceStore()
const slideStore = useSlideStore()

const targetId = ref(null)
const moving = ref(false)

const otherWorkspaces = computed(() =>
  wsStore.workspaces.filter((w) => w.id !== slideStore.currentWorkspaceId)
)

async function confirm() {
  if (!targetId.value || !props.slide) return
  moving.value = true
  try {
    await slideStore.move(props.slide.id, targetId.value)
    const target = wsStore.workspaces.find((w) => w.id === targetId.value)
    ElMessage.success(`已移动到「${target?.name ?? targetId.value}」`)
    targetId.value = null
    visible.value = false
    emit('moved')
  } finally {
    moving.value = false
  }
}
</script>

<style scoped>
.slide-name {
  font-size: 13px;
  color: #606266;
  margin-bottom: 12px;
  word-break: break-all;
}
</style>
