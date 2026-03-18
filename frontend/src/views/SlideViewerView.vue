<template>
  <div class="viewer-page">
    <!-- ── Header ─────────────────────────────────────────────────────────── -->
    <header class="viewer-header">
      <!-- Left: back + filename -->
      <div class="header-left">
        <el-button :icon="ArrowLeft" text class="back-btn" @click="goBack">文件浏览器</el-button>
        <el-divider direction="vertical" />
        <span class="slide-name" :title="slide?.fileName">{{ slide?.fileName ?? '加载中...' }}</span>
      </div>

      <!-- Center: toolbar (only after image is loaded) -->
      <div class="header-tools" v-if="!loading && !errorMsg">
        <!-- View tools -->
        <el-button-group>
          <el-tooltip content="平移/查看 (V)" placement="bottom">
            <el-button size="small" :type="currentTool==='pan' ? 'primary' : ''" @click="setTool('pan')">平移</el-button>
          </el-tooltip>
          <el-tooltip content="选择/删除标注 (S)" placement="bottom">
            <el-button size="small" :type="currentTool==='select' ? 'primary' : ''" @click="setTool('select')">选择</el-button>
          </el-tooltip>
        </el-button-group>

        <el-divider direction="vertical" />

        <!-- Draw tools -->
        <el-button-group>
          <el-tooltip content="画笔 (B)" placement="bottom">
            <el-button size="small" :type="currentTool==='brush' ? 'primary' : ''" @click="setTool('brush')">画笔</el-button>
          </el-tooltip>
          <el-tooltip content="矩形 ROI (R)" placement="bottom">
            <el-button size="small" :type="currentTool==='rect' ? 'primary' : ''" @click="setTool('rect')">矩形</el-button>
          </el-tooltip>
          <el-tooltip content="多边形 ROI (P)" placement="bottom">
            <el-button size="small" :type="currentTool==='polygon' ? 'primary' : ''" @click="setTool('polygon')">多边形</el-button>
          </el-tooltip>
        </el-button-group>

        <el-divider direction="vertical" />

        <!-- Undo / redo -->
        <el-tooltip content="撤销 (Ctrl+Z)" placement="bottom">
          <el-button size="small" :disabled="historyIndex <= 0" @click="undo">
            <el-icon><RefreshLeft /></el-icon>
          </el-button>
        </el-tooltip>
        <el-tooltip content="重做 (Ctrl+Y)" placement="bottom">
          <el-button size="small" :disabled="historyIndex >= history.length - 1" @click="redo">
            <el-icon><RefreshRight /></el-icon>
          </el-button>
        </el-tooltip>

        <el-divider direction="vertical" />

        <!-- Save -->
        <el-button
          size="small"
          :loading="saving"
          :type="isDirty ? 'warning' : 'default'"
          @click="saveAnnotations"
        >{{ saving ? '保存中...' : isDirty ? '保存*' : '已保存' }}</el-button>

        <el-divider direction="vertical" />

        <!-- Mark done -->
        <el-button
          size="small"
          :loading="completing"
          :type="slide?.annoStatus === 'DONE' ? 'success' : 'default'"
          :disabled="slide?.annoStatus === 'NOT_STARTED'"
          @click="markDone"
        >{{ slide?.annoStatus === 'DONE' ? '已完成' : '标记完成' }}</el-button>
      </div>

      <!-- Right: export + zoom controls -->
      <div class="header-right">
        <el-tooltip content="导出数据集 (ZIP)" placement="bottom">
          <el-button
            size="small"
            :icon="Download"
            :disabled="loading || !!errorMsg"
            @click="showExportDialog = true"
          >导出</el-button>
        </el-tooltip>
        <el-divider direction="vertical" />
        <span class="zoom-badge">{{ zoomLabel }}</span>
        <el-button-group>
          <el-button size="small" @click="zoomIn"  title="放大 (+)"><el-icon><Plus /></el-icon></el-button>
          <el-button size="small" @click="zoomFit" title="适合屏幕 (H)">适合</el-button>
          <el-button size="small" @click="zoomOut" title="缩小 (-)"><el-icon><Minus /></el-icon></el-button>
        </el-button-group>
      </div>
    </header>

    <!-- ── Export dialog ──────────────────────────────────────────────────── -->
    <ExportDialog v-model="showExportDialog" :slide-id="slideId" />

    <!-- ── Body ───────────────────────────────────────────────────────────── -->
    <div class="viewer-body">
      <!-- Left: label panel -->
      <LabelPanel
        v-if="!loading && !errorMsg"
        :labels="labels"
        :activeId="activeLabelId"
        :brushSize="brushSize"
        :showBrushSize="currentTool === 'brush'"
        @select="activeLabelId = $event"
        @add="onAddLabel"
        @delete="onDeleteLabel"
        @brush-size="brushSize = $event"
      />

      <!-- Center: OSD + Fabric overlay -->
      <div class="viewer-center">
        <!-- OSD container (absolutely positioned inside viewer-center) -->
        <div ref="osdEl" class="osd-container" />

        <!-- Fabric.js overlay canvas -->
        <div
          ref="fabricWrapper"
          class="fabric-wrapper"
          :class="{ 'tool-active': currentTool !== 'pan' }"
        >
          <canvas ref="fabricEl" />
        </div>

        <!-- Polygon in-progress hint -->
        <div v-if="currentTool === 'polygon' && polygonPoints.length > 0" class="polygon-hint">
          双击或点击起点完成（{{ polygonPoints.length }} 点）· Esc 取消
        </div>

        <!-- Loading overlay -->
        <div v-if="loading" class="loading-overlay">
          <el-icon class="loading-spin"><Loading /></el-icon>
          <span>加载图像中...</span>
        </div>

        <!-- Error overlay -->
        <div v-if="errorMsg" class="error-overlay">
          <el-icon style="font-size:48px;color:#f56c6c"><WarningFilled /></el-icon>
          <p>{{ errorMsg }}</p>
          <el-button @click="goBack">返回</el-button>
        </div>
      </div>
    </div>

    <!-- ── Right-click context menu ──────────────────────────────────────── -->
    <Teleport to="body">
      <div
        v-if="contextMenu.visible"
        class="anno-context-menu"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
        @mousedown.stop
      >
        <div class="ctx-item ctx-danger" @click="onCtxDelete">
          <el-icon><Delete /></el-icon>
          删除标注
        </div>
        <div class="ctx-divider" />
        <div class="ctx-subtitle">更换标签</div>
        <div
          v-for="label in labels"
          :key="label.id"
          class="ctx-item"
          :class="{ 'ctx-active': label.id === contextMenu.labelId }"
          @click="onCtxChangeLabel(label.id)"
        >
          <span class="ctx-dot" :style="{ background: label.color }" />
          <span>{{ label.name }}</span>
        </div>
      </div>
    </Teleport>

    <!-- ── Coordinate bar ─────────────────────────────────────────────────── -->
    <div class="coord-bar" v-if="!loading && !errorMsg">
      <el-icon><Aim /></el-icon>
      <span>X: <strong>{{ coordX }}</strong></span>
      <span>Y: <strong>{{ coordY }}</strong></span>
      <el-divider direction="vertical" />
      <span class="shortcut-hint">
        +/- 缩放 &nbsp;|&nbsp; H 适合 &nbsp;|&nbsp; 方向键平移 &nbsp;|&nbsp;
        Del 删除选中 &nbsp;|&nbsp; Ctrl+Z/Y 撤销/重做 &nbsp;|&nbsp; Ctrl+S 保存
      </span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, Plus, Minus, Loading, WarningFilled, Aim,
  RefreshLeft, RefreshRight, Download, Delete
} from '@element-plus/icons-vue'
import OpenSeadragon from 'openseadragon'
import { fabric } from 'fabric'
import { slideApi } from '@/api/slide'
import { annotationApi } from '@/api/annotation'
import LabelPanel from '@/components/LabelPanel.vue'
import ExportDialog from '@/components/ExportDialog.vue'

const route   = useRoute()
const router  = useRouter()
const slideId = Number(route.params.id)

// ── DOM refs ──────────────────────────────────────────────────────────────────
const osdEl         = ref(null)
const fabricEl      = ref(null)
const fabricWrapper = ref(null)

// ── Slide state ───────────────────────────────────────────────────────────────
const slide       = ref(null)
const loading     = ref(true)
const errorMsg    = ref('')
const slideWidth  = ref(0)
const slideHeight = ref(0)
const coordX      = ref(0)
const coordY      = ref(0)
const zoomRaw     = ref(1)

// ── Annotation state ──────────────────────────────────────────────────────────
const labels        = ref([])
const activeLabelId = ref(null)
const annotations   = ref([])
const brushSize     = ref(20)
const currentTool   = ref('pan')
const isDirty       = ref(false)
const saving        = ref(false)
const completing    = ref(false)

// ── Undo / redo ───────────────────────────────────────────────────────────────
const MAX_HISTORY  = 20
const history      = ref([[]])
const historyIndex = ref(0)

// ── Export ────────────────────────────────────────────────────────────────────
const showExportDialog = ref(false)

// ── Context menu ──────────────────────────────────────────────────────────────
const contextMenu = ref({ visible: false, x: 0, y: 0, annoId: null, labelId: null })

// ── Polygon in-progress ───────────────────────────────────────────────────────
const polygonPoints = ref([])  // [[x,y], ...]

// ── Non-reactive instances ────────────────────────────────────────────────────
let viewer        = null
let fc            = null
let autoSaveTimer = null
let localIdCtr    = 0

// Drawing transient state
let isDrawing  = false
let drawStart  = null
let previewObj = null
let currentScale = 1

// Polygon preview fabric objects
let polyPreviewCircle = null
let polyPreviewLine   = null

// ── Computed ──────────────────────────────────────────────────────────────────
const zoomLabel = computed(() => {
  if (!viewer || !slideWidth.value) return '—'
  const containerW = viewer.viewport.getContainerSize().x
  const mag = (zoomRaw.value * containerW) / slideWidth.value
  return `${Math.round(mag * 100)}%`
})

const activeLabel = computed(() =>
  labels.value.find(l => l.id === activeLabelId.value)
)

// ── Lifecycle ─────────────────────────────────────────────────────────────────
onMounted(async () => {
  window.addEventListener('keydown',   onKeyDown)
  window.addEventListener('mousedown', closeContextMenu)

  // 1. Slide info
  try {
    const res = await slideApi.get(slideId)
    slide.value = res.data
    if (slide.value?.status !== 'READY') {
      errorMsg.value = '图像尚未处理完成，请稍候再试'
      loading.value = false
      return
    }
  } catch {
    errorMsg.value = '无法加载图像信息'
    loading.value = false
    return
  }

  // 2. DZI XML (bypass JSON interceptor)
  let dziParams
  try {
    const resp = await fetch(`/api/slides/${slideId}/dzi`)
    if (!resp.ok) throw new Error(`DZI fetch failed: ${resp.status}`)
    const xml    = new DOMParser().parseFromString(await resp.text(), 'application/xml')
    const imgEl  = xml.querySelector('Image')
    const sizeEl = xml.querySelector('Size')
    if (!imgEl || !sizeEl) throw new Error('DZI XML 解析失败')
    dziParams = {
      tileSize: parseInt(imgEl.getAttribute('TileSize')),
      overlap:  parseInt(imgEl.getAttribute('Overlap')),
      format:   imgEl.getAttribute('Format') ?? 'jpg',
      width:    parseInt(sizeEl.getAttribute('Width')),
      height:   parseInt(sizeEl.getAttribute('Height'))
    }
    slideWidth.value  = dziParams.width
    slideHeight.value = dziParams.height
  } catch (e) {
    errorMsg.value = `加载 DZI 失败：${e.message}`
    loading.value = false
    return
  }

  // 3. Labels + annotations in parallel
  try {
    const [lblRes, annoRes] = await Promise.all([
      annotationApi.listLabels(slideId),
      annotationApi.list(slideId)
    ])
    labels.value      = lblRes.data  ?? []
    annotations.value = (annoRes.data ?? []).map(normalizeAnno)
    if (labels.value.length > 0) activeLabelId.value = labels.value[0].id
    history.value      = [deepClone(annotations.value)]
    historyIndex.value = 0
  } catch (e) {
    console.error('加载标注失败', e)
  }

  // 4. OpenSeadragon
  try {
    viewer = OpenSeadragon({
      element:                  osdEl.value,
      prefixUrl:                '',
      showNavigationControl:    false,
      showNavigator:            true,
      navigatorPosition:        'BOTTOM_RIGHT',
      navigatorSizeRatio:       0.18,
      navigatorMaintainSizeRatio: true,
      animationTime:            0.4,
      blendTime:                0.1,
      constrainDuringPan:       true,
      maxZoomPixelRatio:        4,
      minZoomImageRatio:        0.5,
      visibilityRatio:          0.5,
      defaultZoomLevel:         0,
      gestureSettingsMouse:     { clickToZoom: false },
      tileSources: {
        width:       dziParams.width,
        height:      dziParams.height,
        tileSize:    dziParams.tileSize,
        tileOverlap: dziParams.overlap,
        minLevel:    0,
        getTileUrl(level, x, y) {
          return `/api/slides/${slideId}/tiles/${level}/${x}_${y}.${dziParams.format}`
        }
      }
    })

    viewer.addHandler('open', () => {
      loading.value = false
      zoomRaw.value = viewer.viewport.getZoom()
      initFabricCanvas()
      renderAllAnnotations()
    })

    viewer.addHandler('open-failed', () => {
      loading.value = false
      errorMsg.value = '图像瓦片加载失败，请检查后端服务'
    })

    viewer.addHandler('zoom',            (e) => { zoomRaw.value = e.zoom })
    viewer.addHandler('update-viewport', syncFabricTransform)

    viewer.addHandler('canvas-mousemove', (e) => {
      if (!viewer.isOpen()) return
      const vpPt  = viewer.viewport.pointFromPixel(e.position)
      const imgPt = viewer.viewport.viewportToImageCoordinates(vpPt)
      coordX.value = Math.max(0, Math.round(imgPt.x))
      coordY.value = Math.max(0, Math.round(imgPt.y))
    })
  } catch (e) {
    errorMsg.value = `查看器初始化失败：${e.message}`
    loading.value = false
  }

  // 5. Auto-save every 60 s
  autoSaveTimer = setInterval(() => {
    if (isDirty.value) saveAnnotations()
  }, 60000)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown',   onKeyDown)
  window.removeEventListener('mousedown', closeContextMenu)
  clearInterval(autoSaveTimer)
  fc?.dispose()
  viewer?.destroy()
  viewer = null
  fc     = null
})

// ── Fabric.js initialisation ──────────────────────────────────────────────────
function initFabricCanvas() {
  const container = osdEl.value
  fabricEl.value.width  = container.clientWidth
  fabricEl.value.height = container.clientHeight

  fc = new fabric.Canvas(fabricEl.value, {
    selection:           false,
    renderOnAddRemove:   false,
    enableRetinaScaling: false
  })

  // Keep canvas size in sync with the OSD container
  new ResizeObserver(() => {
    if (!fc || !container) return
    fc.setWidth(container.clientWidth)
    fc.setHeight(container.clientHeight)
    syncFabricTransform()
  }).observe(container)

  fc.on('mouse:down',     onFabricDown)
  fc.on('mouse:move',     onFabricMove)
  fc.on('mouse:up',       onFabricUp)
  fc.on('mouse:dblclick', onFabricDblClick)
  fc.on('path:created',   onPathCreated)

  fabricEl.value.addEventListener('contextmenu', onCanvasContextMenu)

  syncFabricTransform()
}

/**
 * Recalculate the Fabric.js viewport transform to match OSD's current pan/zoom.
 * Objects are stored in image-pixel coordinates; this transform maps them to screen.
 */
function syncFabricTransform() {
  if (!fc || !viewer?.isOpen()) return
  const bounds     = viewer.viewport.getBounds(true)   // viewport units
  const containerW = viewer.viewport.getContainerSize().x
  const W          = slideWidth.value

  // 1 image px → scale screen px
  const scale = containerW / (bounds.width * W)
  currentScale = scale

  // screen offset of image origin (0,0)
  const tx = -(bounds.x * containerW) / bounds.width
  const ty = -(bounds.y * containerW) / bounds.width  // uniform scaling

  fc.setViewportTransform([scale, 0, 0, scale, tx, ty])
  fc.renderAll()
}

// ── Tool management ───────────────────────────────────────────────────────────
function setTool(tool) {
  if (currentTool.value === 'polygon' && polygonPoints.value.length > 0) {
    cancelPolygon()
  }
  currentTool.value = tool
  if (!fc || !viewer) return

  const wrapper = fabricWrapper.value

  if (tool === 'pan') {
    viewer.setMouseNavEnabled(true)
    fc.isDrawingMode = false
    fc.selection     = false
    fc.forEachObject(o => { o.selectable = false; o.evented = false })
    wrapper.style.pointerEvents = 'none'

  } else if (tool === 'select') {
    viewer.setMouseNavEnabled(false)
    fc.isDrawingMode = false
    fc.selection     = false
    fc.forEachObject(o => { o.selectable = true; o.evented = true })
    fc.defaultCursor = 'pointer'
    wrapper.style.pointerEvents = 'auto'

  } else if (tool === 'brush') {
    viewer.setMouseNavEnabled(false)
    fc.isDrawingMode    = true
    fc.freeDrawingBrush = new fabric.PencilBrush(fc)
    fc.freeDrawingBrush.width = brushSize.value
    fc.freeDrawingBrush.color = activeLabel.value?.color ?? '#ff0000'
    fc.defaultCursor = 'crosshair'
    wrapper.style.pointerEvents = 'auto'

  } else {
    // rect / polygon
    viewer.setMouseNavEnabled(false)
    fc.isDrawingMode = false
    fc.selection     = false
    fc.forEachObject(o => { o.selectable = false; o.evented = false })
    fc.defaultCursor = 'crosshair'
    wrapper.style.pointerEvents = 'auto'
  }

  fc.renderAll()
}

watch(brushSize, sz => {
  if (fc?.freeDrawingBrush && currentTool.value === 'brush')
    fc.freeDrawingBrush.width = sz
})
watch(activeLabelId, () => {
  if (fc?.freeDrawingBrush && currentTool.value === 'brush')
    fc.freeDrawingBrush.color = activeLabel.value?.color ?? '#ff0000'
})

// ── Annotation rendering ──────────────────────────────────────────────────────
function renderAllAnnotations() {
  if (!fc) return
  fc.clear()
  for (const anno of annotations.value) renderAnnotation(anno)
  fc.renderAll()
}

function renderAnnotation(anno) {
  const label = labels.value.find(l => l.id === anno.labelId)
  const color = label?.color ?? anno.labelColor ?? '#ff0000'
  const inSelectMode = currentTool.value === 'select'

  const base = {
    stroke:       color,
    strokeWidth:  2,
    strokeUniform: true,
    selectable:   inSelectMode,
    evented:      inSelectMode,
    hasControls:  false,
    hasBorders:   true,
    _annoId:      anno._localId ?? String(anno.id)
  }

  if (anno.type === 'rect') {
    const [[x1, y1], [x2, y2]] = anno.coordinates
    fc.add(new fabric.Rect({
      ...base,
      left:   x1,
      top:    y1,
      width:  Math.abs(x2 - x1),
      height: Math.abs(y2 - y1),
      fill:   hexToRgba(color, 0.15)
    }))

  } else if (anno.type === 'polygon') {
    fc.add(new fabric.Polygon(
      anno.coordinates.map(([x, y]) => ({ x, y })),
      { ...base, fill: hexToRgba(color, 0.15) }
    ))

  } else if (anno.type === 'brush') {
    for (const stroke of anno.coordinates) {
      fc.add(new fabric.Polyline(
        stroke.points.map(([x, y]) => ({ x, y })),
        {
          ...base,
          fill:           'transparent',
          strokeWidth:     stroke.size,
          strokeUniform:   false,  // brush stroke width scales with zoom
          strokeLineCap:  'round',
          strokeLineJoin: 'round'
        }
      ))
    }
  }
}

// ── Drawing: mouse events ─────────────────────────────────────────────────────
function onFabricDown(e) {
  const tool = currentTool.value
  if (tool !== 'rect' && tool !== 'polygon') return
  if (!activeLabel.value) { ElMessage.warning('请先选择一个标签'); return }

  const ptr = fc.getPointer(e.e)

  if (tool === 'rect') {
    isDrawing  = true
    drawStart  = { x: ptr.x, y: ptr.y }
    previewObj = new fabric.Rect({
      left: ptr.x, top: ptr.y, width: 0, height: 0,
      stroke: activeLabel.value.color,
      fill:   hexToRgba(activeLabel.value.color, 0.15),
      strokeWidth: 2, strokeUniform: true,
      selectable: false, evented: false
    })
    fc.add(previewObj)
    return
  }

  if (tool === 'polygon') {
    // Click near first point → close
    if (polygonPoints.value.length >= 3) {
      const [fx, fy] = polygonPoints.value[0]
      const dx = Math.abs(ptr.x - fx) * currentScale
      const dy = Math.abs(ptr.y - fy) * currentScale
      if (dx < 12 && dy < 12) { finalizePolygon(); return }
    }
    polygonPoints.value.push([Math.round(ptr.x), Math.round(ptr.y)])
    updatePolygonPreview()
  }
}

function onFabricMove(e) {
  if (currentTool.value !== 'rect' || !isDrawing || !previewObj) return
  const ptr = fc.getPointer(e.e)
  previewObj.set({
    left:   Math.min(ptr.x, drawStart.x),
    top:    Math.min(ptr.y, drawStart.y),
    width:  Math.abs(ptr.x - drawStart.x),
    height: Math.abs(ptr.y - drawStart.y)
  })
  fc.renderAll()
}

function onFabricUp(e) {
  if (currentTool.value !== 'rect' || !isDrawing || !previewObj) return
  isDrawing = false
  const ptr = fc.getPointer(e.e)
  const x1  = Math.round(Math.min(ptr.x, drawStart.x))
  const y1  = Math.round(Math.min(ptr.y, drawStart.y))
  const x2  = Math.round(Math.max(ptr.x, drawStart.x))
  const y2  = Math.round(Math.max(ptr.y, drawStart.y))

  fc.remove(previewObj)
  previewObj = null

  if (Math.abs(x2 - x1) < 5 || Math.abs(y2 - y1) < 5) { fc.renderAll(); return }
  addAnnotation({ type: 'rect', coordinates: [[x1, y1], [x2, y2]] })
}

function onFabricDblClick() {
  if (currentTool.value !== 'polygon') return
  // dblclick fires two single clicks first; remove the extra last point
  polygonPoints.value.pop()
  if (polygonPoints.value.length < 3) {
    cancelPolygon()
    ElMessage.warning('多边形至少需要 3 个点')
    return
  }
  finalizePolygon()
}

// Polygon preview
function updatePolygonPreview() {
  if (polyPreviewCircle) { fc.remove(polyPreviewCircle); polyPreviewCircle = null }
  if (polyPreviewLine)   { fc.remove(polyPreviewLine);   polyPreviewLine   = null }

  const pts   = polygonPoints.value
  const color = activeLabel.value?.color ?? '#ff0000'

  if (pts.length === 0) { fc.renderAll(); return }

  // First-point circle (click target to close)
  polyPreviewCircle = new fabric.Circle({
    left: pts[0][0] - 5, top: pts[0][1] - 5,
    radius: 5, fill: color, opacity: 0.8,
    selectable: false, evented: false
  })
  fc.add(polyPreviewCircle)

  if (pts.length >= 2) {
    polyPreviewLine = new fabric.Polyline(
      pts.map(([x, y]) => ({ x, y })),
      {
        stroke: color, fill: 'transparent',
        strokeWidth: 2, strokeUniform: true,
        strokeDashArray: [6, 3],
        selectable: false, evented: false
      }
    )
    fc.add(polyPreviewLine)
  }
  fc.renderAll()
}

function cancelPolygon() {
  polygonPoints.value = []
  if (polyPreviewCircle) { fc.remove(polyPreviewCircle); polyPreviewCircle = null }
  if (polyPreviewLine)   { fc.remove(polyPreviewLine);   polyPreviewLine   = null }
  fc?.renderAll()
}

function finalizePolygon() {
  const pts = polygonPoints.value.map(([x, y]) => [Math.round(x), Math.round(y)])
  cancelPolygon()
  addAnnotation({ type: 'polygon', coordinates: pts })
}

// Brush: Fabric's free drawing produces a Path on 'path:created'
function onPathCreated(e) {
  if (currentTool.value !== 'brush' || !activeLabel.value) return
  const path = e.path
  fc.remove(path)  // replace with our polyline

  // Extract sampled points from SVG path commands
  const points = []
  for (const cmd of (path.path ?? [])) {
    if (cmd[0] === 'M' || cmd[0] === 'L') {
      points.push([Math.round(cmd[1]), Math.round(cmd[2])])
    } else if (cmd[0] === 'Q') {
      // Quadratic bezier → use endpoint
      points.push([Math.round(cmd[3]), Math.round(cmd[4])])
    }
  }
  if (points.length < 2) { fc.renderAll(); return }

  addAnnotation({
    type:        'brush',
    coordinates: [{ points, size: brushSize.value }]
  })
}

// ── Annotation CRUD ───────────────────────────────────────────────────────────
function addAnnotation(data) {
  if (!activeLabel.value) { ElMessage.warning('请先选择一个标签'); return }
  const anno = {
    id:          null,
    _localId:    `local_${++localIdCtr}`,
    type:         data.type,
    labelId:      activeLabelId.value,
    labelColor:   activeLabel.value.color,
    coordinates:  data.coordinates
  }
  annotations.value.push(anno)
  renderAnnotation(anno)
  fc.renderAll()
  pushHistory()
  isDirty.value = true
}

async function deleteAnnotation(annoId) {
  const idx = annotations.value.findIndex(a => (a._localId ?? String(a.id)) === annoId)
  if (idx === -1) return
  const deletedLabelId = annotations.value[idx].labelId
  annotations.value.splice(idx, 1)
  fc.getObjects().filter(o => o._annoId === annoId).forEach(o => fc.remove(o))
  fc.discardActiveObject()
  fc.renderAll()
  pushHistory()
  isDirty.value = true

  // 若该标签下已无任何标注，自动删除左侧标签
  if (deletedLabelId && !annotations.value.some(a => a.labelId === deletedLabelId)) {
    try {
      await annotationApi.deleteLabel(deletedLabelId)
    } catch { /* 忽略服务端错误，本地也要清除 */ }
    labels.value = labels.value.filter(l => l.id !== deletedLabelId)
    if (activeLabelId.value === deletedLabelId) {
      activeLabelId.value = labels.value[0]?.id ?? null
    }
  }
}

// ── Undo / redo ───────────────────────────────────────────────────────────────
function pushHistory() {
  history.value = history.value.slice(0, historyIndex.value + 1)
  history.value.push(deepClone(annotations.value))
  if (history.value.length > MAX_HISTORY + 1) history.value.shift()
  historyIndex.value = history.value.length - 1
}

function undo() {
  if (historyIndex.value <= 0) return
  historyIndex.value--
  restoreHistory()
}

function redo() {
  if (historyIndex.value >= history.value.length - 1) return
  historyIndex.value++
  restoreHistory()
}

function restoreHistory() {
  annotations.value = deepClone(history.value[historyIndex.value])
  renderAllAnnotations()
  isDirty.value = true
}

// ── Save ──────────────────────────────────────────────────────────────────────
async function saveAnnotations() {
  if (saving.value) return
  saving.value = true
  try {
    const payload = annotations.value.map(a => ({
      id:          a.id ?? null,
      annoType:    a.type,
      labelId:     a.labelId,
      coordinates: JSON.stringify(a.coordinates)
    }))
    const res   = await annotationApi.saveBatch(slideId, payload)
    const saved = (res.data ?? []).map(normalizeAnno)
    annotations.value = saved
    history.value[historyIndex.value] = deepClone(saved)
    isDirty.value = false
    ElMessage.success('标注已保存')
  } catch {
    // Error shown by axios interceptor
  } finally {
    saving.value = false
  }
}

// ── Mark Done ─────────────────────────────────────────────────────────────────
async function markDone() {
  if (isDirty.value) {
    await saveAnnotations()
  }
  try {
    await ElMessageBox.confirm(
      '确认将该切片标记为已完成？完成后仍可继续编辑标注。',
      '标记完成',
      { type: 'info', confirmButtonText: '确认', cancelButtonText: '取消' }
    )
  } catch { return }
  completing.value = true
  try {
    await annotationApi.markComplete(slideId)
    slide.value.annoStatus = 'DONE'
    ElMessage.success('已标记为完成')
  } catch { /* handled by interceptor */ } finally {
    completing.value = false
  }
}

// ── Label CRUD ────────────────────────────────────────────────────────────────
async function onAddLabel({ name, color }) {
  try {
    const res      = await annotationApi.createLabel(slideId, { name, color })
    const newLabel = res.data
    labels.value.push(newLabel)
    activeLabelId.value = newLabel.id
  } catch { /* handled */ }
}

async function onDeleteLabel(labelId) {
  const hasAnno = annotations.value.some(a => a.labelId === labelId)
  if (hasAnno) {
    try {
      await ElMessageBox.confirm(
        '删除该标签将同时删除其所有标注，是否继续？',
        '确认删除',
        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
      )
    } catch { return }
  }
  try {
    await annotationApi.deleteLabel(labelId)
    labels.value = labels.value.filter(l => l.id !== labelId)
    if (activeLabelId.value === labelId) {
      activeLabelId.value = labels.value[0]?.id ?? null
    }
    annotations.value = annotations.value.filter(a => a.labelId !== labelId)
    renderAllAnnotations()
    pushHistory()
    isDirty.value = true
  } catch { /* handled */ }
}

// ── Context menu ──────────────────────────────────────────────────────────────
function closeContextMenu() {
  contextMenu.value.visible = false
}

function onCanvasContextMenu(e) {
  e.preventDefault()
  if (currentTool.value !== 'select') return
  const target = fc?.findTarget(e)
  if (!target?._annoId) { closeContextMenu(); return }
  const anno = annotations.value.find(a => (a._localId ?? String(a.id)) === target._annoId)
  contextMenu.value = {
    visible: true,
    x: e.clientX,
    y: e.clientY,
    annoId: target._annoId,
    labelId: anno?.labelId ?? null
  }
}

function onCtxDelete() {
  const { annoId } = contextMenu.value
  closeContextMenu()
  if (annoId) deleteAnnotation(annoId)
}

async function onCtxChangeLabel(newLabelId) {
  const { annoId, labelId: oldLabelId } = contextMenu.value
  closeContextMenu()
  if (!annoId || newLabelId === oldLabelId) return
  const anno = annotations.value.find(a => (a._localId ?? String(a.id)) === annoId)
  if (!anno) return
  anno.labelId     = newLabelId
  anno.labelColor  = labels.value.find(l => l.id === newLabelId)?.color ?? anno.labelColor
  activeLabelId.value = newLabelId
  renderAllAnnotations()
  if (oldLabelId && !annotations.value.some(a => a.labelId === oldLabelId)) {
    try { await annotationApi.deleteLabel(oldLabelId) } catch {}
    labels.value = labels.value.filter(l => l.id !== oldLabelId)
    if (activeLabelId.value === oldLabelId) activeLabelId.value = labels.value[0]?.id ?? null
  }
  pushHistory()
  isDirty.value = true
}

// ── Keyboard shortcuts ────────────────────────────────────────────────────────
function onKeyDown(e) {
  // Ignore when typing in an input / textarea
  const tag = document.activeElement?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA') return

  // Ctrl combos
  if (e.ctrlKey) {
    if (e.key === 'z') { e.preventDefault(); undo();             return }
    if (e.key === 'y') { e.preventDefault(); redo();             return }
    if (e.key === 's') { e.preventDefault(); saveAnnotations();  return }
    return
  }

  // Escape
  if (e.key === 'Escape') {
    if (currentTool.value === 'polygon') { cancelPolygon(); return }
    setTool('pan')
    return
  }

  // Delete selected annotation
  if (e.key === 'Delete' || e.key === 'Backspace') {
    const active = fc?.getActiveObject()
    if (active?._annoId) { deleteAnnotation(active._annoId); return }
  }

  // Tool shortcuts (single key, no modifiers)
  switch (e.key.toLowerCase()) {
    case 'v': setTool('pan');     return
    case 's': setTool('select');  return
    case 'b': setTool('brush');   return
    case 'r': setTool('rect');    return
    case 'p': setTool('polygon'); return
  }

  // Viewer navigation
  if (!viewer) return
  switch (e.key) {
    case '+': case '=': e.preventDefault(); zoomIn();  break
    case '-':           e.preventDefault(); zoomOut(); break
    case 'h': case 'H': e.preventDefault(); zoomFit(); break
    case 'ArrowLeft':  e.preventDefault(); viewer.viewport.panBy(new OpenSeadragon.Point(-0.05, 0));  break
    case 'ArrowRight': e.preventDefault(); viewer.viewport.panBy(new OpenSeadragon.Point(0.05, 0));   break
    case 'ArrowUp':    e.preventDefault(); viewer.viewport.panBy(new OpenSeadragon.Point(0, -0.05));  break
    case 'ArrowDown':  e.preventDefault(); viewer.viewport.panBy(new OpenSeadragon.Point(0, 0.05));   break
  }
}

// ── Navigation ────────────────────────────────────────────────────────────────
function goBack() {
  if (slide.value?.workspaceId) {
    router.push({ name: 'fileBrowser', params: { id: slide.value.workspaceId } })
  } else {
    router.back()
  }
}

// ── Zoom ──────────────────────────────────────────────────────────────────────
function zoomIn()  { viewer?.viewport.zoomBy(2.0) }
function zoomOut() { viewer?.viewport.zoomBy(0.5) }
function zoomFit() { viewer?.viewport.goHome() }

// ── Utilities ─────────────────────────────────────────────────────────────────
function hexToRgba(hex, alpha) {
  const m = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex)
  if (!m) return `rgba(255,0,0,${alpha})`
  return `rgba(${parseInt(m[1], 16)},${parseInt(m[2], 16)},${parseInt(m[3], 16)},${alpha})`
}

function deepClone(obj) {
  return JSON.parse(JSON.stringify(obj))
}

/** Parse annotation from server: coordinates may arrive as a JSON string. */
function normalizeAnno(a) {
  return {
    ...a,
    type:        a.type ?? a.annoType,
    _localId:    a._localId ?? `srv_${a.id}`,
    coordinates: typeof a.coordinates === 'string'
      ? JSON.parse(a.coordinates)
      : (a.coordinates ?? [])
  }
}
</script>

<style scoped>
/* ── Page layout ──────────────────────────────────────────────────── */
.viewer-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #1a1a1a;
  overflow: hidden;
  outline: none;
}

/* ── Header ───────────────────────────────────────────────────────── */
.viewer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 16px;
  background: rgba(20, 20, 20, 0.96);
  border-bottom: 1px solid #333;
  flex-shrink: 0;
  gap: 10px;
  z-index: 20;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.back-btn       { color: #ccc !important; flex-shrink: 0; }
.back-btn:hover { color: #fff !important; }

:deep(.el-divider--vertical) {
  border-left-color: #555;
  height: 16px;
}

.slide-name {
  font-size: 13px;
  font-weight: 500;
  color: #e0e0e0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 220px;
}

.header-tools {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.zoom-badge {
  font-size: 12px;
  color: #ccc;
  min-width: 50px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

/* Dark button group */
:deep(.el-button-group .el-button),
.header-tools :deep(.el-button:not(.el-button--primary):not(.el-button--warning)) {
  background: #2c2c2c;
  border-color: #444;
  color: #ccc;
}
:deep(.el-button-group .el-button:hover),
.header-tools :deep(.el-button:not(.el-button--primary):not(.el-button--warning):hover) {
  background: #3a3a3a;
  color: #fff;
}

/* ── Body ─────────────────────────────────────────────────────────── */
.viewer-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* ── Viewer center ────────────────────────────────────────────────── */
.viewer-center {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.osd-container {
  position: absolute;
  inset: 0;
  background: #111;
}

:deep(.openseadragon-container) {
  width:  100% !important;
  height: 100% !important;
}

:deep(.navigator) {
  border: 1px solid #444 !important;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.6) !important;
  bottom: 48px !important;
  right: 8px !important;
}

/* ── Fabric overlay ───────────────────────────────────────────────── */
.fabric-wrapper {
  position: absolute;
  inset: 0;
  pointer-events: none;   /* transparent when pan mode */
  z-index: 5;
}
.fabric-wrapper.tool-active {
  pointer-events: auto;   /* capture events for drawing/select modes */
}

:deep(.canvas-container) {
  width:  100% !important;
  height: 100% !important;
}

/* ── Overlays ─────────────────────────────────────────────────────── */
.loading-overlay,
.error-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(17, 17, 17, 0.9);
  color: #ccc;
  font-size: 15px;
  z-index: 30;
}

.loading-spin {
  font-size: 40px;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

.polygon-hint {
  position: absolute;
  bottom: 10px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.75);
  color: #ddd;
  font-size: 12px;
  padding: 4px 14px;
  border-radius: 12px;
  pointer-events: none;
  z-index: 10;
  white-space: nowrap;
}

/* ── Coordinate bar ───────────────────────────────────────────────── */
.coord-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 5px 14px;
  background: rgba(20, 20, 20, 0.96);
  border-top: 1px solid #333;
  flex-shrink: 0;
  font-size: 12px;
  color: #999;
}

.coord-bar strong {
  color: #ddd;
  font-variant-numeric: tabular-nums;
  min-width: 44px;
  display: inline-block;
}

.coord-bar :deep(.el-icon)              { color: #666; }
.coord-bar :deep(.el-divider--vertical) { border-left-color: #444; margin: 0 2px; }

.shortcut-hint {
  color: #555;
  margin-left: auto;
  font-size: 11px;
}
</style>

<style>
/* ── Annotation right-click context menu (teleported to body) ──── */
.anno-context-menu {
  position: fixed;
  z-index: 9999;
  min-width: 160px;
  background: #1e1e1e;
  border: 1px solid #444;
  border-radius: 6px;
  padding: 4px 0;
  box-shadow: 0 4px 16px rgba(0,0,0,0.5);
  user-select: none;
}

.ctx-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 14px;
  font-size: 13px;
  color: #ddd;
  cursor: pointer;
  transition: background 0.12s;
}
.ctx-item:hover      { background: rgba(255,255,255,0.08); }
.ctx-item.ctx-danger { color: #f56c6c; }
.ctx-item.ctx-danger:hover { background: rgba(245,108,108,0.12); }
.ctx-item.ctx-active { background: rgba(64,158,255,0.15); color: #409eff; }

.ctx-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  border: 1px solid rgba(255,255,255,0.2);
}

.ctx-divider {
  height: 1px;
  background: #333;
  margin: 3px 0;
}

.ctx-subtitle {
  padding: 4px 14px 2px;
  font-size: 11px;
  color: #666;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
</style>
