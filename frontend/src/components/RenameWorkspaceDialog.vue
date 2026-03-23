<template>
  <el-dialog
    :model-value="modelValue"
    :title="$t('workspace.renameWorkspace')"
    width="460px"
    @update:model-value="$emit('update:modelValue', $event)"
    @opened="initForm"
    @closed="resetForm"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="80px"
      @submit.prevent="submit"
    >
      <el-form-item :label="$t('workspace.nameLabel')" prop="name">
        <el-input
          v-model="form.name"
          :placeholder="$t('workspace.newNamePlaceholder')"
          maxlength="100"
          show-word-limit
          autofocus
        />
      </el-form-item>
      <el-form-item :label="$t('workspace.descriptionLabel')" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          :placeholder="$t('workspace.descriptionPlaceholderShort')"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">{{ $t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">{{ $t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { useWorkspaceStore } from '@/stores/workspaceStore'

const { t } = useI18n()

const props = defineProps({
  modelValue: Boolean,
  workspace: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'renamed'])

const store = useWorkspaceStore()
const formRef = ref(null)
const submitting = ref(false)

const form = reactive({ name: '', description: '' })

const rules = {
  name: [
    { required: true, message: () => t('workspace.nameRequiredRule'), trigger: 'blur' },
    { min: 1, max: 100, message: () => t('workspace.nameLengthRule'), trigger: 'blur' }
  ]
}

function initForm() {
  if (props.workspace) {
    form.name = props.workspace.name ?? ''
    form.description = props.workspace.description ?? ''
  }
}

async function submit() {
  await formRef.value.validate()
  if (!props.workspace) return
  submitting.value = true
  try {
    const ws = await store.update(props.workspace.id, {
      name: form.name.trim(),
      description: form.description.trim()
    })
    emit('renamed', ws)
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
