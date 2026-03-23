<template>
  <el-dialog
    v-model="visible"
    :title="$t('importDialog.title')"
    width="460px"
    :close-on-click-modal="false"
    @closed="onClosed"
  >
    <div class="import-body">
      <el-icon class="import-icon"><Upload /></el-icon>

      <!-- Format -->
      <div class="section">
        <p class="section-label">{{ $t('importDialog.formatLabel') }}</p>
        <el-radio-group v-model="format" size="default">
          <el-radio-button value="COCO">COCO JSON</el-radio-button>
          <el-radio-button value="CUSTOM_JSON">{{ $t('importDialog.formatCustom') }}</el-radio-button>
          <el-radio-button value="YOLO">YOLO TXT</el-radio-button>
          <el-radio-button value="VOC">VOC XML</el-radio-button>
          <el-radio-button value="GEOJSON">GeoJSON</el-radio-button>
        </el-radio-group>
        <p class="section-hint">{{ formatHint }}</p>
      </div>

      <!-- Mode -->
      <div class="section">
        <p class="section-label">{{ $t('importDialog.modeLabel') }}</p>
        <el-radio-group v-model="mode" size="default">
          <el-radio-button value="REPLACE">{{ $t('importDialog.modeReplace') }}</el-radio-button>
          <el-radio-button value="MERGE">{{ $t('importDialog.modeMerge') }}</el-radio-button>
        </el-radio-group>
        <p class="section-hint">{{ modeHint }}</p>
      </div>

      <!-- YOLO: classes.txt -->
      <div v-if="format === 'YOLO'" class="section">
        <p class="section-label">{{ $t('importDialog.classFileLabel') }}</p>
        <div
          class="drop-zone"
          :class="{ 'drop-zone--over': isDragOverClass, 'drop-zone--selected': !!classFile }"
          @dragover.prevent="isDragOverClass = true"
          @dragleave.prevent="isDragOverClass = false"
          @drop.prevent="onDropClass"
          @click="$refs.classFileInput.click()"
        >
          <el-icon class="drop-icon"><Document /></el-icon>
          <span v-if="classFile" class="drop-text">{{ classFile.name }}</span>
          <span v-else class="drop-text">{{ $t('importDialog.classFileDrop') }}</span>
        </div>
        <input ref="classFileInput" type="file" accept=".txt" style="display:none" @change="onClassFileChange" />
      </div>

      <!-- File upload -->
      <div class="section">
        <p class="section-label">{{ annoFileLabel }}</p>
        <div
          class="drop-zone"
          :class="{ 'drop-zone--over': isDragOver, 'drop-zone--selected': !!selectedFile }"
          @dragover.prevent="isDragOver = true"
          @dragleave.prevent="isDragOver = false"
          @drop.prevent="onDrop"
          @click="$refs.fileInput.click()"
        >
          <el-icon class="drop-icon"><Document /></el-icon>
          <span v-if="selectedFile" class="drop-text">{{ selectedFile.name }}</span>
          <span v-else class="drop-text">{{ dropHint }}</span>
        </div>
        <input ref="fileInput" type="file" :accept="format === 'YOLO' ? '.txt' : format === 'VOC' ? '.xml' : '.json,.geojson'" style="display:none" @change="onFileChange" />
      </div>

      <!-- Preview -->
      <div v-if="preview" class="preview-box">
        <el-icon><InfoFilled /></el-icon>
        <span v-if="preview.labelCount != null">
          {{ $t('importDialog.previewWithLabel', { annoCount: preview.annotationCount, labelCount: preview.labelCount }) }}
        </span>
        <span v-else>{{ $t('importDialog.previewAnnoOnly', { annoCount: preview.annotationCount }) }}</span>
      </div>

      <div class="import-actions">
        <el-button @click="visible = false">{{ $t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :loading="importing"
          :disabled="!selectedFile"
          @click="doImport"
        >{{ importing ? $t('importDialog.importing') : $t('importDialog.confirmImport') }}</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload, Document, InfoFilled } from '@element-plus/icons-vue'
import { annotationApi } from '@/api/annotation'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps({ modelValue: Boolean, slideId: Number })
const emit = defineEmits(['update:modelValue', 'imported'])

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})

const format = ref('COCO')
const mode = ref('REPLACE')
const selectedFile = ref(null)
const classFile = ref(null)
const isDragOver = ref(false)
const isDragOverClass = ref(false)
const importing = ref(false)
const preview = ref(null)

const formatHint = computed(() => ({
  COCO: t('importDialog.hintCOCO'),
  CUSTOM_JSON: t('importDialog.hintCustom'),
  YOLO: t('importDialog.hintYOLO'),
  VOC: t('importDialog.hintVOC'),
  GEOJSON: t('importDialog.hintGeoJSON'),
})[format.value] ?? '')

const modeHint = computed(() => ({
  REPLACE: t('importDialog.modeHintReplace'),
  MERGE: t('importDialog.modeHintMerge'),
})[mode.value] ?? '')

const annoFileLabel = computed(() => ({
  YOLO: t('importDialog.annoFileLabelYOLO'),
  VOC: t('importDialog.annoFileLabelVOC'),
  GEOJSON: t('importDialog.annoFileLabelGeoJSON'),
})[format.value] ?? t('importDialog.annoFileLabelDefault'))

const dropHint = computed(() => ({
  YOLO: t('importDialog.dropYOLO'),
  VOC: t('importDialog.dropVOC'),
  GEOJSON: t('importDialog.dropGeoJSON'),
})[format.value] ?? t('importDialog.dropJSON'))

function onDrop(e) {
  isDragOver.value = false
  const f = e.dataTransfer.files[0]
  if (f) setFile(f)
}

function onFileChange(e) {
  const f = e.target.files[0]
  if (f) setFile(f)
  e.target.value = ''
}

function onDropClass(e) {
  isDragOverClass.value = false
  const f = e.dataTransfer.files[0]
  if (f) setClassFile(f)
}

function onClassFileChange(e) {
  const f = e.target.files[0]
  if (f) setClassFile(f)
  e.target.value = ''
}

function setClassFile(f) {
  if (!f.name.endsWith('.txt')) {
    ElMessage.warning(t('importDialog.warnSelectTxt'))
    return
  }
  classFile.value = f
}

function setFile(f) {
  const fmt = format.value
  const extMap = { YOLO: '.txt', VOC: '.xml' }
  if (fmt in extMap) {
    if (!f.name.endsWith(extMap[fmt])) {
      ElMessage.warning(t('importDialog.warnSelectExt', { ext: extMap[fmt] }))
      return
    }
  } else {
    // COCO / CUSTOM_JSON / GEOJSON — accept .json or .geojson
    if (!f.name.endsWith('.json') && !f.name.endsWith('.geojson')) {
      ElMessage.warning(t('importDialog.warnSelectJson'))
      return
    }
  }
  selectedFile.value = f
  preview.value = null
  const reader = new FileReader()
  if (fmt === 'YOLO') {
    reader.onload = evt => {
      const lines = (evt.target.result ?? '').split('\n')
        .map(l => l.trim()).filter(l => l && !l.startsWith('#'))
      preview.value = { annotationCount: lines.length, labelCount: null }
    }
    reader.readAsText(f)
  } else if (fmt === 'VOC') {
    reader.onload = evt => {
      try {
        const parser = new DOMParser()
        const xml = parser.parseFromString(evt.target.result, 'application/xml')
        const count = xml.getElementsByTagName('object').length
        preview.value = { annotationCount: count, labelCount: null }
      } catch {
        preview.value = null
      }
    }
    reader.readAsText(f)
  } else {
    reader.onload = evt => {
      try {
        const data = JSON.parse(evt.target.result)
        if (fmt === 'COCO') {
          preview.value = {
            annotationCount: (data.annotations ?? []).length,
            labelCount: (data.categories ?? []).length
          }
        } else if (fmt === 'GEOJSON') {
          const features = data.features ?? []
          const labelSet = new Set(features.map(f =>
            f.properties?.label || f.properties?.name ||
            f.properties?.category || f.properties?.class || 'default'
          ))
          preview.value = {
            annotationCount: features.length,
            labelCount: labelSet.size
          }
        } else {
          preview.value = {
            annotationCount: (data.annotations ?? []).length,
            labelCount: (data.labels ?? []).length
          }
        }
      } catch {
        preview.value = null
      }
    }
    reader.readAsText(f)
  }
}

// Reset state when format changes
watch(format, () => {
  preview.value = null
  selectedFile.value = null
  classFile.value = null
})

async function doImport() {
  if (!selectedFile.value) return
  if (format.value === 'YOLO' && !classFile.value) {
    ElMessage.warning(t('importDialog.warnClassTxt'))
    return
  }
  importing.value = true
  try {
    const res = await annotationApi.importAnnotations(
      props.slideId, selectedFile.value, format.value, mode.value,
      format.value === 'YOLO' ? classFile.value : null
    )
    const result = res.data?.data ?? res.data
    const successMsg = result.labelCount != null
      ? t('importDialog.successMsgWithLabel', { count: result.annotationCount, labelCount: result.labelCount })
      : t('importDialog.successMsg', { count: result.annotationCount })
    ElMessage.success(successMsg)
    emit('imported')
    visible.value = false
  } catch (err) {
    const msg = err.response?.data?.message ?? err.message ?? t('importDialog.failed')
    ElMessage.error(msg)
  } finally {
    importing.value = false
  }
}

function onClosed() {
  selectedFile.value = null
  classFile.value = null
  preview.value = null
  isDragOver.value = false
  isDragOverClass.value = false
  importing.value = false
}
</script>

<style scoped>
.import-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 8px 0 4px;
}

.import-icon {
  font-size: 40px;
  color: var(--el-color-primary);
}

.section {
  width: 100%;
}

.section-label {
  margin: 0 0 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.section-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  line-height: 1.4;
}

.drop-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 2px dashed var(--el-border-color);
  border-radius: 8px;
  padding: 24px 16px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.drop-zone:hover,
.drop-zone--over {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.drop-zone--selected {
  border-color: var(--el-color-success);
  background: var(--el-color-success-light-9);
}

.drop-icon {
  font-size: 28px;
  color: var(--el-text-color-placeholder);
}

.drop-text {
  font-size: 13px;
  color: var(--el-text-color-regular);
  word-break: break-all;
  text-align: center;
}

.preview-box {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 6px;
  background: var(--el-color-info-light-9);
  font-size: 13px;
  color: var(--el-text-color-regular);
  width: 100%;
  box-sizing: border-box;
}

.import-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  width: 100%;
  margin-top: 4px;
}
</style>
