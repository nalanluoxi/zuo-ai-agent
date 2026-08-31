<template>
  <div class="chat-layout">
    <!-- 容器：左侧模块导航 + 右侧内容 -->
    <el-container class="chat-container">
      <!-- 左侧模块导航 -->
      <el-aside class="chat-sidebar" width="220px">
        <div class="sidebar-content">
          <!-- 模块导航菜单 -->
          <div class="module-nav">
            <div 
              class="nav-item" 
              :class="{ active: currentModule === 'chat' }"
              @click="switchModule('chat')"
            >
              <el-icon><ChatDotSquare /></el-icon>
              <span>对话</span>
            </div>
            <div 
              class="nav-item" 
              :class="{ active: currentModule === 'knowledge' }"
              @click="switchModule('knowledge')"
            >
              <el-icon><FolderOpened /></el-icon>
              <span>知识库</span>
            </div>
            <div 
              class="nav-item" 
              :class="{ active: currentModule === 'intent' }"
              @click="switchModule('intent')"
            >
              <el-icon><Connection /></el-icon>
              <span>意图节点</span>
            </div>
            <div 
              class="nav-item" 
              :class="{ active: currentModule === 'dashboard' }"
              @click="switchModule('dashboard')"
            >
              <el-icon><TrendCharts /></el-icon>
              <span>个人看板</span>
            </div>
          </div>

          <!-- 对话模块：对话列表 -->
          <template v-if="currentModule === 'chat'">
            <!-- 新建对话按钮 -->
            <el-button class="new-chat-btn" type="primary" @click="handleNewChat">
              <el-icon><Plus /></el-icon>
              新建对话
            </el-button>

            <!-- 搜索框 -->
            <el-input
              v-model="searchQuery"
              placeholder="搜索对话..."
              size="small"
              clearable
              @input="debounceSearch"
              class="search-input"
            />

            <!-- 对话列表 -->
            <div class="conversation-list">
              <div
                v-for="conv in conversations"
                :key="conv.id"
                class="conversation-item"
                :class="{ active: activeConversationId === conv.id }"
                @click="selectConversation(conv.id)"
              >
                <div class="conv-title">{{ conv.title }}</div>
                <div class="conv-meta">
                  <span class="conv-time">{{ formatTime(conv.updated_at || conv.updateTime) }}</span>
                  <div class="conv-actions">
                    <el-button text size="small" class="conv-edit" @click.stop="showEditDialog(conv)">
                      <el-icon><Edit /></el-icon>
                    </el-button>
                    <el-button text size="small" class="conv-delete" @click.stop="deleteConversation(conv.id)">
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </div>
                </div>
              </div>
            </div>

            <!-- 编辑标题弹窗 -->
            <el-dialog v-model="editDialogVisible" title="编辑对话标题" width="400px">
              <el-input v-model="editTitle" placeholder="请输入新的标题" />
              <template #footer>
                <el-button @click="editDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="saveEditTitle">保存</el-button>
              </template>
            </el-dialog>
          </template>
        </div>
      </el-aside>

      <!-- 右侧主内容区 -->
      <el-main class="chat-main">
        <router-view />
      </el-main>
    </el-container>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../api/request'

const route = useRoute()
const router = useRouter()

// 模块导航
const moduleRoutes = {
  chat: '/chat',
  knowledge: '/chat/knowledge',
  intent: '/chat/intent',
  dashboard: '/chat/dashboard'
}

const currentModule = computed(() => {
  const path = route.path
  if (path.includes('/knowledge')) return 'knowledge'
  if (path.includes('/intent')) return 'intent'
  if (path.includes('/dashboard')) return 'dashboard'
  return 'chat'
})

function switchModule(module: string) {
  router.push(moduleRoutes[module as keyof typeof moduleRoutes])
}

// 对话列表
const conversations = ref<any[]>([])
const activeConversationId = ref<string | null>(null)
const searchQuery = ref('')

// 编辑标题弹窗
const editDialogVisible = ref(false)
const editTitle = ref('')
const editingConversationId = ref('')

let searchTimer: any = null

onMounted(async () => {
  await loadConversations()
  const convId = route.query.conversationId as string
  if (convId) {
    activeConversationId.value = convId
  }
})

// ==================== 对话列表 ====================

async function loadConversations() {
  try {
    const params: any = {}
    if (searchQuery.value) params.search = searchQuery.value
    const json = await request.get('/conversations', { params }) as any
    if (json.code === 0 && json.data?.content) {
      conversations.value = json.data.content
      if (conversations.value.length > 0 && !activeConversationId.value) {
        activeConversationId.value = conversations.value[0].id
        router.replace({ path: '/chat', query: { conversationId: conversations.value[0].id } })
      }
    }
  } catch {}
}

function debounceSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => loadConversations(), 300)
}

async function handleNewChat() {
  try {
    const json = await request.post('/conversations', { title: '新对话' }) as any
    if (json.code === 0 && json.data) {
      const newConv = { id: json.data.conversationId, title: json.data.title, updated_at: new Date().toISOString() }
      conversations.value.unshift(newConv)
      activeConversationId.value = newConv.id
      router.push({ path: '/chat', query: { conversationId: newConv.id } })
      ElMessage.success('新对话已创建')
    }
  } catch {
    ElMessage.error('创建对话失败')
  }
}

function selectConversation(id: string) {
  activeConversationId.value = id
  router.push({ path: '/chat', query: { conversationId: id } })
}

async function deleteConversation(id: string) {
  try {
    await request.delete(`/conversations/${id}`)
    conversations.value = conversations.value.filter(c => c.id !== id)
    if (activeConversationId.value === id) {
      activeConversationId.value = conversations.value[0]?.id || null
      if (activeConversationId.value) {
        router.push({ path: '/chat', query: { conversationId: activeConversationId.value } })
      } else {
        router.push('/chat')
      }
    }
    ElMessage.success('对话已删除')
  } catch {
    ElMessage.error('删除失败')
  }
}

function formatTime(dateStr: string | Date): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  if (diff < 60000) return '刚才'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`
  return date.toLocaleDateString()
}

// ==================== 编辑标题 ====================

function showEditDialog(conv: any) {
  editingConversationId.value = conv.id
  editTitle.value = conv.title
  editDialogVisible.value = true
}

async function saveEditTitle() {
  if (!editTitle.value.trim()) {
    ElMessage.warning('标题不能为空')
    return
  }
  try {
    await request.put(`/conversations/${editingConversationId.value}`, { title: editTitle.value })
    // 更新本地列表
    const conv = conversations.value.find(c => c.id === editingConversationId.value)
    if (conv) {
      conv.title = editTitle.value
    }
    editDialogVisible.value = false
    ElMessage.success('标题已更新')
  } catch {
    ElMessage.error('更新失败')
  }
}
</script>

<style scoped>
.chat-layout {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chat-container {
  flex: 1;
  overflow: hidden;
}

.chat-sidebar {
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  width: 220px !important;
}

.sidebar-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 16px;
  overflow: hidden;
}

.module-nav {
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.module-nav .nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  margin-bottom: 4px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  color: #555;
  transition: all 0.2s;
}

.module-nav .nav-item:hover {
  background: #f0f2f5;
  color: #409eff;
}

.module-nav .nav-item.active {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 500;
}

.new-chat-btn {
  width: 100%;
  margin-bottom: 12px;
}

.search-input {
  margin-bottom: 12px;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  margin-bottom: 12px;
}

.conversation-item {
  padding: 12px;
  margin-bottom: 6px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.15s;
  border: 1px solid #e4e7ed;
}

.conversation-item:hover {
  background: #f5f7fa;
  border-color: #c0c4cc;
}

.conversation-item.active {
  background: #ecf5ff;
  border-color: #409eff;
}

.conv-title {
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  color: #333;
  margin-bottom: 4px;
}

.conv-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.conv-time {
  font-size: 12px;
  color: #999;
}

.conv-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s;
}

.conversation-item:hover .conv-actions {
  opacity: 1;
}

.conv-actions .conv-edit,
.conv-actions .conv-delete {
  padding: 2px 4px;
}

.conv-edit,
.conv-delete {
  opacity: 0;
  transition: opacity 0.15s;
}

.conversation-item:hover .conv-edit,
.conversation-item:hover .conv-delete {
  opacity: 1;
}

.chat-main {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background: #fff;
}
</style>
