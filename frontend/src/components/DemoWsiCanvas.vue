<template>
  <section
    ref="container"
    class="po-wsi-viewer"
    tabindex="0"
    :aria-label="t('po.viewer.title')"
    @keydown="handleKeydown"
    @pointerdown="startPan"
    @pointermove="movePan"
    @pointerup="endPan"
    @pointercancel="endPan"
    @wheel.prevent="handleWheel"
  >
    <canvas ref="canvas" role="img" :aria-label="t('po.viewer.simulatedWsi')"></canvas>

    <div class="po-wsi-badges">
      <span>{{ t('po.viewer.simulatedWsi') }}</span>
      <span>{{ caseItem.specimen.stain }}</span>
    </div>

    <div class="po-wsi-toolbar" @pointerdown.stop>
      <el-tooltip :content="t('po.viewer.zoomIn')" placement="left">
        <button type="button" :aria-label="t('po.viewer.zoomIn')" @click="zoomBy(0.25)">
          <el-icon><ZoomIn /></el-icon>
        </button>
      </el-tooltip>
      <el-tooltip :content="t('po.viewer.zoomOut')" placement="left">
        <button type="button" :aria-label="t('po.viewer.zoomOut')" @click="zoomBy(-0.25)">
          <el-icon><ZoomOut /></el-icon>
        </button>
      </el-tooltip>
      <el-tooltip :content="t('po.viewer.fit')" placement="left">
        <button type="button" :aria-label="t('po.viewer.fit')" @click="fitSlide">
          <el-icon><FullScreen /></el-icon>
        </button>
      </el-tooltip>
      <span class="po-toolbar-divider" aria-hidden="true"></span>
      <el-tooltip
        :content="t(showHeatmap ? 'po.viewer.heatmapOn' : 'po.viewer.heatmapOff')"
        placement="left"
      >
        <button
          type="button"
          :class="{ active: showHeatmap }"
          :aria-label="t('po.viewer.heatmap')"
          :aria-pressed="showHeatmap"
          @click="toggleHeatmap"
        >
          <el-icon><Histogram /></el-icon>
        </button>
      </el-tooltip>
    </div>

    <div v-if="showHeatmap" class="po-heatmap-legend" @pointerdown.stop>
      <span>{{ t('po.viewer.lowSignal') }}</span>
      <span class="po-legend-scale" aria-hidden="true"></span>
      <span>{{ t('po.viewer.highSignal') }}</span>
    </div>

    <div class="po-wsi-hint">
      <span class="po-tabular">{{ Math.round(scale * 100) }}%</span>
      <span>{{ t('po.viewer.canvasHint') }}</span>
    </div>
  </section>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { FullScreen, Histogram, ZoomIn, ZoomOut } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'

const props = defineProps({
  caseItem: {
    type: Object,
    required: true,
  },
  selectedRegion: {
    type: String,
    default: null,
  },
})

const emit = defineEmits(['select-region'])
const { t } = useI18n()

const container = ref(null)
const canvas = ref(null)
const showHeatmap = ref(true)
const scale = ref(1)
const pan = ref({ x: 0, y: 0 })

let texture = null
let resizeObserver = null
let animationFrame = null
let dragging = false
let dragOrigin = null
let lastLayout = { width: 0, height: 0, slideWidth: 0, slideHeight: 0 }

function hashString(value) {
  let hash = 2166136261
  for (let index = 0; index < value.length; index += 1) {
    hash ^= value.charCodeAt(index)
    hash = Math.imul(hash, 16777619)
  }
  return hash >>> 0
}

function randomFactory(seed) {
  let value = seed || 1
  return () => {
    value += 0x6D2B79F5
    let next = value
    next = Math.imul(next ^ (next >>> 15), next | 1)
    next ^= next + Math.imul(next ^ (next >>> 7), next | 61)
    return ((next ^ (next >>> 14)) >>> 0) / 4294967296
  }
}

function tissuePath(context, width, height) {
  const points = []
  const centerX = width * 0.5
  const centerY = height * 0.51
  const radiusX = width * 0.43
  const radiusY = height * 0.38

  for (let index = 0; index <= 56; index += 1) {
    const angle = (index / 56) * Math.PI * 2
    const modulation = 1
      + Math.sin(angle * 3.1) * 0.055
      + Math.sin(angle * 7.3) * 0.035
      + Math.cos(angle * 11.7) * 0.02
    points.push({
      x: centerX + Math.cos(angle) * radiusX * modulation,
      y: centerY + Math.sin(angle) * radiusY * modulation,
    })
  }

  context.beginPath()
  context.moveTo(points[0].x, points[0].y)
  for (let index = 1; index < points.length; index += 1) {
    context.lineTo(points[index].x, points[index].y)
  }
  context.closePath()
}

function createTexture() {
  const width = 1400
  const height = 880
  const source = document.createElement('canvas')
  source.width = width
  source.height = height
  const context = source.getContext('2d')
  const random = randomFactory(hashString(props.caseItem.id))

  context.fillStyle = '#f8eef4'
  context.fillRect(0, 0, width, height)

  context.save()
  tissuePath(context, width, height)
  context.clip()

  context.fillStyle = '#e8a9c4'
  context.fillRect(0, 0, width, height)

  for (let index = 0; index < 100; index += 1) {
    const x = random() * width
    const y = random() * height
    const radiusX = 34 + random() * 115
    const radiusY = 18 + random() * 70
    context.save()
    context.translate(x, y)
    context.rotate((random() - 0.5) * Math.PI)
    context.fillStyle = random() > 0.5 ? 'rgba(245, 194, 211, 0.58)' : 'rgba(197, 105, 157, 0.22)'
    context.beginPath()
    context.ellipse(0, 0, radiusX, radiusY, 0, 0, Math.PI * 2)
    context.fill()
    context.restore()
  }

  for (let index = 0; index < 68; index += 1) {
    const x = random() * width
    const y = random() * height
    const radiusX = 10 + random() * 36
    const radiusY = 6 + random() * 20
    context.save()
    context.translate(x, y)
    context.rotate((random() - 0.5) * Math.PI)
    context.fillStyle = 'rgba(250, 229, 237, 0.92)'
    context.strokeStyle = 'rgba(164, 82, 139, 0.32)'
    context.lineWidth = 2
    context.beginPath()
    context.ellipse(0, 0, radiusX, radiusY, 0, 0, Math.PI * 2)
    context.fill()
    context.stroke()
    context.restore()
  }

  for (let index = 0; index < 1200; index += 1) {
    const x = random() * width
    const y = random() * height
    const radiusX = 1.2 + random() * 2.8
    const radiusY = 1 + random() * 2.1
    context.save()
    context.translate(x, y)
    context.rotate(random() * Math.PI)
    context.fillStyle = random() > 0.15 ? 'rgba(76, 40, 115, 0.64)' : 'rgba(120, 48, 126, 0.55)'
    context.beginPath()
    context.ellipse(0, 0, radiusX, radiusY, 0, 0, Math.PI * 2)
    context.fill()
    context.restore()
  }

  props.caseItem.ai.regions.forEach((region) => {
    const focusX = region.x * width
    const focusY = region.y * height
    const count = 80 + Math.round(region.score * 90)
    for (let index = 0; index < count; index += 1) {
      const angle = random() * Math.PI * 2
      const distance = Math.sqrt(random()) * region.radius * width * 0.7
      const x = focusX + Math.cos(angle) * distance
      const y = focusY + Math.sin(angle) * distance * 0.65
      context.fillStyle = 'rgba(63, 30, 104, 0.78)'
      context.beginPath()
      context.ellipse(x, y, 2 + random() * 3.2, 1.4 + random() * 2.2, random() * Math.PI, 0, Math.PI * 2)
      context.fill()
    }
  })

  context.restore()

  tissuePath(context, width, height)
  context.strokeStyle = 'rgba(119, 56, 116, 0.34)'
  context.lineWidth = 4
  context.stroke()

  texture = source
}

function scheduleDraw() {
  if (animationFrame) cancelAnimationFrame(animationFrame)
  animationFrame = requestAnimationFrame(draw)
}

function drawHeatmap(context, slideWidth, slideHeight) {
  props.caseItem.ai.regions.forEach((region) => {
    const x = (region.x - 0.5) * slideWidth
    const y = (region.y - 0.5) * slideHeight
    const radius = Math.max(42, region.radius * slideWidth)
    const gradient = context.createRadialGradient(x, y, 2, x, y, radius)
    gradient.addColorStop(0, 'rgba(211, 53, 112, 0.64)')
    gradient.addColorStop(0.42, 'rgba(230, 105, 88, 0.38)')
    gradient.addColorStop(0.72, 'rgba(233, 171, 57, 0.2)')
    gradient.addColorStop(1, 'rgba(233, 171, 57, 0)')
    context.fillStyle = gradient
    context.beginPath()
    context.arc(x, y, radius, 0, Math.PI * 2)
    context.fill()
  })
}

function drawRegionMarkers(context, slideWidth, slideHeight) {
  props.caseItem.ai.regions.forEach((region) => {
    const x = (region.x - 0.5) * slideWidth
    const y = (region.y - 0.5) * slideHeight
    const selected = props.selectedRegion === region.id
    const markerRadius = selected ? 18 : 15

    context.lineWidth = selected ? 4 : 2
    context.strokeStyle = selected ? '#ffffff' : '#d44f97'
    context.fillStyle = selected ? '#245f78' : '#a83272'
    context.beginPath()
    context.arc(x, y, markerRadius, 0, Math.PI * 2)
    context.fill()
    context.stroke()

    context.fillStyle = '#ffffff'
    context.font = '700 11px Inter, Arial, sans-serif'
    context.textAlign = 'center'
    context.textBaseline = 'middle'
    context.fillText(region.id.replace('R', ''), x, y + 0.5)
  })
}

function draw() {
  if (!container.value || !canvas.value || !texture) return

  const bounds = container.value.getBoundingClientRect()
  const width = Math.max(1, Math.round(bounds.width))
  const height = Math.max(1, Math.round(bounds.height))
  const pixelRatio = Math.min(window.devicePixelRatio || 1, 2)

  if (canvas.value.width !== Math.round(width * pixelRatio)) {
    canvas.value.width = Math.round(width * pixelRatio)
    canvas.value.height = Math.round(height * pixelRatio)
  }
  canvas.value.style.width = width + 'px'
  canvas.value.style.height = height + 'px'

  const context = canvas.value.getContext('2d')
  context.setTransform(pixelRatio, 0, 0, pixelRatio, 0, 0)
  context.clearRect(0, 0, width, height)
  context.fillStyle = '#10181d'
  context.fillRect(0, 0, width, height)

  const availableWidth = width * 0.92
  const availableHeight = height * 0.86
  const textureRatio = texture.width / texture.height
  let slideWidth = availableWidth
  let slideHeight = slideWidth / textureRatio
  if (slideHeight > availableHeight) {
    slideHeight = availableHeight
    slideWidth = slideHeight * textureRatio
  }

  lastLayout = { width, height, slideWidth, slideHeight }

  context.save()
  context.translate(width / 2 + pan.value.x, height / 2 + pan.value.y)
  context.scale(scale.value, scale.value)
  context.drawImage(texture, -slideWidth / 2, -slideHeight / 2, slideWidth, slideHeight)

  if (showHeatmap.value) {
    context.save()
    context.globalCompositeOperation = 'multiply'
    drawHeatmap(context, slideWidth, slideHeight)
    context.restore()
  }

  drawRegionMarkers(context, slideWidth, slideHeight)
  context.restore()
}

function zoomBy(amount) {
  scale.value = Math.min(3.5, Math.max(0.75, scale.value + amount))
  scheduleDraw()
}

function fitSlide() {
  scale.value = 1
  pan.value = { x: 0, y: 0 }
  scheduleDraw()
}

function toggleHeatmap() {
  showHeatmap.value = !showHeatmap.value
  scheduleDraw()
}

function focusRegion(regionId) {
  const region = props.caseItem.ai.regions.find((item) => item.id === regionId)
  if (!region || !lastLayout.slideWidth) return

  scale.value = 2.05
  pan.value = {
    x: -(region.x - 0.5) * lastLayout.slideWidth * scale.value,
    y: -(region.y - 0.5) * lastLayout.slideHeight * scale.value,
  }
  scheduleDraw()
}

function startPan(event) {
  if (event.button !== 0) return
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
  scheduleDraw()
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
  const keyActions = {
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

  const action = keyActions[event.key]
  if (!action) return
  event.preventDefault()
  action()
  scheduleDraw()
}

function selectNearestRegion(event) {
  if (!lastLayout.slideWidth) return
  const bounds = container.value.getBoundingClientRect()
  const localX = event.clientX - bounds.left - lastLayout.width / 2 - pan.value.x
  const localY = event.clientY - bounds.top - lastLayout.height / 2 - pan.value.y
  const normalizedX = localX / (lastLayout.slideWidth * scale.value) + 0.5
  const normalizedY = localY / (lastLayout.slideHeight * scale.value) + 0.5

  let nearest = null
  let nearestDistance = Number.POSITIVE_INFINITY
  props.caseItem.ai.regions.forEach((region) => {
    const distance = Math.hypot(normalizedX - region.x, normalizedY - region.y)
    if (distance < nearestDistance && distance < region.radius * 0.9) {
      nearest = region
      nearestDistance = distance
    }
  })

  if (nearest) emit('select-region', nearest.id)
}

watch(
  () => props.caseItem.id,
  () => {
    createTexture()
    fitSlide()
  },
)

watch(
  () => props.selectedRegion,
  async (regionId) => {
    await nextTick()
    if (regionId) focusRegion(regionId)
    else scheduleDraw()
  },
)

onMounted(() => {
  createTexture()
  resizeObserver = new ResizeObserver(scheduleDraw)
  resizeObserver.observe(container.value)
  container.value.addEventListener('dblclick', selectNearestRegion)
  scheduleDraw()
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  if (container.value) container.value.removeEventListener('dblclick', selectNearestRegion)
  if (animationFrame) cancelAnimationFrame(animationFrame)
})
</script>

<style scoped>
.po-wsi-viewer {
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

.po-wsi-viewer.is-dragging {
  cursor: grabbing;
}

.po-wsi-viewer:focus-visible {
  outline: 3px solid var(--po-harbor);
  outline-offset: 2px;
}

.po-wsi-viewer canvas {
  display: block;
  width: 100%;
  height: 100%;
}

.po-wsi-badges,
.po-wsi-toolbar,
.po-heatmap-legend,
.po-wsi-hint {
  position: absolute;
  z-index: 2;
}

.po-wsi-badges {
  top: 12px;
  left: 12px;
  display: flex;
  gap: 6px;
}

.po-wsi-badges span {
  padding: 4px 7px;
  border: 1px solid oklch(0.34 0.016 230);
  border-radius: 3px;
  background: rgba(20, 29, 34, 0.88);
  color: var(--po-viewer-ink);
  font-size: 9px;
  font-weight: 700;
}

.po-wsi-badges span:first-child {
  border-color: oklch(0.62 0.1 350);
  color: oklch(0.88 0.065 350);
}

.po-wsi-toolbar {
  top: 12px;
  right: 12px;
  display: grid;
  gap: 4px;
  padding: 4px;
  border: 1px solid oklch(0.28 0.016 230);
  border-radius: var(--po-radius-sm);
  background: rgba(24, 34, 40, 0.94);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.4);
}

.po-wsi-toolbar button {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: var(--po-radius-sm);
  background: transparent;
  color: var(--po-viewer-ink);
  cursor: pointer;
  transition:
    color var(--po-motion-fast) var(--po-ease-state),
    border-color var(--po-motion-fast) var(--po-ease-state),
    background-color var(--po-motion-fast) var(--po-ease-state);
}

.po-wsi-toolbar button:hover {
  border-color: oklch(0.4 0.018 230);
  background: oklch(0.28 0.016 230);
}

.po-wsi-toolbar button:focus-visible {
  border-color: var(--po-harbor);
  outline: 3px solid oklch(0.31 0.08 230);
  outline-offset: 1px;
}

.po-wsi-toolbar button.active {
  border-color: var(--po-eosin);
  background: oklch(0.29 0.045 350);
  color: oklch(0.9 0.06 350);
}

.po-toolbar-divider {
  display: block;
  height: 1px;
  margin: 2px 4px;
  background: oklch(0.32 0.016 230);
}

.po-heatmap-legend {
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

.po-legend-scale {
  width: 84px;
  height: 7px;
  border-radius: 2px;
  background: linear-gradient(90deg, rgba(233, 171, 57, 0.34), rgba(211, 53, 112, 0.9));
}

.po-wsi-hint {
  right: 12px;
  bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: min(420px, calc(100% - 230px));
  padding: 6px 8px;
  border: 1px solid oklch(0.28 0.016 230);
  border-radius: 4px;
  background: rgba(20, 29, 34, 0.9);
  color: oklch(0.78 0.01 230);
  font-size: 9px;
}

.po-wsi-hint .po-tabular {
  color: var(--po-viewer-ink);
  font-family: "SFMono-Regular", Consolas, monospace;
}

@media (max-width: 760px) {
  .po-wsi-viewer {
    height: 360px;
    min-height: 360px;
    pointer-events: none;
  }

  .po-wsi-toolbar,
  .po-wsi-hint {
    display: none;
  }

  .po-heatmap-legend {
    max-width: calc(100% - 104px);
  }
}
</style>
