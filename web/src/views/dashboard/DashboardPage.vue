<template>
  <div class="dashboard-page">
    <!-- 顶部 4 个统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-label">当月 Token</div>
          <div class="stat-value">{{ overview.monthTokens ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-label">当月对话</div>
          <div class="stat-value">{{ overview.monthMessages ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-label">当天 Token</div>
          <div class="stat-value">{{ overview.todayTokens ?? 0 }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-label">当天对话</div>
          <div class="stat-value">{{ overview.todayMessages ?? 0 }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 4 个详情卡片：左右左右排列 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>Token 消耗趋势（input / output）</span>
              <el-radio-group v-model="tokenPeriod" size="small" @change="loadTokenTrend">
                <el-radio-button value="day">1天</el-radio-button>
                <el-radio-button value="week">7天</el-radio-button>
                <el-radio-button value="month">本月</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="tokenChartEl" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>消息频次统计</span>
              <el-radio-group v-model="msgPeriod" size="small" @change="loadMessageTrend">
                <el-radio-button value="day">1天</el-radio-button>
                <el-radio-button value="week">7天</el-radio-button>
                <el-radio-button value="month">本月</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="msgChartEl" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>检索耗时统计</span>
              <el-radio-group v-model="retrievalPeriod" size="small" @change="loadRetrievalTrend">
                <el-radio-button value="day">1天</el-radio-button>
                <el-radio-button value="week">7天</el-radio-button>
                <el-radio-button value="month">本月</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="retrievalChartEl" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>知识库使用频率 Top N</span>
              <div class="header-controls">
                <el-radio-group v-model="kbPeriod" size="small" @change="loadTopKB">
                  <el-radio-button value="day">1天</el-radio-button>
                  <el-radio-button value="week">7天</el-radio-button>
                  <el-radio-button value="month">本月</el-radio-button>
                </el-radio-group>
                <el-input-number v-model="topN" :min="1" :max="20" size="small" @change="loadTopKB" style="width:120px" />
              </div>
            </div>
          </template>
          <el-table :data="topKBList" stripe style="width:100%">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="name" label="知识库" min-width="150" show-overflow-tooltip />
            <el-table-column prop="visit_count" label="访问会话数" width="120" sortable />
            <el-table-column prop="query_count" label="检索次数" width="120" sortable />
            <el-table-column prop="file_count" label="文件数" width="100" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import request from '../../api/request'

// ── 顶部统计 ─
const overview = ref<any>({})

// ── 时间选择 ─
const tokenPeriod = ref('week')
const msgPeriod = ref('week')
const retrievalPeriod = ref('week')
const kbPeriod = ref('week')
const topN = ref(5)

// ── 图表 DOM ──
const tokenChartEl = ref<HTMLDivElement>()
const msgChartEl = ref<HTMLDivElement>()
const retrievalChartEl = ref<HTMLDivElement>()

let tokenChart: echarts.ECharts | null = null
let msgChart: echarts.ECharts | null = null
let retrievalChart: echarts.ECharts | null = null

// ── 知识库列表 ──
const topKBList = ref<any[]>([])

// ═══════════════════════════════
//  数据加载
// ═══════════════════════════════

async function loadOverview() {
  try {
    const json = await request.get('/dashboard/overview') as any
    overview.value = json.data || json || {}
  } catch { /* 容错 */ }
}

async function loadTokenTrend() {
  try {
    const json = await request.get('/dashboard/token-trend', { params: { period: tokenPeriod.value } }) as any
    const data = json.data || json
    const list = data.trendData || []
    await nextTick()
    renderTokenChart(list)
  } catch { /* 容错 */ }
}

async function loadMessageTrend() {
  try {
    const json = await request.get('/dashboard/message-trend', { params: { period: msgPeriod.value } }) as any
    const data = json.data || json
    const list = data.trendData || []
    await nextTick()
    renderMsgChart(list)
  } catch { /* 容错 */ }
}

async function loadRetrievalTrend() {
  try {
    const json = await request.get('/dashboard/retrieval-trend', { params: { period: retrievalPeriod.value } }) as any
    const data = json.data || json
    const list = data.trendData || []
    await nextTick()
    renderRetrievalChart(list)
  } catch { /* 容错 */ }
}

async function loadTopKB() {
  try {
    const json = await request.get('/dashboard/top-knowledge-bases', {
      params: { period: kbPeriod.value, topN: topN.value }
    }) as any
    const data = json.data || json
    topKBList.value = data.knowledgeBases || []
  } catch { /* 容错 */ }
}

// ═══════════════════════════════
//  ECharts 渲染
// ═══════════════════════════════

function formatDateLabel(d: string) {
  // 把 "2026-08-30" 切成 "08-30"
  const parts = d.split('-')
  return parts.length === 3 ? `${parts[1]}-${parts[2]}` : d
}

function renderTokenChart(list: any[]) {
  tokenChart?.dispose()
  if (!tokenChartEl.value || list.length === 0) return
  tokenChart = echarts.init(tokenChartEl.value)
  tokenChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: {
      data: ['Input Token', 'Output Token', '平均Token/请求', '最大Token/请求', '最小Token/请求'],
      bottom: 0,
      itemWidth: 12,
      itemGap: 10,
      textStyle: { fontSize: 11 }
    },
    grid: { left: 60, right: 20, top: 15, bottom: 50 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: list.map((d) => formatDateLabel(d.date)),
    },
    yAxis: { type: 'value', name: 'Token 数' },
    series: [
      {
        name: 'Input Token',
        type: 'line',
        smooth: true,
        data: list.map((d) => d.inputTokens ?? 0),
        itemStyle: { color: '#409eff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64,158,255,0.35)' },
            { offset: 1, color: 'rgba(64,158,255,0.05)' },
          ]),
        },
      },
      {
        name: 'Output Token',
        type: 'line',
        smooth: true,
        data: list.map((d) => d.outputTokens ?? 0),
        itemStyle: { color: '#67c23a' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(103,194,58,0.35)' },
            { offset: 1, color: 'rgba(103,194,58,0.05)' },
          ]),
        },
      },
      {
        name: '平均Token/请求',
        type: 'line',
        smooth: true,
        data: list.map((d) => d.avgTokens ?? 0),
        itemStyle: { color: '#ff9800' },
        lineStyle: { type: 'dashed' }
      },
      {
        name: '最大Token/请求',
        type: 'line',
        smooth: true,
        data: list.map((d) => d.maxTokens ?? 0),
        itemStyle: { color: '#f44336' },
        lineStyle: { type: 'dotted' }
      },
      {
        name: '最小Token/请求',
        type: 'line',
        smooth: true,
        data: list.map((d) => d.minTokens ?? 0),
        itemStyle: { color: '#4caf50' },
        lineStyle: { type: 'dashed' }
      }
    ],
  })
}

function renderMsgChart(list: any[]) {
  msgChart?.dispose()
  if (!msgChartEl.value || list.length === 0) return
  msgChart = echarts.init(msgChartEl.value)
  msgChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: {
      data: ['消息数'],
      bottom: 0,
      itemWidth: 12,
      itemGap: 10,
      textStyle: { fontSize: 11 }
    },
    grid: { left: 60, right: 20, top: 15, bottom: 50 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: list.map((d) => formatDateLabel(d.date)),
    },
    yAxis: { type: 'value', name: '消息数', minInterval: 1 },
    series: [
      {
        name: '消息数',
        type: 'line',
        smooth: true,
        data: list.map((d) => d.messageCount ?? 0),
        itemStyle: { color: '#e6a23c' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(230,162,60,0.35)' },
            { offset: 1, color: 'rgba(230,162,60,0.05)' },
          ]),
        },
      },
    ],
  })
}

function renderRetrievalChart(list: any[]) {
  retrievalChart?.dispose()
  if (!retrievalChartEl.value || list.length === 0) return
  retrievalChart = echarts.init(retrievalChartEl.value)
  retrievalChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: {
      data: ['平均耗时(ms)', '最大耗时(ms)', '最小耗时(ms)'],
      bottom: 0,
      itemWidth: 12,
      itemGap: 10,
      textStyle: { fontSize: 11 }
    },
    grid: { left: 60, right: 20, top: 15, bottom: 50 },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: list.map((d) => formatDateLabel(d.date)),
    },
    yAxis: { type: 'value', name: '耗时(ms)' },
    series: [
      {
        name: '平均耗时(ms)',
        type: 'line',
        smooth: true,
        data: list.map((d) => d.avgLatencyMs ?? 0),
        itemStyle: { color: '#f56c6c' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(245,108,108,0.35)' },
            { offset: 1, color: 'rgba(245,108,108,0.05)' },
          ]),
        },
      },
      {
        name: '最大耗时(ms)',
        type: 'line',
        smooth: true,
        data: list.map((d) => d.maxLatencyMs ?? 0),
        itemStyle: { color: '#f44336' },
        lineStyle: { type: 'dotted' }
      },
      {
        name: '最小耗时(ms)',
        type: 'line',
        smooth: true,
        data: list.map((d) => d.minLatencyMs ?? 0),
        itemStyle: { color: '#4caf50' },
        lineStyle: { type: 'dashed' }
      }
    ],
  })
}

// ═══════════════════════════════
//  生命周期
// ═══════════════════════════════

onMounted(() => {
  loadOverview()
  loadTokenTrend()
  loadMessageTrend()
  loadRetrievalTrend()
  loadTopKB()

  // 窗口大小变化时重新调整图表
  window.addEventListener('resize', handleResize)
})

function handleResize() {
  tokenChart?.resize()
  msgChart?.resize()
  retrievalChart?.resize()
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  tokenChart?.dispose()
  msgChart?.dispose()
  retrievalChart?.dispose()
})
</script>

<style scoped>
.dashboard-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px;
}

.stats-row {
  margin: 0;
}

.stat-card {
  text-align: center;
}

.stat-card :deep(.el-card__body) {
  padding: 16px 8px;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
}

.chart-card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chart-container {
  height: 280px;
}
</style>
