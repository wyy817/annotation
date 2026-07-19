<template>
  <el-drawer
    v-model="visible"
    class="po-case-create-drawer"
    :title="t('po.caseCreate.title')"
    size="560px"
    destroy-on-close
    @open="prepare"
  >
    <div class="po-case-create">
      <el-steps :active="step" finish-status="success" simple>
        <el-step :title="t('po.caseCreate.detailsStep')" />
        <el-step :title="t('po.caseCreate.reviewStep')" />
      </el-steps>

      <div v-if="optionsLoading" class="po-case-create-loading">
        <el-skeleton :rows="8" animated />
      </div>

      <el-form
        v-else-if="step === 0"
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="po-case-form"
      >
        <section>
          <h3>{{ t('po.caseCreate.caseDetails') }}</h3>
          <div class="po-case-form-grid">
            <el-form-item :label="t('po.caseCreate.accession')" prop="accessionReference">
              <el-input v-model="form.accessionReference" :placeholder="t('po.caseCreate.accessionPlaceholder')" />
            </el-form-item>
            <el-form-item :label="t('po.caseCreate.patientAge')" prop="patientAge">
              <el-input-number v-model="form.patientAge" :min="0" :max="120" controls-position="right" />
            </el-form-item>
            <el-form-item class="po-span-two" :label="t('po.caseCreate.specimenName')" prop="specimenName">
              <el-input v-model="form.specimenName" :placeholder="t('po.caseCreate.specimenPlaceholder')" />
            </el-form-item>
          </div>
        </section>

        <section>
          <h3>{{ t('po.caseCreate.assignment') }}</h3>
          <el-form-item :label="t('po.caseCreate.primarySlide')" prop="primarySlideId">
            <el-select v-model="form.primarySlideId" filterable :placeholder="t('po.caseCreate.slidePlaceholder')">
              <el-option
                v-for="slide in options.slides"
                :key="slide.id"
                :label="slide.fileName"
                :value="slide.id"
              >
                <span>{{ slide.fileName }}</span>
                <small class="po-option-meta">{{ slide.format }} / {{ formatSize(slide.fileSize) }}</small>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item :label="t('po.caseCreate.pathologist')" prop="assignedPathologistId">
            <el-select v-model="form.assignedPathologistId" :placeholder="t('po.caseCreate.pathologistPlaceholder')">
              <el-option
                v-for="user in options.pathologists"
                :key="user.id"
                :label="user.displayName"
                :value="user.id"
              >
                <span>{{ user.displayName }}</span>
                <small class="po-option-meta">{{ user.username }}</small>
              </el-option>
            </el-select>
          </el-form-item>
        </section>

        <section>
          <h3>{{ t('po.caseCreate.timing') }}</h3>
          <div class="po-case-form-grid">
            <el-form-item :label="t('po.caseCreate.receivedAt')" prop="receivedAt">
              <el-date-picker v-model="form.receivedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
            </el-form-item>
            <el-form-item :label="t('po.caseCreate.dueAt')" prop="dueAt">
              <el-date-picker v-model="form.dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" />
            </el-form-item>
          </div>
        </section>

        <div class="po-profile-lock" role="note">
          <el-icon aria-hidden="true"><Lock /></el-icon>
          <div>
            <strong>{{ t('po.caseCreate.profile') }}</strong>
            <span>{{ t('po.disease.breastLymphNode') }} / H&amp;E</span>
          </div>
        </div>
      </el-form>

      <div v-else class="po-case-review-summary">
        <header>
          <el-icon aria-hidden="true"><DocumentChecked /></el-icon>
          <div>
            <h3>{{ t('po.caseCreate.reviewTitle') }}</h3>
            <p>{{ t('po.caseCreate.reviewBody') }}</p>
          </div>
        </header>
        <dl>
          <div><dt>{{ t('po.caseCreate.accession') }}</dt><dd>{{ form.accessionReference }}</dd></div>
          <div><dt>{{ t('po.caseCreate.patientAge') }}</dt><dd>{{ form.patientAge }}</dd></div>
          <div><dt>{{ t('po.caseCreate.specimenName') }}</dt><dd>{{ form.specimenName }}</dd></div>
          <div><dt>{{ t('po.caseCreate.primarySlide') }}</dt><dd>{{ selectedSlide?.fileName }}</dd></div>
          <div><dt>{{ t('po.caseCreate.pathologist') }}</dt><dd>{{ selectedPathologist?.displayName }}</dd></div>
          <div><dt>{{ t('po.caseCreate.receivedAt') }}</dt><dd>{{ formatDate(form.receivedAt) }}</dd></div>
          <div><dt>{{ t('po.caseCreate.dueAt') }}</dt><dd>{{ formatDate(form.dueAt) }}</dd></div>
        </dl>
        <div class="po-review-notice">
          <el-icon aria-hidden="true"><InfoFilled /></el-icon>
          <span>{{ t('po.caseCreate.bindingNotice') }}</span>
        </div>
      </div>
    </div>

    <template #footer>
      <div class="po-case-create-actions">
        <el-button @click="visible = false">{{ t('po.caseCreate.cancel') }}</el-button>
        <el-button v-if="step === 1" @click="step = 0">{{ t('po.caseCreate.back') }}</el-button>
        <el-button v-if="step === 0" type="primary" :disabled="optionsLoading" @click="review">
          {{ t('po.caseCreate.continue') }}
        </el-button>
        <el-button v-else type="primary" :loading="submitting" @click="submit">
          {{ t('po.caseCreate.create') }}
        </el-button>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { DocumentChecked, InfoFilled, Lock } from '@element-plus/icons-vue'
import { caseApi } from '@/api/cases'
import { useCaseStore } from '@/stores/caseStore'
import { formatDateTime } from '@/utils/formatters'

const props = defineProps({
  modelValue: Boolean,
  initialSlideId: { type: Number, default: null },
})
const emit = defineEmits(['update:modelValue', 'created'])
const { t, locale } = useI18n()
const caseStore = useCaseStore()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})
const formRef = ref(null)
const step = ref(0)
const optionsLoading = ref(false)
const submitting = ref(false)
const options = reactive({ slides: [], pathologists: [], defaultDiseaseProfileId: 'BREAST_LYMPH_NODE_HE' })
const form = reactive({
  accessionReference: '',
  primarySlideId: null,
  diseaseProfileId: 'BREAST_LYMPH_NODE_HE',
  specimenName: '',
  patientAge: null,
  receivedAt: '',
  dueAt: '',
  assignedPathologistId: null,
})

const rules = {
  accessionReference: [{ required: true, message: () => t('po.caseCreate.required'), trigger: 'blur' }],
  primarySlideId: [{ required: true, message: () => t('po.caseCreate.required'), trigger: 'change' }],
  specimenName: [{ required: true, message: () => t('po.caseCreate.required'), trigger: 'blur' }],
  patientAge: [{ required: true, message: () => t('po.caseCreate.required'), trigger: 'change' }],
  receivedAt: [{ required: true, message: () => t('po.caseCreate.required'), trigger: 'change' }],
  dueAt: [
    { required: true, message: () => t('po.caseCreate.required'), trigger: 'change' },
    {
      validator: (_rule, value, callback) => {
        if (value && form.receivedAt && new Date(value) <= new Date(form.receivedAt)) {
          callback(new Error(t('po.caseCreate.dueAfterReceived')))
        } else callback()
      },
      trigger: 'change',
    },
  ],
  assignedPathologistId: [{ required: true, message: () => t('po.caseCreate.required'), trigger: 'change' }],
}

const selectedSlide = computed(() => options.slides.find((slide) => slide.id === form.primarySlideId))
const selectedPathologist = computed(() => options.pathologists.find((user) => user.id === form.assignedPathologistId))

async function prepare() {
  step.value = 0
  resetForm()
  optionsLoading.value = true
  try {
    const response = await caseApi.creationOptions()
    Object.assign(options, response.data || {})
    form.diseaseProfileId = options.defaultDiseaseProfileId
    if (props.initialSlideId && options.slides.some((slide) => slide.id === props.initialSlideId)) {
      form.primarySlideId = props.initialSlideId
    }
    if (options.pathologists.length === 1) form.assignedPathologistId = options.pathologists[0].id
  } finally {
    optionsLoading.value = false
  }
}

function resetForm() {
  const now = new Date()
  const due = new Date(now.getTime() + 4 * 60 * 60 * 1000)
  Object.assign(form, {
    accessionReference: '',
    primarySlideId: props.initialSlideId,
    diseaseProfileId: 'BREAST_LYMPH_NODE_HE',
    specimenName: '',
    patientAge: null,
    receivedAt: toLocalValue(now),
    dueAt: toLocalValue(due),
    assignedPathologistId: null,
  })
}

function toLocalValue(value) {
  const pad = (part) => String(part).padStart(2, '0')
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}:00`
}

async function review() {
  if (!formRef.value) return
  await formRef.value.validate()
  step.value = 1
}

async function submit() {
  submitting.value = true
  try {
    const item = await caseStore.createCase({ ...form })
    ElMessage.success(t('po.caseCreate.created', { id: item.id }))
    emit('created', item)
    visible.value = false
  } finally {
    submitting.value = false
  }
}

function formatSize(bytes) {
  if (!bytes) return '-'
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`
  return `${(bytes / 1024 ** 3).toFixed(2)} GB`
}

function formatDate(value) {
  return value ? formatDateTime(value, locale.value) : '-'
}

watch(() => props.initialSlideId, (value) => {
  if (visible.value && value) form.primarySlideId = value
})
</script>

<style scoped>
.po-case-create {
  display: grid;
  gap: 24px;
}

.po-case-create-loading {
  padding-top: 12px;
}

.po-case-form {
  display: grid;
  gap: 22px;
}

.po-case-form section + section {
  padding-top: 20px;
  border-top: 1px solid var(--po-hairline);
}

.po-case-form h3,
.po-case-review-summary h3 {
  margin: 0 0 14px;
  color: var(--po-ink);
  font-size: 16px;
  font-weight: 650;
}

.po-case-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
}

.po-span-two {
  grid-column: 1 / -1;
}

.po-case-form :deep(.el-select),
.po-case-form :deep(.el-date-editor),
.po-case-form :deep(.el-input-number) {
  width: 100%;
}

.po-option-meta {
  float: right;
  margin-left: 20px;
  color: var(--po-ink-muted);
}

.po-profile-lock,
.po-review-notice {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 11px;
  border: 1px solid var(--po-hairline);
  border-radius: var(--po-radius-sm);
  background: var(--po-surface-subtle);
}

.po-profile-lock > div {
  display: grid;
  gap: 2px;
}

.po-profile-lock strong {
  font-size: 12px;
}

.po-profile-lock span,
.po-review-notice {
  color: var(--po-ink-muted);
  font-size: 12px;
}

.po-case-review-summary > header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--po-hairline);
}

.po-case-review-summary > header > .el-icon {
  color: var(--po-verified);
  font-size: 24px;
}

.po-case-review-summary h3 {
  margin-bottom: 3px;
}

.po-case-review-summary p {
  margin: 0;
  color: var(--po-ink-muted);
  font-size: 13px;
}

.po-case-review-summary dl {
  margin: 0;
}

.po-case-review-summary dl > div {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  gap: 16px;
  padding: 11px 0;
  border-bottom: 1px solid var(--po-hairline);
}

.po-case-review-summary dt {
  color: var(--po-ink-muted);
  font-size: 12px;
}

.po-case-review-summary dd {
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--po-ink);
  font-size: 13px;
  font-weight: 600;
}

.po-review-notice {
  margin-top: 18px;
}

.po-case-create-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

@media (max-width: 640px) {
  .po-case-form-grid {
    grid-template-columns: 1fr;
  }

  .po-span-two {
    grid-column: auto;
  }

  .po-case-review-summary dl > div {
    grid-template-columns: 1fr;
    gap: 3px;
  }
}
</style>
