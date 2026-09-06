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

    <!-- 第一行图表：Token 消耗 + 消息频次 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <div class="header-top">
                <span class="chart-title">Token 消耗趋势（input / output）</span>
                <el-select v-model="tokenUsageType" placeholder="全部类型" clearable size="small" style="width:85px" @change="loadTokenTrend">
                  <el-option label="全部" value="" />
                  <el-option label="对话" value="CONVERSATION" />
                  <el-option label="向量入库" value="EMBEDDING" />
                  <el-option label="检索" value="RETRIEVAL" />
                </el-select>
                <el-radio-group v-model="tokenPeriod" size="small" @change="handlePeriodChange('token')">
                  <el-radio-button value="day">1天</el-radio-button>
                  <el-radio-button value="week">7天</el-radio-button>
                  <el-radio-button value="month">本月</el-radio-button>
                  <el-radio-button value="custom">自定义</el-radio-button>
                </el-radio-group>
              </div>
              <div class="header-bottom">
                <el-date-picker
                  v-model="tokenDateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                  size="small"
                  style="width: 280px"
                  @change="loadTokenTrend"
                />
              </div>
            </div>
          </template>
          <div ref="tokenChartEl" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <div class="header-top">
                <span>消息频次统计</span>
                <el-radio-group v-model="msgPeriod" size="small" @change="handlePeriodChange('msg')">
                  <el-radio-button value="day">1天</el-radio-button>
                  <el-radio-button value="week">7天</el-radio-button>
                  <el-radio-button value="month">本月</el-radio-button>
                  <el-radio-button value="custom">自定义</el-radio-button>
                </el-radio-group>
              </div>
              <div class="header-bottom">
                <el-date-picker
                  v-model="msgDateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                  size="small"
                  style="width: 280px"
                  @change="loadMessageTrend"
                />
              </div>
            </div>
          </template>
          <div ref="msgChartEl" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第二行图表：检索耗时 + ETL 入库趋势 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <div class="header-top">
                <span>检索耗时统计</span>
                <el-radio-group v-model="retrievalPeriod" size="small" @change="handlePeriodChange('retrieval')">
                  <el-radio-button value="day">1天</el-radio-button>
                  <el-radio-button value="week">7天</el-radio-button>
                  <el-radio-button value="month">本月</el-radio-button>
                  <el-radio-button value="custom">自定义</el-radio-button>
                </el-radio-group>
              </div>
              <div class="header-bottom">
                <el-date-picker
                  v-model="retrievalDateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                  size="small"
                  style="width: 280px"
                  @change="loadRetrievalTrend"
                />
              </div>
            </div>
          </template>
          <div ref="retrievalChartEl" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <div class="header-top">
                <span>ETL 入库耗时统计</span>
                <el-select
                  v-model="durationKbId"
                  placeholder="全部知识库"
                  clearable
                  size="small"
                  style="width: 200px"
                  @change="loadIngestionDurationStats"
                >
                  <el-option label="全部知识库" :value="null" />
                  <el-option
                    v-for="kb in knowledgeBaseList"
                    :key="'d-'+kb.id"
                    :label="kb.name"
                    :value="kb.id"
                  />
                </el-select>
              </div>
              <div class="header-top">
                <span class="header-sub-label">时间范围</span>
                <el-radio-group v-model="durationPeriod" size="small" @change="handlePeriodChange('duration')">
                  <el-radio-button value="day">1天</el-radio-button>
                  <el-radio-button value="week">7天</el-radio-button>
                  <el-radio-button value="month">本月</el-radio-button>
                  <el-radio-button value="custom">自定义</el-radio-button>
                </el-radio-group>
              </div>
              <div class="header-bottom">
                <el-date-picker
                  v-model="durationDateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                  size="small"
                  style="width: 280px"
                  @change="loadIngestionDurationStats"
                />
              </div>
            </div>
          </template>
          <div ref="durationChartEl" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第三行：Top N 知识库 + ETL 入库概览 -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <div class="header-top">
                <span>知识库使用频率 Top N</span>
                <div class="header-controls">
                  <el-radio-group v-model="kbPeriod" size="small" @change="handlePeriodChange('kb')">
                    <el-radio-button value="day">1天</el-radio-button>
                    <el-radio-button value="week">7天</el-radio-button>
                    <el-radio-button value="month">本月</el-radio-button>
                    <el-radio-button value="custom">自定义</el-radio-button>
                  </el-radio-group>
                  <el-input-number v-model="topN" :min="1" :max="20" size="small" @change="loadTopKB" style="width:120px" />
                </div>
              </div>
              <div class="header-bottom">
                <el-date-picker
                  v-model="kbDateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                  size="small"
                  style="width: 280px"
                  @change="loadTopKB"
                />
              </div>
            </div>
          </template>
          <el-table :data="topKBList" height="280" stripe style="width:100%">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="name" label="知识库" min-width="150" show-overflow-tooltip />
            <el-table-column prop="visit_count" label="访问会话数" width="120" sortable />
            <el-table-column prop="query_count" label="检索次数" width="120" sortable />
            <el-table-column prop="file_count" label="文件数" width="100" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <div class="header-top">
                <span>ETL 入库概览</span>
                <el-select
                  v-model="overviewKbId"
                  placeholder="全部知识库"
                  clearable
                  size="small"
                  style="width: 200px"
                  @change="loadIngestionOverview"
                >
                  <el-option label="全部知识库" :value="null" />
                  <el-option
                    v-for="kb in knowledgeBaseList"
                    :key="kb.id"
                    :label="kb.name"
                    :value="kb.id"
                  />
                </el-select>
              </div>
            </div>
          </template>
          <div ref="overviewChartEl" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 第四行：ETL 入库趋势 + RAG 检索精细化耗时统计（合并到同一栅格流，避免出现孤立半空行） -->
    <el-row :gutter="16">
      <el-col :xs="24" :md="12" class="stage-col">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <div class="header-top">
                <span>ETL 入库趋势</span>
                <el-radio-group v-model="ingestionPeriod" size="small" @change="handlePeriodChange('ingestion')">
                  <el-radio-button value="day">1天</el-radio-button>
                  <el-radio-button value="week">7天</el-radio-button>
                  <el-radio-button value="month">本月</el-radio-button>
                  <el-radio-button value="custom">自定义</el-radio-button>
                </el-radio-group>
              </div>
              <div class="header-bottom">
                <el-date-picker
                  v-model="ingestionDateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                  size="small"
                  style="width: 280px"
                  @change="loadIngestionTrend"
                />
              </div>
            </div>
          </template>
          <div ref="ingestionChartEl" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :md="12" v-for="(state, index) in stageStates" :key="state.key" class="stage-col">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <div class="header-top">
                <span>RAG 阶段耗时 · {{ state.label }}</span>
                <el-radio-group v-model="state.period" size="small" @change="handleStagePeriodChange(index)">
                  <el-radio-button value="day">1天</el-radio-button>
                  <el-radio-button value="week">7天</el-radio-button>
                  <el-radio-button value="month">本月</el-radio-button>
                  <el-radio-button value="custom">自定义</el-radio-button>
                </el-radio-group>
              </div>
              <div class="header-bottom">
                <el-date-picker
                  v-model="state.dateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                  size="small"
                  style="width: 280px"
                  @change="loadStageTrend(index)"
                />
              </div>
            </div>
          </template>
          <div ref="stageChartEls" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import request from '../../api/request'

// ── RAG 检索精细化耗时统计（每阶段一张独立看板） ──
const RAG_STAGES = [
  { key: 'REWRITE', label: '提示词改写' },
  { key: 'HYDE', label: 'HyDE 假设生成' },
  { key: 'CLASSIFY', label: '意图识别' },
  { key: 'RETRIEVE', label: '检索' },
  { key: 'RERANK', label: 'Rerank 重排序' },
  { key: 'LLM', label: '增强生成' },
]
const stageStates = reactive(
  RAG_STAGES.map((s) => ({ ...s, period: 'week', dateRange: null as [string, string] | null }))
)
const stageChartEls = ref<HTMLDivElement[]>([])
const stageCharts: (echarts.ECharts | null)[] = new Array(RAG_STAGES.length).fill(null)

// ── 顶部统计 ─
const overview = ref<any>({})

// ── 时间选择 ─
const tokenPeriod = ref('week')
const tokenUsageType = ref('')
const msgPeriod = ref('week')
const retrievalPeriod = ref('week')
const kbPeriod = ref('week')
const durationPeriod = ref('week')
const ingestionPeriod = ref('week')
const topN = ref(5)

// ── 知识库筛选 ─
const overviewKbId = ref<number | null>(null)
const durationKbId = ref<number | null>(null)
const knowledgeBaseList = ref<any[]>([])

// ── 自定义日期范围 ─
const tokenDateRange = ref<[string, string] | null>(null)
const msgDateRange = ref<[string, string] | null>(null)
const retrievalDateRange = ref<[string, string] | null>(null)
const kbDateRange = ref<[string, string] | null>(null)
const durationDateRange = ref<[string, string] | null>(null)
const ingestionDateRange = ref<[string, string] | null>(null)

// ── 图表 DOM ──
const tokenChartEl = ref<HTMLDivElement>()
const msgChartEl = ref<HTMLDivElement>()
const retrievalChartEl = ref<HTMLDivElement>()
const overviewChartEl = ref<HTMLDivElement>()
const durationChartEl = ref<HTMLDivElement>()
const ingestionChartEl = ref<HTMLDivElement>()

let tokenChart: echarts.ECharts | null = null
let msgChart: echarts.ECharts | null = null
let retrievalChart: echarts.ECharts | null = null
let overviewChart: echarts.ECharts | null = null
let durationChart: echarts.ECharts | null = null
let ingestionChart: echarts.ECharts | null = null

// ── 知识库列表 ──
const topKBList = ref<any[]>([])

// ═══════════════════════════════
//  数据加载
// ═══════════════════════════════

async function loadKnowledgeBaseList() {
  try {
    const json = await request.get('/knowledge-base/page', { params: { current: 1, pageSize: 100 } }) as any
    const data = json.data || json
    knowledgeBaseList.value = data.records || []
  } catch { /* 容错 */ }
}

async function loadOverview() {
  try {
    const json = await request.get('/dashboard/overview') as any
    overview.value = json.data || json || {}
  } catch { /* 容错 */ }
}

async function loadIngestionOverview() {
  try {
    const params: any = {}
    if (overviewKbId.value) {
      params.kbId = overviewKbId.value
    }
    const json = await request.get('/dashboard/ingestion/overview', { params }) as any
    const data = json.data || json || {}
    await nextTick()
    renderOverviewChart(data)
  } catch { /* 容错 */ }
}

async function loadIngestionDurationStats() {
  try {
    const params: any = { period: durationPeriod.value }
    if (durationPeriod.value === 'custom' && durationDateRange.value) {
      params.startDate = durationDateRange.value[0]
      params.endDate = durationDateRange.value[1]
    }
    if (durationKbId.value) {
      params.kbId = durationKbId.value
    }
    const json = await request.get('/dashboard/ingestion/duration-stats', { params }) as any
    const data = json.data || json || {}
    await nextTick()
    renderDurationChart(data)
  } catch { /* 容错 */ }
}

async function loadIngestionTrend() {
  try {
    const params: any = { period: ingestionPeriod.value }
    if (ingestionPeriod.value === 'custom' && ingestionDateRange.value) {
      params.startDate = ingestionDateRange.value[0]
      params.endDate = ingestionDateRange.value[1]
    }
    const json = await request.get('/dashboard/ingestion/trend', { params }) as any
    const data = json.data || json
    const list = data.trendData || []
    await nextTick()
    renderIngestionChart(list)
  } catch { /* 容错 */ }
}

async function loadStageTrend(index: number) {
  const state = stageStates[index]
  try {
    const params: any = { stage: state.key, period: state.period }
    if (state.period === 'custom' && state.dateRange) {
      params.startDate = state.dateRange[0]
      params.endDate = state.dateRange[1]
    }
    const json = await request.get('/dashboard/rag-stage-trend', { params }) as any
    const data = json.data || json
    const list = data.trendData || []
    await nextTick()
    renderStageChart(index, list)
  } catch { /* 容错 */ }
}

function handleStagePeriodChange(index: number) {
  const state = stageStates[index]
  if (state.period === 'custom' && !state.dateRange) return
  loadStageTrend(index)
}

// ── 切换 period 时，如果是 custom 但没有选日期，不请求；否则清空对应日期范围并请求 ──
function handlePeriodChange(chart: 'token' | 'msg' | 'retrieval' | 'kb' | 'duration' | 'ingestion') {
  if (chart === 'token') {
    if (tokenPeriod.value === 'custom' && !tokenDateRange.value) return
    loadTokenTrend()
  } else if (chart === 'msg') {
    if (msgPeriod.value === 'custom' && !msgDateRange.value) return
    loadMessageTrend()
  } else if (chart === 'retrieval') {
    if (retrievalPeriod.value === 'custom' && !retrievalDateRange.value) return
    loadRetrievalTrend()
  } else if (chart === 'duration') {
    if (durationPeriod.value === 'custom' && !durationDateRange.value) return
    loadIngestionDurationStats()
  } else if (chart === 'ingestion') {
    if (ingestionPeriod.value === 'custom' && !ingestionDateRange.value) return
    loadIngestionTrend()
  } else if (chart === 'kb') {
    if (kbPeriod.value === 'custom' && !kbDateRange.value) return
    loadTopKB()
  }
}

async function loadTokenTrend() {
  try {
    const params: any = { period: tokenPeriod.value }
    if (tokenPeriod.value === 'custom' && tokenDateRange.value) {
      params.startDate = tokenDateRange.value[0]
      params.endDate = tokenDateRange.value[1]
    }
    if (tokenUsageType.value) {
      params.usageType = tokenUsageType.value
    }
    const json = await request.get('/dashboard/token-trend', { params }) as any
    const data = json.data || json
    const list = data.trendData || []
    await nextTick()
    renderTokenChart(list)
  } catch { /* 容错 */ }
}

async function loadMessageTrend() {
  try {
    const params: any = { period: msgPeriod.value }
    if (msgPeriod.value === 'custom' && msgDateRange.value) {
      params.startDate = msgDateRange.value[0]
      params.endDate = msgDateRange.value[1]
    }
    const json = await request.get('/dashboard/message-trend', { params }) as any
    const data = json.data || json
    const list = data.trendData || []
    await nextTick()
    renderMsgChart(list)
  } catch { /* 容错 */ }
}

async function loadRetrievalTrend() {
  try {
    const params: any = { period: retrievalPeriod.value }
    if (retrievalPeriod.value === 'custom' && retrievalDateRange.value) {
      params.startDate = retrievalDateRange.value[0]
      params.endDate = retrievalDateRange.value[1]
    }
    const json = await request.get('/dashboard/retrieval-trend', { params }) as any
    const data = json.data || json
    const list = data.trendData || []
    await nextTick()
    renderRetrievalChart(list)
  } catch { /* 容错 */ }
}

async function loadTopKB() {
  try {
    const params: any = { period: kbPeriod.value, topN: topN.value }
    if (kbPeriod.value === 'custom' && kbDateRange.value) {
      params.startDate = kbDateRange.value[0]
      params.endDate = kbDateRange.value[1]
    }
    const json = await request.get('/dashboard/top-knowledge-bases', { params }) as any
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
        itemStyle: { color: '#409eff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64,158,255,0.35)' },
            { offset: 1, color: 'rgba(64,158,255,0.05)' },
          ]),
        },
      },
      {
        name: '最大耗时(ms)',
        type: 'line',
        smooth: true,
        data: list.map((d) => d.maxLatencyMs ?? 0),
        itemStyle: { color: '#f56c6c' },
        lineStyle: { type: 'dotted' }
      },
      {
        name: '最小耗时(ms)',
        type: 'line',
        smooth: true,
        data: list.map((d) => d.minLatencyMs ?? 0),
        itemStyle: { color: '#67c23a' },
        lineStyle: { type: 'dashed' }
      }
    ],
  })
}

function renderOverviewChart(data: any) {
  overviewChart?.dispose()
  if (!overviewChartEl.value) return
  overviewChart = echarts.init(overviewChartEl.value)

  const categories = ['原始文件数', '成功分块文件数', '失败分块文件数', '成功分块数', '向量入库分块数', '入库失败']
  const values = [
    data.totalFiles ?? 0,
    data.successChunkFiles ?? 0,
    data.failedChunkFiles ?? 0,
    data.totalChunks ?? 0,
    data.vectorizeFiles ?? 0,
    data.ingestionFailedFiles ?? 0
  ]
  const colors = ['#409eff', '#67c23a', '#f56c6c', '#e6a23c', '#909399', '#f44336']

  overviewChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 60, right: 20, top: 15, bottom: 30 },
    xAxis: {
      type: 'category',
      data: categories,
      axisLabel: {
        interval: 0,
        rotate: 15,
        fontSize: 11
      }
    },
    yAxis: { type: 'value', name: '数量', minInterval: 1 },
    series: [
      {
        type: 'bar',
        barWidth: '50%',
        data: values.map((v, i) => ({
          value: v,
          itemStyle: { color: colors[i] }
        })),
        label: {
          show: true,
          position: 'top',
          fontSize: 11
        }
      }
    ],
  })
}

function renderDurationChart(data: any) {
  durationChart?.dispose()
  if (!durationChartEl.value) return
  durationChart = echarts.init(durationChartEl.value)

  const stages = ['文件上传', '文件解析', '分块处理', '向量入库']
  const stageKeys = ['upload', 'parse', 'chunk', 'vectorize']

  const avgData = stageKeys.map(key => data[key]?.avg ?? 0)
  const maxData = stageKeys.map(key => data[key]?.max ?? 0)
  const minData = stageKeys.map(key => data[key]?.min ?? 0)

  // 检查是否有数据
  const hasData = avgData.some(v => v > 0) || maxData.some(v => v > 0) || minData.some(v => v > 0)

  if (!hasData) {
    durationChart.setOption({
      title: {
        text: '暂无数据',
        left: 'center',
        top: 'center',
        textStyle: { color: '#909399', fontSize: 14 }
      },
      xAxis: { show: false },
      yAxis: { show: false },
      series: []
    })
    return
  }

  durationChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    legend: {
      data: ['平均耗时', '最大耗时', '最小耗时'],
      top: '0%'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: stages,
      axisLabel: {
        interval: 0,
        rotate: 0
      }
    },
    yAxis: {
      type: 'value',
      name: '耗时(ms)',
      minInterval: 1
    },
    series: [
      {
        name: '平均耗时',
        type: 'bar',
        data: avgData,
        itemStyle: { color: '#409EFF' },
        label: {
          show: true,
          position: 'top',
          formatter: '{c}ms'
        }
      },
      {
        name: '最大耗时',
        type: 'bar',
        data: maxData,
        itemStyle: { color: '#F56C6C' },
        label: {
          show: true,
          position: 'top',
          formatter: '{c}ms'
        }
      },
      {
        name: '最小耗时',
        type: 'bar',
        data: minData,
        itemStyle: { color: '#67C23A' },
        label: {
          show: true,
          position: 'top',
          formatter: '{c}ms'
        }
      }
    ]
  })
}

function renderIngestionChart(list: any[]) {
  ingestionChart?.dispose()
  if (!ingestionChartEl.value) return
  ingestionChart = echarts.init(ingestionChartEl.value)

  // 空数据时显示"暂无数据"提示
  if (list.length === 0) {
    ingestionChart.setOption({
      title: {
        text: '暂无数据',
        left: 'center',
        top: 'center',
        textStyle: { color: '#909399', fontSize: 14 }
      },
      xAxis: { show: false },
      yAxis: { show: false },
      series: []
    })
    return
  }

  ingestionChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: {
      data: ['成功文档', '失败文档', '平均耗时(ms)'],
      bottom: 0,
      itemWidth: 12,
      itemGap: 10,
      textStyle: { fontSize: 11 }
    },
    grid: { left: 60, right: 60, top: 15, bottom: 50 },
    xAxis: {
      type: 'category',
      boundaryGap: true,
      data: list.map((d) => formatDateLabel(d.date)),
    },
    yAxis: [
      { type: 'value', name: '文档数', minInterval: 1 },
      { type: 'value', name: '耗时(ms)' }
    ],
    series: [
      {
        name: '成功文档',
        type: 'bar',
        data: list.map((d) => d.successDocs ?? 0),
        itemStyle: { color: '#67c23a' },
      },
      {
        name: '失败文档',
        type: 'bar',
        data: list.map((d) => d.failedDocs ?? 0),
        itemStyle: { color: '#f56c6c' },
      },
      {
        name: '平均耗时(ms)',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        data: list.map((d) => d.avgDurationMs ?? 0),
        itemStyle: { color: '#409eff' },
      }
    ],
  })
}

function renderStageChart(index: number, list: any[]) {
  stageCharts[index]?.dispose()
  const el = stageChartEls.value[index]
  if (!el || list.length === 0) return
  const chart = echarts.init(el)
  stageCharts[index] = chart
  chart.setOption({
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
        data: list.map((d) => d.avgDurationMs ?? 0),
        itemStyle: { color: '#409eff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64,158,255,0.35)' },
            { offset: 1, color: 'rgba(64,158,255,0.05)' },
          ]),
        },
      },
      {
        name: '最大耗时(ms)',
        type: 'line',
        smooth: true,
        data: list.map((d) => d.maxDurationMs ?? 0),
        itemStyle: { color: '#f56c6c' },
        lineStyle: { type: 'dotted' }
      },
      {
        name: '最小耗时(ms)',
        type: 'line',
        smooth: true,
        data: list.map((d) => d.minDurationMs ?? 0),
        itemStyle: { color: '#67c23a' },
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
  loadKnowledgeBaseList()
  loadIngestionOverview()
  loadIngestionDurationStats()
  loadIngestionTrend()
  loadTokenTrend()
  loadMessageTrend()
  loadRetrievalTrend()
  loadTopKB()
  stageStates.forEach((_, index) => loadStageTrend(index))

  // 窗口大小变化时重新调整图表
  window.addEventListener('resize', handleResize)
})

function handleResize() {
  tokenChart?.resize()
  msgChart?.resize()
  retrievalChart?.resize()
  ingestionChart?.resize()
  overviewChart?.resize()
  durationChart?.resize()
  stageCharts.forEach((chart) => chart?.resize())
}

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  tokenChart?.dispose()
  msgChart?.dispose()
  retrievalChart?.dispose()
  ingestionChart?.dispose()
  overviewChart?.dispose()
  durationChart?.dispose()
  stageCharts.forEach((chart) => chart?.dispose())
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

.stat-value-small {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.chart-card {
  border-radius: 8px;
}

.chart-card :deep(.el-card__header) {
  min-height: 76px;
  box-sizing: border-box;
}

.card-header {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  min-height: 60px;
}

.header-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: nowrap;
  width: 100%;
  min-height: 24px;
}

.chart-title {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}

.header-sub-label {
  font-size: 12px;
  color: #909399;
}

.header-controls {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: nowrap;
}

.header-bottom {
  display: flex;
  align-items: center;
  gap: 12px;
}

.chart-container {
  height: 280px;
}

.stage-col {
  margin-bottom: 16px;
}
</style>
