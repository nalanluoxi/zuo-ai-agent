<template>
  <div>
    <div class="page-header">
      <h3>链路追踪</h3>
      <el-select v-model="hours" style="width: 120px" @change="loadOverview">
        <el-option :value="1" label="近1小时" />
        <el-option :value="6" label="近6小时" />
        <el-option :value="24" label="近24小时" />
        <el-option :value="72" label="近3天" />
      </el-select>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ overview.totalSpans?.toLocaleString() || '0' }}</div>
          <div class="stat-label">总 Span 数</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ (overview.avgDurationMs || 0).toFixed(0) }} ms</div>
          <div class="stat-label">平均耗时</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value" :style="{ color: (overview.errorCount || 0) > 0 ? '#f56c6c' : '#67c23a' }">{{ overview.errorCount || 0 }}</div>
          <div class="stat-label">错误数</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ overview.errorRate || '0.00%' }}</div>
          <div class="stat-label">错误率</div>
        </div>
      </el-col>
    </el-row>

    <!-- 趋势图 -->
    <div class="stat-card">
      <div ref="chartRef" style="height: 300px"></div>
    </div>

    <!-- Span 搜索 -->
    <div class="stat-card" style="margin-top: 16px">
      <h4 style="margin-bottom: 12px">Span 列表</h4>
      <el-form :inline="true" style="margin-bottom: 12px">
        <el-form-item label="服务">
          <el-input v-model="searchService" placeholder="service name" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchStatus" clearable>
            <el-option value="OK" label="OK" />
            <el-option value="ERROR" label="ERROR" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="searchSpans">搜索</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="spans" stripe>
        <el-table-column prop="traceId" label="Trace ID" width="280" />
        <el-table-column prop="operationName" label="操作" />
        <el-table-column prop="serviceName" label="服务" width="140" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ERROR' ? 'danger' : 'success'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="时间" width="180" />
      </el-table>
      <el-pagination
        v-if="total > 0"
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total"
        :page-size="50"
        v-model:current-page="currentPage"
        @current-change="searchSpans"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import request from '../api/request'

const hours = ref(24)
const overview = ref<any>({})
const trend = ref<any[]>([])
const spans = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const searchService = ref('')
const searchStatus = ref('')
const chartRef = ref<HTMLElement>()

onMounted(async () => {
  await loadOverview()
  await loadTrend()
})

async function loadOverview() {
  try {
    overview.value = await request.get('/traces/overview', { params: { hours: hours.value } }) as any
  } catch {}
}

async function loadTrend() {
  try {
    trend.value = await request.get('/traces/trend', { params: { hours: hours.value } }) as any
    await nextTick()
    renderChart()
  } catch {}
}

async function searchSpans() {
  try {
    const res = await request.get('/traces/search', {
      params: {
        serviceName: searchService.value || undefined,
        status: searchStatus.value || undefined,
        current: currentPage.value,
        size: 50
      }
    }) as any
    spans.value = res.records || []
    total.value = res.total || 0
  } catch {}
}

function renderChart() {
  if (!chartRef.value) return
  const chart = echarts.init(chartRef.value)
  const xData = trend.value.map((t: any) => t.hour_bucket)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['请求数', '平均耗时(ms)', '错误数'] },
    xAxis: { type: 'category', data: xData },
    yAxis: [
      { type: 'value', name: '数量' },
      { type: 'value', name: 'ms' }
    ],
    series: [
      { name: '请求数', type: 'bar', data: trend.value.map((t: any) => t.count) },
      { name: '平均耗时(ms)', type: 'line', yAxisIndex: 1, data: trend.value.map((t: any) => t.avg_duration_ms?.toFixed(0)) },
      { name: '错误数', type: 'line', data: trend.value.map((t: any) => t.error_count) }
    ]
  })
}
</script>
