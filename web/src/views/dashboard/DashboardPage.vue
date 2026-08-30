<template>
  <div class="dashboard-page">
    <!-- Token 统计卡片 -->
    <el-row :gutter="20" class="stats-row">
      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-label">今日消耗</div>
            <div class="stat-value">{{ tokenStats.todayTotal || 0 }}</div>
            <div class="stat-unit">Token</div>
            <div class="stat-cost">¥{{ (tokenStats.todayCost || 0).toFixed(3) }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-label">本月消耗</div>
            <div class="stat-value">{{ tokenStats.monthTotal || 0 }}</div>
            <div class="stat-unit">Token</div>
            <div class="stat-cost">¥{{ (tokenStats.monthCost || 0).toFixed(2) }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-label">剩余额度</div>
            <div class="stat-value">{{ tokenStats.remainingTokens || 0 }}</div>
            <div class="stat-unit">Token</div>
            <div class="stat-cost">约 {{ tokenStats.estimatedDays || 0 }} 天</div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :sm="12" :md="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-label">命中率</div>
            <div class="stat-value">{{ retrievalStats.hitRate || 0 }}%</div>
            <div class="stat-unit">命中</div>
            <div class="stat-cost">{{ retrievalStats.hitQueries || 0 }} / {{ retrievalStats.totalQueries || 0 }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Token 消耗趋势和命中率趋势 -->
    <el-row :gutter="20" class="chart-row">
      <el-col :xs="24" :md="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>Token 消耗趋势（最近 7 天）</span>
              <el-button text size="small" @click="refreshTokenTrend">刷新</el-button>
            </div>
          </template>
          <div ref="tokenTrendChart" class="chart-container"></div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12">
        <el-card class="chart-card">
          <template #header>
            <div class="card-header">
              <span>命中率趋势（最近 7 天）</span>
              <el-button text size="small" @click="refreshRetrievalTrend">刷新</el-button>
            </div>
          </template>
          <div ref="retrievalTrendChart" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 热门知识库和最近对话 -->
    <el-row :gutter="20" class="data-row">
      <el-col :xs="24" :md="12">
        <el-card class="data-card">
          <template #header>
            <span>热门知识库 Top 5</span>
          </template>
          <el-table :data="topKnowledgeBases" style="width: 100%">
            <el-table-column prop="name" label="知识库" width="150" />
            <el-table-column prop="usageCount" label="使用次数" width="100" />
            <el-table-column prop="lastUsedTime" label="最后使用" width="120">
              <template #default="{ row }">
                {{ formatDate(row.lastUsedTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="viewKB(row.id)">
                  查看
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12">
        <el-card class="data-card">
          <template #header>
            <span>最近对话 Top 10</span>
          </template>
          <el-table :data="recentConversations" style="width: 100%">
            <el-table-column prop="title" label="对话主题" width="150" show-overflow-tooltip />
            <el-table-column prop="messageCount" label="消息数" width="80" />
            <el-table-column prop="lastMessageTime" label="时间" width="120">
              <template #default="{ row }">
                {{ formatDate(row.lastMessageTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="viewConversation(row.id)">
                  查看
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import request from '../../api/request'

const router = useRouter()

// Token 统计
const tokenStats = ref<any>({
  todayTotal: 0,
  todayCost: 0,
  monthTotal: 0,
  monthCost: 0,
  remainingTokens: 0,
  estimatedDays: 0
})

// 检索统计
const retrievalStats = ref<any>({
  totalQueries: 0,
  hitQueries: 0,
  hitRate: 0
})

// Token 趋势数据
const tokenTrendData = ref<any[]>([])

// 命中率趋势数据
const retrievalTrendData = ref<any[]>([])

// 热门知识库
const topKnowledgeBases = ref<any[]>([])

// 最近对话
const recentConversations = ref<any[]>([])

// 图表引用
const tokenTrendChart = ref<HTMLDivElement>()
const retrievalTrendChart = ref<HTMLDivElement>()

// 方法
const formatDate = (dateStr: string | Date): string => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  const diffHours = Math.floor(diffMs / 3600000)
  const diffDays = Math.floor(diffMs / 86400000)

  if (diffMins < 1) return '刚刚'
  if (diffMins < 60) return `${diffMins}分钟前`
  if (diffHours < 24) return `${diffHours}小时前`
  if (diffDays < 7) return `${diffDays}天前`
  return date.toLocaleDateString('zh-CN')
}

async function fetchTokenStats() {
  try {
    const json = await request.get('/dashboard/token-stats') as any
    if (json.code === 0 && json.data) {
      tokenStats.value = { ...tokenStats.value, ...json.data }
    }
  } catch (e) {
    console.error('获取 Token 统计失败', e)
  }
}

async function fetchTokenTrend() {
  try {
    const json = await request.get('/dashboard/token-trend', { params: { days: 7 } }) as any
    if (json.code === 0 && json.data) {
      tokenTrendData.value = json.data.trend || json.data.trendData || []
      await nextTick()
      initTokenTrendChart()
    }
  } catch (e) {
    console.error('获取 Token 趋势失败', e)
  }
}

async function fetchRetrievalStats() {
  try {
    const json = await request.get('/dashboard/retrieval-stats') as any
    if (json.code === 0 && json.data) {
      retrievalStats.value = { ...retrievalStats.value, ...json.data }
    }
  } catch (e) {
    console.error('获取检索统计失败', e)
  }
}

async function fetchRetrievalTrend() {
  try {
    const json = await request.get('/dashboard/retrieval-trend', { params: { days: 7 } }) as any
    if (json.code === 0 && json.data) {
      retrievalTrendData.value = json.data.trend || json.data.trendData || []
      await nextTick()
      initRetrievalTrendChart()
    }
  } catch (e) {
    console.error('获取检索趋势失败', e)
  }
}

async function fetchTopKnowledgeBases() {
  try {
    const json = await request.get('/dashboard/top-knowledge-bases', { params: { limit: 5 } }) as any
    if (json.code === 0 && json.data) {
      topKnowledgeBases.value = json.data.list || []
    }
  } catch (e) {
    console.error('获取热门知识库失败', e)
  }
}

async function fetchRecentConversations() {
  try {
    const json = await request.get('/dashboard/recent-conversations', { params: { limit: 10 } }) as any
    if (json.code === 0 && json.data) {
      recentConversations.value = json.data.list || []
    }
  } catch (e) {
    console.error('获取最近对话失败', e)
  }
}

const refreshTokenTrend = async () => {
  ElMessage.info('刷新中...')
  await fetchTokenTrend()
}

const refreshRetrievalTrend = async () => {
  ElMessage.info('刷新中...')
  await fetchRetrievalTrend()
}

const initTokenTrendChart = () => {
  if (!tokenTrendChart.value || tokenTrendData.value.length === 0) return
  const chart = echarts.init(tokenTrendChart.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: tokenTrendData.value.map(d => d.date),
      boundaryGap: false
    },
    yAxis: {
      type: 'value',
      name: 'Token 数'
    },
    series: [
      {
        name: 'Token 消耗',
        type: 'line',
        data: tokenTrendData.value.map(d => d.total),
        smooth: true,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#409eff' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.2)' }
          ])
        },
        itemStyle: { color: '#409eff' }
      }
    ],
    grid: { left: 60, right: 20, top: 20, bottom: 30 }
  })
}

const initRetrievalTrendChart = () => {
  if (!retrievalTrendChart.value || retrievalTrendData.value.length === 0) return
  const chart = echarts.init(retrievalTrendChart.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: retrievalTrendData.value.map(d => d.date),
      boundaryGap: false
    },
    yAxis: {
      type: 'value',
      name: '命中率(%)',
      max: 100
    },
    series: [
      {
        name: '命中率',
        type: 'line',
        data: retrievalTrendData.value.map(d => d.hitRate),
        smooth: true,
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#67c26a' },
            { offset: 1, color: 'rgba(103, 194, 106, 0.2)' }
          ])
        },
        itemStyle: { color: '#67c26a' }
      }
    ],
    grid: { left: 60, right: 20, top: 20, bottom: 30 }
  })
}

const viewKB = (id: string) => {
  router.push(`/chat/knowledge/${id}`)
}

const viewConversation = (id: string) => {
  router.push({ path: '/chat', query: { conversationId: id } })
}

// 初始化
onMounted(async () => {
  await Promise.all([
    fetchTokenStats(),
    fetchTokenTrend(),
    fetchRetrievalStats(),
    fetchRetrievalTrend(),
    fetchTopKnowledgeBases(),
    fetchRecentConversations()
  ])
})
</script>

<style scoped>
.dashboard-page {
  padding: 0;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  border-radius: 8px;
  overflow: hidden;
}

:deep(.el-card__body) {
  padding: 20px;
}

.stat-content {
  text-align: center;
}

.stat-label {
  font-size: 12px;
  color: #666;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 32px;
  font-weight: 600;
  color: #409eff;
  margin-bottom: 4px;
}

.stat-unit {
  font-size: 12px;
  color: #999;
  margin-bottom: 8px;
}

.stat-cost {
  font-size: 14px;
  color: #333;
  font-weight: 500;
}

.chart-row {
  margin-bottom: 20px;
}

.chart-card {
  border-radius: 8px;
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chart-container {
  height: 300px;
}

.data-row {
  margin-bottom: 0;
}

.data-card {
  border-radius: 8px;
  overflow: hidden;
}

:deep(.el-table) {
  margin-top: 0;
}
</style>
