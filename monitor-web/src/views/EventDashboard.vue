<template>
  <div>
    <div class="page-header">
      <h3>事件中心</h3>
      <el-select v-model="hours" style="width: 120px" @change="loadOverview">
        <el-option :value="1" label="近1小时" />
        <el-option :value="6" label="近6小时" />
        <el-option :value="24" label="近24小时" />
      </el-select>
    </div>

    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="8">
        <div class="stat-card">
          <div class="stat-value">{{ overview.totalEvents?.toLocaleString() || '0' }}</div>
          <div class="stat-label">总事件数</div>
        </div>
      </el-col>
      <el-col :span="16">
        <div class="stat-card">
          <h4 style="margin-bottom: 8px">按类型分布</h4>
          <el-tag v-for="s in (overview.typeStats || [])" :key="s.event_type" style="margin: 4px" type="info">
            {{ s.event_type }}: {{ s.cnt }}
          </el-tag>
          <span v-if="!overview.typeStats?.length" style="color: #999">暂无数据</span>
        </div>
      </el-col>
    </el-row>

    <div class="stat-card">
      <h4 style="margin-bottom: 12px">事件列表</h4>
      <el-form :inline="true" style="margin-bottom: 12px">
        <el-form-item label="类型">
          <el-input v-model="searchType" placeholder="event type" clearable />
        </el-form-item>
        <el-form-item label="服务">
          <el-input v-model="searchService" placeholder="service name" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="searchEvents">搜索</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="events" stripe>
        <el-table-column prop="eventType" label="类型" width="120" />
        <el-table-column prop="source" label="来源" width="160" />
        <el-table-column prop="message" label="消息" show-overflow-tooltip />
        <el-table-column prop="serviceName" label="服务" width="140" />
        <el-table-column prop="host" label="主机" width="120" />
        <el-table-column prop="eventTime" label="时间" width="180" />
      </el-table>
      <el-pagination
        v-if="total > 0"
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total" :page-size="50"
        v-model:current-page="currentPage"
        @current-change="searchEvents"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../api/request'

const hours = ref(24)
const overview = ref<any>({})
const events = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)
const searchType = ref('')
const searchService = ref('')

onMounted(async () => { await loadOverview() })

async function loadOverview() {
  try { overview.value = await request.get('/event/overview', { params: { hours: hours.value } }) as any } catch {}
}

async function searchEvents() {
  try {
    const res = await request.get('/event/search', {
      params: { eventType: searchType.value || undefined, serviceName: searchService.value || undefined, current: currentPage.value, size: 50 }
    }) as any
    events.value = res.records || []
    total.value = res.total || 0
  } catch {}
}
</script>
