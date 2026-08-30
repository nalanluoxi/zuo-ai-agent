<template>
  <div>
    <div class="page-header"><h3>日志搜索</h3></div>

    <div class="stat-card">
      <el-form :inline="true" style="margin-bottom: 12px">
        <el-form-item label="关键词">
          <el-input v-model="keyword" placeholder="搜索日志内容" clearable style="width: 240px" />
        </el-form-item>
        <el-form-item label="服务">
          <el-input v-model="service" placeholder="service name" clearable />
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="level" clearable>
            <el-option value="ERROR" label="ERROR" />
            <el-option value="WARN" label="WARN" />
            <el-option value="INFO" label="INFO" />
            <el-option value="DEBUG" label="DEBUG" />
          </el-select>
        </el-form-item>
        <el-form-item label="Trace ID">
          <el-input v-model="traceId" placeholder="trace id" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">搜索</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="logs" stripe>
        <el-table-column prop="logTs" label="时间" width="180" />
        <el-table-column prop="logLevel" label="级别" width="80">
          <template #default="{ row }">
            <el-tag :type="row.logLevel === 'ERROR' ? 'danger' : row.logLevel === 'WARN' ? 'warning' : 'info'" size="small">
              {{ row.logLevel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="serviceName" label="服务" width="140" />
        <el-table-column prop="loggerName" label="Logger" width="200" show-overflow-tooltip />
        <el-table-column prop="message" label="消息" show-overflow-tooltip />
        <el-table-column prop="traceId" label="Trace ID" width="260" show-overflow-tooltip />
      </el-table>

      <el-pagination
        v-if="total > 0"
        style="margin-top: 12px; justify-content: flex-end"
        layout="total, prev, pager, next"
        :total="total" :page-size="50"
        v-model:current-page="currentPage"
        @current-change="search"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import request from '../api/request'

const keyword = ref('')
const service = ref('')
const level = ref('')
const traceId = ref('')
const logs = ref<any[]>([])
const total = ref(0)
const currentPage = ref(1)

async function search() {
  try {
    const res = await request.get('/search', {
      params: {
        keyword: keyword.value || undefined,
        service: service.value || undefined,
        level: level.value || undefined,
        traceId: traceId.value || undefined,
        current: currentPage.value, size: 50
      }
    }) as any
    logs.value = res.records || []
    total.value = res.total || 0
  } catch {}
}
</script>
