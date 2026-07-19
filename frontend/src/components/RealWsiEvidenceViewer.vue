<template>
  <section
    ref="container"
    class="po-real-viewer"
    tabindex="0"
    :aria-label="t('po.viewer.realWsiEvidence')"
    @keydown="handleKeydown"
    @pointerdown="startPan"
    @pointermove="movePan"
    @pointerup="endPan"
    @pointercancel="endPan"
    @wheel.prevent="handleWheel"
  >
    <div v-if="imageUrl" class="po-real-image-stage">
      <img
        :key="imageUrl"
        :src="imageUrl"
        :alt="t(mode === 'evidence' ? 'po.viewer.modelEvidence' : 'po.viewer.relativeAttention')"
        draggable="false"
        :style="imageStyle"
        @load="imageLoaded = true"
      />
    </div>
    <div v-else class="po-real-image-loading" aria-live="polite">
      <el-skeleton animated :rows="5" />
    </div>

    <div class="po-real-viewer-badges">
      <span>{{ t(analysis.isPrecomputed ? 'po.mode.precomputedModelOutput' : 'po.mode.localModelOutput') }}</span>
      <span>{{ t(mode === 'evidence' ? 'po.viewer.modelEvidence' : 'po.viewer.relativeAttention') }}</span>
    </div>

    <div class="po-real-viewer-toolbar" @pointerdown.stop>
      <div class="po-mode-control" role="group" :aria-label="t('po.viewer.layerMode')">
        <el-tooltip :content="t('po.viewer.modelEvidence')" placement="bottom">
          <button
            type="button"
            :class="{ active: mode === 'evidence' }"
            :aria-pressed="mode === 'evidence'"
            @click="mode = 'evidence'"
          >
            <el-icon><DataAnalysis /></el-icon>
            <span>{{ t('po.viewer.evidenceShort') }}</span>
          </button>
        </el-tooltip>
        <el-tooltip :content="t('po.viewer.relativeAttention')" placement="bottom">
          <button
            type="button"
            :class="{ active: mode === 'attention' }"
            :aria-pressed="mode === 'attention'"
            @click="mode = 'attention'"
          >
            <el-icon><Histogram /></el-icon>
            <span>{{ t('po.viewer.attentionShort') }}</span>
          </button>
        </el-tooltip>
      </div>

      <span class="po-real-toolbar-divider" aria-hidden="true"></span>

      <el-tooltip :content="t('po.viewer.zoomIn')" placement="bottom">
        <button type="button" :aria-label="t('po.viewer.zoomIn')" @click="zoomBy(0.25)">
          <el-icon><ZoomIn /></el-icon>
        </button>
      </el-tooltip>
      <el-tooltip :content="t('po.viewer.zoomOut')" placement="bottom">
        <button type="button" :aria-label="t('po.viewer.zoomOut')" @click="zoomBy(-0.25)">
          <el-icon><ZoomOut /></el-icon>
        </button>
      </el-tooltip>
      <el-tooltip :content="t('po.viewer.fit')" placement="bottom">
        <button type="button" :aria-label="t('po.viewer.fit')" @click="fitSlide">
          <el-icon><FullScreen /></el-icon>
        </button>
      </el-tooltip>
    </div>

    <figure v-if="selectedPatchUrl && selectedCandidate" class="po-evidence-patch" @pointerdown.stop>
      <img :src="selectedPatchUrl" :alt="t('po.viewer.selectedPatch')" />
      <figcaption>
        <strong>{{ selectedCandidate.regionId }}</strong>
        <span class="po-tabular">
          {{ formatPercent(selectedCandidate.maxEvidenceIndex, locale) }}
        </span>
      </figcaption>
    </figure>

    <div class="po-real-legend" @pointerdown.stop>
      <span>{{ t('po.viewer.lowSignal') }}</span>
      <span class="po-real-legend-scale" aria-hidden="true"></span>
      <span>{{ t('po.viewer.highSignal') }}</span>
    </div>

    <span class="po-real-zoom po-tabular">{{ Math.round(scale * 100) }}%</span>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import {
  DataAnalysis,
  FullScreen,
  Histogram,
  ZoomIn,
  ZoomOut,
} from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { formatPercent } from '@/utils/formatters'

const props = defineProps({
  analysis: {
    type: Object,
    required: true,
  },
  artifactUrls: {
    type: Object,
    required: true,
  },
  selectedRegion: {
    type: String,
    default: null,
  },
})

const { t, locale } = useI18n()
const container = ref(null)
const mode = ref('evidence')
const scale = ref(1)
const pan = ref({ x: 0, y: 0 })
const imageLoaded = ref(false)

let dragging = false
let dragOrigin = null

const imageUrl = computed(() => (
  mode.value === 'attention'
    ? props.artifactUrls.relativeAttentionOverview
    : props.artifactUrls.modelEvidenceOverview
))

const selectedCandidateIndex = computed(() => (
  (props.analysis.candidates || []).findIndex(
    (candidate) => candidate.regionId === props.selectedRegion,
  )
))

const selectedCandidate = computed(() => (
  selectedCandidateIndex.value >= 0
    ? props.analysis.candidates?.[selectedCandidateIndex.value]
    : null
))

const selectedPatchUrl = computed(() => (
  selectedCandidateIndex.value >= 0
    ? props.artifactUrls[`evidencePatch${selectedCandidateIndex.value + 1}`]
    : null
))

const imageStyle = computed(() => ({
  transform: `translate(calc(-50% + ${pan.value.x}px), calc(-50% + ${pan.value.y}px)) scale(${scale.value})`,
}))

function zoomBy(amount) {
  scale.value = Math.min(4, Math.max(0.75, scale.value + amount))
}

function fitSlide() {
  scale.value = 1
  pan.value = { x: 0, y: 0 }
}

function startPan(event) {
  if (event.button !== 0 || !imageLoaded.value) return
  dragging = true
  dragOrigin = {
    x: event.clientX,
    y: event.clientY,
    panX: pan.value.x,
    panY: pan.value.y,
  }
  container.value.setPointerCapture(event.pointerId)
  container.value.classList.add('is-dragging')
}

function movePan(event) {
  if (!dragging || !dragOrigin) return
  pan.value = {
    x: dragOrigin.panX + event.clientX - dragOrigin.x,
    y: dragOrigin.panY + event.clientY - dragOrigin.y,
  }
}

function endPan(event) {
  if (!dragging) return
  dragging = false
  dragOrigin = null
  if (container.value.hasPointerCapture(event.pointerId)) {
    container.value.releasePointerCapture(event.pointerId)
  }
  container.value.classList.remove('is-dragging')
}

function handleWheel(event) {
  zoomBy(event.deltaY > 0 ? -0.15 : 0.15)
}

function handleKeydown(event) {
  const step = event.shiftKey ? 48 : 24
  const actions = {
    ArrowLeft: () => { pan.value = { ...pan.value, x: pan.value.x + step } },
    ArrowRight: () => { pan.value = { ...pan.value, x: pan.value.x - step } },
    ArrowUp: () => { pan.value = { ...pan.value, y: pan.value.y + step } },
    ArrowDown: () => { pan.value = { ...pan.value, y: pan.value.y - step } },
    '+': () => zoomBy(0.25),
    '=': () => zoomBy(0.25),
    '-': () => zoomBy(-0.25),
    h: fitSlide,
    H: fitSlide,
  }
  const action = actions[event.key]
  if (!action) return
  event.preventDefault()
  action()
}

watch(mode, () => {
  imageLoaded.value = false
  fitSlide()
})
</script>

<style scoped>
.po-real-viewer {
  position: relative;
  width: 100%;
  height: clamp(500px, calc(100vh - 230px), 760px);
  min-height: 500px;
  overflow: hidden;
  border: 1px solid oklch(0.28 0.016 230);
  border-radius: var(--po-radius-sm);
  background: var(--po-viewer);
  color: var(--po-viewer-ink);
  cursor: grab;
  touch-action: none;
  user-select: none;
}

.po-real-viewer.is-dragging {
  cursor: grabbing;
}

.po-real-viewer:focus-visible {
  outline: 3px solid var(--po-harbor);
  outline-offset: 2px;
}

.po-real-image-stage {
  position: absolute;
  inset: 0;
  overflow: hidden;
}

.po-real-image-stage > img {
  position: absolute;
  top: 50%;
  left: 50%;
  width: auto;
  max-width: 92%;
  height: 90%;
  object-fit: contain;
  transform-origin: center;
  transition: transform var(--po-motion-standard) var(--po-ease-state);
  -webkit-user-drag: none;
}

.po-real-viewer.is-dragging .po-real-image-stage > img {
  transition: none;
}

.po-real-image-loading {
  position: absolute;
  inset: 72px 18%;
  display: grid;
  align-content: center;
  opacity: 0.32;
}

.po-real-viewer-badges,
.po-real-viewer-toolbar,
.po-evidence-patch,
.po-real-legend,
.po-real-zoom {
  position: absolute;
  z-index: 2;
}

.po-real-viewer-badges {
  top: 12px;
  left: 12px;
  display: flex;
  gap: 6px;
}

.po-real-viewer-badges span,
.po-real-zoom {
  padding: 4px 7px;
  border: 1px solid oklch(0.34 0.016 230);
  border-radius: 3px;
  background: rgba(20, 29, 34, 0.9);
  color: var(--po-viewer-ink);
  font-size: 9px;
  font-weight: 650;
}

.po-real-viewer-badges span:first-child {
  border-color: oklch(0.62 0.1 155);
  color: oklch(0.86 0.07 155);
}

.po-real-viewer-toolbar {
  top: 12px;
  right: 12px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border: 1px solid oklch(0.28 0.016 230);
  border-radius: var(--po-radius-sm);
  background: rgba(24, 34, 40, 0.94);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
}

.po-mode-control {
  display: flex;
}

.po-real-viewer-toolbar button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 36px;
  padding: 0 9px;
  border: 1px solid transparent;
  border-radius: 4px;
  background: transparent;
  color: var(--po-viewer-ink);
  font-size: 10px;
  font-weight: 600;
  cursor: pointer;
  transition:
    color var(--po-motion-fast) var(--po-ease-state),
    border-color var(--po-motion-fast) var(--po-ease-state),
    background-color var(--po-motion-fast) var(--po-ease-state);
}

.po-real-viewer-toolbar > button {
  width: 36px;
  padding: 0;
}

.po-real-viewer-toolbar button:hover {
  border-color: oklch(0.4 0.018 230);
  background: oklch(0.28 0.016 230);
}

.po-real-viewer-toolbar button:focus-visible {
  border-color: var(--po-harbor);
  outline: 3px solid oklch(0.31 0.08 230);
  outline-offset: 1px;
}

.po-real-viewer-toolbar button.active {
  border-color: var(--po-eosin);
  background: oklch(0.29 0.045 350);
  color: oklch(0.9 0.06 350);
}

.po-real-toolbar-divider {
  width: 1px;
  height: 24px;
  margin: 0 2px;
  background: oklch(0.32 0.016 230);
}

.po-evidence-patch {
  right: 12px;
  bottom: 48px;
  width: min(188px, 28%);
  margin: 0;
  overflow: hidden;
  border: 1px solid oklch(0.42 0.05 350);
  border-radius: var(--po-radius-sm);
  background: var(--po-viewer-panel);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.38);
}

.po-evidence-patch img {
  display: block;
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
}

.po-evidence-patch figcaption {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 8px;
  color: var(--po-viewer-ink);
  font-size: 9px;
}

.po-real-legend {
  bottom: 12px;
  left: 12px;
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 6px 8px;
  border: 1px solid oklch(0.28 0.016 230);
  border-radius: 4px;
  background: rgba(20, 29, 34, 0.9);
  color: oklch(0.78 0.01 230);
  font-size: 9px;
  font-weight: 600;
}

.po-real-legend-scale {
  width: 84px;
  height: 7px;
  border-radius: 2px;
  background: linear-gradient(90deg, #352a87, #19a5c9, #7fd34e, #f6c746, #c51b31);
}

.po-real-zoom {
  right: 12px;
  bottom: 12px;
  font-family: "SFMono-Regular", Consolas, monospace;
}

@media (max-width: 760px) {
  .po-real-viewer {
    height: 360px;
    min-height: 360px;
    pointer-events: none;
  }

  .po-real-viewer-toolbar,
  .po-real-zoom,
  .po-evidence-patch {
    display: none;
  }
}
</style>
