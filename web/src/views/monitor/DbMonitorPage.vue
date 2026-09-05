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
          <el-col :span="12">
            <el-card>
              <template #header>
                <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
                  <span>连接数趋势</span>
                  <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
                    <el-radio-group v-model="connTrendPeriod" size="small" @change="loadAccessTrend">
                      <el-radio-button value="day">天</el-radio-button>
                      <el-radio-button value="7">7天</el-radio-button>
                      <el-radio-button value="month">月</el-radio-button>
                      <el-radio-button value="custom">自定义</el-radio-button>
                    </el-radio-group>
                    <el-date-picker
                      v-model="connTrendDateRange"
                      type="daterange"
                      range-separator="至"
                      start-placeholder="开始日期"
                      end-placeholder="结束日期"
                      format="YYYY-MM-DD"
                      value-format="YYYY-MM-DD"
                      size="small"
                      style="width:220px"
                      @change="loadAccessTrend"
                    />
                  </div>
                </div>
              </template>
              <div ref="connChart" style="height:260px"></div>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card>
              <template #header>
                <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
                  <span>QPS 趋势</span>
                  <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
                    <el-radio-group v-model="latTrendPeriod" size="small" @change="loadQpsTrend">
                      <el-radio-button value="day">天</el-radio-button>
                      <el-radio-button value="7">7天</el-radio-button>
                      <el-radio-button value="month">月</el-radio-button>
                      <el-radio-button value="custom">自定义</el-radio-button>
                    </el-radio-group>
                    <el-date-picker
                      v-model="latTrendDateRange"
                      type="daterange"
                      range-separator="至"
                      start-placeholder="开始日期"
                      end-placeholder="结束日期"
                      format="YYYY-MM-DD"
                      value-format="YYYY-MM-DD"
                      size="small"
                      style="width:220px"
                      @change="loadQpsTrend"
                    />
                  </div>
                </div>
              </template>
              <div ref="latChart" style="height:260px"></div>
            </el-card>
          </el-col>
        </el-row>
        <el-card class="mt"><template #header>热门查询 Top 10</template><el-table :data="hotQueries" stripe><el-table-column prop="query" label="SQL" show-overflow-tooltip /><el-table-column prop="calls" label="调用次数" width="100" /><el-table-column prop="mean_time" label="平均耗时(ms)" width="120" /></el-table></el-card>
        <el-card class="mt"><template #header>慢查询</template><el-table :data="slowQueries" stripe><el-table-column prop="query" label="SQL" show-overflow-tooltip /><el-table-column prop="calls" label="次数" width="80" /><el-table-column prop="mean_time" label="平均耗时(ms)" width="120" /></el-table></el-card>
      </el-tab-pane>
      <el-tab-pane label="表空间" name="tablespace">
        <div style="margin-bottom:12px;display:flex;gap:12px;align-items:center">
          <el-input 
            v-model="tableSearchKeyword" 
            placeholder="搜索表名" 
            clearable 
            style="width:240px"
            @keyup.enter="searchTables"
            @clear="searchTables"
          />
          <el-button @click="searchTables">搜索</el-button>
          <el-tag v-if="tableSearchKeyword" type="info">
            找到 {{ tableTotal }} 个结果
          </el-tag>
        </div>
        <el-table :data="tables" stripe @row-click="viewTableData">
          <el-table-column prop="schemaname" label="Schema" width="100" />
          <el-table-column prop="tablename" label="表名" />
          <el-table-column prop="size" label="大小" width="120" />
          <el-table-column label="操作" width="180">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click.stop="viewTableStructure(row)">表结构</el-button>
              <el-button text type="primary" size="small" @click.stop="viewTableData(row)">查看数据</el-button>
            </template>
          </el-table-column>
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
    
    <!-- 表结构对话框 -->
    <el-dialog v-model="showTableStructure" :title="'表结构: ' + selectedTable" width="60%">
      <el-table :data="tableStructure" stripe border>
        <el-table-column prop="column_name" label="列名" width="150" />
        <el-table-column prop="data_type" label="数据类型" width="120" />
        <el-table-column prop="is_nullable" label="可空" width="80">
          <template #default="{ row }">
            <el-tag :type="row.is_nullable === 'YES' ? 'success' : 'danger'" size="small">
              {{ row.is_nullable === 'YES' ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="column_default" label="默认值" />
        <el-table-column prop="comment" label="注释" show-overflow-tooltip />
      </el-table>
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

// 表搜索和结构
const tableSearchKeyword = ref('')
const showTableStructure = ref(false)
const tableStructure = ref<any[]>([])

// 时间维度选择
const connTrendPeriod = ref('7')
const latTrendPeriod = ref('7')
const connTrendDateRange = ref<[string, string] | null>(null)
const latTrendDateRange = ref<[string, string] | null>(null)

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

// 获取时间范围参数
function getTimeParams(period: string, dateRange: [string, string] | null): any {
  const params: any = { period }
  if (period === 'custom' && dateRange) {
    params.startDate = dateRange[0]
    params.endDate = dateRange[1]
  }
  return params
}

// 本地时区格式化 yyyy-MM-dd（避免 toISOString 的 UTC 偏移问题）
function formatDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// 补全日期序列，缺失的填充0（后端已补零，这里兜底）
function fillDateGaps(list: any[], period: string, dateRange: [string, string] | null): any[] {
  if (list.length === 0) return []
  
  // 天视图：补全24小时
  if (period === 'day') {
    const hourMap = new Map<string, any>()
    list.forEach((item: any) => {
      const hour = item.date?.split(' ')[1]?.substring(0, 2) || '00'
      hourMap.set(hour, item)
    })
    return Array.from({ length: 24 }, (_, i) => {
      const hour = String(i).padStart(2, '0')
      return hourMap.get(hour) || { date: `${hour}:00`, value: 0 }
    })
  }
  
  // 7天视图：补全7天
  if (period === '7') {
    const dateMap = new Map<string, any>()
    list.forEach((item: any) => {
      dateMap.set(item.date, item)
    })
    const result: any[] = []
    const today = new Date()
    for (let i = 6; i >= 0; i--) {
      const d = new Date(today)
      d.setDate(d.getDate() - i)
      const dateStr = formatDate(d)
      result.push(dateMap.get(dateStr) || { date: dateStr, value: 0 })
    }
    return result
  }
  
  // 月视图：补全当月所有天
  if (period === 'month' || period === '30') {
    const dateMap = new Map<string, any>()
    list.forEach((item: any) => {
      dateMap.set(item.date, item)
    })
    const now = new Date()
    const year = now.getFullYear()
    const month = now.getMonth()
    const daysInMonth = new Date(year, month + 1, 0).getDate()
    const result: any[] = []
    for (let i = 1; i <= daysInMonth; i++) {
      const dateStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(i).padStart(2, '0')}`
      result.push(dateMap.get(dateStr) || { date: dateStr, value: 0 })
    }
    return result
  }
  
  // 自定义视图：补全范围内的所有天
  if (period === 'custom' && dateRange) {
    const dateMap = new Map<string, any>()
    list.forEach((item: any) => {
      dateMap.set(item.date, item)
    })
    const result: any[] = []
    const start = new Date(dateRange[0] + 'T00:00:00')
    const end = new Date(dateRange[1] + 'T00:00:00')
    for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
      const dateStr = formatDate(d)
      result.push(dateMap.get(dateStr) || { date: dateStr, value: 0 })
    }
    return result
  }
  
  return list
}

// 修复：/log/monitor/db/tables 返回 {tables, total} 对象，不是数组
async function loadTables(page: number = 1) {
  try {
    const params: any = { page, size: 20 }
    if (tableSearchKeyword.value) {
      params.keyword = tableSearchKeyword.value
    }
    const res = await request.get('/log/monitor/db/tables', { params }) as any
    tables.value = res?.tables || []
    tableTotal.value = res?.total || tables.value.length
    tableCount.value = res?.total || tables.value.length
  } catch { tables.value = []; tableTotal.value = 0 }
}

// 连接数/访问趋势：主服务真实数据 /monitor/db/access-trend
async function loadAccessTrend() {
  try {
    const params = getTimeParams(connTrendPeriod.value, connTrendDateRange.value)
    const res = await request.get('/monitor/db/access-trend', { params }) as any
    const data = res.data || res
    let list = data.trendData || data.trend || data.list || []
    // 补全时间序列
    list = fillDateGaps(list, connTrendPeriod.value, connTrendDateRange.value)
    if (connChart.value) {
      connChartInstance?.dispose()
      connChartInstance = echarts.init(connChart.value)
      connChartInstance.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: { 
          type: 'category', 
          data: list.map((d: any) => {
            // 天视图显示小时，其他显示日期
            if (connTrendPeriod.value === 'day') {
              return d.date?.split(' ')[1]?.substring(0, 5) || d.date
            }
            return d.date?.substring(5) || d.date // MM-DD
          })
        },
        yAxis: { type: 'value' },
        series: [{ 
          name: '访问量', 
          type: 'line', 
          data: list.map((d: any) => d.totalCount ?? d.accessCount ?? d.value ?? 0), 
          smooth: true, 
          areaStyle: {} 
        }]
      })
    }
  } catch (e) { console.error('加载访问趋势失败', e) }
}

// QPS 趋势：主服务真实数据 /monitor/db/qps-trend
async function loadQpsTrend() {
  try {
    const params = getTimeParams(latTrendPeriod.value, latTrendDateRange.value)
    const res = await request.get('/monitor/db/qps-trend', { params }) as any
    const data = res.data || res
    let list = data.trendData || data.trend || data.list || []
    // 补全时间序列
    list = fillDateGaps(list, latTrendPeriod.value, latTrendDateRange.value)
    if (latChart.value) {
      latChartInstance?.dispose()
      latChartInstance = echarts.init(latChart.value)
      latChartInstance.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: { 
          type: 'category', 
          data: list.map((d: any) => {
            // 天视图显示小时，其他显示日期
            if (latTrendPeriod.value === 'day') {
              return d.date?.split(' ')[1]?.substring(0, 5) || d.date
            }
            return d.date?.substring(5) || d.date // MM-DD
          })
        },
        yAxis: { type: 'value' },
        series: [{ 
          name: 'QPS', 
          type: 'line', 
          data: list.map((d: any) => d.qps ?? d.value ?? 0), 
          smooth: true, 
          areaStyle: {} 
        }]
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

function searchTables() {
  loadTables(1)
}

async function viewTableStructure(row: any) {
  selectedTable.value = row.tablename
  showTableStructure.value = true
  try {
    const res = await request.get(`/log/monitor/db/table-structure?table=${row.tablename}`) as any
    tableStructure.value = res?.columns || res?.data?.columns || []
  } catch { 
    tableStructure.value = []
  }
}
</script>

<style scoped>
.mb { margin-bottom: 16px; } .mt { margin-top: 16px; }
</style>