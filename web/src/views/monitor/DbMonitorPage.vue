<template>
  <div>
    <h3>DB 监控</h3>
    <el-tabs v-model="tab">
      <el-tab-pane label="数据看板" name="dashboard">
        <el-row :gutter="16" class="mb">
          <el-col :span="4"><el-statistic title="总连接数" :value="connTotal" /></el-col>
          <el-col :span="4"><el-statistic title="活跃连接" :value="connActive" /></el-col>
          <el-col :span="4"><el-statistic title="事务数" :value="qps" /></el-col>
          <el-col :span="4"><el-statistic title="延迟" :value="dbLatency" suffix="ms" /></el-col>
          <el-col :span="4"><el-statistic title="表数量" :value="tableCount" /></el-col>
          <el-col :span="4"><el-statistic title="平均查询耗时" :value="avgQueryTime" suffix="ms" /></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12"><el-card><template #header>连接数趋势</template><div ref="connChart" style="height:260px"></div></el-card></el-col>
          <el-col :span="12"><el-card><template #header>延迟趋势</template><div ref="latChart" style="height:260px"></div></el-card></el-col>
        </el-row>
        <el-card class="mt"><template #header>热门查询 Top 10</template><el-table :data="hotQueries" stripe><el-table-column prop="query" label="SQL" show-overflow-tooltip /><el-table-column prop="calls" label="调用次数" width="100" /><el-table-column prop="mean_time" label="平均耗时(ms)" width="120" /></el-table></el-card>
        <el-card class="mt"><template #header>慢查询</template><el-table :data="slowQueries" stripe><el-table-column prop="query" label="SQL" show-overflow-tooltip /><el-table-column prop="calls" label="次数" width="80" /><el-table-column prop="mean_time" label="平均耗时(ms)" width="120" /></el-table></el-card>
      </el-tab-pane>
      <el-tab-pane label="表空间" name="tablespace">
        <el-table :data="tables" stripe @row-click="viewTableData">
          <el-table-column prop="schemaname" label="Schema" width="100" />
          <el-table-column prop="tablename" label="表名" />
          <el-table-column prop="size" label="大小" width="120" />
          <el-table-column label="操作" width="100"><template #default="{ row }"><el-button text type="primary" size="small" @click.stop="viewTableData(row)">查看数据</el-button></template></el-table-column>
        </el-table>
        <el-pagination v-if="tableTotal > 20" :total="tableTotal" :page-size="20" layout="prev,pager,next" @current-change="loadTables" class="mt" />
      </el-tab-pane>
    </el-tabs>
    <el-dialog v-model="showTableData" :title="'表数据: ' + selectedTable" width="80%">
      <el-table :data="tableRows" stripe max-height="500" border>
        <el-table-column v-for="col in tableColumns" :key="col" :prop="col" :label="col" show-overflow-tooltip width="150" />
      </el-table>
      <el-pagination :total="tableRowTotal" :page-size="20" layout="prev,pager,next" @current-change="loadTableData" class="mt" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import request from '../../api/request'
import * as echarts from 'echarts'

const tab = ref('dashboard')
const connTotal = ref(0); const connActive = ref(0); const dbLatency = ref(0); const qps = ref(0)
const tableCount = ref(0); const avgQueryTime = ref(0)
const tables = ref<any[]>([]); const slowQueries = ref<any[]>([]); const hotQueries = ref<any[]>([])
const tableTotal = ref(0)
const connChart = ref<HTMLDivElement>(); const latChart = ref<HTMLDivElement>()
const showTableData = ref(false); const selectedTable = ref('')
const tableColumns = ref<string[]>([]); const tableRows = ref<any[]>([]); const tableRowTotal = ref(0)

// ECharts 实例引用，用于防止内存泄漏
let connChartInstance: echarts.ECharts | null = null
let latChartInstance: echarts.ECharts | null = null

onMounted(async () => {
  try { const r = await request.get('/log/monitor/db/connections') as any; connTotal.value = r.total; connActive.value = r.active } catch {}
  try { const r = await request.get('/log/monitor/db/latency') as any; dbLatency.value = r.latencyMs } catch {}
  try { const r = await request.get('/log/monitor/db/qps') as any; qps.value = r.transactions || 0 } catch {}
  try { hotQueries.value = await request.get('/log/monitor/db/hot-queries?limit=10') as any[] } catch {}
  try { slowQueries.value = await request.get('/log/monitor/db/slow-queries?limit=10') as any[] } catch {}
  await loadTables(1)
  if (tables.value.length > 0) {
    const avgTime = slowQueries.value.reduce((s: number, q: any) => s + (q.mean_time || 0), 0) / (slowQueries.value.length || 1)
    avgQueryTime.value = Math.round(avgTime * 100) / 100
  }
  await nextTick()
  await loadAccessTrend()
  await loadQpsTrend()
})

// 修复：/log/monitor/db/tables 返回 {tables, total} 对象，不是数组
async function loadTables(page: number = 1) {
  try {
    const res = await request.get('/log/monitor/db/tables', { params: { page, size: 20 } }) as any
    tables.value = res?.tables || []
    tableTotal.value = res?.total || tables.value.length
    tableCount.value = res?.total || tables.value.length
  } catch { tables.value = []; tableTotal.value = 0 }
}

// 连接数/访问趋势：主服务真实数据 /monitor/db/access-trend
async function loadAccessTrend() {
  try {
    const res = await request.get('/monitor/db/access-trend', { params: { days: 7 } }) as any
    const data = res.data || res
    const list = data.trendData || data.trend || data.list || []
    if (connChart.value) {
      connChartInstance?.dispose()
      connChartInstance = echarts.init(connChart.value)
      connChartInstance.setOption({
        tooltip: {},
        xAxis: { type: 'category', data: list.map((d: any) => d.date) },
        yAxis: { type: 'value' },
        series: [{ name: '访问量', type: 'line', data: list.map((d: any) => d.totalCount ?? d.accessCount ?? d.value ?? 0), smooth: true }]
      })
    }
  } catch (e) { console.error('加载访问趋势失败', e) }
}

// QPS 趋势：主服务真实数据 /monitor/db/qps-trend
async function loadQpsTrend() {
  try {
    const res = await request.get('/monitor/db/qps-trend', { params: { days: 7 } }) as any
    const data = res.data || res
    const list = data.trendData || data.trend || data.list || []
    if (latChart.value) {
      latChartInstance?.dispose()
      latChartInstance = echarts.init(latChart.value)
      latChartInstance.setOption({
        tooltip: {},
        xAxis: { type: 'category', data: list.map((d: any) => d.date) },
        yAxis: { type: 'value' },
        series: [{ name: 'QPS', type: 'line', data: list.map((d: any) => d.qps ?? d.value ?? 0), smooth: true }]
      })
    }
  } catch (e) { console.error('加载QPS趋势失败', e) }
}

// 组件卸载时清理 ECharts 实例，防止内存泄漏
onBeforeUnmount(() => {
  connChartInstance?.dispose()
  latChartInstance?.dispose()
})

async function viewTableData(row: any) {
  selectedTable.value = row.tablename
  showTableData.value = true
  await loadTableData(1)
}

async function loadTableData(page: number = 1) {
  try {
    const res = await request.get(`/log/monitor/db/table-data?table=${selectedTable.value}&page=${page}&size=20`) as any
    const rows = res?.rows || res?.data?.rows || []
    if (rows.length > 0) { tableColumns.value = Object.keys(rows[0]) } else { tableColumns.value = [] }
    tableRows.value = rows
    tableRowTotal.value = res?.total || res?.data?.total || rows.length
  } catch { tableRows.value = []; tableColumns.value = [] }
}
</script>

<style scoped>
.mb { margin-bottom: 16px; } .mt { margin-top: 16px; }
</style>