<template>
  <div>
    <div class="page-header"><h3>Redis 监控</h3></div>

    <el-row :gutter="16" style="margin-bottom: 16px">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ info.redisVersion || '-' }}</div>
          <div class="stat-label">Redis 版本</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ info.usedMemoryFormatted || '0 B' }}</div>
          <div class="stat-label">已用内存</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ info.instantaneousOpsPerSecond || 0 }}</div>
          <div class="stat-label">QPS (ops/sec)</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ info.hitRate || '0%' }}</div>
          <div class="stat-label">命中率</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="stat-card">
          <h4 style="margin-bottom: 12px">基础信息</h4>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="运行时间">{{ formatUptime(info.uptimeInSeconds) }}</el-descriptions-item>
            <el-descriptions-item label="连接客户端">{{ info.connectedClients }}</el-descriptions-item>
            <el-descriptions-item label="Key 总数">{{ info.totalKeys?.toLocaleString() }}</el-descriptions-item>
            <el-descriptions-item label="最大内存">{{ info.maxMemoryFormatted || '无限制' }}</el-descriptions-item>
            <el-descriptions-item label="内存使用率">{{ info.memoryRate || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="stat-card">
          <h4 style="margin-bottom: 12px">缓存命中</h4>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="命中次数">{{ info.keyspaceHits?.toLocaleString() || '0' }}</el-descriptions-item>
            <el-descriptions-item label="未命中次数">{{ info.keyspaceMisses?.toLocaleString() || '0' }}</el-descriptions-item>
            <el-descriptions-item label="命中率">{{ info.hitRate || '0%' }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import request from '../api/request'

const info = ref<any>({})

onMounted(async () => {
  try { info.value = await request.get('/monitor/redis-info') as any } catch {}
})

function formatUptime(seconds: number) {
  if (!seconds) return '-'
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const mins = Math.floor((seconds % 3600) / 60)
  return `${days}天 ${hours}时 ${mins}分`
}
</script>
