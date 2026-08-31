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
      <el-col :span="12"><el-card><template #header>内存趋势</template><div ref="memChart" style="height:260px"></div></el-card></el-col>
      <el-col :span="12"><el-card><template #header>延迟趋势</template><div ref="latChart" style="height:260px"></div></el-card></el-col>
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
const memChart = ref<HTMLDivElement>(); const latChart = ref<HTMLDivElement>()

// ECharts 实例引用，用于防止内存泄漏
let memChartInstance: echarts.ECharts | null = null
let latChartInstance: echarts.ECharts | null = null

onMounted(async () => {
  await searchKeys()
  try { const r = await request.get('/log/monitor/redis/latency') as any; latency.value = r.latencyMs } catch {}
  try { bigKeys.value = await request.get('/log/monitor/redis/bigkeys') as any[] } catch {}
  await nextTick()
  await loadMemTrend()
  await loadQpsTrend()
})

// 内存趋势：主服务真实数据 /monitor/redis/memory-trend
async function loadMemTrend() {
  try {
    const res = await request.get('/monitor/redis/memory-trend', { params: { days: 7 } }) as any
    const data = res.data || res
    const list = data.trendData || data.trend || data.list || []
    if (memChart.value) {
      memChartInstance?.dispose()
      memChartInstance = echarts.init(memChart.value)
      memChartInstance.setOption({
        tooltip: {},
        xAxis: { type: 'category', data: list.map((d: any) => d.date) },
        yAxis: { type: 'value' },
        series: [{ name: '内存MB', type: 'line', data: list.map((d: any) => Math.round((d.usedMemoryBytes ?? d.memoryMb ?? d.value ?? 0) / 1024 / 1024)), smooth: true, areaStyle: {} }]
      })
    }
  } catch (e) { console.error('加载内存趋势失败', e) }
}

// QPS 趋势：主服务真实数据 /monitor/redis/qps-trend
async function loadQpsTrend() {
  try {
    const res = await request.get('/monitor/redis/qps-trend', { params: { days: 7 } }) as any
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
  memChartInstance?.dispose()
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