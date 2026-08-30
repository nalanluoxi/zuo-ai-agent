<template>
  <div class="chat-page">
    <!-- 欢迎页（无消息时显示） -->
    <div v-if="messages.length === 0 && !loading" class="welcome-page">
      <div class="welcome-icon">
        <el-icon :size="48"><ChatDotSquare /></el-icon>
      </div>
      <h2>ZUO AI 智能助手</h2>
      <p>输入您的问题，开始对话</p>
    </div>

    <!-- 消息列表 -->
    <div class="messages-container" ref="messagesContainer">
      <div v-for="(msg, idx) in messages" :key="idx" :class="['message-wrapper', msg.role]">
        <div class="message-content">
          <div :class="['message-bubble', msg.role]">
            <div v-if="msg.role === 'assistant'" class="markdown-body" v-html="renderMarkdown(msg.content)"></div>
            <span v-else>{{ msg.content }}</span>
          </div>
          <div v-if="msg.role === 'user'" class="message-actions">
            <el-button text type="danger" size="small" @click="deleteMessage(idx)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <div v-if="msg.role === 'assistant'" class="message-feedback">
            <el-button text size="small" @click="feedbackMessage(idx, 'like')">
              <el-icon><ThumbsUp /></el-icon>
            </el-button>
            <el-button text size="small" @click="feedbackMessage(idx, 'dislike')">
              <el-icon><ThumbsDown /></el-icon>
            </el-button>
          </div>
        </div>
        <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
      </div>

      <!-- 流式输出中 -->
      <div v-if="loading" class="message-wrapper assistant">
        <div class="message-content">
          <div class="message-bubble assistant">
            <span v-if="streamingContent">{{ streamingContent }}</span>
            <span v-else><el-icon class="is-loading"><Loading /></el-icon> AI 正在思考中...</span>
            <span v-if="streamingContent" class="cursor-blink">|</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入框 -->
    <div class="input-section">
      <el-input
        v-model="inputText"
        type="textarea"
        :rows="2"
        placeholder="输入您的问题... (Ctrl+Enter 发送)"
        @keydown.ctrl.enter="sendMessage"
        @keydown.meta.enter="sendMessage"
        :disabled="loading"
      />
      <div class="input-actions">
        <span class="input-hint">Ctrl + Enter 发送</span>
        <div class="input-buttons">
          <el-button @click="resetChat" :disabled="messages.length === 0 || loading" size="small">
            <el-icon><Refresh /></el-icon>
            重置
          </el-button>
          <el-button type="primary" @click="sendMessage" :loading="loading" :disabled="!inputText.trim()" size="small">
            <el-icon><Send /></el-icon>
            发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../api/request'

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
  feedback?: 'like' | 'dislike'
}

const route = useRoute()
const messages = ref<Message[]>([])
const inputText = ref('')
const loading = ref(false)
const streamingContent = ref('')
const messagesContainer = ref<HTMLElement | null>(null)
let eventSource: EventSource | null = null

const conversationId = ref('')

onMounted(async () => {
  // 从路由参数或 props 获取 conversationId
  conversationId.value = (route.query.conversationId as string) || ''
  
  // 如果有 conversationId，加载历史消息
  if (conversationId.value) {
    await loadMessages()
  }
})

onUnmounted(() => {
  closeEventSource()
})

function closeEventSource() {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

async function loadMessages() {
  if (!conversationId.value) return
  try {
    const json = await request.get(`/conversations/${conversationId.value}/messages`, { params: { pageSize: 100 } }) as any
    if (json.code === 0 && json.data?.list) {
      messages.value = json.data.list.map((m: any) => ({
        id: m.id,
        role: m.role === 'user' ? 'user' : 'assistant',
        content: m.content,
        timestamp: new Date(m.create_time || m.createdAt)
      }))
      await nextTick()
      scrollToBottom()
    }
  } catch {}
}

const formatTime = (date: Date): string => {
  const d = new Date(date)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function renderMarkdown(text: string): string {
  // 简单 markdown 渲染
  return text
    .replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\n/g, '<br>')
}

const sendMessage = async () => {
  if (!inputText.value.trim()) {
    ElMessage.warning('请输入消息')
    return
  }
  if (loading.value) return

  // 如果没有 conversationId，先创建对话
  if (!conversationId.value) {
    try {
      const json = await request.post('/conversations', { title: inputText.value.slice(0, 20) }) as any
      if (json.code === 0 && json.data) {
        conversationId.value = json.data.conversationId
      } else {
        ElMessage.error('创建对话失败')
        return
      }
    } catch (e) {
      ElMessage.error('创建对话失败')
      return
    }
  }

  // 添加用户消息
  messages.value.push({
    id: Date.now().toString(),
    role: 'user',
    content: inputText.value,
    timestamp: new Date()
  })

  const userMessage = inputText.value
  inputText.value = ''
  loading.value = true
  streamingContent.value = ''

  await nextTick()
  scrollToBottom()

  // 调用后端 SSE 流式接口
  const token = localStorage.getItem('satoken') || ''
  const url = `/api/chat/stream/smart?message=${encodeURIComponent(userMessage)}&conversationId=${conversationId.value}&satoken=${token}`

  closeEventSource()
  eventSource = new EventSource(url)

  eventSource.onmessage = (event) => {
    if (event.data === '[DONE]') {
      // 流结束，添加完整的 assistant 消息
      messages.value.push({
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: streamingContent.value,
        timestamp: new Date()
      })
      streamingContent.value = ''
      loading.value = false
      closeEventSource()
      scrollToBottom()
    } else {
      // 追加内容
      streamingContent.value += event.data
      scrollToBottom()
    }
  }

  // 处理后端返回的错误事件
  eventSource.addEventListener('error', (event: any) => {
    if (event.data) {
      ElMessage.error(event.data)
      if (streamingContent.value) {
        messages.value.push({
          id: (Date.now() + 1).toString(),
          role: 'assistant',
          content: streamingContent.value,
          timestamp: new Date()
        })
        streamingContent.value = ''
      }
      loading.value = false
      closeEventSource()
    }
  })

  eventSource.onerror = () => {
    loading.value = false
    if (streamingContent.value) {
      messages.value.push({
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: streamingContent.value,
        timestamp: new Date()
      })
      streamingContent.value = ''
    } else if (messages.value.length === 0 || messages.value[messages.value.length - 1]?.role !== 'assistant') {
      ElMessage.error('连接失败，请检查服务是否启动')
    }
    closeEventSource()
  }
}

const deleteMessage = (idx: number) => {
  messages.value.splice(idx, 1)
  ElMessage.success('消息已删除')
}

const feedbackMessage = async (idx: number, feedback: 'like' | 'dislike') => {
  if (messages.value[idx]) {
    const message = messages.value[idx]
    const messageId = message.id || `msg_${Date.now()}`
    
    try {
      // 调用真实的反馈 API
      await request.post('/feedback', {
        conversationId: conversationId.value || 'default',
        messageId: messageId,
        feedbackType: feedback === 'like' ? 1 : 0
      })
      
      message.feedback = feedback
      ElMessage.success(feedback === 'like' ? '感谢点赞' : '感谢反馈')
    } catch (e) {
      console.error('反馈失败：', e)
      ElMessage.error('反馈失败')
    }
  }
}

const resetChat = () => {
  messages.value = []
  inputText.value = ''
  streamingContent.value = ''
  loading.value = false
  closeEventSource()
  ElMessage.success('对话已重置')
}

const scrollToBottom = () => {
  if (messagesContainer.value) {
    setTimeout(() => {
      messagesContainer.value!.scrollTop = messagesContainer.value!.scrollHeight
    }, 50)
  }
}
</script>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 900px;
  margin: 0 auto;
  gap: 12px;
}

.welcome-page {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 12px;
  color: #999;
}

.welcome-icon {
  color: #409eff;
  opacity: 0.6;
}

.welcome-page h2 {
  font-size: 20px;
  color: #333;
  font-weight: 600;
}

.welcome-page p {
  font-size: 14px;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.message-wrapper {
  display: flex;
  flex-direction: column;
  gap: 4px;
  animation: slideIn 0.25s ease-out;
}

.message-wrapper.user {
  align-items: flex-end;
}

.message-wrapper.assistant {
  align-items: flex-start;
}

@keyframes slideIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.message-content {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  max-width: 80%;
}

.message-wrapper.user .message-content {
  justify-content: flex-end;
}

.message-bubble {
  padding: 10px 14px;
  border-radius: 12px;
  word-break: break-word;
  line-height: 1.6;
  font-size: 14px;
}

.message-bubble.user {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 4px;
}

.message-bubble.assistant {
  background: #f5f7fa;
  color: #333;
  border: 1px solid #e4e7ed;
  border-bottom-left-radius: 4px;
}

.markdown-body :deep(pre) {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 8px 0;
  font-size: 13px;
}

.markdown-body :deep(code) {
  background: #e8e8e8;
  padding: 2px 4px;
  border-radius: 3px;
  font-size: 13px;
}

.markdown-body :deep(pre code) {
  background: transparent;
  padding: 0;
}

.message-actions,
.message-feedback {
  display: none;
  gap: 4px;
}

.message-wrapper:hover .message-actions,
.message-wrapper:hover .message-feedback {
  display: flex;
}

.message-time {
  font-size: 11px;
  color: #bbb;
  margin: 0 8px;
}

.cursor-blink {
  animation: blink 1s step-end infinite;
  color: #409eff;
}

@keyframes blink {
  50% { opacity: 0; }
}

.input-section {
  padding: 12px 16px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e4e7ed;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}

.input-hint {
  font-size: 12px;
  color: #bbb;
}

.input-buttons {
  display: flex;
  gap: 8px;
}

:deep(.el-textarea__inner) {
  font-size: 14px;
  resize: none;
  border: none;
  box-shadow: none;
  padding: 8px 0;
}

:deep(.el-icon.is-loading) {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
