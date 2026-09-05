<template>
  <div>
    <h3>全局数据大盘</h3>
    
    <!-- 系统概览 -->
    <el-row :gutter="16" class="mb">
      <el-col :span="4"><el-statistic title="总用户" :value="overview.totalUsers || 0" /></el-col>
      <el-col :span="4"><el-statistic title="7天活跃" :value="overview.activeUsers7Days || 0" /></el-col>
      <el-col :span="4"><el-statistic title="总对话" :value="overview.totalConversations || 0" /></el-col>
      <el-col :span="4"><el-statistic title="总知识库" :value="overview.totalKnowledgeBases || 0" /></el-col>
      <el-col :span="4"><el-statistic title="总租户" :value="overview.totalTenants || 0" /></el-col>
      <el-col :span="4"><el-statistic title="平均延迟" :value="e2eLatency.avgDurationMs || 0" suffix="ms" /></el-col>
    </el-row>
    
    <!-- Token 消耗趋势 -->
    <el-row :gutter="16" class="mb">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <div class="header-top">
                <div style="display:flex;align-items:center;gap:8px">
                  <span style="font-weight:bold">Token 消耗趋势</span>
                  <el-select
                    v-model="tokenUserId"
                    placeholder="全部用户"
                    clearable
                    filterable
                    size="small"
                    style="width:140px"
                    @change="loadTokenTrend"
                  >
                    <el-option
                      v-for="user in userList"
                      :key="user.id"
                      :label="user.nickname || user.username"
                      :value="user.id"
                    />
                  </el-select>
                </div>
                <div style="display:flex;gap:8px;align-items:center">
                  <el-radio-group v-model="tokenTrendPeriod" size="small" @change="handleTokenPeriodChange">
                    <el-radio-button value="day">天</el-radio-button>
                    <el-radio-button value="week">近 7 天</el-radio-button>
                    <el-radio-button value="month">当月</el-radio-button>
                    <el-radio-button value="custom">自定义</el-radio-button>
                  </el-radio-group>
                </div>
              </div>
              <div class="header-bottom">
                <el-date-picker
                  v-model="tokenTrendDateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                  size="small"
                  style="width:280px"
                  @change="loadTokenTrend"
                />
              </div>
            </div>
          </template>
          <div ref="tokenTrendChart" style="height:300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <div class="header-top">
                <div style="display:flex;align-items:center;gap:8px">
                  <span style="font-weight:bold">用户活跃度趋势</span>
                  <el-select
                    v-model="activityUserId"
                    placeholder="全部用户"
                    clearable
                    filterable
                    size="small"
                    style="width:140px"
                    @change="loadActivityTrend"
                  >
                    <el-option
                      v-for="user in userList"
                      :key="user.id"
                      :label="user.nickname || user.username"
                      :value="user.id"
                    />
                  </el-select>
                </div>
                <div style="display:flex;gap:8px;align-items:center">
                  <el-radio-group v-model="activityTrendPeriod" size="small" @change="handleActivityPeriodChange">
                    <el-radio-button value="day">天</el-radio-button>
                    <el-radio-button value="week">近 7 天</el-radio-button>
                    <el-radio-button value="month">当月</el-radio-button>
                    <el-radio-button value="custom">自定义</el-radio-button>
                  </el-radio-group>
                </div>
              </div>
              <div class="header-bottom">
                <el-date-picker
                  v-model="activityTrendDateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                  size="small"
                  style="width:280px"
                  @change="loadActivityTrend"
                />
              </div>
            </div>
          </template>
          <div ref="activityTrendChart" style="height:300px"></div>
        </el-card>
      </el-col>
    </el-row>
    
    <!-- TopN 排行 -->
    <el-row :gutter="16" class="mb">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-weight:bold">活跃用户 Top{{ activeTopN }}</span>
              <el-input-number v-model="activeTopN" :min="5" :max="50" size="small" @change="loadTopActiveUsers" style="float:right" />
            </div>
          </template>
          <el-table :data="topActiveUsers" stripe max-height="400">
            <el-table-column prop="nickname" label="昵称" />
            <el-table-column prop="username" label="账号" />
            <el-table-column prop="conversationCount" label="对话数" sortable />
            <el-table-column prop="lastActiveAt" label="最后活跃" width="160" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-weight:bold">Token 消耗 Top{{ tokenTopN }}</span>
              <el-input-number v-model="tokenTopN" :min="5" :max="50" size="small" @change="loadTopTokenUsers" style="float:right" />
            </div>
          </template>
          <el-table :data="topTokenUsers" stripe max-height="400">
            <el-table-column prop="nickname" label="昵称" />
            <el-table-column prop="username" label="账号" />
            <el-table-column prop="totalTokens" label="总Token" sortable />
            <el-table-column prop="inputTokens" label="输入Token" />
            <el-table-column prop="outputTokens" label="输出Token" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
    
    <!-- 全链路阶段耗时 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-top">
            <div style="display:flex;align-items:center;gap:8px">
              <span style="font-weight:bold">全链路阶段耗时趋势</span>
              <el-select
                v-model="stageUserId"
                placeholder="全部用户"
                clearable
                filterable
                size="small"
                style="width:140px"
                @change="loadStageLatency"
              >
                <el-option
                  v-for="user in userList"
                  :key="user.id"
                  :label="user.nickname || user.username"
                  :value="user.id"
                />
              </el-select>
            </div>
            <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
              <el-radio-group v-model="stageLatencyPeriod" size="small" @change="handleStagePeriodChange">
                <el-radio-button value="day">天</el-radio-button>
                <el-radio-button value="week">近 7 天</el-radio-button>
                <el-radio-button value="month">当月</el-radio-button>
                <el-radio-button value="custom">自定义</el-radio-button>
              </el-radio-group>
              <el-date-picker
                v-model="stageLatencyDateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                size="small"
                style="width:240px"
                @change="loadStageLatency"
              />
              <el-button size="small" @click="loadStageLatency">刷新</el-button>
            </div>
          </div>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="12" v-for="stage in stageLatency" :key="stage.nodeType" class="mb">
          <el-card shadow="hover">
            <template #header>
              <div style="display:flex;justify-content:space-between;align-items:center">
                <span>{{ stage.displayName }}</span>
                <span style="color:#666;font-size:12px">
                  平均: {{ Math.round(stage.avgDurationMs) }}ms | 
                  最大: {{ Math.round(stage.maxDurationMs || 0) }}ms | 
                  总调用: {{ stage.totalCalls }}
                </span>
              </div>
            </template>
            <div :ref="el => setStageChartRef(stage.nodeType, el as HTMLElement)" style="height:200px"></div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="stageLatency.length === 0" description="暂无数据" />
    </el-card>

    <!-- 全链路详情列表 -->
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-top">
            <div style="display:flex;align-items:center;gap:8px">
              <span style="font-weight:bold">全链路详情</span>
              <el-select
                v-model="traceUserId"
                placeholder="全部用户"
                clearable
                filterable
                size="small"
                style="width:140px"
                @change="loadTraceDetails"
              >
                <el-option
                  v-for="user in userList"
                  :key="user.id"
                  :label="user.nickname || user.username"
                  :value="user.id"
                />
              </el-select>
              <el-input 
                v-model="traceSearchKeyword" 
                placeholder="搜索用户名/昵称" 
                clearable 
                size="small" 
                style="width:160px"
                @keyup.enter="loadTraceDetails"
                @clear="loadTraceDetails"
              />
            </div>
            <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
              <el-radio-group v-model="traceDetailPeriod" size="small" @change="handleTraceDetailPeriodChange">
                <el-radio-button value="day">天</el-radio-button>
                <el-radio-button value="week">近 7 天</el-radio-button>
                <el-radio-button value="month">当月</el-radio-button>
                <el-radio-button value="custom">自定义</el-radio-button>
              </el-radio-group>
              <el-date-picker
                v-model="traceDetailDateRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                size="small"
                style="width:240px"
                @change="loadTraceDetails"
              />
              <el-button size="small" @click="loadTraceDetails">搜索</el-button>
            </div>
          </div>
        </div>
      </template>
      <el-table :data="traceDetails" stripe row-key="traceId">
        <el-table-column label="用户" width="150">
          <template #default="{ row }">
            <div>
              <div v-if="row.nickname" style="font-weight:500">{{ row.nickname }}</div>
              <div style="color:#666;font-size:12px">{{ row.username || '匿名用户' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="原始问题" min-width="300">
          <template #default="{ row }">
            <el-button
              v-if="row.originalPrompt"
              type="primary"
              link
              class="prompt-link"
              @click="showPromptDialog(row.originalPrompt)"
            >
              <span class="prompt-text">{{ row.originalPrompt }}</span>
            </el-button>
            <span v-else style="color:#999">无</span>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="发起时间" width="160" />
        <el-table-column label="耗时(ms)" width="100">
          <template #default="{ row }">{{ Math.round(row.durationMs || 0) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'ERROR' ? 'danger' : 'warning'" size="small">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="goTraceDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top:16px;display:flex;justify-content:flex-end">
        <el-pagination
          v-model:current-page="traceDetailPage"
          v-model:page-size="traceDetailPageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="traceDetailTotal"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadTraceDetails"
          @current-change="loadTraceDetails"
        />
      </div>
    </el-card>

    <!-- 知识库统计 -->
    <el-card class="mb">
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span style="font-weight:bold">知识库统计</span>
          <el-select v-model="kbStatsUserId" placeholder="全部用户" clearable filterable size="small" style="width:140px" @change="loadKbStats">
            <el-option v-for="user in userList" :key="user.id" :label="user.nickname || user.username" :value="user.id" />
          </el-select>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="6"><el-statistic title="知识库总数" :value="kbStats.kbCount || 0" /></el-col>
        <el-col :span="6"><el-statistic title="文档总数" :value="kbStats.docCount || 0" /></el-col>
        <el-col :span="6"><el-statistic title="分块总数" :value="kbStats.totalChunks || 0" /></el-col>
        <el-col :span="6"><el-statistic title="总大小(MB)" :value="kbStats.totalSizeMb || 0" /></el-col>
      </el-row>
    </el-card>

    <!-- ETL 入库概览 + 知识库使用 TopN -->
    <el-row :gutter="16" class="mb">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span style="font-weight:bold">ETL 入库概览</span>
              <el-select v-model="etlOverviewUserId" placeholder="全部用户" clearable filterable size="small" style="width:140px" @change="loadEtlOverview">
                <el-option v-for="user in userList" :key="user.id" :label="user.nickname || user.username" :value="user.id" />
              </el-select>
            </div>
          </template>
          <div ref="etlOverviewChart" style="height:300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <div class="header-top">
                <span style="font-weight:bold">知识库使用频率 TopN</span>
                <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
                  <el-select v-model="kbTopUserId" placeholder="全部用户" clearable filterable size="small" style="width:120px" @change="loadKbTop">
                    <el-option v-for="user in userList" :key="user.id" :label="user.nickname || user.username" :value="user.id" />
                  </el-select>
                  <el-radio-group v-model="kbTopPeriod" size="small" @change="loadKbTop">
                    <el-radio-button value="day">天</el-radio-button>
                    <el-radio-button value="week">近 7 天</el-radio-button>
                    <el-radio-button value="month">当月</el-radio-button>
                    <el-radio-button value="custom">自定义</el-radio-button>
                  </el-radio-group>
                  <el-date-picker v-model="kbTopDateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" format="YYYY-MM-DD" value-format="YYYY-MM-DD" size="small" style="width:240px" @change="loadKbTop" />
                  <el-input-number v-model="kbTopN" :min="1" :max="20" size="small" style="width:100px" @change="loadKbTop" />
                </div>
              </div>
            </div>
          </template>
          <el-table :data="kbTopList" stripe height="300">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="name" label="知识库" min-width="140" show-overflow-tooltip />
            <el-table-column prop="visit_count" label="访问会话数" width="110" sortable />
            <el-table-column prop="query_count" label="检索次数" width="100" sortable />
            <el-table-column prop="file_count" label="文件数" width="90" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- ETL 入库耗时 + ETL 入库趋势 -->
    <el-row :gutter="16" class="mb">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <div class="header-top">
                <span style="font-weight:bold">ETL 入库耗时统计</span>
                <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
                  <el-select v-model="etlDurationUserId" placeholder="全部用户" clearable filterable size="small" style="width:120px" @change="loadEtlDuration">
                    <el-option v-for="user in userList" :key="user.id" :label="user.nickname || user.username" :value="user.id" />
                  </el-select>
                  <el-radio-group v-model="etlDurationPeriod" size="small" @change="handleEtlDurationPeriodChange">
                    <el-radio-button value="day">天</el-radio-button>
                    <el-radio-button value="week">近 7 天</el-radio-button>
                    <el-radio-button value="month">当月</el-radio-button>
                    <el-radio-button value="custom">自定义</el-radio-button>
                  </el-radio-group>
                  <el-date-picker v-model="etlDurationDateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" format="YYYY-MM-DD" value-format="YYYY-MM-DD" size="small" style="width:240px" @change="loadEtlDuration" />
                </div>
              </div>
            </div>
          </template>
          <div ref="etlDurationChart" style="height:300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <div class="header-top">
                <span style="font-weight:bold">ETL 入库趋势</span>
                <div style="display:flex;gap:8px;align-items:center;flex-wrap:wrap">
                  <el-select v-model="etlTrendUserId" placeholder="全部用户" clearable filterable size="small" style="width:120px" @change="loadEtlTrend">
                    <el-option v-for="user in userList" :key="user.id" :label="user.nickname || user.username" :value="user.id" />
                  </el-select>
                  <el-radio-group v-model="etlTrendPeriod" size="small" @change="handleEtlTrendPeriodChange">
                    <el-radio-button value="day">天</el-radio-button>
                    <el-radio-button value="week">近 7 天</el-radio-button>
                    <el-radio-button value="month">当月</el-radio-button>
                    <el-radio-button value="custom">自定义</el-radio-button>
                  </el-radio-group>
                  <el-date-picker v-model="etlTrendDateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" format="YYYY-MM-DD" value-format="YYYY-MM-DD" size="small" style="width:240px" @change="loadEtlTrend" />
                </div>
              </div>
            </div>
          </template>
          <div ref="etlTrendChart" style="height:300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 原始问题查看对话框 -->
    <el-dialog v-model="promptDialogVisible" title="原始问题" width="60%">
      <pre style="max-height:500px;overflow:auto;background:#f5f7fa;padding:12px;border-radius:4px;font-size:13px;white-space:pre-wrap;word-break:break-all">{{ promptDialogContent }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import request from '../../api/request'
import * as echarts from 'echarts'

const router = useRouter()

const overview = ref<any>({})
const e2eLatency = ref<any>({})
const tokenTrendPeriod = ref('week')
const tokenTrendDateRange = ref<[string, string] | null>(null)
const tokenUserId = ref<number | null>(null)
const activityTrendPeriod = ref('week')
const activityTrendDateRange = ref<[string, string] | null>(null)
const activityUserId = ref<number | null>(null)
const userList = ref<any[]>([])
const activeTopN = ref(10)
const tokenTopN = ref(10)
const stageTopN = ref(10)
const topActiveUsers = ref<any[]>([])
const topTokenUsers = ref<any[]>([])
const stageLatency = ref<any[]>([])

// 链路详情相关
const traceSearchKeyword = ref('')
const traceDetailPeriod = ref('week')
const traceDetailDateRange = ref<[string, string] | null>(null)
const traceDetailPage = ref(1)
const traceDetailPageSize = ref(20)
const traceDetailTotal = ref(0)
const traceDetails = ref<any[]>([])
const traceUserId = ref<number | null>(null)

// 阶段耗时筛选
const stageUserId = ref<number | null>(null)

// 原始问题查看对话框
const promptDialogVisible = ref(false)
const promptDialogContent = ref('')

const tokenTrendChart = ref<HTMLDivElement>()
const activityTrendChart = ref<HTMLDivElement>()

// ECharts 实例引用，用于防止内存泄漏
let tokenTrendChartInstance: echarts.ECharts | null = null
let activityTrendChartInstance: echarts.ECharts | null = null
const stageChartInstances = new Map<string, echarts.ECharts>()
const stageChartRefs = new Map<string, HTMLDivElement | HTMLElement>()

// 全链路阶段耗时的时间范围
const stageLatencyPeriod = ref('week')
const stageLatencyDateRange = ref<[string, string] | null>(null)

// ═══ 知识库统计 + ETL 看板 ═══
const kbStats = ref<any>({})
const kbStatsUserId = ref<any>(null)
const etlOverviewUserId = ref<any>(null)
const kbTopUserId = ref<any>(null)
const kbTopPeriod = ref('week')
const kbTopDateRange = ref<[string, string] | null>(null)
const kbTopN = ref(5)
const kbTopList = ref<any[]>([])
const etlDurationUserId = ref<any>(null)
const etlDurationPeriod = ref('week')
const etlDurationDateRange = ref<[string, string] | null>(null)
const etlTrendUserId = ref<any>(null)
const etlTrendPeriod = ref('week')
const etlTrendDateRange = ref<[string, string] | null>(null)

const etlOverviewChart = ref<HTMLDivElement>()
const etlDurationChart = ref<HTMLDivElement>()
const etlTrendChart = ref<HTMLDivElement>()
let etlOverviewChartInstance: echarts.ECharts | null = null
let etlDurationChartInstance: echarts.ECharts | null = null
let etlTrendChartInstance: echarts.ECharts | null = null

function buildPeriodParams(period: string, range: [string, string] | null, userId: any): any {
  const params: any = { period }
  if (period === 'custom' && range) {
    params.startDate = range[0]
    params.endDate = range[1]
  }
  if (userId) params.userId = userId
  return params
}

async function loadKbStats() {
  try {
    const params: any = {}
    if (kbStatsUserId.value) params.userId = kbStatsUserId.value
    const res = await request.get('/admin/dashboard/kb-stats', { params }) as any
    kbStats.value = res.data || res || {}
  } catch { /* 容错 */ }
}

async function loadEtlOverview() {
  try {
    const params: any = {}
    if (etlOverviewUserId.value) params.userId = etlOverviewUserId.value
    const res = await request.get('/admin/dashboard/ingestion/overview', { params }) as any
    const data = res.data || res || {}
    await nextTick()
    if (!etlOverviewChart.value) return
    etlOverviewChartInstance?.dispose()
    etlOverviewChartInstance = echarts.init(etlOverviewChart.value)
    const categories = ['原始文件数', '成功分块文件数', '失败分块文件数', '成功分块数', '向量入库分块数', '入库失败']
    const values = [data.totalFiles ?? 0, data.successChunkFiles ?? 0, data.failedChunkFiles ?? 0, data.totalChunks ?? 0, data.vectorizeFiles ?? 0, data.ingestionFailedFiles ?? 0]
    const colors = ['#409eff', '#67c23a', '#f56c6c', '#e6a23c', '#909399', '#f44336']
    etlOverviewChartInstance.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 60, right: 20, top: 15, bottom: 30 },
      xAxis: { type: 'category', data: categories, axisLabel: { interval: 0, rotate: 15, fontSize: 11 } },
      yAxis: { type: 'value', name: '数量', minInterval: 1 },
      series: [{ type: 'bar', barWidth: '50%', data: values.map((v, i) => ({ value: v, itemStyle: { color: colors[i] } })), label: { show: true, position: 'top', fontSize: 11 } }]
    })
  } catch { /* 容错 */ }
}

async function loadKbTop() {
  try {
    if (kbTopPeriod.value === 'custom' && !kbTopDateRange.value) return
    const params = buildPeriodParams(kbTopPeriod.value, kbTopDateRange.value, kbTopUserId.value)
    params.topN = kbTopN.value
    const res = await request.get('/admin/dashboard/top-knowledge-bases', { params }) as any
    const data = res.data || res
    kbTopList.value = data.knowledgeBases || []
  } catch { /* 容错 */ }
}

async function loadEtlDuration() {
  try {
    if (etlDurationPeriod.value === 'custom' && !etlDurationDateRange.value) return
    const params = buildPeriodParams(etlDurationPeriod.value, etlDurationDateRange.value, etlDurationUserId.value)
    const res = await request.get('/admin/dashboard/ingestion/duration-stats', { params }) as any
    const data = res.data || res || {}
    await nextTick()
    if (!etlDurationChart.value) return
    etlDurationChartInstance?.dispose()
    etlDurationChartInstance = echarts.init(etlDurationChart.value)
    const stages = ['文件上传', '文件解析', '分块处理', '向量入库']
    const keys = ['upload', 'parse', 'chunk', 'vectorize']
    const avgData = keys.map(k => data[k]?.avg ?? 0)
    const maxData = keys.map(k => data[k]?.max ?? 0)
    const minData = keys.map(k => data[k]?.min ?? 0)
    const hasData = [...avgData, ...maxData, ...minData].some(v => v > 0)
    if (!hasData) {
      etlDurationChartInstance.setOption({ title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#909399', fontSize: 14 } }, xAxis: { show: false }, yAxis: { show: false }, series: [] })
      return
    }
    etlDurationChartInstance.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { data: ['平均耗时', '最大耗时', '最小耗时'], top: '0%' },
      grid: { left: '3%', right: '4%', bottom: '3%', top: '15%', containLabel: true },
      xAxis: { type: 'category', data: stages },
      yAxis: { type: 'value', name: '耗时(ms)', minInterval: 1 },
      series: [
        { name: '平均耗时', type: 'bar', data: avgData, itemStyle: { color: '#409EFF' }, label: { show: true, position: 'top', formatter: '{c}ms' } },
        { name: '最大耗时', type: 'bar', data: maxData, itemStyle: { color: '#F56C6C' }, label: { show: true, position: 'top', formatter: '{c}ms' } },
        { name: '最小耗时', type: 'bar', data: minData, itemStyle: { color: '#67C23A' }, label: { show: true, position: 'top', formatter: '{c}ms' } }
      ]
    })
  } catch { /* 容错 */ }
}

function handleEtlDurationPeriodChange() { loadEtlDuration() }

async function loadEtlTrend() {
  try {
    if (etlTrendPeriod.value === 'custom' && !etlTrendDateRange.value) return
    const params = buildPeriodParams(etlTrendPeriod.value, etlTrendDateRange.value, etlTrendUserId.value)
    const res = await request.get('/admin/dashboard/ingestion/trend', { params }) as any
    const data = res.data || res
    const list = data.trendData || []
    await nextTick()
    if (!etlTrendChart.value) return
    etlTrendChartInstance?.dispose()
    etlTrendChartInstance = echarts.init(etlTrendChart.value)
    if (list.length === 0) {
      etlTrendChartInstance.setOption({ title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#909399', fontSize: 14 } }, xAxis: { show: false }, yAxis: { show: false }, series: [] })
      return
    }
    etlTrendChartInstance.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['成功文档', '失败文档', '平均耗时(ms)'], bottom: 0 },
      grid: { left: 60, right: 60, top: 15, bottom: 50 },
      xAxis: { type: 'category', boundaryGap: true, data: list.map((d: any) => d.date) },
      yAxis: [{ type: 'value', name: '文档数', minInterval: 1 }, { type: 'value', name: '耗时(ms)' }],
      series: [
        { name: '成功文档', type: 'bar', data: list.map((d: any) => d.successDocs ?? 0), itemStyle: { color: '#67c23a' } },
        { name: '失败文档', type: 'bar', data: list.map((d: any) => d.failedDocs ?? 0), itemStyle: { color: '#f56c6c' } },
        { name: '平均耗时(ms)', type: 'line', yAxisIndex: 1, smooth: true, data: list.map((d: any) => d.avgDurationMs ?? 0), itemStyle: { color: '#409eff' } }
      ]
    })
  } catch { /* 容错 */ }
}

function handleEtlTrendPeriodChange() { loadEtlTrend() }

onMounted(async () => {
  await loadUserList()
  await Promise.all([
    loadOverview(),
    loadE2ELatency(),
    loadTokenTrend(),
    loadActivityTrend(),
    loadTopActiveUsers(),
    loadTopTokenUsers(),
    loadStageLatency(),
    loadTraceDetails(),
    loadKbStats(),
    loadEtlOverview(),
    loadKbTop(),
    loadEtlDuration(),
    loadEtlTrend()
  ])
})

async function loadUserList() {
  try {
    const res = await request.get('/admin/dashboard/user-list') as any
    const data = res.data || res
    userList.value = data.users || []
  } catch (e) { console.error(e) }
}

async function loadOverview() {
  try {
    const res = await request.get('/admin/dashboard/overview') as any
    const data = res.data || res
    // 后端返回嵌套结构 {systemStats:{...}, ragStats:{...}, ...}，前端需要扁平化
    const sys = data.systemStats || {}
    const e2e = await request.get('/admin/dashboard/e2e-latency').then((r: any) => r.data || r).catch(() => ({}))
    overview.value = {
      totalUsers: sys.totalUsers || 0,
      activeUsers7Days: sys.activeUsers7Days !== undefined ? sys.activeUsers7Days : (data.activeUsers7Days || 0),
      totalConversations: sys.totalConversations || 0,
      totalKnowledgeBases: sys.totalKnowledgeBases || 0,
      totalTenants: sys.totalTenants || 0,
      avgDurationMs: e2e.avgDurationMs || 0
    }
  } catch (e) { console.error(e) }
}

async function loadE2ELatency() {
  try {
    const res = await request.get('/admin/dashboard/e2e-latency')
    e2eLatency.value = (res as any).data || res
  } catch (e) { console.error(e) }
}

async function loadTokenTrend() {
  try {
    const params: any = { period: tokenTrendPeriod.value }
    if (tokenTrendPeriod.value === 'custom' && tokenTrendDateRange.value) {
      params.startDate = tokenTrendDateRange.value[0]
      params.endDate = tokenTrendDateRange.value[1]
    }
    if (tokenUserId.value) {
      params.userId = tokenUserId.value
    }
    const res = await request.get('/admin/dashboard/token-trend', { params }) as any
    const data = res.data || res
    const trendData = data.trendData || []
    await nextTick()
    if (tokenTrendChart.value) {
      // 清理旧实例
      if (tokenTrendChartInstance) {
        tokenTrendChartInstance.dispose()
      }
      // 创建新实例
      tokenTrendChartInstance = echarts.init(tokenTrendChart.value)
      tokenTrendChartInstance.setOption({
        tooltip: { trigger: 'axis' },
        legend: { data: ['输入Token', '输出Token'] },
        xAxis: { 
          type: 'category', 
          data: trendData.length > 0 ? trendData.map((d: any) => d.date) : ['暂无数据']
        },
        yAxis: { type: 'value' },
        series: [
          { 
            name: '输入Token', 
            type: 'line', 
            data: trendData.length > 0 ? trendData.map((d: any) => d.inputTokens || 0) : [0], 
            smooth: true, 
            areaStyle: {} 
          },
          { 
            name: '输出Token', 
            type: 'line', 
            data: trendData.length > 0 ? trendData.map((d: any) => d.outputTokens || 0) : [0], 
            smooth: true, 
            areaStyle: {} 
          }
        ]
      })
    }
  } catch (e) { console.error(e) }
}

async function loadActivityTrend() {
  try {
    const params: any = { period: activityTrendPeriod.value }
    if (activityTrendPeriod.value === 'custom' && activityTrendDateRange.value) {
      params.startDate = activityTrendDateRange.value[0]
      params.endDate = activityTrendDateRange.value[1]
    }
    if (activityUserId.value) {
      params.userId = activityUserId.value
    }
    const res = await request.get('/admin/dashboard/user-activity-trend', { params }) as any
    const data = res.data || res
    const trendData = data.trendData || []
    await nextTick()
    if (activityTrendChart.value) {
      // 清理旧实例
      if (activityTrendChartInstance) {
        activityTrendChartInstance.dispose()
      }
      // 创建新实例
      activityTrendChartInstance = echarts.init(activityTrendChart.value)
      activityTrendChartInstance.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: { 
          type: 'category', 
          data: trendData.length > 0 ? trendData.map((d: any) => d.date) : ['暂无数据']
        },
        yAxis: { type: 'value' },
        series: [
          { 
            name: '活跃用户', 
            type: 'line', 
            data: trendData.length > 0 ? trendData.map((d: any) => d.activeUsers || 0) : [0], 
            smooth: true, 
            areaStyle: {} 
          }
        ]
      })
    }
  } catch (e) { console.error(e) }
}

function handleTokenPeriodChange() {
  loadTokenTrend()
}

function handleActivityPeriodChange() {
  loadActivityTrend()
}

async function loadTopActiveUsers() {
  try {
    const res = await request.get(`/admin/dashboard/top-active-users?limit=${activeTopN.value}`) as any
    const data = res.data || res
    // 后端返回 snake_case (conversation_count, last_active_at)，前端需要 camelCase
    topActiveUsers.value = (data.users || []).map((u: any) => ({
      ...u,
      conversationCount: u.conversationCount ?? u.conversation_count ?? 0,
      lastActiveAt: u.lastActiveAt ?? u.last_active_at ?? null
    }))
  } catch (e) { console.error(e) }
}

async function loadTopTokenUsers() {
  try {
    const res = await request.get(`/admin/dashboard/top-token-users?limit=${tokenTopN.value}`) as any
    const data = res.data || res
    // 后端返回 snake_case (input_tokens, output_tokens, total_tokens)，前端需要 camelCase
    topTokenUsers.value = (data.users || []).map((u: any) => ({
      ...u,
      totalTokens: u.totalTokens ?? u.total_tokens ?? 0,
      inputTokens: u.inputTokens ?? u.input_tokens ?? 0,
      outputTokens: u.outputTokens ?? u.output_tokens ?? 0
    }))
  } catch (e) { console.error(e) }
}

async function loadStageLatency() {
  try {
    const params: any = { limit: stageTopN.value, period: stageLatencyPeriod.value }
    if (stageLatencyPeriod.value === 'custom' && stageLatencyDateRange.value) {
      params.startDate = stageLatencyDateRange.value[0]
      params.endDate = stageLatencyDateRange.value[1]
    }
    if (stageUserId.value) {
      params.userId = stageUserId.value
    }
    const res = await request.get('/admin/dashboard/stage-latency', { params }) as any
    const data = res.data || res
    stageLatency.value = data.stages || []
    
    // 等待 DOM 更新后绘制图表
    await nextTick()
    
    // 为每个阶段绘制折线图（显示 3 条线：最大/最小/平均）
    for (const stage of stageLatency.value) {
      const chartRef = stageChartRefs.get(stage.nodeType)
      if (chartRef) {
        // 清理旧实例
        const oldInstance = stageChartInstances.get(stage.nodeType)
        if (oldInstance) {
          oldInstance.dispose()
        }
        // 创建新实例
        const chart = echarts.init(chartRef)
        stageChartInstances.set(stage.nodeType, chart)
        
        const trendData = stage.trendData || []
        chart.setOption({
          tooltip: { 
            trigger: 'axis',
            formatter: (params: any) => {
              const date = params[0]?.axisValue || ''
              let result = `<strong>${date}</strong><br/>`
              for (const p of params) {
                result += `${p.marker} ${p.seriesName}: <strong>${Math.round(p.value)}ms</strong><br/>`
              }
              return result
            }
          },
          legend: { data: ['最大耗时', '平均耗时', '最小耗时'], bottom: 0 },
          grid: { left: '10%', right: '5%', bottom: '20%', top: '10%' },
          xAxis: {
            type: 'category',
            data: trendData.length > 0 ? trendData.map((d: any) => d.date) : ['暂无数据']
          },
          yAxis: { type: 'value', name: 'ms' },
          series: [
            {
              name: '最大耗时',
              type: 'line',
              data: trendData.length > 0 ? trendData.map((d: any) => d.maxDurationMs || 0) : [0],
              smooth: true,
              lineStyle: { type: 'dashed' },
              itemStyle: { color: '#F56C6C' }
            },
            {
              name: '平均耗时',
              type: 'line',
              data: trendData.length > 0 ? trendData.map((d: any) => d.avgDurationMs || 0) : [0],
              smooth: true,
              areaStyle: { opacity: 0.3 },
              itemStyle: { color: '#409EFF' }
            },
            {
              name: '最小耗时',
              type: 'line',
              data: trendData.length > 0 ? trendData.map((d: any) => d.minDurationMs || 0) : [0],
              smooth: true,
              lineStyle: { type: 'dashed' },
              itemStyle: { color: '#67C23A' }
            }
          ]
        })
      }
    }
  } catch (e) { console.error(e) }
}

function setStageChartRef(nodeType: string, el: HTMLElement | null) {
  if (el) {
    stageChartRefs.set(nodeType, el)
  }
}

function handleStagePeriodChange() {
  loadStageLatency()
}

async function loadTraceDetails() {
  try {
    const params: any = {
      keyword: traceSearchKeyword.value,
      period: traceDetailPeriod.value,
      page: traceDetailPage.value,
      pageSize: traceDetailPageSize.value
    }
    if (traceDetailPeriod.value === 'custom' && traceDetailDateRange.value) {
      params.startDate = traceDetailDateRange.value[0]
      params.endDate = traceDetailDateRange.value[1]
    }
    if (traceUserId.value) {
      params.userId = traceUserId.value
    }
    const res = await request.get('/admin/dashboard/trace-details', { params }) as any
    const data = res.data || res
    traceDetailTotal.value = data.total || 0
    // 后端 JdbcTemplate 返回 snake_case，统一映射为 camelCase
    traceDetails.value = (data.data || []).map(mapTraceRow)
  } catch (e) {
    console.error(e)
    traceDetails.value = []
    traceDetailTotal.value = 0
  }
}

// 链路列表行 snake_case → camelCase 映射
function mapTraceRow(item: any) {
  return {
    id: item.id,
    traceId: item.trace_id ?? item.traceId,
    conversationId: item.conversation_id ?? item.conversationId,
    originalPrompt: item.original_prompt ?? item.originalPrompt,
    status: item.status,
    durationMs: item.duration_ms ?? item.durationMs,
    createTime: formatDateTime(item.create_time ?? item.createTime),
    userId: item.user_id ?? item.userId,
    username: item.username,
    nickname: item.nickname
  }
}

// 格式化时间：兼容 ISO 字符串 / epoch 毫秒
function formatDateTime(val: any): string {
  if (!val) return ''
  const d = new Date(val)
  if (isNaN(d.getTime())) return String(val)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function showPromptDialog(content: string) {
  promptDialogContent.value = content
  promptDialogVisible.value = true
}

// 跳转链路详情页
function goTraceDetail(row: any) {
  if (!row.traceId) return
  router.push(`/manage/trace/${row.traceId}`)
}

function handleTraceDetailPeriodChange() {
  traceDetailPage.value = 1
  loadTraceDetails()
}

// 页面卸载时清理 ECharts 实例
onBeforeUnmount(() => {
  if (tokenTrendChartInstance) {
    tokenTrendChartInstance.dispose()
    tokenTrendChartInstance = null
  }
  if (activityTrendChartInstance) {
    activityTrendChartInstance.dispose()
    activityTrendChartInstance = null
  }
  etlOverviewChartInstance?.dispose()
  etlDurationChartInstance?.dispose()
  etlTrendChartInstance?.dispose()
  // 清理阶段耗时图表实例
  for (const instance of stageChartInstances.values()) {
    instance.dispose()
  }
  stageChartInstances.clear()
  stageChartRefs.clear()
})
</script>

<style scoped>
.mb { margin-bottom: 16px; }
.mt { margin-top: 16px; }

.card-header {
  display: flex;
  flex-direction: column;
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

.header-bottom {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 原始问题：单行截断，点击弹窗看全文 */
.prompt-link {
  max-width: 100%;
  justify-content: flex-start;
}

.prompt-text {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #303133;
}
</style>
