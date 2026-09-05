<template>
  <div>
    <h3>Redis 监控</h3>
    <el-row :gutter="16" class="mb">
      <el-col :span="6"><el-statistic title="Key 总数" :value="keyCount" /></el-col>
      <el-col :span="6"><el-statistic title="延迟" :value="latency" suffix="ms" /></el-col>
      <el-col :span="6"><el-statistic title="命中率" :value="hitRate" suffix="%" /></el-col>
      <el-col :span="6"><el-statistic title="Big Key" :value="bigKeys.length" /></el-col>
    </el-row>
    <el-row :gutter="16">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
              <span>内存趋势</span>
              <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
                <el-radio-group v-model="memTrendPeriod" size="small" @change="loadMemTrend">
                  <el-radio-button value="day">天</el-radio-button>
                  <el-radio-button value="7">7天</el-radio-button>
                  <el-radio-button value="month">月</el-radio-button>
                  <el-radio-button value="custom">自定义</el-radio-button>
                </el-radio-group>
                <el-date-picker
                  v-model="memTrendDateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  format="YYYY-MM-DD"
                  value-format="YYYY-MM-DD"
                  size="small"
                  style="width:220px"
                  @change="loadMemTrend"
                />
              </div>
            </div>
          </template>
          <div ref="memChart" style="height:260px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
              <span>QPS 趋势</span>
              <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
                <el-radio-group v-model="qpsTrendPeriod" size="small" @change="loadQpsTrend">
                  <el-radio-button value="day">天</el-radio-button>
                  <el-radio-button value="7">7天</el-radio-button>
                  <el-radio-button value="month">月</el-radio-button>
                  <el-radio-button value="custom">自定义</el-radio-button>
                </el-radio-group>
                <el-date-picker
                  v-model="qpsTrendDateRange"
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
          <div ref="qpsChart" style="height:260px"></div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="16" class="mt">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
              <span>延迟趋势</span>
              <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
                <el-radio-group v-model="latTrendPeriod" size="small" @change="loadLatencyTrend">
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
                  @change="loadLatencyTrend"
                />
              </div>
            </div>
          </template>
          <div ref="latChart" style="height:260px"></div>
        </el-card>
      </el-col>
    </el-row>
    <el-card class="mt"><template #header>Big Key 列表 (>10KB)</template><el-table :data="bigKeys" stripe><el-table-column prop="key" label="Key" /><el-table-column prop="type" label="类型" width="80" /><el-table-column prop="size" label="大小(bytes)" width="120" /></el-table></el-card>
    <el-card class="mt"><template #header>Key 查询 <el-input v-model="keyPattern" placeholder="输入 pattern，如 *" style="width:200px;margin-left:12px" @keyup.enter="searchKeys" /></template>
      <el-table :data="keys" stripe max-height="300"><el-table-column prop="key" label="Key" /><el-table-column prop="type" label="类型" width="80" /><el-table-column prop="size" label="大小" width="80" /><el-table-column prop="ttl" label="TTL(s)" width="80" /><el-table-column label="操作" width="100"><template #default="{ row }"><el-button text type="primary" @click="viewKey(row.key)">查看值</el-button></template></el-table-column></el-table>
    </el-card>
    <el-dialog v-model="showValue" title="Key 详情"><p><strong>Key:</strong> {{ currentKey }}</p><p><strong>大小:</strong> {{ currentSize }} bytes</p><p v-if="currentIsBig" class="text-danger">⚠️ Big Key</p><el-input v-model="currentValue" type="textarea" :rows="10" readonly /></el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import request from '../../api/request'
import * as echarts from 'echarts'

const keyCount = ref(0); const latency = ref(0); const hitRate = ref(0)
const keys = ref<any[]>([]); const bigKeys = ref<any[]>([]); const keyPattern = ref('*')
const showValue = ref(false); const currentKey = ref(''); const currentValue = ref(''); const currentSize = ref(0); const currentIsBig = ref(false)
const memChart = ref<HTMLDivElement>(); const qpsChart = ref<HTMLDivElement>(); const latChart = ref<HTMLDivElement>()

// 时间维度选择
const memTrendPeriod = ref('7')
const qpsTrendPeriod = ref('7')
const latTrendPeriod = ref('7')
const memTrendDateRange = ref<[string, string] | null>(null)
const qpsTrendDateRange = ref<[string, string] | null>(null)
const latTrendDateRange = ref<[string, string] | null>(null)

// ECharts 实例引用，用于防止内存泄漏
let memChartInstance: echarts.ECharts | null = null
let qpsChartInstance: echarts.ECharts | null = null
let latChartInstance: echarts.ECharts | null = null

onMounted(async () => {
  await searchKeys()
  try { const r = await request.get('/log/monitor/redis/latency') as any; latency.value = r.latencyMs } catch {}
  try { bigKeys.value = await request.get('/log/monitor/redis/bigkeys') as any[] } catch {}
  await nextTick()
  await loadMemTrend()
  await loadQpsTrend()
  await loadLatencyTrend()
})

// 获取时间范围参数（仅自定义时才携带日期范围）
function getTimeParams(period: string, dateRange: [string, string] | null): any {
  const params: any = { period }
  if (period === 'custom' && dateRange) {
    params.startDate = dateRange[0]
    params.endDate = dateRange[1]
  }
  return params
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

// 本地时区格式化 yyyy-MM-dd（避免 toISOString 的 UTC 偏移问题）
function formatDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

// x 轴标签：天视图显示小时，其他显示日期
function xLabel(date: string, period: string): string {
  if (period === 'day') {
    return date?.split(' ')[1]?.substring(0, 5) || date
  }
  return date?.substring(5) || date // MM-DD
}

// 内存趋势：主服务真实数据 /monitor/redis/memory-trend
async function loadMemTrend() {
  try {
    const params = getTimeParams(memTrendPeriod.value, memTrendDateRange.value)
    const res = await request.get('/monitor/redis/memory-trend', { params }) as any
    const data = res.data || res
    let list = data.trendData || data.trend || data.list || []
    list = fillDateGaps(list, memTrendPeriod.value, memTrendDateRange.value)
    if (memChart.value) {
      memChartInstance?.dispose()
      memChartInstance = echarts.init(memChart.value)
      memChartInstance.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: list.map((d: any) => xLabel(d.date, memTrendPeriod.value)) },
        yAxis: { type: 'value' },
        series: [{
          name: '内存MB',
          type: 'line',
          data: list.map((d: any) => Math.round((d.usedMemoryBytes ?? d.memoryMb ?? d.value ?? 0) / 1024 / 1024)),
          smooth: true,
          areaStyle: {}
        }]
      })
    }
  } catch (e) { console.error('加载内存趋势失败', e) }
}

// QPS 趋势：主服务真实数据 /monitor/redis/qps-trend
async function loadQpsTrend() {
  try {
    const params = getTimeParams(qpsTrendPeriod.value, qpsTrendDateRange.value)
    const res = await request.get('/monitor/redis/qps-trend', { params }) as any
    const data = res.data || res
    let list = data.trendData || data.trend || data.list || []
    list = fillDateGaps(list, qpsTrendPeriod.value, qpsTrendDateRange.value)
    if (qpsChart.value) {
      qpsChartInstance?.dispose()
      qpsChartInstance = echarts.init(qpsChart.value)
      qpsChartInstance.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: list.map((d: any) => xLabel(d.date, qpsTrendPeriod.value)) },
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

// 延迟趋势：主服务真实数据 /monitor/redis/latency-trend
async function loadLatencyTrend() {
  try {
    const params = getTimeParams(latTrendPeriod.value, latTrendDateRange.value)
    const res = await request.get('/monitor/redis/latency-trend', { params }) as any
    const data = res.data || res
    let list = data.trendData || data.trend || data.list || []
    list = fillDateGaps(list, latTrendPeriod.value, latTrendDateRange.value)
    if (latChart.value) {
      latChartInstance?.dispose()
      latChartInstance = echarts.init(latChart.value)
      latChartInstance.setOption({
        tooltip: { trigger: 'axis' },
        xAxis: { type: 'category', data: list.map((d: any) => xLabel(d.date, latTrendPeriod.value)) },
        yAxis: { type: 'value', name: 'ms' },
        series: [{
          name: '延迟(ms)',
          type: 'line',
          data: list.map((d: any) => d.latencyMs ?? d.value ?? 0),
          smooth: true,
          areaStyle: {}
        }]
      })
    }
  } catch (e) { console.error('加载延迟趋势失败', e) }
}

// 组件卸载时清理 ECharts 实例，防止内存泄漏
onBeforeUnmount(() => {
  memChartInstance?.dispose()
  qpsChartInstance?.dispose()
  latChartInstance?.dispose()
})

async function searchKeys() {
  try { const r = await request.get(`/log/monitor/redis/keys?pattern=${keyPattern.value}&limit=100`) as any; keyCount.value = r.total; keys.value = r.keys || [] } catch {}
}

async function viewKey(key: string) {
  try { const r = await request.get(`/log/monitor/redis/key/${key}`) as any; currentKey.value = r.key; currentSize.value = r.size; currentIsBig.value = r.isBigKey; currentValue.value = r.value; showValue.value = true } catch {}
}
</script>

<style scoped>
.mb { margin-bottom: 16px; } .mt { margin-top: 16px; } .text-danger { color: #f56c6c; }
</style>
