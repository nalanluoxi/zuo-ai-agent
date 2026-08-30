<template>
  <div>
    <h3>日志系统</h3>
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索关键词" clearable class="search-input" @keyup.enter="search" />
      <el-select v-model="level" placeholder="级别" clearable style="width:120px">
        <el-option label="ERROR" value="ERROR" /><el-option label="WARN" value="WARN" /><el-option label="INFO" value="INFO" />
      </el-select>
      <el-select v-model="service" placeholder="服务" clearable style="width:150px">
        <el-option label="zuo-ai-agent" value="zuo-ai-agent" /><el-option label="auth-service" value="auth-service" />
      </el-select>
      <el-button type="primary" @click="search">搜索</el-button>
    </div>
    <el-table :data="logs" stripe max-height="500" v-loading="loading">
      <el-table-column prop="logTs" label="时间" width="180" />
      <el-table-column prop="logLevel" label="级别" width="80"><template #default="{ row }"><el-tag :type="row.logLevel === 'ERROR' ? 'danger' : row.logLevel === 'WARN' ? 'warning' : 'info'" size="small">{{ row.logLevel }}</el-tag></template></el-table-column>
      <el-table-column prop="serviceName" label="服务" width="120" />
      <el-table-column prop="message" label="消息" show-overflow-tooltip />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import request from '../../api/request'

const logs = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const level = ref('')
const service = ref('')

async function search() {
  loading.value = true
  try {
    const params: any = { current: 1, size: 50 }
    if (keyword.value) params.keyword = keyword.value
    if (level.value) params.level = level.value
    if (service.value) params.service = service.value
    const res = await request.get('/log/search', { params }) as any
    logs.value = res?.records || []
  } catch { logs.value = [] }
  finally { loading.value = false }
}
</script>

<style scoped>
.toolbar { display: flex; gap: 8px; margin-bottom: 16px; }
.search-input { width: 300px; }
</style>