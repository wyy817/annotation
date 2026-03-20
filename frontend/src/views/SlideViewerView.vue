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
          <el-tooltip content="选择/删除标注" placement="bottom">
            <el-button size="small" :type="currentTool==='select' ? 'primary' : ''" @click="setTool('select')">选择</el-button>
          </el-tooltip>
        </el-button-group>

        <el-divider direction="vertical" />

        <!-- Draw tools (annotator only) -->
        <template v-if="!isReviewer">
          <el-button-group>
            <el-tooltip content="画笔 (B)" placement="bottom">
              <el-button size="small" :type="currentTool==='brush' ? 'primary' : ''" @click="setTool('brush')">画笔</el-button>
            </el-tooltip>
            <el-tooltip content="推拉边缘 (N) Alt+滚轮调整大小" placement="bottom">
              <el-button size="small" :type="currentTool==='nudge' ? 'primary' : ''" @click="setTool('nudge')">推拉</el-button>
            </el-tooltip>
            <el-tooltip content="橡皮擦 (E) Alt+滚轮调整大小" placement="bottom">
              <el-button size="small" :type="currentTool==='eraser' ? 'primary' : ''" @click="setTool('eraser')">橡皮擦</el-button>
            </el-tooltip>
            <el-tooltip content="点标注 (P)" placement="bottom">
              <el-button size="small" :type="currentTool==='point' ? 'primary' : ''" @click="setTool('point')">点</el-button>
            </el-tooltip>
            <el-tooltip content="AI 辅助分割 (M)" placement="bottom">
              <el-button size="small" :type="currentTool==='sam' ? 'primary' : ''" @click="setTool('sam')">
                <el-icon><MagicStick /></el-icon>
              </el-button>
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
        </template>

        <!-- Annotator: submit for review -->
        <template v-if="!isReviewer">
          <el-button
            v-if="slide?.reviewStatus === 'PENDING_REVIEW'"
            size="small"
            type="warning"
            disabled
          >待审核中</el-button>
          <el-button
            v-else
            size="small"
            :loading="completing"
            :disabled="slide?.annoStatus === 'NOT_STARTED'"
            :type="slide?.reviewStatus === 'REJECTED' ? 'warning' : slide?.reviewStatus === 'APPROVED' ? 'success' : 'default'"
            @click="markDone"
          >{{ slide?.reviewStatus === 'REJECTED' ? '重新提交' : slide?.reviewStatus === 'APPROVED' ? '重新提交审核' : '提交审核' }}</el-button>
        </template>

        <!-- Reviewer: approve / reject -->
        <template v-if="isReviewer && slide?.reviewStatus === 'PENDING_REVIEW'">
          <el-divider direction="vertical" />
          <el-button size="small" type="success" :loading="reviewing === 'approve'" @click="handleApprove">通过</el-button>
          <el-button size="small" type="danger"  :loading="reviewing === 'reject'"  @click="handleRejectPrompt">拒绝</el-button>
        </template>
      </div>

      <!-- Right: export + zoom controls -->
      <div class="header-right">
        <template v-if="!isReviewer">
          <el-tooltip content="导入标注 (JSON)" placement="bottom">
            <el-button
              size="small"
              :icon="Upload"
              :disabled="loading || !!errorMsg"
              @click="showImportDialog = true"
            >导入</el-button>
          </el-tooltip>
          <el-divider direction="vertical" />
        </template>
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

    <!-- ── Rejection banner (annotator only) ─────────────────────────────── -->
    <div
      v-if="!isReviewer && slide?.reviewStatus === 'REJECTED' && slide?.reviewerComment"
      class="reject-banner"
    >
      <el-icon style="margin-right:6px"><WarningFilled /></el-icon>
      <strong>审核未通过：</strong>{{ slide.reviewerComment }}
    </div>

    <!-- ── Export dialog ──────────────────────────────────────────────────── -->
    <ExportDialog v-model="showExportDialog" :slide-id="slideId" />
    <ImportDialog v-model="showImportDialog" :slide-id="slideId" @imported="onImported" />

    <!-- ── Version history drawer ─────────────────────────────────────────── -->
    <el-drawer
      v-model="showVersionDrawer"
      title="版本历史"
      direction="rtl"
      size="360px"
      append-to-body
    >
      <div v-if="versionsLoading" class="ver-loading">
        <el-icon class="loading-spin"><Loading /></el-icon>
        <span>加载中...</span>
      </div>
      <div v-else-if="versions.length === 0" class="ver-empty">
        暂无版本记录
      </div>
      <el-timeline v-else>
        <el-timeline-item
          v-for="ver in versions"
          :key="ver.id"
          :type="triggerType[ver.trigger] || ''"
          :timestamp="formatTime(ver.createdAt)"
          placement="top"
        >
          <div class="ver-card">
            <div class="ver-title">
              <el-tag :type="triggerType[ver.trigger] || ''" size="small" effect="plain">
                {{ triggerLabel[ver.trigger] ?? ver.trigger }}
              </el-tag>
              <span class="ver-num">v{{ ver.versionNumber }}</span>
              <el-button
                size="small"
                :type="ghostVersionNum === ver.versionNumber ? 'warning' : ''"
                :loading="ghostLoading && ghostVersionNum === ver.versionNumber"
                plain
                class="ver-compare-btn"
                @click="loadGhostVersion(ver)"
              >{{ ghostVersionNum === ver.versionNumber ? '对比中' : '查看对比' }}</el-button>
            </div>
            <div v-if="ver.reviewerComment" class="ver-comment">
              "{{ ver.reviewerComment }}"
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-drawer>

    <!-- ── Audit log drawer ───────────────────────────────────────────────── -->
    <el-drawer
      v-model="showAuditDrawer"
      title="操作历史"
      direction="rtl"
      size="400px"
      append-to-body
    >
      <div v-if="auditLoading" class="ver-loading">
        <el-icon class="loading-spin"><Loading /></el-icon>
        <span>加载中...</span>
      </div>
      <div v-else-if="auditLogs.length === 0" class="ver-empty">暂无操作记录</div>
      <el-timeline v-else>
        <el-timeline-item
          v-for="log in auditLogs"
          :key="log.id"
          :type="auditActionType[log.action] || ''"
          :timestamp="formatTime(log.createdAt)"
          placement="top"
        >
          <div class="ver-card">
            <div class="ver-title">
              <el-tag :type="auditActionType[log.action] || ''" size="small" effect="plain">
                {{ auditActionLabel[log.action] ?? log.action }}
              </el-tag>
              <span style="margin-left:8px;font-size:12px;color:#606266">{{ log.username }}</span>
            </div>
            <div v-if="log.detail" class="ver-comment">{{ formatAuditDetail(log.detail) }}</div>
          </div>
        </el-timeline-item>
      </el-timeline>
      <div v-if="!auditLoading && auditHasMore" style="text-align:center;margin-top:12px">
        <el-button size="small" :loading="auditLoadingMore" @click="loadMoreAudit">加载更多</el-button>
      </div>
    </el-drawer>

    <!-- ── Shortcut help dialog ────────────────────────────────────────── -->
    <el-dialog v-model="showShortcutHelp" title="快捷键帮助" width="520px" append-to-body>
      <div class="shortcut-help-grid">
        <div class="shortcut-group">
          <h4>工具切换</h4>
          <div class="shortcut-row"><kbd>V</kbd><span>平移 / 查看</span></div>
          <div class="shortcut-row"><kbd>B</kbd><span>画笔</span></div>
          <div class="shortcut-row"><kbd>N</kbd><span>推拉边缘</span></div>
          <div class="shortcut-row"><kbd>E</kbd><span>橡皮擦</span></div>
          <div class="shortcut-row"><kbd>P</kbd><span>点标注</span></div>
          <div class="shortcut-row"><kbd>M</kbd><span>AI 辅助分割（点/框模式）</span></div>
          <div class="shortcut-row"><kbd>右键</kbd><span>AI模式：添加背景排除点（红）</span></div>
          <div class="shortcut-row"><kbd>Esc</kbd><span>返回平移模式</span></div>
        </div>
        <div class="shortcut-group">
          <h4>编辑操作</h4>
          <div class="shortcut-row"><kbd>Ctrl + Z</kbd><span>撤销</span></div>
          <div class="shortcut-row"><kbd>Ctrl + Y</kbd><span>重做</span></div>
          <div class="shortcut-row"><kbd>Ctrl + S</kbd><span>保存</span></div>
          <div class="shortcut-row"><kbd>Delete</kbd><span>删除选中标注</span></div>
          <div class="shortcut-row"><kbd>Alt + 滚轮</kbd><span>调整画笔/橡皮擦大小</span></div>
        </div>
        <div class="shortcut-group">
          <h4>视图导航</h4>
          <div class="shortcut-row"><kbd>W A S D</kbd><span>平移画布</span></div>
          <div class="shortcut-row"><kbd>方向键</kbd><span>平移画布</span></div>
          <div class="shortcut-row"><kbd>+ / -</kbd><span>放大 / 缩小</span></div>
          <div class="shortcut-row"><kbd>H</kbd><span>适合屏幕</span></div>
          <div class="shortcut-row"><kbd>?</kbd><span>打开此帮助</span></div>
        </div>
      </div>
    </el-dialog>

    <!-- ── Body ───────────────────────────────────────────────────────────── -->
    <div class="viewer-body">
      <!-- Left: label panel (hidden for reviewers) -->
      <LabelPanel
        ref="labelPanelRef"
        v-if="!loading && !errorMsg && !isReviewer"
        :labels="labels"
        :activeId="activeLabelId"
        :brushSize="brushSize"
        :showBrushSize="currentTool === 'brush'"
        :nudgeSize="nudgeSize"
        :nudgeStrength="nudgeStrength"
        :showNudgeSize="currentTool === 'nudge'"
        :eraserSize="eraserSize"
        :eraserMode="eraserMode"
        :showEraserSize="currentTool === 'eraser'"
        :pointSize="pointScreenR"
        :showPointSize="currentTool === 'point'"
        :editingId="pendingEditLabelId"
        @select="activeLabelId = (activeLabelId === $event ? null : $event)"
        @add="onAddLabel"
        @delete="onDeleteLabel"
        @brush-size="brushSize = $event"
        @nudge-size="nudgeSize = $event"
        @nudge-strength="nudgeStrength = $event"
        @eraser-size="eraserSize = $event"
        @eraser-mode="eraserMode = $event"
        @point-size="pointScreenR = $event"
        @rename="onRenameLabel"
        @color-change="onColorChangeLabel"
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


        <!-- ── Right floating dock ──────────────────────────────── -->
        <div class="right-dock" v-if="!loading && !errorMsg">
          <NotificationBell />
          <div class="dock-divider" />
          <el-tooltip content="版本历史" placement="left">
            <button class="dock-btn" @click="openVersionHistory">
              <el-icon><Clock /></el-icon>
            </button>
          </el-tooltip>
          <el-tooltip content="操作历史" placement="left">
            <button class="dock-btn" @click="openAuditLog">
              <el-icon><Document /></el-icon>
            </button>
          </el-tooltip>
          <div class="dock-divider" />
          <el-tooltip content="快捷键帮助 (?)" placement="left">
            <button class="dock-btn" @click="showShortcutHelp = true">
              <el-icon><QuestionFilled /></el-icon>
            </button>
          </el-tooltip>
        </div>

        <!-- SAM action bar -->
        <Transition name="sam-fade">
          <div
            v-if="currentTool === 'sam' && !loading && !errorMsg && !isReviewer"
            class="sam-action-bar"
          >
            <!-- Mode toggle -->
            <template v-if="!samLoading">
              <el-button-group size="small" style="margin-right:4px">
                <el-tooltip content="点提示：左键目标，右键背景" placement="top">
                  <el-button :type="samMode==='point' ? 'primary' : ''" @click="setSamMode('point')">点</el-button>
                </el-tooltip>
                <el-tooltip content="框提示：拖拽框选目标区域" placement="top">
                  <el-button :type="samMode==='box' ? 'primary' : ''" @click="setSamMode('box')">框</el-button>
                </el-tooltip>
              </el-button-group>
              <el-divider direction="vertical" style="border-color:#555;margin:0 6px;" />
            </template>

            <template v-if="samLoading">
              <el-icon class="loading-spin" style="font-size:14px"><Loading /></el-icon>
              <span class="sam-status-text">AI 推理中...</span>
            </template>
            <template v-else-if="samPreview">
              <span class="sam-status-text">置信度 {{ (samPreview.score * 100).toFixed(1) }}%</span>
              <el-divider direction="vertical" style="border-color:#555;margin:0 4px;" />
              <el-button size="small" type="success" @click="samAccept">✓ 接受</el-button>
              <el-button size="small" type="danger" plain @click="samReject">✗ 拒绝</el-button>
              <el-button size="small" plain @click="samClear">重选</el-button>
              <el-divider direction="vertical" style="border-color:#555;margin:0 4px;" />
              <span class="sam-status-text" style="opacity:0.6">继续{{ samMode==='box' ? '拖拽' : '点击' }}可精化</span>
            </template>
            <template v-else-if="samHasPrompts">
              <span class="sam-status-text">
                <span v-if="samPoints.filter(p=>p.label===1).length > 0" style="color:#4caf50">● {{ samPoints.filter(p=>p.label===1).length }}</span>
                <span v-if="samPoints.filter(p=>p.label===0).length > 0" style="color:#f44336;margin-left:6px">● {{ samPoints.filter(p=>p.label===0).length }}</span>
                <span v-if="samBox" style="margin-left:6px;color:#ffc107">□ 框</span>
              </span>
              <el-button size="small" type="danger" plain style="margin-left:6px" @click="samClear">清除</el-button>
            </template>
            <template v-else>
              <span v-if="samMode==='point'" class="sam-status-text">左键目标点（绿）&nbsp;右键背景点（红）&nbsp;多次点击精化</span>
              <span v-else class="sam-status-text">拖拽框选目标区域，可配合点提示精化</span>
            </template>
          </div>
        </Transition>

        <!-- Ghost comparison badge -->
        <Transition name="sam-fade">
          <div v-if="ghostVersionNum !== null && !loading && !errorMsg" class="ghost-badge">
            <span class="ghost-badge-text">当前 vs v{{ ghostVersionNum }}</span>
            <el-divider direction="vertical" style="border-color:rgba(255,140,0,0.4);margin:0 8px;" />
            <el-button size="small" plain class="ghost-close-btn" @click="clearGhost">关闭对比</el-button>
          </div>
        </Transition>

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
        Del 删除选中 &nbsp;|&nbsp; B 画笔 &nbsp;|&nbsp; P 点标注 &nbsp;|&nbsp; M AI辅助 &nbsp;|&nbsp; E 橡皮擦 &nbsp;|&nbsp; Ctrl+Z/Y 撤销/重做 &nbsp;|&nbsp; Ctrl+S 保存
      </span>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, Plus, Minus, Loading, WarningFilled, Aim,
  RefreshLeft, RefreshRight, Download, Upload, Delete, QuestionFilled, MagicStick, Clock, Document
} from '@element-plus/icons-vue'
import OpenSeadragon from 'openseadragon'
import { fabric } from 'fabric'
import { slideApi } from '@/api/slide'
import { annotationApi } from '@/api/annotation'
import { reviewApi } from '@/api/review'
import { samApi } from '@/api/sam'
import { auditLogApi } from '@/api/auditLog'
import { useUserStore } from '@/stores/userStore'
import LabelPanel from '@/components/LabelPanel.vue'
import ExportDialog from '@/components/ExportDialog.vue'
import ImportDialog from '@/components/ImportDialog.vue'
import NotificationBell from '@/components/NotificationBell.vue'

const route      = useRoute()
const router     = useRouter()
const slideId    = Number(route.params.id)
const userStore  = useUserStore()
const isReviewer = computed(() => userStore.isReviewer)

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
const activeLabelId      = ref(null)
const pendingEditLabelId = ref(null)   // triggers inline edit in LabelPanel
const annotations   = ref([])
const brushSize     = ref(20)
const nudgeSize     = ref(30)          // nudge brush radius in image pixels
const nudgeStrength = ref(0.35)        // displacement factor (0–1)
const eraserSize    = ref(20)          // eraser radius in image pixels
const eraserMode    = ref('pixel')     // 'pixel' or 'stroke'
const pointScreenR  = ref(7)           // desired screen radius (px) for point markers
let tempLabelIdCtr  = 0               // counter for local-only label IDs (negative)
const currentTool   = ref('pan')
const isDirty       = ref(false)
const saving        = ref(false)
const completing    = ref(false)
const reviewing     = ref(false)   // reviewer approve/reject loading

// ── Undo / redo ───────────────────────────────────────────────────────────────
const MAX_HISTORY  = 20
const history      = ref([{ annotations: [], labels: [] }])
const historyIndex = ref(0)

// ── Export / Import ───────────────────────────────────────────────────────────
const showExportDialog = ref(false)
const showImportDialog = ref(false)
const showShortcutHelp = ref(false)

// ── Version history ───────────────────────────────────────────────────────────
const showVersionDrawer  = ref(false)
const versions           = ref([])
const versionsLoading    = ref(false)
const ghostAnnotations   = ref([])
const ghostLabels        = ref([])
const ghostVersionNum    = ref(null)   // version number currently shown as ghost, null = none
const ghostLoading       = ref(false)

const triggerLabel = {
  SUBMIT:   '首次提交',
  RESUBMIT: '重新提交',
  REJECT:   '被拒绝',
  APPROVE:  '已通过'
}
const triggerType = {
  SUBMIT:   '',
  RESUBMIT: 'warning',
  REJECT:   'danger',
  APPROVE:  'success'
}

// ── Audit log ─────────────────────────────────────────────────────────────────
const showAuditDrawer  = ref(false)
const auditLogs        = ref([])
const auditLoading     = ref(false)
const auditLoadingMore = ref(false)
const auditPage        = ref(0)
const auditHasMore     = ref(false)

const auditActionLabel = {
  SAVE:     '保存标注',
  SUBMIT:   '提交审核',
  RESUBMIT: '重新提交',
  APPROVE:  '审核通过',
  REJECT:   '审核拒绝',
  IMPORT:   '导入标注'
}
const auditActionType = {
  SAVE:     '',
  SUBMIT:   'primary',
  RESUBMIT: 'warning',
  APPROVE:  'success',
  REJECT:   'danger',
  IMPORT:   'info'
}

function formatAuditDetail(detail) {
  try {
    const obj = typeof detail === 'string' ? JSON.parse(detail) : detail
    return Object.entries(obj).map(([k, v]) => `${k}: ${v}`).join('  |  ')
  } catch {
    return detail
  }
}

async function openAuditLog() {
  showAuditDrawer.value = true
  auditLogs.value = []
  auditPage.value = 0
  auditHasMore.value = false
  auditLoading.value = true
  try {
    const res = await auditLogApi.list({ slideId, page: 0, size: 20 })
    const page = res.data
    auditLogs.value = page.records ?? []
    auditHasMore.value = auditLogs.value.length < page.total
    auditPage.value = 1
  } finally {
    auditLoading.value = false
  }
}

async function loadMoreAudit() {
  auditLoadingMore.value = true
  try {
    const res = await auditLogApi.list({ slideId, page: auditPage.value, size: 20 })
    const page = res.data
    auditLogs.value.push(...(page.records ?? []))
    auditHasMore.value = auditLogs.value.length < page.total
    auditPage.value++
  } finally {
    auditLoadingMore.value = false
  }
}

// Reload labels + annotations from server after a successful import
async function onImported() {
  try {
    const [lblRes, annoRes] = await Promise.all([
      annotationApi.listLabels(slideId),
      annotationApi.list(slideId)
    ])
    labels.value      = lblRes.data  ?? []
    annotations.value = (annoRes.data ?? []).map(normalizeAnno)
    if (labels.value.length > 0 && !activeLabelId.value)
      activeLabelId.value = labels.value[0].id
    history.value      = [{ annotations: deepClone(annotations.value), labels: deepClone(labels.value) }]
    historyIndex.value = 0
    isDirty.value      = false
    renderAllAnnotations()
  } catch (e) {
    console.error('刷新标注失败', e)
  }
}

async function openVersionHistory() {
  showVersionDrawer.value = true
  if (versions.value.length > 0) return
  versionsLoading.value = true
  try {
    const res = await annotationApi.listVersions(slideId)
    versions.value = res.data ?? []
  } catch {
    ElMessage.error('加载版本历史失败')
  } finally {
    versionsLoading.value = false
  }
}

function formatTime(ts) {
  if (!ts) return ''
  const d = new Date(ts)
  return d.toLocaleString('zh-CN', { hour12: false })
}

async function loadGhostVersion(ver) {
  // Toggle off if already showing this version
  if (ghostVersionNum.value === ver.versionNumber) {
    clearGhost()
    return
  }
  ghostLoading.value = true
  ghostVersionNum.value = ver.versionNumber
  try {
    const res = await annotationApi.getVersionAnnotations(slideId, ver.id)
    const data = res.data ?? {}
    ghostAnnotations.value = (data.annotations ?? []).map(normalizeAnno)
    ghostLabels.value = data.labels ?? []
    renderGhostAnnotations()
  } catch {
    ElMessage.error('加载版本快照失败')
    ghostVersionNum.value = null
  } finally {
    ghostLoading.value = false
  }
}

function renderGhostAnnotations() {
  if (!fc) return
  fc.getObjects().filter(o => o._isGhost).forEach(o => fc.remove(o))
  if (ghostAnnotations.value.length === 0) { fc.renderAll(); return }

  const GHOST = '#ff8c00'
  for (const anno of ghostAnnotations.value) {
    const base = {
      stroke:         GHOST,
      strokeWidth:    2,
      strokeDashArray:[6, 4],
      strokeUniform:  true,
      selectable:     false,
      evented:        false,
      hasControls:    false,
      hasBorders:     false,
      opacity:        0.7,
      _isGhost:       true,
    }

    if (anno.type === 'rect') {
      const [[x1, y1], [x2, y2]] = anno.coordinates
      fc.add(new fabric.Rect({
        ...base,
        left:   x1,
        top:    y1,
        width:  Math.abs(x2 - x1),
        height: Math.abs(y2 - y1),
        fill:   hexToRgba(GHOST, 0.15),
      }))
    } else if (anno.type === 'polygon') {
      const svgPath = smoothPolygonToSvgPath(anno.coordinates)
      if (svgPath) {
        fc.add(new fabric.Path(svgPath, { ...base, fill: 'transparent' }))
      } else {
        fc.add(new fabric.Polyline(
          anno.coordinates.map(([x, y]) => ({ x, y })),
          { ...base, fill: 'transparent' }
        ))
      }
    } else if (anno.type === 'brush') {
      for (const stroke of anno.coordinates) {
        fc.add(new fabric.Polyline(
          stroke.points.map(([x, y]) => ({ x, y })),
          {
            ...base,
            fill:           'transparent',
            strokeWidth:     stroke.size,
            strokeUniform:   false,
            strokeLineCap:  'round',
            strokeLineJoin: 'round',
          }
        ))
      }
    } else if (anno.type === 'point') {
      const [x, y] = anno.coordinates
      const scale = fc.viewportTransform?.[0] ?? 0.01
      const r = Math.max(1, pointScreenR.value / scale)
      fc.add(new fabric.Circle({
        ...base,
        left:            x - r,
        top:             y - r,
        radius:          r,
        fill:            hexToRgba(GHOST, 0.5),
        stroke:          '#ffffff',
        strokeDashArray: null,
      }))
    }
  }
  fc.renderAll()
}

function clearGhost() {
  fc?.getObjects().filter(o => o._isGhost).forEach(o => fc.remove(o))
  fc?.renderAll()
  ghostAnnotations.value = []
  ghostLabels.value = []
  ghostVersionNum.value = null
}

// ── SAM state ─────────────────────────────────────────────────────────────────
const samPoints   = ref([])    // [{ x, y, label }]  label: 1=fg, 0=bg
const samLoading  = ref(false)
const samPreview  = ref(null)  // { polygons: [[[x,y],...]], score: number }
const samMode     = ref('point')  // 'point' | 'box'
const samBox      = ref(null)     // { x1, y1, x2, y2 } in level-0 WSI coords (finalized)
let   samBoxStart = null          // drag start { x, y } (non-reactive)
let   samIsDragging = false

// ── Context menu ──────────────────────────────────────────────────────────────
const contextMenu = ref({ visible: false, x: 0, y: 0, annoId: null, labelId: null })

// ── Non-reactive instances ────────────────────────────────────────────────────
let viewer        = null
let fc            = null
let autoSaveTimer = null
let localIdCtr    = 0

// Nudge tool state
let nudgeCursorCircle = null   // fabric.Circle for brush preview
let nudgeLastPtr      = null   // last pointer position during drag
let isNudging         = false
let nudgeDidChange    = false  // whether any vertex was actually displaced

// Eraser tool state
let eraserCursorCircle = null  // fabric.Circle for eraser preview
let isErasing          = false

// ── Computed ──────────────────────────────────────────────────────────────────
const zoomLabel = computed(() => {
  if (!viewer || !slideWidth.value) return '—'
  const containerW = viewer.viewport.getContainerSize().x
  const mag = (zoomRaw.value * containerW) / slideWidth.value
  return `${Math.round(mag * 100)}%`
})

const labelPanelRef = ref(null)
const activeLabel = computed(() =>
  labels.value.find(l => l.id === activeLabelId.value)
)
const samHasPrompts = computed(() => samPoints.value.length > 0 || samBox.value !== null)

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
    const resp = await fetch(`/api/slides/${slideId}/dzi`, {
      headers: { Authorization: `Bearer ${userStore.token}` }
    })
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
    history.value      = [{ annotations: deepClone(annotations.value), labels: deepClone(labels.value) }]
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
      loadTilesWithAjax:        true,
      ajaxHeaders:              { Authorization: `Bearer ${userStore.token}` },
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
  fc.on('path:created',   onPathCreated)

  fabricEl.value.addEventListener('contextmenu', onCanvasContextMenu)
  fabricWrapper.value.addEventListener('wheel', onCanvasWheel, { passive: false })

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

  // screen offset of image origin (0,0)
  const tx = -(bounds.x * containerW) / bounds.width
  const ty = -(bounds.y * containerW) / bounds.width  // uniform scaling

  fc.setViewportTransform([scale, 0, 0, scale, tx, ty])
  updatePointObjects()
  updateSamPointObjects()
  fc.renderAll()
}

function updatePointObjects() {
  if (!fc) return
  const scale = fc.viewportTransform[0]
  if (!scale || scale <= 0) return
  const r = pointScreenR.value / scale
  fc.forEachObject(o => {
    if (o._isPoint) {
      o.set({ radius: r, left: o._pointX - r, top: o._pointY - r })
      o.setCoords()
    }
  })
}

// ── Tool management ───────────────────────────────────────────────────────────
function setTool(tool) {
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

  } else if (tool === 'nudge') {
    viewer.setMouseNavEnabled(false)
    fc.isDrawingMode = false
    fc.selection     = false
    fc.forEachObject(o => { o.selectable = false; o.evented = false })
    fc.defaultCursor = 'none'
    wrapper.style.pointerEvents = 'auto'
    // Show nudge cursor circle
    updateNudgeCursor(null)

  } else if (tool === 'eraser') {
    viewer.setMouseNavEnabled(false)
    fc.isDrawingMode = false
    fc.selection     = false
    fc.forEachObject(o => { o.selectable = false; o.evented = false })
    fc.defaultCursor = 'none'
    wrapper.style.pointerEvents = 'auto'
    updateEraserCursor(null)

  } else if (tool === 'point') {
    viewer.setMouseNavEnabled(false)
    fc.isDrawingMode = false
    fc.selection     = false
    fc.forEachObject(o => { o.selectable = false; o.evented = false })
    fc.defaultCursor = 'crosshair'
    wrapper.style.pointerEvents = 'auto'

  } else if (tool === 'sam') {
    viewer.setMouseNavEnabled(false)
    fc.isDrawingMode = false
    fc.selection     = false
    fc.forEachObject(o => { o.selectable = false; o.evented = false })
    fc.defaultCursor = 'crosshair'
    wrapper.style.pointerEvents = 'auto'
  }

  // Clean up SAM state when switching away
  if (tool !== 'sam') samClear()

  // Clean up nudge cursor when switching away
  if (tool !== 'nudge' && nudgeCursorCircle) {
    fc.remove(nudgeCursorCircle)
    nudgeCursorCircle = null
  }
  // Clean up eraser cursor when switching away
  if (tool !== 'eraser' && eraserCursorCircle) {
    fc.remove(eraserCursorCircle)
    eraserCursorCircle = null
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
watch(pointScreenR, () => {
  updatePointObjects()
  fc?.renderAll()
})

// ── Annotation rendering ──────────────────────────────────────────────────────
function renderAllAnnotations() {
  if (!fc) return
  fc.clear()
  for (const anno of annotations.value) renderAnnotation(anno)
  renderGhostAnnotations()   // re-add ghost overlay after clear (calls renderAll)
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
    const svgPath = smoothPolygonToSvgPath(anno.coordinates)
    if (svgPath) {
      fc.add(new fabric.Path(svgPath, { ...base, fill: 'transparent' }))
    } else {
      fc.add(new fabric.Polyline(
        anno.coordinates.map(([x, y]) => ({ x, y })),
        { ...base, fill: 'transparent' }
      ))
    }

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

  } else if (anno.type === 'point') {
    const [x, y] = anno.coordinates
    const scale = fc.viewportTransform?.[0] ?? 0.01
    const r = Math.max(1, pointScreenR.value / scale)
    fc.add(new fabric.Circle({
      ...base,
      left:          x - r,
      top:           y - r,
      radius:        r,
      fill:          hexToRgba(color, 0.85),
      stroke:        '#ffffff',
      strokeWidth:   2,
      strokeUniform: true,
      _isPoint:      true,
      _pointX:       x,
      _pointY:       y,
    }))
  }
}

// ── Drawing: mouse events ─────────────────────────────────────────────────────
function onFabricDown(e) {
  const tool = currentTool.value
  const ptr = fc.getPointer(e.e)

  if (tool === 'nudge') { onNudgeDown(ptr); return }
  if (tool === 'eraser') { onEraserDown(ptr); return }
  if (tool === 'point') {
    addAnnotation({ type: 'point', coordinates: [Math.round(ptr.x), Math.round(ptr.y)] })
    return
  }
  if (tool === 'sam') { onSamDown(ptr, e.e.button ?? 0); return }
}

function onFabricMove(e) {
  const tool = currentTool.value
  if (tool === 'nudge') { onNudgeMove(fc.getPointer(e.e)); return }
  if (tool === 'eraser') { onEraserMove(fc.getPointer(e.e)); return }
  if (tool === 'sam') { onSamMove(fc.getPointer(e.e)); return }
}

function onFabricUp(e) {
  if (currentTool.value === 'nudge') { onNudgeUp(); return }
  if (currentTool.value === 'eraser') { onEraserUp(); return }
  if (currentTool.value === 'sam') { onSamUp(fc.getPointer(e.e)); return }
}

// ── Polygon self-intersection removal ────────────────────────────────────────
// After nudge, the polygon can fold on itself. This detects crossing edges
// and removes the loop between them, keeping the shorter path.
function removePolygonSelfIntersections(coords) {
  if (coords.length < 4) return coords

  // Find intersection point of segments (p1->p2) and (p3->p4)
  function segIntersect(p1, p2, p3, p4) {
    const d1x = p2[0] - p1[0], d1y = p2[1] - p1[1]
    const d2x = p4[0] - p3[0], d2y = p4[1] - p3[1]
    const cross = d1x * d2y - d1y * d2x
    if (Math.abs(cross) < 1e-10) return null
    const t = ((p3[0] - p1[0]) * d2y - (p3[1] - p1[1]) * d2x) / cross
    const u = ((p3[0] - p1[0]) * d1y - (p3[1] - p1[1]) * d1x) / cross
    if (t > 0.001 && t < 0.999 && u > 0.001 && u < 0.999) {
      return [Math.round(p1[0] + t * d1x), Math.round(p1[1] + t * d1y)]
    }
    return null
  }

  // Iteratively remove one self-intersection at a time
  let pts = coords.slice()
  let maxIter = 50
  while (maxIter-- > 0) {
    let found = false
    const n = pts.length
    for (let i = 0; i < n && !found; i++) {
      for (let j = i + 2; j < n; j++) {
        if (i === 0 && j === n - 1) continue // adjacent (wrap-around)
        const ip = segIntersect(pts[i], pts[(i + 1) % n], pts[j], pts[(j + 1) % n])
        if (ip) {
          // Two paths: remove i+1..j  or  remove j+1..i (wrapping)
          // Keep the path with fewer removed vertices (shorter loop)
          const loopLen = j - i - 1
          const otherLen = n - j - 1 + i
          if (loopLen <= otherLen) {
            // Remove vertices between i and j, insert intersection point
            pts = [...pts.slice(0, i + 1), ip, ...pts.slice(j + 1)]
          } else {
            // Remove the other side
            pts = [...pts.slice(i + 1, j + 1), ip]
          }
          found = true
          break
        }
      }
    }
    if (!found) break
  }
  return pts.length >= 3 ? pts : coords
}

// ── Catmull-Rom spline → smooth SVG path ──────────────────────────────────────
// Converts polygon vertices to a closed smooth SVG path using Catmull-Rom → cubic Bezier
function smoothPolygonToSvgPath(coords, tension = 0.35) {
  const n = coords.length
  if (n < 3) return null
  const pts = coords.map(([x, y]) => ({ x, y }))

  // Build cubic bezier commands for each segment (open curve, no closing)
  const commands = []
  commands.push(`M ${pts[0].x} ${pts[0].y}`)

  for (let i = 0; i < n - 1; i++) {
    const p0 = pts[Math.max(0, i - 1)]
    const p1 = pts[i]
    const p2 = pts[i + 1]
    const p3 = pts[Math.min(n - 1, i + 2)]

    // Catmull-Rom to cubic Bezier control points
    const cp1x = p1.x + (p2.x - p0.x) * tension
    const cp1y = p1.y + (p2.y - p0.y) * tension
    const cp2x = p2.x - (p3.x - p1.x) * tension
    const cp2y = p2.y - (p3.y - p1.y) * tension

    commands.push(`C ${cp1x} ${cp1y} ${cp2x} ${cp2y} ${p2.x} ${p2.y}`)
  }
  return commands.join(' ')
}

// Simplify polygon by removing near-collinear points (Douglas-Peucker-like)
function simplifyPolygon(coords, tolerance = 3) {
  if (coords.length < 5) return coords
  const result = []
  for (let i = 0; i < coords.length; i++) {
    const prev = coords[(i - 1 + coords.length) % coords.length]
    const curr = coords[i]
    const next = coords[(i + 1) % coords.length]
    // Distance from curr to line(prev, next)
    const dx = next[0] - prev[0], dy = next[1] - prev[1]
    const len = Math.sqrt(dx * dx + dy * dy)
    if (len < 1e-10) { result.push(curr); continue }
    const dist = Math.abs((curr[0] - prev[0]) * dy - (curr[1] - prev[1]) * dx) / len
    if (dist >= tolerance) result.push(curr)
  }
  return result.length >= 3 ? result : coords
}

// ── Nudge tool: cursor + vertex displacement ─────────────────────────────────
function updateNudgeCursor(ptr) {
  if (nudgeCursorCircle) { fc.remove(nudgeCursorCircle); nudgeCursorCircle = null }
  if (!ptr) { fc.renderAll(); return }
  const r = nudgeSize.value
  nudgeCursorCircle = new fabric.Circle({
    left: ptr.x - r, top: ptr.y - r,
    radius: r,
    fill: 'transparent',
    stroke: '#ffffff',
    strokeWidth: 1.5,
    strokeDashArray: [4, 3],
    strokeUniform: true,
    selectable: false,
    evented: false,
    excludeFromExport: true
  })
  fc.add(nudgeCursorCircle)
  fc.renderAll()
}

function onNudgeDown(ptr) {
  isNudging = true
  nudgeDidChange = false
  nudgeLastPtr = { x: ptr.x, y: ptr.y }
}

// Returns the minimum distance from point (cx, cy) to line segment (ax,ay)-(bx,by)
function pointToSegmentDist(cx, cy, ax, ay, bx, by) {
  const abx = bx - ax, aby = by - ay
  const len2 = abx * abx + aby * aby
  if (len2 === 0) return Math.sqrt((cx - ax) ** 2 + (cy - ay) ** 2)
  let t = ((cx - ax) * abx + (cy - ay) * aby) / len2
  t = Math.max(0, Math.min(1, t))
  const px = ax + t * abx, py = ay + t * aby
  return Math.sqrt((cx - px) ** 2 + (cy - py) ** 2)
}

// Insert extra vertices along polygon edges that pass through the brush circle
// so that even long edges without nearby vertices can be pushed/pulled.
function subdivideEdgesInRadius(coords, cx, cy, radius) {
  const result = []
  const n = coords.length
  for (let i = 0; i < n; i++) {
    const a = coords[i]
    const b = coords[(i + 1) % n]
    result.push(a)

    // Check if edge passes near the brush center
    const edgeDist = pointToSegmentDist(cx, cy, a[0], a[1], b[0], b[1])
    if (edgeDist >= radius) continue

    // Both endpoints already close enough — no need to subdivide
    const dA = Math.sqrt((a[0] - cx) ** 2 + (a[1] - cy) ** 2)
    const dB = Math.sqrt((b[0] - cx) ** 2 + (b[1] - cy) ** 2)
    if (dA < radius && dB < radius) continue

    // Insert sample points along the edge within the brush radius
    const edgeLen = Math.sqrt((b[0] - a[0]) ** 2 + (b[1] - a[1]) ** 2)
    // Skip if edge is already short enough (vertices are dense)
    const minEdge = Math.max(3, radius * 0.15)
    if (edgeLen < minEdge) continue
    const step = Math.max(5, radius * 0.4)  // spacing between inserted points
    const steps = Math.ceil(edgeLen / step)
    for (let s = 1; s < steps; s++) {
      const t = s / steps
      const px = a[0] + (b[0] - a[0]) * t
      const py = a[1] + (b[1] - a[1]) * t
      const d = Math.sqrt((px - cx) ** 2 + (py - cy) ** 2)
      if (d < radius) {
        result.push([Math.round(px), Math.round(py)])
      }
    }
  }
  return result
}

function onNudgeMove(ptr) {
  // Always update cursor position
  updateNudgeCursor(ptr)

  if (!isNudging || !nudgeLastPtr) return

  const dx = ptr.x - nudgeLastPtr.x
  const dy = ptr.y - nudgeLastPtr.y
  const moveDist = Math.sqrt(dx * dx + dy * dy)
  if (moveDist < 0.5) return  // ignore sub-pixel jitter

  const radius = nudgeSize.value
  let changed = false

  for (const anno of annotations.value) {
    // Convert rect to polygon — check edges, not just corners
    if (anno.type === 'rect') {
      const [[x1, y1], [x2, y2]] = anno.coordinates
      const corners = [[x1,y1],[x2,y1],[x2,y2],[x1,y2]]
      const edgeHit = corners.some((a, i) => {
        const b = corners[(i + 1) % 4]
        return pointToSegmentDist(ptr.x, ptr.y, a[0], a[1], b[0], b[1]) < radius
      })
      if (edgeHit) {
        anno.type = 'polygon'
        anno.coordinates = corners
      }
    }

    if (anno.type === 'polygon') {
      // Only subdivide if vertex density in brush area is low
      const nearCount = anno.coordinates.filter(([px, py]) =>
        Math.sqrt((px - ptr.x) ** 2 + (py - ptr.y) ** 2) < radius
      ).length
      if (nearCount < Math.max(4, Math.ceil(radius * 0.15))) {
        anno.coordinates = subdivideEdgesInRadius(anno.coordinates, ptr.x, ptr.y, radius)
      }

      for (let i = 0; i < anno.coordinates.length; i++) {
        const [px, py] = anno.coordinates[i]
        const dist = Math.sqrt((px - ptr.x) ** 2 + (py - ptr.y) ** 2)
        if (dist < radius) {
          const factor = nudgeStrength.value * (1 - dist / radius)
          anno.coordinates[i] = [
            Math.round(px + dx * factor),
            Math.round(py + dy * factor)
          ]
          changed = true
        }
      }

      // Merge consecutive vertices that are too close to prevent fold-up artifacts
      if (changed && anno.coordinates.length > 4) {
        const minDist = Math.max(3, radius * 0.2)
        const merged = [anno.coordinates[0]]
        for (let i = 1; i < anno.coordinates.length; i++) {
          const prev = merged[merged.length - 1]
          const cur = anno.coordinates[i]
          const d = Math.sqrt((cur[0] - prev[0]) ** 2 + (cur[1] - prev[1]) ** 2)
          if (d >= minDist) merged.push(cur)
        }
        if (merged.length >= 3) anno.coordinates = merged
      }
    } else if (anno.type === 'brush') {
      for (const stroke of anno.coordinates) {
        for (let i = 0; i < stroke.points.length; i++) {
          const [px, py] = stroke.points[i]
          const dist = Math.sqrt((px - ptr.x) ** 2 + (py - ptr.y) ** 2)
          if (dist < radius) {
            const factor = nudgeStrength.value * (1 - dist / radius)
            stroke.points[i] = [
              Math.round(px + dx * factor),
              Math.round(py + dy * factor)
            ]
            changed = true
          }
        }
      }
    } else if (anno.type === 'point') {
      const [px, py] = anno.coordinates
      const dist = Math.sqrt((px - ptr.x) ** 2 + (py - ptr.y) ** 2)
      if (dist < radius) {
        const factor = nudgeStrength.value * (1 - dist / radius)
        anno.coordinates = [
          Math.round(px + dx * factor),
          Math.round(py + dy * factor)
        ]
        changed = true
      }
    }
  }

  if (changed) {
    nudgeDidChange = true
    renderAllAnnotations()
    // Re-add the nudge cursor on top after re-render
    updateNudgeCursor(ptr)
  }

  nudgeLastPtr = { x: ptr.x, y: ptr.y }
}

function onNudgeUp() {
  if (isNudging) {
    isNudging = false
    nudgeLastPtr = null
    if (nudgeDidChange) {
      // Post-process: remove self-intersections and simplify
      for (const anno of annotations.value) {
        if (anno.type === 'polygon' && anno.coordinates.length >= 4) {
          anno.coordinates = removePolygonSelfIntersections(anno.coordinates)
          anno.coordinates = simplifyPolygon(anno.coordinates)
        }
      }
      renderAllAnnotations()
      pushHistory()
      isDirty.value = true
    }
    nudgeDidChange = false
  }
}

// Scroll wheel: zoom viewport by default; Alt+wheel adjusts radius for nudge/eraser
function onCanvasWheel(e) {
  const tool = currentTool.value

  // Alt+wheel → adjust tool radius for nudge / eraser
  if (e.altKey) {
    if (tool === 'nudge') {
      e.preventDefault()
      e.stopPropagation()
      const delta = e.deltaY > 0 ? -3 : 3
      nudgeSize.value = Math.max(5, Math.min(200, nudgeSize.value + delta))
      if (fc) updateNudgeCursor(fc.getPointer(e))
      return
    } else if (tool === 'eraser') {
      e.preventDefault()
      e.stopPropagation()
      const delta = e.deltaY > 0 ? -3 : 3
      eraserSize.value = Math.max(1, Math.min(200, eraserSize.value + delta))
      if (fc) updateEraserCursor(fc.getPointer(e))
      return
    }
  }

  // Default: forward wheel to OpenSeadragon for zoom
  if (viewer) {
    e.preventDefault()
    e.stopPropagation()
    const factor = e.deltaY < 0 ? 1.2 : 1 / 1.2
    const viewportPoint = viewer.viewport.pointFromPixel(
      new OpenSeadragon.Point(e.offsetX, e.offsetY)
    )
    viewer.viewport.zoomBy(factor, viewportPoint)
  }
}

// ── Eraser tool ──────────────────────────────────────────────────────────────
function updateEraserCursor(ptr) {
  if (eraserCursorCircle) { fc.remove(eraserCursorCircle); eraserCursorCircle = null }
  if (!ptr) { fc.renderAll(); return }
  const r = eraserSize.value
  eraserCursorCircle = new fabric.Circle({
    left: ptr.x - r, top: ptr.y - r,
    radius: r,
    fill: 'rgba(255,80,80,0.1)',
    stroke: '#ff4444',
    strokeWidth: 1.5,
    strokeDashArray: [4, 3],
    strokeUniform: true,
    selectable: false,
    evented: false,
    excludeFromExport: true
  })
  fc.add(eraserCursorCircle)
  fc.renderAll()
}

function onEraserDown(ptr) {
  isErasing = true
  applyEraser(ptr)
}

function onEraserMove(ptr) {
  updateEraserCursor(ptr)
  if (!isErasing) return
  applyEraser(ptr)
}

function onEraserUp() {
  if (isErasing) {
    isErasing = false
    // Clean up empty annotations
    annotations.value = annotations.value.filter(a => {
      if (a.type === 'brush') return a.coordinates.length > 0
      if (a.type === 'polygon') return a.coordinates.length >= 3
      return true
    })
    renderAllAnnotations()
    updateEraserCursor(null)
    pushHistory()
    isDirty.value = true
  }
}

function applyEraser(ptr) {
  const radius = eraserSize.value
  const mode = eraserMode.value
  let changed = false

  if (mode === 'stroke') {
    // ── Stroke mode: remove entire strokes/annotations that intersect ──
    for (let ai = annotations.value.length - 1; ai >= 0; ai--) {
      const anno = annotations.value[ai]

      if (anno.type === 'brush') {
        // Remove individual strokes that have any point within radius
        const before = anno.coordinates.length
        anno.coordinates = anno.coordinates.filter(stroke =>
          !stroke.points.some(([px, py]) =>
            Math.sqrt((px - ptr.x) ** 2 + (py - ptr.y) ** 2) < radius
          )
        )
        if (anno.coordinates.length !== before) changed = true
      } else if (anno.type === 'polygon') {
        // Remove polygon if any vertex is within radius
        const hit = anno.coordinates.some(([px, py]) =>
          Math.sqrt((px - ptr.x) ** 2 + (py - ptr.y) ** 2) < radius
        )
        if (hit) { annotations.value.splice(ai, 1); changed = true }
      } else if (anno.type === 'rect') {
        const [[x1, y1], [x2, y2]] = anno.coordinates
        const corners = [[x1,y1],[x2,y1],[x2,y2],[x1,y2]]
        const hit = corners.some(([px, py]) =>
          Math.sqrt((px - ptr.x) ** 2 + (py - ptr.y) ** 2) < radius
        ) || corners.some((a, i) => {
          const b = corners[(i + 1) % 4]
          return pointToSegmentDist(ptr.x, ptr.y, a[0], a[1], b[0], b[1]) < radius
        })
        if (hit) { annotations.value.splice(ai, 1); changed = true }
      } else if (anno.type === 'point') {
        const [px, py] = anno.coordinates
        if (Math.sqrt((px - ptr.x) ** 2 + (py - ptr.y) ** 2) < radius) {
          annotations.value.splice(ai, 1)
          changed = true
        }
      }
    }

  } else {
    // ── Pixel mode: remove individual points from brush strokes ──
    for (const anno of annotations.value) {
      if (anno.type === 'brush') {
        const newStrokes = []
        for (const stroke of anno.coordinates) {
          // Split stroke: keep segments outside the eraser radius
          let segment = []
          for (const pt of stroke.points) {
            const dist = Math.sqrt((pt[0] - ptr.x) ** 2 + (pt[1] - ptr.y) ** 2)
            if (dist < radius) {
              // Point is inside eraser — break the stroke here
              if (segment.length >= 2) {
                newStrokes.push({ points: segment, size: stroke.size })
              }
              segment = []
              changed = true
            } else {
              segment.push(pt)
            }
          }
          if (segment.length >= 2) {
            newStrokes.push({ points: segment, size: stroke.size })
          } else if (segment.length < stroke.points.length) {
            changed = true
          }
        }
        anno.coordinates = newStrokes
      } else if (anno.type === 'polygon') {
        // For polygons in pixel mode: remove vertices within radius
        const before = anno.coordinates.length
        anno.coordinates = anno.coordinates.filter(([px, py]) =>
          Math.sqrt((px - ptr.x) ** 2 + (py - ptr.y) ** 2) >= radius
        )
        if (anno.coordinates.length !== before) changed = true
      }
    }
    // Delete point annotations within eraser radius
    for (let ai = annotations.value.length - 1; ai >= 0; ai--) {
      if (annotations.value[ai].type === 'point') {
        const [px, py] = annotations.value[ai].coordinates
        if (Math.sqrt((px - ptr.x) ** 2 + (py - ptr.y) ** 2) < radius) {
          annotations.value.splice(ai, 1)
          changed = true
        }
      }
    }
  }

  if (changed) {
    renderAllAnnotations()
    updateEraserCursor(ptr)
  }
}

// Brush: Fabric's free drawing produces a Path on 'path:created'
// 落笔→停笔→首尾闭合，生成封闭多边形
function onPathCreated(e) {
  if (currentTool.value !== 'brush') return
  const path = e.path
  fc.remove(path)

  // Extract sampled points from SVG path commands
  const points = []
  for (const cmd of (path.path ?? [])) {
    if (cmd[0] === 'M' || cmd[0] === 'L') {
      points.push([Math.round(cmd[1]), Math.round(cmd[2])])
    } else if (cmd[0] === 'Q') {
      points.push([Math.round(cmd[3]), Math.round(cmd[4])])
    }
  }
  if (points.length < 3) { fc.renderAll(); return }

  // Simplify to reduce noise, then store as closed polygon
  const simplified = simplifyPolygon(points, 3)
  addAnnotation({
    type:        'polygon',
    coordinates: simplified
  })
}

// ── SAM tool ──────────────────────────────────────────────────────────────────

function setSamMode(mode) {
  samMode.value = mode
  samClear()
}

function updateSamPointObjects() {
  if (!fc) return
  const scale = fc.viewportTransform?.[0]
  if (!scale || scale <= 0) return
  const r = 8 / scale
  fc.forEachObject(o => {
    if (o._isSamPoint) {
      o.set({ radius: r, left: o._samX - r, top: o._samY - r })
      o.setCoords()
    }
  })
}

function renderSamOverlay() {
  if (!fc) return
  // Remove old SAM objects
  fc.getObjects().filter(o => o._isSamPreview || o._isSamPoint || o._isSamBox).forEach(o => fc.remove(o))

  const scale = fc.viewportTransform?.[0] ?? 0.01
  const r = 8 / scale

  // Draw box prompt (yellow dashed rectangle)
  if (samBox.value) {
    const { x1, y1, x2, y2 } = samBox.value
    fc.add(new fabric.Rect({
      left: x1, top: y1, width: x2 - x1, height: y2 - y1,
      fill: 'rgba(255,193,7,0.08)',
      stroke: '#ffc107',
      strokeWidth: 2 / scale,
      strokeDashArray: [8 / scale, 4 / scale],
      strokeUniform: true,
      selectable: false, evented: false,
      _isSamBox: true
    }))
  }

  // Draw SAM prompt points: green = fg (label=1), red = bg (label=0)
  for (const pt of samPoints.value) {
    const color = pt.label === 1 ? '#4caf50' : '#f44336'
    fc.add(new fabric.Circle({
      left: pt.x - r, top: pt.y - r, radius: r,
      fill: color, stroke: '#ffffff', strokeWidth: 2,
      strokeUniform: true, selectable: false, evented: false,
      _isSamPoint: true, _samX: pt.x, _samY: pt.y
    }))
  }

  // Draw preview polygons
  if (samPreview.value) {
    for (const poly of samPreview.value.polygons) {
      if (poly.length < 3) continue
      fc.add(new fabric.Polygon(
        poly.map(([x, y]) => ({ x, y })),
        {
          fill: 'rgba(0,200,255,0.18)',
          stroke: '#00c8ff',
          strokeWidth: 2,
          strokeUniform: true,
          selectable: false, evented: false,
          _isSamPreview: true
        }
      ))
    }
  }

  fc.renderAll()
}

function samClear() {
  samPoints.value = []
  samPreview.value = null
  samBox.value = null
  samBoxStart = null
  samIsDragging = false
  if (fc) {
    fc.getObjects().filter(o => o._isSamPreview || o._isSamPoint || o._isSamBox).forEach(o => fc.remove(o))
    fc.renderAll()
  }
}

async function onSamDown(ptr, button = 0) {
  if (samLoading.value) return
  if (samMode.value === 'box') {
    // Start box drag — do not predict yet
    samBoxStart = { x: ptr.x, y: ptr.y }
    samIsDragging = true
    return
  }
  // Point mode: left-click = fg (1), right-click = bg (0)
  const label = button === 2 ? 0 : 1
  samPoints.value.push({ x: Math.round(ptr.x), y: Math.round(ptr.y), label })
  renderSamOverlay()
  await runSamPredict()
}

function onSamMove(ptr) {
  if (!samIsDragging || !samBoxStart) return
  const x1 = Math.min(samBoxStart.x, ptr.x)
  const y1 = Math.min(samBoxStart.y, ptr.y)
  const x2 = Math.max(samBoxStart.x, ptr.x)
  const y2 = Math.max(samBoxStart.y, ptr.y)
  samBox.value = { x1: Math.round(x1), y1: Math.round(y1), x2: Math.round(x2), y2: Math.round(y2) }
  renderSamOverlay()
}

async function onSamUp(_ptr) {
  if (!samIsDragging) return
  samIsDragging = false
  samBoxStart = null
  if (!samBox.value) return
  const { x1, y1, x2, y2 } = samBox.value
  // Ignore tiny accidental drags (< 10px)
  if ((x2 - x1) < 10 || (y2 - y1) < 10) {
    samBox.value = null
    return
  }
  await runSamPredict()
}

async function runSamPredict() {
  if (samLoading.value) return
  samLoading.value = true
  samPreview.value = null
  try {
    const payload = {
      points: samPoints.value.map(p => [p.x, p.y]),
      point_labels: samPoints.value.map(p => p.label)
    }
    if (samBox.value) {
      const { x1, y1, x2, y2 } = samBox.value
      payload.box = [x1, y1, x2, y2]
    }
    const res = await samApi.predict(slideId, payload)
    samPreview.value = res.data
    renderSamOverlay()
  } catch {
    ElMessage.error('SAM 推理失败，请确认 SAM 服务已启动')
    samClear()
  } finally {
    samLoading.value = false
  }
}

function samAccept() {
  if (!samPreview.value?.polygons?.length) { samReject(); return }
  // Pick the polygon with the most vertices (outer boundary)
  const best = samPreview.value.polygons.reduce((a, b) => a.length >= b.length ? a : b)
  const coords = best.map(([x, y]) => [Math.round(x), Math.round(y)])
  samClear()
  addAnnotation({ type: 'polygon', coordinates: coords })
}

function samReject() {
  samClear()
}

// ── Annotation CRUD ───────────────────────────────────────────────────────────
async function addAnnotation(data) {
  // Auto-create a label locally if none is active
  if (!activeLabel.value) {
    const color = labelPanelRef.value?.newColor ?? nextAutoColor()
    const name  = `未命名标签${labels.value.length + 1}`
    const newLabel = { id: --tempLabelIdCtr, name, color }
    labels.value.push(newLabel)
    activeLabelId.value = newLabel.id
    // Trigger inline edit so user can rename immediately
    pendingEditLabelId.value = newLabel.id
    nextTick(() => { pendingEditLabelId.value = null })
  }

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
      if (deletedLabelId > 0) await annotationApi.deleteLabel(deletedLabelId)
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
  history.value.push({ annotations: deepClone(annotations.value), labels: deepClone(labels.value) })
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
  const snapshot = history.value[historyIndex.value]
  annotations.value = deepClone(snapshot.annotations)
  labels.value = deepClone(snapshot.labels)
  // Keep active label valid
  if (activeLabelId.value && !labels.value.some(l => l.id === activeLabelId.value)) {
    activeLabelId.value = labels.value[0]?.id ?? null
  }
  renderAllAnnotations()
  isDirty.value = true
}

// ── Save ──────────────────────────────────────────────────────────────────────
async function saveAnnotations() {
  if (saving.value) return
  saving.value = true
  try {
    // 1. Persist any local-only labels (negative id) to the server
    const idMap = {}  // tempId → realId
    for (const label of labels.value) {
      if (label.id < 0) {
        const res = await annotationApi.createLabel(slideId, { name: label.name, color: label.color })
        const realLabel = res.data
        idMap[label.id] = realLabel.id
        label.id = realLabel.id
      }
    }
    // Also update any renamed labels that are already persisted
    for (const label of labels.value) {
      if (label._dirty) {
        await annotationApi.updateLabel(label.id, { name: label.name, color: label.color })
        delete label._dirty
      }
    }
    // 2. Remap temp labelIds in annotations
    for (const anno of annotations.value) {
      if (idMap[anno.labelId] != null) {
        anno.labelId = idMap[anno.labelId]
      }
    }
    // Update activeLabelId if it was a temp id
    if (idMap[activeLabelId.value] != null) {
      activeLabelId.value = idMap[activeLabelId.value]
    }

    // 3. Save annotations batch
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
    if (slide.value && slide.value.annoStatus === 'NOT_STARTED' && saved.length > 0) {
      slide.value.annoStatus = 'IN_PROGRESS'
    }
    ElMessage.success('标注已保存')
  } catch {
    // Error shown by axios interceptor
  } finally {
    saving.value = false
  }
}

// ── Submit for review (annotator) ─────────────────────────────────────────────
async function markDone() {
  if (isDirty.value) {
    await saveAnnotations()
  }
  const isResubmit = slide.value?.reviewStatus === 'REJECTED' || slide.value?.reviewStatus === 'APPROVED'
  try {
    await ElMessageBox.confirm(
      isResubmit ? '确认重新提交该切片进行审核？' : '确认提交该切片进行审核？提交后审核者将收到审核请求。',
      isResubmit ? '重新提交审核' : '提交审核',
      { type: 'info', confirmButtonText: '确认', cancelButtonText: '取消' }
    )
  } catch { return }
  completing.value = true
  try {
    await annotationApi.markComplete(slideId)
    slide.value.annoStatus = 'DONE'
    slide.value.reviewStatus = 'PENDING_REVIEW'
    ElMessage.success(isResubmit ? '已重新提交，等待审核' : '已提交审核，等待审核者审核')
  } catch { /* handled by interceptor */ } finally {
    completing.value = false
  }
}

// ── Approve / Reject (reviewer) ───────────────────────────────────────────────
async function handleApprove() {
  try {
    await ElMessageBox.confirm('确认通过该切片的标注？', '通过审核', {
      type: 'success', confirmButtonText: '确认通过', cancelButtonText: '取消'
    })
  } catch { return }
  reviewing.value = 'approve'
  try {
    await reviewApi.approve(slideId)
    slide.value.reviewStatus = 'APPROVED'
    ElMessage.success('已通过审核')
  } catch { /* handled */ } finally {
    reviewing.value = false
  }
}

async function handleRejectPrompt() {
  let comment = ''
  try {
    const { value } = await ElMessageBox.prompt('请填写拒绝原因：', '拒绝标注', {
      confirmButtonText: '确认拒绝',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValidator: (v) => v?.trim() ? true : '拒绝原因不能为空'
    })
    comment = value
  } catch { return }
  reviewing.value = 'reject'
  try {
    await reviewApi.reject(slideId, comment)
    slide.value.reviewStatus = 'REJECTED'
    slide.value.reviewerComment = comment
    ElMessage.success('已拒绝，标注者将收到反馈')
  } catch { /* handled */ } finally {
    reviewing.value = false
  }
}

// ── Label CRUD ────────────────────────────────────────────────────────────────
function onAddLabel({ name, color }) {
  const newLabel = { id: --tempLabelIdCtr, name, color }
  labels.value.push(newLabel)
  activeLabelId.value = newLabel.id
  isDirty.value = true
}

function onRenameLabel({ id, name }) {
  const label = labels.value.find(l => l.id === id)
  if (label) {
    label.name = name
    label._dirty = true
  }
  isDirty.value = true
}

function onColorChangeLabel({ id, color }) {
  const label = labels.value.find(l => l.id === id)
  if (!label) return
  label.color = color
  label._dirty = true
  // Update labelColor on all annotations belonging to this label
  for (const anno of annotations.value) {
    if (anno.labelId === id) {
      anno.labelColor = color
    }
  }
  isDirty.value = true
  renderAllAnnotations()
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
    // Only call server delete for persisted labels (positive id)
    if (labelId > 0) await annotationApi.deleteLabel(labelId)
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
    try { if (oldLabelId > 0) await annotationApi.deleteLabel(oldLabelId) } catch {}
    labels.value = labels.value.filter(l => l.id !== oldLabelId)
    if (activeLabelId.value === oldLabelId) activeLabelId.value = labels.value[0]?.id ?? null
  }
  pushHistory()
  isDirty.value = true
}

// ── Keyboard shortcuts ────────────────────────────────────────────────────────
function onKeyDown(e) {
  // Ctrl combos — always handle, even when focus is in an input/slider
  if (e.ctrlKey) {
    if (e.key === 'z') { e.preventDefault(); undo();             return }
    if (e.key === 'y') { e.preventDefault(); redo();             return }
    if (e.key === 's') { e.preventDefault(); saveAnnotations();  return }
    return
  }

  // Ignore when typing in an input / textarea
  const tag = document.activeElement?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA') return

  // Escape
  if (e.key === 'Escape') {
    setTool('pan')
    return
  }

  // Delete selected annotation
  if (e.key === 'Delete' || e.key === 'Backspace') {
    const active = fc?.getActiveObject()
    if (active?._annoId) { deleteAnnotation(active._annoId); return }
  }

  // WASD / Arrow keys — canvas panning (check before tool shortcuts)
  if (viewer) {
    const k = e.key.toLowerCase()
    if (k === 'a' || e.key === 'ArrowLeft')  { e.preventDefault(); viewer.viewport.panBy(new OpenSeadragon.Point(-0.05, 0));  return }
    if (k === 'd' || e.key === 'ArrowRight') { e.preventDefault(); viewer.viewport.panBy(new OpenSeadragon.Point(0.05, 0));   return }
    if (k === 'w' || e.key === 'ArrowUp')    { e.preventDefault(); viewer.viewport.panBy(new OpenSeadragon.Point(0, -0.05));  return }
    if (k === 's' || e.key === 'ArrowDown')  { e.preventDefault(); viewer.viewport.panBy(new OpenSeadragon.Point(0, 0.05));   return }
  }

  // Tool shortcuts (single key, no modifiers)
  switch (e.key.toLowerCase()) {
    case 'v': setTool('pan');     return
    case 'b': setTool('brush');   return
    case 'n': setTool('nudge');   return
    case 'e': setTool('eraser');  return
    case 'p': setTool('point');   return
    case 'm': setTool('sam');     return
  }

  // Viewer navigation
  if (!viewer) return
  switch (e.key) {
    case '+': case '=': e.preventDefault(); zoomIn();  break
    case '-':           e.preventDefault(); zoomOut(); break
    case 'h': case 'H': e.preventDefault(); zoomFit(); break
    case '?':           e.preventDefault(); showShortcutHelp.value = true; break
  }
}

// ── Navigation ────────────────────────────────────────────────────────────────
async function goBack() {
  if (isDirty.value) {
    try {
      await ElMessageBox.confirm(
        '当前标注尚未保存，是否保存后再离开？',
        '未保存的更改',
        {
          type: 'warning',
          confirmButtonText: '保存并离开',
          cancelButtonText: '不保存',
          distinguishCancelAndClose: true,
        }
      )
      // User clicked "保存并离开"
      await saveAnnotations()
    } catch (action) {
      if (action === 'close') return   // clicked X, stay on page
      // action === 'cancel' → "不保存", discard and leave
    }
  }
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
const AUTO_COLORS = [
  '#e6194b', '#3cb44b', '#4363d8', '#f58231', '#911eb4',
  '#42d4f4', '#f032e6', '#bfef45', '#fabed4', '#469990',
  '#dcbeff', '#9a6324', '#800000', '#aaffc3', '#808000',
  '#ffd8b1', '#000075', '#a9a9a9'
]
let autoColorIdx = 0

function nextAutoColor() {
  return AUTO_COLORS[autoColorIdx++ % AUTO_COLORS.length]
}

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

/* ── Right floating dock ──────────────────────────────────────── */
.right-dock {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 10;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  background: rgba(20, 20, 20, 0.82);
  backdrop-filter: blur(6px);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 20px;
  padding: 8px 5px;
  box-shadow: 0 4px 16px rgba(0,0,0,0.5);
}

.dock-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: transparent;
  color: #bbb;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  transition: background 0.15s, color 0.15s;
}

.dock-btn:hover {
  background: rgba(255,255,255,0.12);
  color: #fff;
}

.dock-divider {
  width: 18px;
  height: 1px;
  background: rgba(255,255,255,0.12);
  margin: 2px 0;
}

/* NotificationBell inside dock */
.right-dock :deep(.el-button.is-circle) {
  width: 32px;
  height: 32px;
  background: transparent;
  border: none;
  color: #bbb;
}
.right-dock :deep(.el-button.is-circle:hover) {
  background: rgba(255,255,255,0.12);
  color: #fff;
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

/* ── SAM action bar ───────────────────────────────────────────────── */
.sam-action-bar {
  position: absolute;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 16px;
  background: rgba(20, 20, 30, 0.92);
  border: 1px solid #00c8ff55;
  border-radius: 20px;
  backdrop-filter: blur(6px);
  box-shadow: 0 2px 12px rgba(0,200,255,0.15);
  z-index: 10;
  white-space: nowrap;
}

.sam-status-text {
  font-size: 12px;
  color: #aaa;
}

.sam-fade-enter-active,
.sam-fade-leave-active {
  transition: opacity 0.2s, transform 0.2s;
}
.sam-fade-enter-from,
.sam-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(6px);
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

/* ── Shortcut help dialog ──────────────────────────────────────── */
.shortcut-help-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
.shortcut-help-grid .shortcut-group:last-child {
  grid-column: 1 / -1;
}
.shortcut-group h4 {
  margin: 0 0 8px;
  font-size: 14px;
  color: var(--el-text-color-primary);
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding-bottom: 4px;
}
.shortcut-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 13px;
}
.shortcut-row kbd {
  display: inline-block;
  min-width: 28px;
  padding: 2px 7px;
  font-family: inherit;
  font-size: 12px;
  text-align: center;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  box-shadow: 0 1px 0 var(--el-border-color-lighter);
}
.shortcut-row span {
  color: var(--el-text-color-regular);
}

/* ── Ghost comparison badge ───────────────────────────────────────── */
.ghost-badge {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  padding: 6px 14px;
  background: rgba(20, 12, 0, 0.92);
  border: 1px solid rgba(255, 140, 0, 0.5);
  border-radius: 20px;
  backdrop-filter: blur(6px);
  box-shadow: 0 2px 12px rgba(255, 140, 0, 0.2);
  z-index: 10;
  white-space: nowrap;
}
.ghost-badge-text {
  font-size: 13px;
  color: #ffc04d;
  font-weight: 500;
}
.ghost-close-btn {
  border-color: rgba(255, 140, 0, 0.4) !important;
  color: #ffc04d !important;
  background: transparent !important;
}
.ghost-close-btn:hover {
  background: rgba(255, 140, 0, 0.12) !important;
}

/* ── Version history drawer ─────────────────────────────────────── */
.ver-loading,
.ver-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
  padding: 40px 0;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.ver-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.ver-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ver-num {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.ver-compare-btn {
  margin-left: auto;
  flex-shrink: 0;
}
.ver-comment {
  font-size: 13px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border-left: 3px solid var(--el-color-danger-light-5);
  padding: 4px 8px;
  border-radius: 0 4px 4px 0;
  font-style: italic;
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

.reject-banner {
  display: flex;
  align-items: center;
  padding: 8px 20px;
  background: #fef0f0;
  color: #f56c6c;
  font-size: 13px;
  border-bottom: 1px solid #fbc4c4;
}
</style>
