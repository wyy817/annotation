<template>
  <el-dialog
    :model-value="modelValue"
    title="新建工作空间"
    width="460px"
    @update:model-value="$emit('update:modelValue', $event)"
    @closed="resetForm"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="80px"
      @submit.prevent="submit"
    >
      <el-form-item label="名称" prop="name">
        <el-input
          v-model="form.name"
          placeholder="请输入工作空间名称"
          maxlength="100"
          show-word-limit
          autofocus
        />
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          placeholder="（可选）简要描述该工作空间的用途"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useWorkspaceStore } from '@/stores/workspaceStore'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue', 'created'])

const store = useWorkspaceStore()
const formRef = ref(null)
const submitting = ref(false)

const form = reactive({ name: '', description: '' })

const rules = {
  name: [
    { required: true, message: '名称不能为空', trigger: 'blur' },
    { min: 1, max: 100, message: '长度 1~100 个字符', trigger: 'blur' }
  ]
}

async function submit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    const ws = await store.create({ name: form.name.trim(), description: form.description.trim() })
    emit('created', ws)
    emit('update:modelValue', false)
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  form.name = ''
  form.description = ''
  formRef.value?.clearValidate()
}
</script>
