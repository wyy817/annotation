<template>
  <div class="stats-panel">
    <div class="toolbar">
      <el-button :icon="Refresh" @click="loadStats">刷新</el-button>
      <el-select
        v-model="selectedWorkspace"
        placeholder="全部工作区"
        clearable
        style="width: 200px; margin-left: 12px"
        @change="loadStats"
      >
        <el-option
          v-for="ws in workspaces"
          :key="ws.id"
          :label="ws.name"
          :value="ws.id"
        />
      </el-select>
    </div>

    <!-- Summary cards -->
    <div class="summary-row">
      <div class="summary-card">
        <div class="summary-value">{{ stats.totalSlides }}</div>
        <div class="summary-label">切片总数</div>
      </div>
      <div class="summary-card">
        <div class="summary-value">{{ stats.totalAnnotations }}</div>
        <div class="summary-label">标注总数</div>
      </div>
      <div class="summary-card">
        <div class="summary-value">{{ stats.labelStats.length }}</div>
        <div class="summary-label">标签类别</div>
      </div>
      <div class="summary-card">
        <div class="summary-value">{{ avgAnnotationsPerSlide }}</div>
        <div class="summary-label">平均标注/切片</div>
      </div>
    </div>

    <el-empty v-if="!stats.totalAnnotations" description="暂无标注数据" class="empty-state" />

    <template v-else>
      <!-- Charts row 1: label count + type distribution -->
      <div class="chart-row">
        <div class="chart-box">
          <h3 class="chart-title">各类标签数量分布</h3>
          <div ref="labelCountChart" class="chart-canvas"></div>
        </div>
        <div class="chart-box chart-box-small">
          <h3 class="chart-title">标注类型分布</h3>
          <div ref="typeChart" class="chart-canvas"></div>
        </div>
      </div>

      <!-- Charts row 2: label area + per-slide -->
      <div class="chart-row">
        <div class="chart-box">
          <h3 class="chart-title">各类标签面积占比</h3>
          <div ref="labelAreaChart" class="chart-canvas"></div>
        </div>
        <div class="chart-box">
          <h3 class="chart-title">切片标注数量 (Top 20)</h3>
          <div ref="slideChart" class="chart-canvas"></div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { BarChart, PieChart } from 'echarts/charts'
import {
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { statisticsApi } from '@/api/statistics'
import { workspaceApi } from '@/api/workspace'

echarts.use([
  BarChart, PieChart,
  TitleComponent, TooltipComponent, GridComponent, LegendComponent,
  CanvasRenderer
])

const TYPE_LABELS = { polygon: '多边形', rect: '矩形', brush: '画笔' }

const selectedWorkspace = ref(null)
const workspaces = ref([])
const stats = ref({
  labelStats: [],
  typeStats: [],
  slideStats: [],
  totalAnnotations: 0,
  totalSlides: 0
})

const labelCountChart = ref(null)
const typeChart = ref(null)
const labelAreaChart = ref(null)
const slideChart = ref(null)

let charts = []

const avgAnnotationsPerSlide = computed(() => {
  if (!stats.value.totalSlides) return 0
  return (stats.value.totalAnnotations / stats.value.totalSlides).toFixed(1)
})

onMounted(async () => {
  const wsRes = await workspaceApi.list()
  workspaces.value = wsRes.data
  await loadStats()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  charts.forEach(c => c.dispose())
  charts = []
})

function handleResize() {
  charts.forEach(c => c.resize())
}

async function loadStats() {
  const res = await statisticsApi.getAnnotationStats(selectedWorkspace.value)
  stats.value = res.data
  await nextTick()
  renderCharts()
}

function renderCharts() {
  charts.forEach(c => c.dispose())
  charts = []

  if (!stats.value.totalAnnotations) return

  renderLabelCountChart()
  renderTypeChart()
  renderLabelAreaChart()
  renderSlideChart()
}

function initChart(el) {
  if (!el) return null
  const chart = echarts.init(el)
  charts.push(chart)
  return chart
}

function renderLabelCountChart() {
  const chart = initChart(labelCountChart.value)
  if (!chart) return
  const data = stats.value.labelStats
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 80, right: 24, top: 16, bottom: 40 },
    xAxis: { type: 'value', name: '数量' },
    yAxis: {
      type: 'category',
      data: data.map(d => d.name),
      axisLabel: { width: 60, overflow: 'truncate' }
    },
    series: [{
      type: 'bar',
      data: data.map(d => ({
        value: d.count,
        itemStyle: { color: d.color }
      })),
      barMaxWidth: 28,
      label: { show: true, position: 'right', fontSize: 12 }
    }]
  })
}

function renderTypeChart() {
  const chart = initChart(typeChart.value)
  if (!chart) return
  const data = stats.value.typeStats
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['36%', '64%'],
      center: ['50%', '45%'],
      label: { formatter: '{b}\n{d}%' },
      data: data.map(d => ({
        name: TYPE_LABELS[d.type] || d.type,
        value: d.count
      }))
    }]
  })
}

function renderLabelAreaChart() {
  const chart = initChart(labelAreaChart.value)
  if (!chart) return
  const data = stats.value.labelStats.filter(d => d.area > 0)
  if (!data.length) {
    chart.setOption({
      title: { text: '无面积数据', left: 'center', top: 'center', textStyle: { color: '#c0c4cc', fontSize: 14 } }
    })
    return
  }
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {d}%' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie',
      radius: ['0%', '64%'],
      center: ['50%', '45%'],
      roseType: 'area',
      label: { formatter: '{b}\n{d}%' },
      data: data.map(d => ({
        name: d.name,
        value: Math.round(d.area),
        itemStyle: { color: d.color }
      }))
    }]
  })
}

function renderSlideChart() {
  const chart = initChart(slideChart.value)
  if (!chart) return
  const data = stats.value.slideStats
  chart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 120, right: 24, top: 16, bottom: 40 },
    xAxis: { type: 'value', name: '标注数' },
    yAxis: {
      type: 'category',
      data: data.map(d => d.fileName).reverse(),
      axisLabel: { width: 100, overflow: 'truncate' }
    },
    series: [{
      type: 'bar',
      data: [...data].reverse().map(d => d.annotationCount),
      barMaxWidth: 24,
      label: { show: true, position: 'right', fontSize: 12 },
      itemStyle: { color: '#409eff' }
    }]
  })
}
</script>

<style scoped>
.stats-panel {
  padding: 0;
}

.toolbar {
  margin-bottom: 16px;
  display: flex;
  align-items: center;
}

.empty-state {
  margin-top: 60px;
}

.summary-row {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
}

.summary-card {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.summary-value {
  font-size: 28px;
  font-weight: 700;
  color: #409eff;
}

.summary-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.chart-row {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.chart-box {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.chart-box-small {
  flex: 0 0 340px;
}

.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px 0;
}

.chart-canvas {
  width: 100%;
  height: 320px;
}
</style>
