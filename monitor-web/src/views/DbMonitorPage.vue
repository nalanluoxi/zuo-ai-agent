<template>
  <div>
    <div class="page-header"><h3>数据库监控</h3></div>

    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="8">
        <div class="stat-card">
          <div class="stat-value">{{ dbInfo.databaseCount || 0 }}</div>
          <div class="stat-label">Schema 数量</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card">
          <div class="stat-value">{{ dbInfo.totalTables || 0 }}</div>
          <div class="stat-label">总表数</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card">
          <div class="stat-value">{{ dbInfo.databaseSize || '-' }}</div>
          <div class="stat-label">数据库大小</div>
        </div>
      </el-col>
    </el-row>

    <div class="stat-card">
      <h4 style="margin-bottom: 12px">Schema 表统计</h4>
      <el-table :data="dbInfo.databases || []" stripe>
        <el-table-column prop="table_schema" label="Schema" />
        <el-table-column prop="table_count" label="表数量" />
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../api/request'

const dbInfo = ref<any>({})

onMounted(async () => {
  try { dbInfo.value = await request.get('/monitor/db-info') as any } catch {}
})
</script>
