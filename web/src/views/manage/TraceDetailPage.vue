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
            <div style="display:flex;gap:8px;align-items:center">
              <el-tag :type="trace?.status === 'SUCCESS' ? 'success' : trace?.status === 'ERROR' ? 'danger' : 'warning'">
                {{ trace?.status || '-' }}
              </el-tag>
              <el-button size="small" type="primary" @click="submitReplayRequest" :loading="replaySubmitting" :disabled="!trace">
                提交回放申请
              </el-button>
            </div>
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
                <!-- REWRITE: 原始输入 + 改写开关 -->
                <div v-if="node.nodeType === 'REWRITE'" class="io-structured">
                  <div class="field-row"><span class="field-label">原始输入</span><div class="field-value query-content">{{ parseInput(node.inputData)?.originalPrompt || '-' }}</div></div>
                  <div class="field-row"><span class="field-label">启用改写</span><span class="field-value">{{ parseInput(node.inputData)?.enableRewrite ? '是' : '否' }}</span></div>
                </div>
                <!-- HYDE: 查询词 + 是否启用 -->
                <div v-else-if="node.nodeType === 'HYDE'" class="io-structured">
                  <div class="field-row"><span class="field-label">查询词</span><div class="field-value query-content">{{ parseInput(node.inputData)?.query || '-' }}</div></div>
                  <div class="field-row"><span class="field-label">启用 HyDE</span><span class="field-value">{{ parseInput(node.inputData)?.enabled ? '是' : '否' }}</span></div>
                </div>
                <!-- CLASSIFY: 输入查询 -->
                <div v-else-if="node.nodeType === 'CLASSIFY'" class="io-structured">
                  <div class="field-row"><span class="field-label">输入查询</span><div class="field-value query-content">{{ parseInput(node.inputData)?.query || '-' }}</div></div>
                </div>
                <!-- RETRIEVE: 显示查询词 + 知识库 + HyDE 信息 -->
                <div v-else-if="node.nodeType === 'RETRIEVE'" class="io-structured">
                  <div class="field-row"><span class="field-label">查询词</span><div class="field-value query-content">{{ parseInput(node.inputData)?.query || '-' }}</div></div>
                  <div class="field-row"><span class="field-label">知识库</span><span class="field-value">{{ parseInput(node.inputData)?.kbId ? ('ID=' + parseInput(node.inputData).kbId) : '全局' }}</span></div>
                  <div class="field-row"><span class="field-label">HyDE</span><span class="field-value">{{ parseInput(node.inputData)?.hydeEnabled ? '已启用' : '未启用' }}</span></div>
                  <div class="field-row" v-if="parseInput(node.inputData)?.equivQueryCount"><span class="field-label">等价查询数</span><span class="field-value">{{ parseInput(node.inputData).equivQueryCount }}</span></div>
                </div>
                <!-- RERANK: 显示待排序文档列表 -->
                <div v-else-if="node.nodeType === 'RERANK'" class="io-structured">
                  <div class="field-row"><span class="field-label">启用 Rerank</span><span class="field-value">{{ parseInput(node.inputData)?.enableRerank ? '是' : '否' }}</span></div>
                  <div class="field-row"><span class="field-label">待排序文档数</span><span class="field-value">{{ parseInput(node.inputData)?.inputCount || 0 }}</span></div>
                  <div v-if="parseInput(node.inputData)?.docs?.length" class="doc-list-scroll">
                    <div style="font-size:11px;color:#909399;margin-bottom:4px">点击文档展开/收起完整内容</div>
                    <template v-for="(doc, idx) in parseInput(node.inputData).docs" :key="idx">
                      <div class="doc-card-llm" @click="toggleDoc(Number(idx))">
                        <div class="doc-header">
                          <span class="doc-index">文档 #{{ Number(idx) + 1 }}</span>
                        </div>
                        <pre class="doc-content-llm">{{ doc.content }}</pre>
                      </div>
                      <div v-show="expandedDocs[Number(idx)]" class="doc-expanded">
                        <span class="doc-expanded-label">文档 #{{ Number(idx) + 1 }} 完整内容</span>
                        <pre class="doc-expanded-content">{{ doc.content }}</pre>
                      </div>
                    </template>
                  </div>
                </div>
                <!-- PROMPT: 领域 + 模板 + 记忆 + 完整文档 -->
                <div v-else-if="node.nodeType === 'PROMPT'" class="io-structured">
                  <div class="field-row"><span class="field-label">领域</span><span class="field-value">{{ parseInput(node.inputData)?.domain || '-' }}</span></div>
                  <div class="field-row"><span class="field-label">文档数</span><span class="field-value">{{ parseInput(node.inputData)?.docCount || 0 }}</span></div>
                  <div v-if="parseInput(node.inputData)?.rawTemplate" class="field-row">
                    <span class="field-label">原始模板</span>
                    <pre class="prompt-input-content scrollable">{{ parseInput(node.inputData).rawTemplate }}</pre>
                  </div>
                  <div v-if="parseInput(node.inputData)?.memoryContext" class="field-row">
                    <span class="field-label">对话记忆</span>
                    <pre class="memory-content scrollable">{{ parseInput(node.inputData).memoryContext }}</pre>
                  </div>
                  <div v-if="parseInput(node.inputData)?.docs?.length" class="doc-list-scroll">
                    <div style="font-size:11px;color:#909399;margin-bottom:4px">参考文档：</div>
                    <div v-for="(doc, idx) in parseInput(node.inputData).docs" :key="idx" class="doc-card-llm">
                      <div class="doc-header">
                        <span class="doc-index">#{{ Number(idx) + 1 }}</span>
                        <span v-if="doc.score" class="doc-score">{{ typeof doc.score === 'number' ? doc.score.toFixed(2) : doc.score }}</span>
                      </div>
                      <pre class="doc-content-llm">{{ doc.content }}</pre>
                    </div>
                  </div>
                </div>
                <!-- LLM: 模型 + 系统提示词 + 用户输入 -->
                <div v-else-if="node.nodeType === 'LLM'" class="io-structured">
                  <div class="field-row"><span class="field-label">模型</span><span class="field-value"><el-tag size="small">{{ parseInput(node.inputData)?.modelId || '-' }}</el-tag></span></div>
                  <div v-if="parseInput(node.inputData)?.systemPrompt" class="field-row">
                    <span class="field-label">系统提示词</span>
                    <pre class="prompt-input-content">{{ parseInput(node.inputData).systemPrompt }}</pre>
                  </div>
                  <div v-if="parseInput(node.inputData)?.userPrompt" class="field-row">
                    <span class="field-label">用户输入</span>
                    <div class="field-value query-content">{{ parseInput(node.inputData).userPrompt }}</div>
                  </div>
                </div>
                <!-- 其他阶段 fallback -->
                <pre v-else-if="node.inputData" class="io-content">{{ formatJson(node.inputData) }}</pre>
                <span v-else style="color:#999">-</span>
              </div>
              <div class="io-block">
                <div class="io-label">输出数据</div>
                <!-- REWRITE: 改写结果 -->
                <div v-if="node.nodeType === 'REWRITE'" class="io-structured">
                  <div class="field-row">
                    <span class="field-label">改写结果</span>
                    <div class="field-value query-content">{{ parseOutput(node.outputData)?.rewrittenQuery || '-' }}</div>
                  </div>
                </div>
                <!-- HYDE: 假设文档 + 等价查询 -->
                <div v-else-if="node.nodeType === 'HYDE'" class="io-structured">
                  <div class="field-row"><span class="field-label">HyDE 启用</span><span class="field-value">{{ parseOutput(node.outputData)?.hydeEnabled ? '是' : '否' }}</span></div>
                  <div class="field-row" v-if="parseOutput(node.outputData)?.skipped"><span class="field-label">状态</span><span class="field-value"><el-tag size="small">跳过</el-tag></span></div>
                  <div v-if="parseOutput(node.outputData)?.hydeDoc" class="field-row">
                    <span class="field-label">假设文档</span>
                    <pre class="hyde-doc-full">{{ parseOutput(node.outputData).hydeDoc }}</pre>
                  </div>
                  <div v-if="parseOutput(node.outputData)?.equivQueries?.length" class="field-row">
                    <span class="field-label">等价查询</span>
                    <div class="field-value">
                      <div v-for="(q, idx) in parseOutput(node.outputData).equivQueries" :key="idx" class="equiv-query">{{ Number(idx) + 1 }}. {{ q }}</div>
                    </div>
                  </div>
                </div>
                <!-- CLASSIFY: 意图标签 + 置信度 -->
                <div v-else-if="node.nodeType === 'CLASSIFY'" class="io-structured">
                  <div class="field-row"><span class="field-label">意图标签</span><span class="field-value"><el-tag size="small" :type="parseOutput(node.outputData)?.isSystem ? 'warning' : ''">{{ parseOutput(node.outputData)?.label || '-' }}</el-tag></span></div>
                  <div class="field-row"><span class="field-label">置信度</span><span class="field-value"><strong>{{ typeof parseOutput(node.outputData)?.confidence === 'number' ? (parseOutput(node.outputData).confidence * 100).toFixed(1) : parseOutput(node.outputData)?.confidence }}%</strong></span></div>
                  <div class="field-row"><span class="field-label">系统意图</span><span class="field-value">{{ parseOutput(node.outputData)?.isSystem ? '是' : '否' }}</span></div>
                  <div class="field-row" v-if="parseOutput(node.outputData)?.kbId"><span class="field-label">知识库 ID</span><span class="field-value">{{ parseOutput(node.outputData).kbId }}</span></div>
                </div>
                <!-- RETRIEVE: 显示每篇检索到的文档 -->
                <div v-else-if="node.nodeType === 'RETRIEVE'" class="io-structured">
                  <div class="field-row"><span class="field-label">检索到</span><span class="field-value"><strong>{{ parseOutput(node.outputData)?.count || 0 }}</strong> 篇文档</span></div>
                  <div v-if="parseOutput(node.outputData)?.docs?.length" class="doc-list-scroll">
                    <div v-for="(doc, idx) in parseOutput(node.outputData).docs" :key="idx" class="doc-card-llm">
                      <div class="doc-header">
                        <span class="doc-index">文档 {{ Number(idx) + 1 }}</span>
                        <span v-if="doc.score" class="doc-score">分数: {{ typeof doc.score === 'number' ? doc.score.toFixed(3) : doc.score }}</span>
                      </div>
                      <pre class="doc-content-llm">{{ doc.content }}</pre>
                      <div class="doc-meta">来源: {{ doc.source || '向量检索' }}<span v-if="doc.kbId"> · 知识库 ID: {{ doc.kbId }}</span></div>
                    </div>
                  </div>
                </div>
                <!-- RERANK: 显示每篇重排序后的文档及分数 -->
                <div v-else-if="node.nodeType === 'RERANK'" class="io-structured">
                  <div class="field-row"><span class="field-label">结果</span><span class="field-value">从 <strong>{{ parseOutput(node.outputData)?.inputCount || 0 }}</strong> 篇中保留 <strong>{{ parseOutput(node.outputData)?.count || 0 }}</strong> 篇</span></div>
                  <div v-if="parseOutput(node.outputData)?.docs?.length" class="doc-list-scroll">
                    <div style="font-size:11px;color:#909399;margin-bottom:4px">点击文档展开/收起完整内容</div>
                    <template v-for="(doc, idx) in parseOutput(node.outputData).docs" :key="idx">
                      <div class="doc-card-llm ranked" @click="toggleRankedDoc(Number(idx))">
                        <div class="doc-header">
                          <span class="doc-index">第 {{ Number(idx) + 1 }} 名</span>
                          <span v-if="doc.score" class="doc-score">Rerank 分数: {{ typeof doc.score === 'number' ? doc.score.toFixed(1) : doc.score }}</span>
                        </div>
                        <pre class="doc-content-llm">{{ doc.content }}</pre>
                      </div>
                      <div v-show="expandedRankedDocs[Number(idx)]" class="doc-expanded">
                        <span class="doc-expanded-label">第 {{ Number(idx) + 1 }} 名 完整内容</span>
                        <pre class="doc-expanded-content">{{ doc.content }}</pre>
                      </div>
                    </template>
                  </div>
                </div>
                <!-- PROMPT: 显示完整的提示词 -->
                <div v-else-if="node.nodeType === 'PROMPT'" class="io-structured prompt-display">
                  <div class="field-row"><span class="field-label">场景</span><span class="field-value"><el-tag size="small" type="warning">{{ parseOutput(node.outputData)?.scene || '-' }}</el-tag></span></div>
                  <div class="field-row"><span class="field-label">提示词长度</span><span class="field-value">{{ parseOutput(node.outputData)?.promptLength || 0 }} 字符</span></div>
                  <div v-if="parseOutput(node.outputData)?.fullPrompt" class="field-row">
                    <span class="field-label">完整提示词</span>
                    <div class="field-value">
                      <pre class="prompt-content scrollable">{{ parseOutput(node.outputData).fullPrompt }}</pre>
                    </div>
                  </div>
                  <!-- 调试：显示原始 output_data -->
                  <div v-if="!parseOutput(node.outputData)?.fullPrompt" class="field-row" style="background:#fff2e8;padding:8px;border-radius:4px">
                    <span class="field-label">⚠️ 调试</span>
                    <div class="field-value" style="font-size:11px">
                      <div><strong>outputData 类型:</strong> {{ typeof node.outputData }}</div>
                      <div><strong>outputData 值:</strong> {{ String(node.outputData).slice(0, 500) }}</div>
                      <div><strong>解析后:</strong> {{ JSON.stringify(parseOutput(node.outputData))?.slice(0, 200) }}</div>
                    </div>
                  </div>
                </div>
                <!-- LLM: 模型回复 + token -->
                <div v-else-if="node.nodeType === 'LLM'" class="io-structured">
                  <div class="field-row"><span class="field-label">模型</span><span class="field-value"><el-tag size="small">{{ parseOutput(node.outputData)?.modelId || '-' }}</el-tag></span></div>
                  <div class="field-row">
                    <span class="field-label">Token</span>
                    <span class="field-value">输入 <strong>{{ parseOutput(node.outputData)?.inputTokens || 0 }}</strong> / 输出 <strong>{{ parseOutput(node.outputData)?.outputTokens || 0 }}</strong></span>
                  </div>
                  <div v-if="parseOutput(node.outputData)?.response" class="field-row">
                    <span class="field-label">模型回复</span>
                    <pre class="llm-response">{{ parseOutput(node.outputData).response }}</pre>
                  </div>
                </div>
                <!-- 其他阶段 fallback -->
                <pre v-else-if="node.outputData" class="io-content">{{ formatJson(node.outputData) }}</pre>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const loading = ref(false)
const trace = ref<any>(null)
const nodes = ref<any[]>([])
const replaySubmitting = ref(false)

// 文档展开状态管理
const expandedDocs = ref<Record<number, boolean>>({})
const expandedRankedDocs = ref<Record<number, boolean>>({})

function toggleDoc(idx: number) {
  expandedDocs.value[idx] = !expandedDocs.value[idx]
}

function toggleRankedDoc(idx: number) {
  expandedRankedDocs.value[idx] = !expandedRankedDocs.value[idx]
}

onMounted(() => {
  loadTraceDetail()
})

function goBack() {
  router.push('/manage/dashboard')
}

async function submitReplayRequest() {
  if (!trace.value) return
  try {
    await ElMessageBox.confirm(
      `确认将以下提问提交为回放申请？\n\n"${trace.value.originalPrompt}"`,
      '提交回放申请',
      { confirmButtonText: '确认提交', cancelButtonText: '取消' }
    )
    replaySubmitting.value = true
    await request.post('/rag-lab/data-replay', {
      traceId: trace.value.traceId,
      questionText: trace.value.originalPrompt,
      sourceConversationId: trace.value.conversationId,
      createUserId: auth.userInfo?.id
    })
    ElMessage.success('回放申请已提交')
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '提交失败')
    }
  } finally {
    replaySubmitting.value = false
  }
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

// 解析输入数据（兼容 string / object）
function parseInput(data: any): any {
  if (!data) return {}
  if (typeof data === 'object') return data
  try { return JSON.parse(data) } catch { return {} }
}

// 解析输出数据（兼容 string / object）
function parseOutput(data: any): any {
  if (!data) return {}
  if (typeof data === 'object') return data
  try { return JSON.parse(data) } catch { return {} }
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
  word-break: break-word;
  overflow-wrap: break-word;
  overflow-x: auto;
  color: #606266;
  max-width: 100%;
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
  grid-template-columns: 4fr 5fr;
  gap: 12px;
  min-width: 0;
}

.io-block {
  min-width: 0;
  overflow: hidden;
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
  word-break: break-word;
  overflow-wrap: break-word;
  margin: 0;
  max-width: 100%;
  box-sizing: border-box;
}

/* 结构化展示 */
.io-structured {
  font-size: 13px;
  line-height: 1.6;
  max-width: 100%;
  overflow: hidden;
}
.field-row {
  display: flex;
  gap: 8px;
  padding: 4px 0;
  border-bottom: 1px solid #f0f0f0;
  max-width: 100%;
  min-width: 0;
  box-sizing: border-box;
}
.field-row:last-child {
  border-bottom: none;
}
.field-label {
  font-size: 12px;
  color: #909399;
  min-width: 80px;
  flex-shrink: 0;
}
.field-value {
  color: #303133;
  word-break: break-word;
  overflow-wrap: break-word;
  min-width: 0;
  flex: 1;
  max-width: 100%;
}

/* 文档列表 - 固定高度滚动 */
.doc-list-scroll {
  margin-top: 8px;
  max-height: 300px;
  overflow-y: auto;
  overflow-x: hidden;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 8px;
  background: #fafafa;
  max-width: 100%;
  box-sizing: border-box;
}

/* LLM 风格文档卡片 */
.doc-card-llm {
  background: #fff;
  border-radius: 6px;
  padding: 10px 12px;
  border-left: 3px solid #909399;
  margin-bottom: 8px;
  max-width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}
.doc-card-llm.ranked {
  border-left-color: #67C23A;
}
.doc-card-llm:last-child {
  margin-bottom: 0;
}

/* LLM 风格文档内容 */
.doc-content-llm {
  background: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: break-word;
  margin: 6px 0;
  max-height: 150px;
  max-width: 100%;
  overflow-y: auto;
  overflow-x: auto;
  box-sizing: border-box;
}

.doc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
  font-size: 12px;
  min-width: 0;
}
.doc-index {
  font-weight: 500;
  color: #303133;
}
.doc-score {
  font-size: 12px;
  color: #E6A23C;
  font-weight: 500;
  flex-shrink: 0;
  margin-left: 8px;
}
.doc-meta {
  font-size: 11px;
  color: #909399;
  margin-top: 4px;
}

/* 展开的完整文档 */
.doc-expanded {
  margin-top: 6px;
  padding: 10px;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  max-width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}
.doc-expanded-label {
  font-size: 11px;
  color: #909399;
  font-weight: 500;
}
.doc-expanded-content {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: break-word;
  overflow-x: auto;
  max-height: 400px;
  overflow-y: auto;
  background: #f5f7fa;
  padding: 8px;
  border-radius: 4px;
  max-width: 100%;
  box-sizing: border-box;
}

/* 可点击折叠的迷你文档 */
.mini-doc.collapsible {
  cursor: pointer;
  transition: background 0.2s;
  max-width: 100%;
  box-sizing: border-box;
}
.mini-doc.collapsible:hover {
  background: #eef0f3;
}

/* 滚动容器 */
.scrollable {
  max-height: 200px;
  overflow-y: auto;
  overflow-x: auto;
  max-width: 100%;
  box-sizing: border-box;
}

/* 待排序文档小列表 */
.mini-doc {
  display: flex;
  gap: 8px;
  font-size: 12px;
  padding: 4px 8px;
  background: #fafafa;
  border-radius: 3px;
  align-items: flex-start;
  max-width: 100%;
  box-sizing: border-box;
}
.mini-doc-idx {
  color: #909399;
  flex-shrink: 0;
}
.mini-doc-content {
  color: #606266;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

/* 提示词展示 */
.prompt-section {
  margin-top: 8px;
}
.prompt-display {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.prompt-content {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: break-word;
  overflow-x: auto;
  max-height: 400px;
  overflow-y: auto;
  margin: 0;
  color: #303133;
  max-width: 100%;
  box-sizing: border-box;
}

/* HyDE 等价查询 */
.equiv-query {
  padding: 2px 0;
  font-size: 12px;
  color: #606266;
}
.hyde-doc {
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: break-word;
  overflow-x: auto;
  background: #f5f7fa;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  margin-top: 4px;
  max-width: 100%;
  box-sizing: border-box;
}
.hyde-doc-full {
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: break-word;
  overflow-x: auto;
  background: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  font-size: 12px;
  margin: 4px 0 0;
  max-height: 200px;
  overflow: auto;
  max-width: 100%;
  box-sizing: border-box;
}

/* 查询内容展示 */
.query-content {
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: break-word;
  background: #f0f7ff;
  padding: 8px 10px;
  border-radius: 4px;
  font-size: 13px;
  margin: 4px 0 0;
  line-height: 1.5;
  max-width: 100%;
  box-sizing: border-box;
}

/* 对话记忆展示 */
.memory-content {
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: break-word;
  overflow-x: auto;
  background: #fff8e6;
  padding: 8px 10px;
  border-radius: 4px;
  font-size: 12px;
  margin: 4px 0 0;
  max-height: 150px;
  overflow: auto;
  line-height: 1.5;
  max-width: 100%;
  box-sizing: border-box;
}

/* LLM 阶段输入提示词 */
.prompt-input-content {
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: break-word;
  overflow-x: auto;
  background: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  font-size: 12px;
  margin: 4px 0 0;
  max-height: 200px;
  overflow: auto;
  line-height: 1.5;
  max-width: 100%;
  box-sizing: border-box;
}

/* LLM 回复展示 */
.llm-response {
  white-space: pre-wrap;
  word-break: break-word;
  overflow-wrap: break-word;
  overflow-x: auto;
  background: #f0f9eb;
  padding: 12px;
  border-radius: 4px;
  font-size: 13px;
  margin: 4px 0 0;
  max-height: 300px;
  overflow: auto;
  line-height: 1.6;
  border-left: 3px solid #67c23a;
  max-width: 100%;
  box-sizing: border-box;
}

/* 小文档分数 */
.mini-doc-score {
  color: #909399;
  font-size: 11px;
  flex-shrink: 0;
  margin-left: auto;
}

@media (max-width: 768px) {
  .io-grid {
    grid-template-columns: 1fr;
  }
}
</style>
