<template>
  <div>
    <div class="page-header"><h3>日志聚合分析</h3></div>

    <div class="stat-card">
      <el-form :inline="true" style="margin-bottom: 12px">
        <el-form-item label="关键词">
          <el-input v-model="keyword" placeholder="搜索 token" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="服务">
          <el-input v-model="serviceName" placeholder="service name" clearable />
        </el-form-item>
        <el-form-item label="粒度">
          <el-select v-model="granularity" style="width: 100px">
            <el-option value="hour" label="小时" />
            <el-option value="day" label="天" />
            <el-option value="week" label="周" />
            <el-option value="month" label="月" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">搜索</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="results" stripe>
        <el-table-column prop="token" label="Token" width="200" />
        <el-table-column prop="service_name" label="服务" width="140" />
        <el-table-column prop="log_level" label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="row.log_level === 'ERROR' ? 'danger' : row.log_level === 'WARN' ? 'warning' : 'info'" size="small">
              {{ row.log_level }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="time_bucket" label="时间桶" width="180" />
        <el-table-column prop="total_docs" label="文档数" width="100" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="showTokenTrend(row)">趋势</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="results.length" style="margin-top: 12px; color: #999">
        共 {{ results.length }} 条记录
      </div>
    </div>

    <!-- Token 趋势弹窗 -->
    <el-dialog v-model="trendVisible" :title="`${trendToken} - 出现趋势`" width="700px">
      <div ref="trendChartRef" style="height: 350px"></div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import * as echarts from 'echarts'
import request from '../api/request'

const keyword = ref('')
const serviceName = ref('')
const granularity = ref('hour')
const results = ref<any[]>([])

const trendVisible = ref(false)
const trendToken = ref('')
const trendChartRef = ref<HTMLElement>()

async function search() {
  try {
    const res = await request.get('/aggregation/search', {
      params: {
        keyword: keyword.value || undefined,
        serviceName: serviceName.value || undefined,
        granularity: granularity.value
      }
    }) as any
    results.value = res.items || []
  } catch {}
}

async function showTokenTrend(row: any) {
  trendToken.value = row.token
  trendVisible.value = true
  try {
    const data = await request.get('/aggregation/trend', {
      params: { token: row.token, serviceName: row.service_name, hours: 24 }
    }) as any
    await nextTick()
    renderTrend(data)
  } catch {}
}

function renderTrend(data: any[]) {
  if (!trendChartRef.value) return
  const chart = echarts.init(trendChartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: data.map((t: any) => t.hour_bucket) },
    yAxis: { type: 'value', name: '文档数' },
    series: [{
      name: '文档数',
      type: 'bar',
      data: data.map((t: any) => t.total_count),
      itemStyle: { color: '#409EFF' }
    }]
  })
}
</script>
