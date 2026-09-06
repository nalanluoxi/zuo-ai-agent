<template>
  <div class="trace-detail-page">
    <!-- 顶部：返回 + 标题 -->
    <div class="page-header">
      <el-button text @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        返回全局看板
      </el-button>
      <h3 class="page-title">链路详情</h3>
    </div>

    <el-empty v-if="!loading && !trace" description="链路不存在或已删除" />

    <template v-else>
      <!-- 概览区 -->
      <el-card v-loading="loading">
        <template #header>
          <div style="display:flex;justify-content:space-between;align-items:center">
            <span style="font-weight:bold">调用概览</span>
            <el-tag :type="trace?.status === 'SUCCESS' ? 'success' : trace?.status === 'ERROR' ? 'danger' : 'warning'">
              {{ trace?.status || '-' }}
            </el-tag>
          </div>
        </template>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="用户">
            <span v-if="trace?.nickname">{{ trace.nickname }}（{{ trace.username }}）</span>
            <span v-else>{{ trace?.username || '匿名用户' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="发起时间">{{ trace?.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="总耗时">
            <strong style="color:#409EFF">{{ Math.round(trace?.durationMs || 0) }}ms</strong>
          </el-descriptions-item>
          <el-descriptions-item label="灰度分组">
            <el-tag v-if="trace?.grayTag && trace.grayTag !== 'BASELINE'" :type="trace.grayTag === 'TAG_A' ? '' : 'warning'" size="small">
              {{ trace.grayTag }}
            </el-tag>
            <span v-else style="color:#999">BASELINE</span>
          </el-descriptions-item>
          <el-descriptions-item label="TraceId" :span="2">
            <span style="font-family:monospace;font-size:12px">{{ trace?.traceId }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="原始问题" :span="3">
            <div class="prompt-full">{{ trace?.originalPrompt || '无' }}</div>
          </el-descriptions-item>
        </el-descriptions>

        <!-- 节点时间轴 -->
        <div v-if="nodes.length > 0" class="timeline-box">
          <div style="display:flex;align-items:center;margin-bottom:8px">
            <span style="font-size:12px;color:#909399;width:60px">0ms</span>
            <div style="flex:1;height:6px;background:#e4e7ed;border-radius:3px;position:relative">
              <div
                v-for="(node, idx) in nodes"
                :key="'bar-' + idx"
                :style="getTimelineBarStyle(node, idx)"
                :title="`${getStageDisplayName(node.nodeType)}: ${Math.round(node.durationMs || 0)}ms`"
                style="position:absolute;height:100%;border-radius:3px;cursor:pointer"
              />
            </div>
            <span style="font-size:12px;color:#909399;width:60px;text-align:right">{{ Math.round(trace?.durationMs || 0) }}ms</span>
          </div>
          <div style="display:flex;gap:12px;flex-wrap:wrap;font-size:12px;color:#606266">
            <span v-for="node in nodes" :key="'legend-' + node.nodeId" style="display:flex;align-items:center;gap:4px">
              <i :style="{ display: 'inline-block', width: '8px', height: '8px', borderRadius: '2px', background: getNodeColor(node.nodeType) }"></i>
              {{ getStageDisplayName(node.nodeType) }}
            </span>
          </div>
        </div>
      </el-card>

      <!-- 阶段流水 -->
      <h4 class="stage-section-title">阶段流水（共 {{ nodes.length }} 个阶段）</h4>
      <el-empty v-if="!loading && nodes.length === 0" description="暂无阶段数据" />

      <el-timeline v-else class="stage-timeline">
        <el-timeline-item
          v-for="node in nodes"
          :key="node.nodeId || node.id"
          :color="getNodeColor(node.nodeType)"
          :hollow="node.status !== 'SUCCESS'"
          placement="top"
        >
          <el-card shadow="hover" class="stage-card">
            <template #header>
              <div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:8px">
                <div style="display:flex;align-items:center;gap:8px">
                  <el-tag :color="getNodeColor(node.nodeType)" effect="dark" size="small">
                    {{ getStageDisplayName(node.nodeType) }}
                  </el-tag>
                  <span style="font-weight:500">{{ node.nodeName || '-' }}</span>
                  <el-tag :type="node.status === 'SUCCESS' ? 'success' : node.status === 'ERROR' ? 'danger' : 'warning'" size="small">
                    {{ node.status }}
                  </el-tag>
                </div>
                <div style="display:flex;gap:16px;font-size:13px;color:#606266">
                  <span>耗时 <strong style="color:#409EFF">{{ Math.round(node.durationMs || 0) }}ms</strong></span>
                  <span v-if="node.promptTokens || node.completionTokens">
                    Token <strong>{{ node.promptTokens || 0 }}</strong> / <strong>{{ node.completionTokens || 0 }}</strong>
                  </span>
                </div>
              </div>
            </template>

            <!-- 错误信息 -->
            <el-alert
              v-if="node.errorMessage"
              :title="node.errorMessage"
              type="error"
              :closable="false"
              style="margin-bottom:12px"
            />

            <!-- 输入/输出数据 -->
            <div class="io-grid">
              <div class="io-block">
                <div class="io-label">输入数据</div>
                <pre v-if="node.inputData" class="io-content">{{ formatJson(node.inputData) }}</pre>
                <span v-else style="color:#999">-</span>
              </div>
              <div class="io-block">
                <div class="io-label">输出数据</div>
                <pre v-if="node.outputData" class="io-content">{{ formatJson(node.outputData) }}</pre>
                <span v-else style="color:#999">-</span>
              </div>
            </div>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../../api/request'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const trace = ref<any>(null)
const nodes = ref<any[]>([])

onMounted(() => {
  loadTraceDetail()
})

function goBack() {
  router.push('/manage/dashboard')
}

async function loadTraceDetail() {
  const traceId = route.params.traceId as string
  if (!traceId) return
  loading.value = true
  try {
    const res = await request.get(`/admin/dashboard/trace-detail/${traceId}`) as any
    const data = res.data || res
    if (data.found && data.data) {
      trace.value = mapTraceRun(data.data)
      // 后端 JdbcTemplate 返回 snake_case，统一映射为 camelCase
      nodes.value = (data.data.nodes || []).map(mapTraceNode)
    } else {
      trace.value = null
      nodes.value = []
    }
  } catch (e) {
    console.error(e)
    trace.value = null
    nodes.value = []
  } finally {
    loading.value = false
  }
}

// run snake_case → camelCase 映射
function mapTraceRun(item: any) {
  return {
    id: item.id,
    traceId: item.trace_id ?? item.traceId,
    conversationId: item.conversation_id ?? item.conversationId,
    originalPrompt: item.original_prompt ?? item.originalPrompt,
    status: item.status,
    durationMs: item.duration_ms ?? item.durationMs,
    createTime: formatDateTime(item.create_time ?? item.createTime),
    grayTag: item.gray_tag ?? item.grayTag,
    userId: item.user_id ?? item.userId,
    username: item.username,
    nickname: item.nickname
  }
}

// node snake_case → camelCase 映射
function mapTraceNode(n: any) {
  return {
    id: n.id,
    nodeId: n.node_id ?? n.nodeId,
    nodeName: n.node_name ?? n.nodeName,
    nodeType: n.node_type ?? n.nodeType,
    status: n.status,
    durationMs: n.duration_ms ?? n.durationMs,
    inputData: n.input_data ?? n.inputData,
    outputData: n.output_data ?? n.outputData,
    errorMessage: n.error_message ?? n.errorMessage,
    promptTokens: n.prompt_tokens ?? n.promptTokens,
    completionTokens: n.completion_tokens ?? n.completionTokens,
    startTime: n.start_time ?? n.startTime
  }
}

// 格式化时间：兼容 ISO 字符串 / epoch 毫秒
function formatDateTime(val: any): string {
  if (!val) return ''
  const d = new Date(val)
  if (isNaN(d.getTime())) return String(val)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

// JSON 美化输出
function formatJson(data: any): string {
  if (typeof data !== 'string') {
    try { return JSON.stringify(data, null, 2) } catch { return String(data) }
  }
  try {
    return JSON.stringify(JSON.parse(data), null, 2)
  } catch {
    return data
  }
}

function getStageDisplayName(nodeType: string): string {
  // 与个人看板/全局看板统一的阶段命名
  const names: Record<string, string> = {
    'REWRITE': '提示词改写',
    'HYDE': 'HyDE 假设生成',
    'CLASSIFY': '意图识别',
    'RETRIEVE': '检索',
    'RERANK': 'Rerank 重排序',
    'PROMPT': 'Prompt 组装',
    'LLM': '增强生成'
  }
  return names[nodeType] || nodeType
}

function getNodeColor(nodeType: string): string {
  const colors: Record<string, string> = {
    'REWRITE': '#409EFF',
    'HYDE': '#9C27B0',
    'CLASSIFY': '#E6A23C',
    'RETRIEVE': '#909399',
    'RERANK': '#67C23A',
    'PROMPT': '#F56C6C',
    'LLM': '#409EFF'
  }
  return colors[nodeType] || '#909399'
}

function getTimelineBarStyle(node: any, index: number): any {
  const totalDuration = trace.value?.durationMs
  if (!totalDuration || !node.durationMs) return { display: 'none' }

  // 计算节点的起始位置（假设节点按顺序执行）
  let startTime = 0
  for (let i = 0; i < index; i++) {
    startTime += nodes.value[i].durationMs || 0
  }

  const leftPercent = (startTime / totalDuration) * 100
  const widthPercent = Math.max(1, (node.durationMs / totalDuration) * 100)

  return {
    left: leftPercent + '%',
    width: widthPercent + '%',
    background: getNodeColor(node.nodeType)
  }
}
</script>

<style scoped>
.trace-detail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  margin: 0;
}

.prompt-full {
  white-space: pre-wrap;
  word-break: break-all;
  color: #606266;
}

.timeline-box {
  margin-top: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
}

.stage-section-title {
  margin: 8px 0 0;
}

.stage-timeline {
  padding-left: 4px;
}

.stage-card :deep(.el-card__header) {
  padding: 10px 16px;
}

.io-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.io-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

.io-content {
  max-height: 300px;
  overflow: auto;
  background: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}

@media (max-width: 768px) {
  .io-grid {
    grid-template-columns: 1fr;
  }
}
</style>
