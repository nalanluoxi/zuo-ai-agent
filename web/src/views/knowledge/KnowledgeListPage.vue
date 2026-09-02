<template>
  <div class="knowledge-list-page">
    <!-- 选项卡 -->
    <el-tabs v-model="activeTab" class="tabs">
      <!-- Tab 1: 搜索所有知识库 -->
      <el-tab-pane label="搜索所有" name="search">
        <div class="search-section">
          <!-- 搜索和过滤工具栏 -->
          <div class="toolbar">
            <el-input
              v-model="searchQuery"
              placeholder="搜索知识库名称..."
              style="width: 300px"
              clearable
              @input="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>

            <el-select
              v-model="filterReadability"
              placeholder="可读性"
              style="width: 150px; margin-left: 10px"
              clearable
              @change="handleFilter"
            >
              <el-option label="全部" value="" />
              <el-option label="公开" value="public" />
              <el-option label="团队" value="team" />
              <el-option label="私密" value="private" />
            </el-select>

            <el-button type="primary" @click="showCreateDialog = true" style="margin-left: 10px">
              <el-icon><Plus /></el-icon>
              新建知识库
            </el-button>
          </div>

          <!-- 知识库卡片列表 -->
          <div class="kb-grid">
            <el-card
              v-for="kb in searchResults"
              :key="kb.id"
              class="kb-card"
              @click="navigateToDetail(kb.id)"
            >
              <div class="kb-header">
                <h3 class="kb-name">{{ kb.name }}</h3>
                <el-tag :type="getReadabilityType(kb.readability)">
                  {{ getReadabilityLabel(kb.readability) }}
                </el-tag>
              </div>

              <p class="kb-description">{{ kb.description }}</p>

              <div class="kb-stats">
                <span class="stat-item">
                  <el-icon><DocumentCopy /></el-icon>
                  {{ kb.documentCount || kb.docCount || 0 }} 个文件
                </span>
                <span class="stat-item">
                  <el-icon><User /></el-icon>
                  {{ kb.createdByUsername || '系统' }}
                </span>
              </div>

              <div class="kb-footer">
                <span class="creator">创建者: {{ kb.createdByUsername || '系统' }}</span>
                <span class="time">{{ formatDate(kb.createTime) }}</span>
              </div>
            </el-card>

            <!-- 空状态 -->
            <div v-if="searchResults.length === 0" class="empty-state">
              <el-empty description="没有找到知识库" />
            </div>
          </div>

          <!-- 分页 -->
          <el-pagination
            v-if="totalSearch > 20"
            v-model:current-page="searchPage"
            :page-size="20"
            :total="totalSearch"
            layout="prev, pager, next"
            class="pagination"
            @current-change="handleSearch"
          />
        </div>
      </el-tab-pane>

      <!-- Tab 2: 我的知识库 -->
      <el-tab-pane label="我的知识库" name="mine">
        <div class="my-kb-section">
          <!-- 工具栏 -->
          <div class="toolbar">
            <el-input
              v-model="mySearchQuery"
              placeholder="搜索我的知识库..."
              style="width: 300px"
              clearable
              @input="handleMySearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>

            <el-button type="primary" @click="showCreateDialog = true; editingKBId = null; createForm = { name: '', description: '', readability: 'private', intentNodeIds: [] }" style="margin-left: 10px">
              <el-icon><Plus /></el-icon>
              新建知识库
            </el-button>
          </div>

          <!-- 知识库表格 -->
          <el-table :data="myKnowledgeBases" style="width: 100%">
            <el-table-column prop="name" label="知识库名称" width="200" />
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
            <el-table-column prop="documentCount" label="文件数" width="80">
              <template #default="{ row }">{{ row.documentCount || row.docCount || 0 }}</template>
            </el-table-column>
            <el-table-column prop="readability" label="可读性" width="100">
              <template #default="{ row }">
                <el-tag :type="getReadabilityType(row.readability)">
                  {{ getReadabilityLabel(row.readability) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" width="160">
              <template #default="{ row }">
                {{ formatDate(row.createTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <span class="action-btn-wrapper">
                  <el-button text type="primary" @click="navigateToDetail(row.id)">
                    详情
                  </el-button>
                </span>
                <span class="action-btn-wrapper">
                  <el-button text type="primary" @click="editKB(row)">
                    编辑
                  </el-button>
                </span>
                <span class="action-btn-wrapper">
                  <el-button text type="danger" @click="deleteKB(row.id)">
                    删除
                  </el-button>
                </span>
              </template>
            </el-table-column>
          </el-table>

          <!-- 空状态 -->
          <el-empty v-if="myKnowledgeBases.length === 0" description="还没有创建知识库" class="mt" />

          <!-- 分页 -->
          <el-pagination
            v-if="totalMy > 20"
            v-model:current-page="myPage"
            :page-size="20"
            :total="totalMy"
            layout="prev, pager, next"
            class="pagination mt"
            @current-change="handleMySearch"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 创建知识库弹窗 -->
    <el-dialog v-model="showCreateDialog" :title="editingKBId ? '编辑知识库' : '新建知识库'" width="560px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="知识库名称">
          <el-input v-model="createForm.name" placeholder="请输入知识库名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入知识库描述"
          />
        </el-form-item>
        <el-form-item label="可读性">
          <el-select v-model="createForm.readability">
            <el-option label="私密" value="private" />
            <el-option label="团队" value="team" />
            <el-option label="公开" value="public" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定意图节点">
          <el-cascader
            v-model="createForm.intentNodeIds"
            :options="cascaderOptions"
            :props="{ multiple: true, checkStrictly: false, emitPath: false }"
            placeholder="选择要绑定的意图节点（可选）"
            style="width: 100%"
            filterable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

// Tab 相关
const activeTab = ref('search')

// 搜索 Tab 数据（分离数据源）
const searchQuery = ref('')
const filterReadability = ref('')
const searchPage = ref(1)
const totalSearch = ref(0)
const searchResults = ref<any[]>([])

// 我的知识库 Tab 数据（分离数据源）
const mySearchQuery = ref('')
const myPage = ref(1)
const totalMy = ref(0)
const myKnowledgeBases = ref<any[]>([])

// 创建/编辑知识库表单
const showCreateDialog = ref(false)
const editingKBId = ref<string | null>(null)
const intentNodeList = ref<any[]>([])
const cascaderOptions = ref<any[]>([])
const createForm = ref({
  name: '',
  description: '',
  readability: 'private',
  intentNodeIds: [] as string[]
})

// debounce 计时器
let searchTimer: ReturnType<typeof setTimeout> | null = null
let mySearchTimer: ReturnType<typeof setTimeout> | null = null

// 加载意图节点列表（用于绑定选择）
async function loadIntentNodes() {
  try {
    const res = await request.get('/intent/nodes') as any
    const allNodes = res?.data || res || []
    intentNodeList.value = allNodes
    cascaderOptions.value = buildCascaderTree(allNodes, null)
  } catch { /* ignore */ }
}

// 构建 cascader 树结构
function buildCascaderTree(nodes: any[], parentId: string | null): any[] {
  return nodes
    .filter((n: any) => String(n.parentId) === String(parentId))
    .map((n: any) => {
      const children = buildCascaderTree(nodes, n.id)
      return {
        value: n.id,
        label: n.label,
        children: children.length > 0 ? children : undefined
      }
    })
}

// 加载搜索 Tab 数据（后端搜索）
async function loadSearchResults() {
  try {
    const params: any = { current: searchPage.value, pageSize: 20 }
    if (searchQuery.value) {
      params.name = searchQuery.value
    }
    const res = await request.get('/knowledge-base/page', { params }) as any
    const data = res.data || res
    searchResults.value = data.records || []
    totalSearch.value = data.total || 0
  } catch (e) {
    console.error('加载知识库失败：', e)
    ElMessage.error('加载知识库失败')
  }
}

// 加载我的知识库 Tab 数据（后端过滤 createdBy）
async function loadMyKnowledgeBases() {
  try {
    const userId = auth.userInfo?.id || auth.userInfo?.loginId
    const params: any = { current: myPage.value, pageSize: 20 }
    if (userId != null) {
      params.createdBy = String(userId)
    }
    if (mySearchQuery.value) {
      params.name = mySearchQuery.value
    }
    const res = await request.get('/knowledge-base/page', { params }) as any
    const data = res.data || res
    myKnowledgeBases.value = data.records || []
    totalMy.value = data.total || 0
  } catch (e) {
    console.error('加载我的知识库失败：', e)
    ElMessage.error('加载我的知识库失败')
  }
}

// 加载知识库数据（根据当前 Tab）
onMounted(async () => {
  await loadSearchResults()
})

// 路由变化时刷新（从详情页返回等场景）
watch(route, async () => {
  if (route.path === '/chat/knowledge') {
    if (activeTab.value === 'search') {
      await loadSearchResults()
    } else if (activeTab.value === 'mine') {
      await loadMyKnowledgeBases()
    }
  }
})

onUnmounted(() => {
  if (searchTimer) clearTimeout(searchTimer)
  if (mySearchTimer) clearTimeout(mySearchTimer)
})

// Tab 切换时加载对应数据
watch(activeTab, (newTab) => {
  if (newTab === 'search') {
    loadSearchResults()
  } else if (newTab === 'mine') {
    loadMyKnowledgeBases()
  }
})

// 新建/编辑知识库对话框打开时加载意图节点
watch(showCreateDialog, async (visible) => {
  if (visible) {
    await loadIntentNodes()
  }
})

// 搜索 debounce（300ms）
const handleSearch = () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(async () => {
    searchPage.value = 1
    await loadSearchResults()
  }, 300)
}

const handleFilter = () => {
  handleSearch()
}

const handleMySearch = () => {
  if (mySearchTimer) clearTimeout(mySearchTimer)
  mySearchTimer = setTimeout(async () => {
    myPage.value = 1
    await loadMyKnowledgeBases()
  }, 300)
}

// 分页切换
watch(searchPage, () => loadSearchResults())
watch(myPage, () => loadMyKnowledgeBases())

// 方法
const formatDate = (date: any): string => {
  if (!date) return ''
  return new Date(date).toLocaleDateString('zh-CN')
}

const getReadabilityType = (readability: string): string => {
  const typeMap: Record<string, string> = {
    public: 'success',
    team: 'warning',
    private: 'info'
  }
  return typeMap[readability] || 'info'
}

const getReadabilityLabel = (readability: string): string => {
  const labelMap: Record<string, string> = {
    public: '公开',
    team: '团队',
    private: '私密'
  }
  return labelMap[readability] || readability || '私密'
}

const navigateToDetail = (id: string) => {
  router.push(`/chat/knowledge/${id}`)
}

const editKB = async (kb: any) => {
  editingKBId.value = kb.id

  // 加载意图节点列表
  await loadIntentNodes()

  // 加载当前知识库已绑定的节点
  let boundNodeIds: string[] = []
  try {
    const allNodes = intentNodeList.value
    boundNodeIds = allNodes
      .filter((n: any) => n.kbId === kb.id)
      .map((n: any) => n.id)
  } catch {}

  createForm.value = {
    name: kb.name,
    description: kb.description || '',
    readability: kb.readability || 'private',
    intentNodeIds: boundNodeIds
  }
  showCreateDialog.value = true
}

const deleteKB = async (id: string) => {
  try {
    await ElMessageBox.confirm('确定要删除此知识库吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await request.delete(`/knowledge-base/${id}`)
    ElMessage.success('删除成功')
    // 删除后重新加载当前 Tab 的数据
    if (activeTab.value === 'mine') {
      await loadMyKnowledgeBases()
    } else {
      await loadSearchResults()
    }
  } catch (e: any) {
    if (e?.toString()?.includes('cancel')) return
    console.error('删除失败：', e)
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

const handleCreate = async () => {
  if (!createForm.value.name) {
    ElMessage.warning('请输入知识库名称')
    return
  }

  try {
    if (editingKBId.value) {
      // 编辑模式
      await request.put('/knowledge-base', {
        id: editingKBId.value,
        name: createForm.value.name,
        description: createForm.value.description,
        intentNodeIds: createForm.value.intentNodeIds
      })
      ElMessage.success('知识库更新成功')
    } else {
      // 创建模式
      await request.post('/knowledge-base', {
        name: createForm.value.name,
        description: createForm.value.description,
        readability: createForm.value.readability,
        intentNodeIds: createForm.value.intentNodeIds
      })
      ElMessage.success('知识库创建成功')
    }

    showCreateDialog.value = false
    editingKBId.value = null
    createForm.value = { name: '', description: '', readability: 'private', intentNodeIds: [] }
    // 创建/编辑后重新加载当前 Tab 的数据
    if (activeTab.value === 'mine') {
      await loadMyKnowledgeBases()
    } else {
      await loadSearchResults()
    }
  } catch (e: any) {
    console.error('操作失败：', e)
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}
</script>

<style scoped>
.knowledge-list-page {
  padding: 0;
}

.tabs {
  margin-bottom: 0;
}

.search-section,
.my-kb-section {
  padding: 20px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
  margin-bottom: 20px;
}

.kb-card {
  cursor: pointer;
  transition: all 0.3s;
}

.kb-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transform: translateY(-2px);
}

:deep(.el-card__body) {
  padding: 16px;
}

.kb-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.kb-name {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.kb-description {
  margin: 12px 0;
  color: #666;
  font-size: 14px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.kb-stats {
  display: flex;
  gap: 16px;
  margin: 12px 0;
  padding: 12px 0;
  border-top: 1px solid #f0f0f0;
  border-bottom: 1px solid #f0f0f0;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #999;
}

.kb-footer {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #999;
}

.creator {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.time {
  white-space: nowrap;
}

.empty-state {
  padding: 40px 0;
  text-align: center;
}

.pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}

:deep(.el-table) {
  margin-top: 0;
}

.mt {
  margin-top: 20px;
}

:deep(.el-icon) {
  vertical-align: -3px;
  margin-right: 4px;
}

.action-btn-wrapper {
  display: inline-flex;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
  margin-right: 4px;
}
</style>