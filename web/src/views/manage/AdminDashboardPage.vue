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
            Token 消耗趋势
            <el-select v-model="tokenTrendDays" size="small" @change="loadTokenTrend" style="float:right">
              <el-option :value="7" label="7天" />
              <el-option :value="30" label="30天" />
            </el-select>
          </template>
          <div ref="tokenTrendChart" style="height:300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            用户活跃度趋势
            <el-select v-model="activityTrendDays" size="small" @change="loadActivityTrend" style="float:right">
              <el-option :value="7" label="7天" />
              <el-option :value="30" label="30天" />
            </el-select>
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
            活跃用户 Top{{ activeTopN }}
            <el-input-number v-model="activeTopN" :min="5" :max="50" size="small" @change="loadTopActiveUsers" style="float:right" />
          </template>
          <el-table :data="topActiveUsers" stripe max-height="400">
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="conversationCount" label="对话数" sortable />
            <el-table-column prop="lastActiveAt" label="最后活跃" width="160" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            Token 消耗 Top{{ tokenTopN }}
            <el-input-number v-model="tokenTopN" :min="5" :max="50" size="small" @change="loadTopTokenUsers" style="float:right" />
          </template>
          <el-table :data="topTokenUsers" stripe max-height="400">
            <el-table-column prop="username" label="用户名" />
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
        全链路阶段耗时统计
        <el-button size="small" @click="loadStageLatency" style="float:right">刷新</el-button>
      </template>
      <el-table :data="stageLatency" stripe>
        <el-table-column prop="stageDisplayName" label="阶段" width="120" />
        <el-table-column label="平均耗时(ms)" sortable width="140">
          <template #default="{ row }">{{ Math.round(row.avgDuration) }}</template>
        </el-table-column>
        <el-table-column label="最大耗时(ms)" sortable width="140">
          <template #default="{ row }">{{ Math.round(row.maxDuration) }}</template>
        </el-table-column>
        <el-table-column label="最小耗时(ms)" sortable width="140">
          <template #default="{ row }">{{ Math.round(row.minDuration) }}</template>
        </el-table-column>
        <el-table-column label="Top{{ stageTopN }}慢请求">
          <template #default="{ row }">
            <el-popover trigger="click" width="400">
              <template #reference>
                <el-button size="small">查看 Top{{ stageTopN }}</el-button>
              </template>
              <el-table :data="row.topSlowRequests" stripe size="small">
                <el-table-column prop="trace_id" label="Trace ID" width="200" />
                <el-table-column prop="duration_ms" label="耗时(ms)" width="100" />
                <el-table-column prop="create_time" label="时间" width="160" />
              </el-table>
            </el-popover>
          </template>
        </el-table-column>
      </el-table>
      <el-row :gutter="16" class="mt">
        <el-col :span="8">
          阶段 TopN 数量：
          <el-input-number v-model="stageTopN" :min="5" :max="20" size="small" @change="loadStageLatency" />
        </el-col>
      </el-row>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import request from '../../api/request'
import * as echarts from 'echarts'

const overview = ref<any>({})
const e2eLatency = ref<any>({})
const tokenTrendDays = ref(7)
const activityTrendDays = ref(7)
const activeTopN = ref(10)
const tokenTopN = ref(10)
const stageTopN = ref(10)
const topActiveUsers = ref<any[]>([])
const topTokenUsers = ref<any[]>([])
const stageLatency = ref<any[]>([])

const tokenTrendChart = ref<HTMLDivElement>()
const activityTrendChart = ref<HTMLDivElement>()

onMounted(async () => {
  await Promise.all([
    loadOverview(),
    loadE2ELatency(),
    loadTokenTrend(),
    loadActivityTrend(),
    loadTopActiveUsers(),
    loadTopTokenUsers(),
    loadStageLatency()
  ])
})

async function loadOverview() {
  try {
    const res = await request.get('/admin/dashboard/overview')
    overview.value = (res as any).data || res
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
    const res = await request.get(`/admin/dashboard/token-trend?days=${tokenTrendDays.value}`) as any
    const data = res.data || res
    await nextTick()
    if (tokenTrendChart.value) {
      const c = echarts.init(tokenTrendChart.value)
      c.setOption({
        tooltip: { trigger: 'axis' },
        legend: { data: ['输入Token', '输出Token'] },
        xAxis: { type: 'category', data: data.trendData.map((d: any) => d.date) },
        yAxis: { type: 'value' },
        series: [
          { name: '输入Token', type: 'line', data: data.trendData.map((d: any) => d.inputTokens), smooth: true, areaStyle: {} },
          { name: '输出Token', type: 'line', data: data.trendData.map((d: any) => d.outputTokens), smooth: true, areaStyle: {} }
        ]
      })
    }
  } catch (e) { console.error(e) }
}

async function loadActivityTrend() {
  try {
    const res = await request.get(`/admin/dashboard/user-activity-trend?days=${activityTrendDays.value}`) as any
    const data = res.data || res
    await nextTick()
    if (activityTrendChart.value) {
      const c = echarts.init(activityTrendChart.value)
      c.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: data.trendData.map((d: any) => d.date) },
        yAxis: { type: 'value' },
        series: [
          { name: '活跃用户', type: 'line', data: data.trendData.map((d: any) => d.activeUsers), smooth: true, areaStyle: {} }
        ]
      })
    }
  } catch (e) { console.error(e) }
}

async function loadTopActiveUsers() {
  try {
    const res = await request.get(`/admin/dashboard/top-active-users?limit=${activeTopN.value}`) as any
    const data = res.data || res
    topActiveUsers.value = data.users || []
  } catch (e) { console.error(e) }
}

async function loadTopTokenUsers() {
  try {
    const res = await request.get(`/admin/dashboard/top-token-users?limit=${tokenTopN.value}`) as any
    const data = res.data || res
    topTokenUsers.value = data.users || []
  } catch (e) { console.error(e) }
}

async function loadStageLatency() {
  try {
    const res = await request.get(`/admin/dashboard/stage-latency?limit=${stageTopN.value}`) as any
    const data = res.data || res
    stageLatency.value = data.stages || []
  } catch (e) { console.error(e) }
}
</script>

<style scoped>
.mb { margin-bottom: 16px; }
.mt { margin-top: 16px; }
</style>
