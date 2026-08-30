<template>
  <div>
    <div class="page-header"><h3>心跳监控</h3></div>

    <!-- 服务状态概览 -->
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="8">
        <div class="stat-card">
          <div class="stat-value" style="color: #67c23a">{{ statusBoard.healthyCount || 0 }}</div>
          <div class="stat-label">健康服务</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card">
          <div class="stat-value" style="color: #f56c6c">{{ statusBoard.unhealthyCount || 0 }}</div>
          <div class="stat-label">异常服务</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card">
          <div class="stat-value">{{ (statusBoard.services || []).length }}</div>
          <div class="stat-label">注册服务总数</div>
        </div>
      </el-col>
    </el-row>

    <!-- 服务状态表 -->
    <div class="stat-card">
      <h4 style="margin-bottom: 12px">服务状态详情</h4>
      <el-table :data="statusBoard.services || []" stripe>
        <el-table-column prop="service_name" label="服务名" width="160" />
        <el-table-column prop="host" label="主机" width="140" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'HEALTHY' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cpu_usage" label="CPU(%)" width="100">
          <template #default="{ row }">{{ (row.cpu_usage || 0).toFixed(1) }}</template>
        </el-table-column>
        <el-table-column prop="memory_usage" label="内存(%)" width="100">
          <template #default="{ row }">{{ (row.memory_usage || 0).toFixed(1) }}</template>
        </el-table-column>
        <el-table-column prop="active_threads" label="线程数" width="80" />
        <el-table-column prop="gc_count" label="GC 次数" width="90" />
        <el-table-column prop="heartbeat_time" label="最后心跳" width="180" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="showTrend(row.service_name)">趋势</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 趋势弹窗 -->
    <el-dialog v-model="trendVisible" :title="`${trendService} - 资源趋势`" width="700px">
      <div ref="trendChartRef" style="height: 350px"></div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import request from '../api/request'

const statusBoard = ref<any>({})
const trendVisible = ref(false)
const trendService = ref('')
const trendChartRef = ref<HTMLElement>()

onMounted(async () => { await loadStatus() })

async function loadStatus() {
  try { statusBoard.value = await request.get('/heartbeat/status') as any } catch {}
}

async function showTrend(serviceName: string) {
  trendService.value = serviceName
  trendVisible.value = true
  try {
    const data = await request.get('/heartbeat/trend', { params: { serviceName, hours: 24 } }) as any
    await nextTick()
    renderTrend(data)
  } catch {}
}

function renderTrend(data: any[]) {
  if (!trendChartRef.value) return
  const chart = echarts.init(trendChartRef.value)
  const xData = data.map((t: any) => t.hour_bucket)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['CPU均值(%)', '内存均值(%)', 'CPU峰值(%)', '内存峰值(%)'] },
    xAxis: { type: 'category', data: xData },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
    series: [
      { name: 'CPU均值(%)', type: 'line', data: data.map((t: any) => t.avg_cpu?.toFixed(1)) },
      { name: '内存均值(%)', type: 'line', data: data.map((t: any) => t.avg_memory?.toFixed(1)) },
      { name: 'CPU峰值(%)', type: 'line', lineStyle: { type: 'dashed' }, data: data.map((t: any) => t.max_cpu?.toFixed(1)) },
      { name: '内存峰值(%)', type: 'line', lineStyle: { type: 'dashed' }, data: data.map((t: any) => t.max_memory?.toFixed(1)) }
    ]
  })
}
</script>
