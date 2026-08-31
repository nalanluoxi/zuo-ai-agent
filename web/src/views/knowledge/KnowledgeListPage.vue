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
                  {{ kb.fileCount }} 个文件
                </span>
                <span class="stat-item">
                  <el-icon><User /></el-icon>
                  {{ kb.owners.length }} 个 Owner
                </span>
              </div>

              <div class="kb-footer">
                <span class="creator">创建者: {{ kb.creator }}</span>
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

            <el-button type="primary" @click="showCreateDialog = true; editingKBId = null; createForm = { name: '', description: '', readability: 'private' }" style="margin-left: 10px">
              <el-icon><Plus /></el-icon>
              新建知识库
            </el-button>
          </div>

          <!-- 知识库表格 -->
          <el-table :data="myKnowledgeBases" style="width: 100%">
            <el-table-column prop="name" label="知识库名称" width="200" />
            <el-table-column prop="description" label="描述" show-overflow-tooltip />
            <el-table-column prop="fileCount" label="文件数" width="80" />
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
                <el-button text type="primary" @click="navigateToDetail(row.id)">
                  详情
                </el-button>
                <el-button text type="primary" @click="editKB(row)">
                  编辑
                </el-button>
                <el-button text type="danger" @click="deleteKB(row.id)">
                  删除
                </el-button>
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
    <el-dialog v-model="showCreateDialog" :title="editingKBId ? '编辑知识库' : '新建知识库'" width="500px">
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
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'
import { useAuthStore } from '../../stores/auth'

const router = useRouter()
const auth = useAuthStore()

// 真实数据
const allKnowledgeBases = ref<any[]>([])

// Tab 相关
const activeTab = ref('search')

// 搜索 Tab 数据
const searchQuery = ref('')
const filterReadability = ref('')
const searchPage = ref(1)
const totalSearch = ref(0)

// 我的知识库 Tab 数据
const mySearchQuery = ref('')
const myPage = ref(1)
const totalMy = ref(0)

// 创建/编辑知识库表单
const showCreateDialog = ref(false)
const editingKBId = ref<string | null>(null)
const createForm = ref({
  name: '',
  description: '',
  readability: 'private'
})

// 加载知识库数据
onMounted(async () => {
  await loadAllKnowledgeBases()
})

async function loadAllKnowledgeBases() {
  try {
    const res = await request.get('/knowledge-base/page', {
      params: { current: searchPage.value, pageSize: 20 }
    }) as any
    const data = res.data || res
    allKnowledgeBases.value = data.records || []
    totalSearch.value = data.total || 0
  } catch (e) {
    console.error('加载知识库失败：', e)
    ElMessage.error('加载知识库失败')
  }
}

// 计算属性
const searchResults = computed(() => {
  let results = allKnowledgeBases.value
  
  // 过滤可读性
  if (filterReadability.value) {
    results = results.filter(kb => kb.readability === filterReadability.value)
  }
  
  // 搜索
  if (searchQuery.value) {
    results = results.filter(kb => 
      kb.name.includes(searchQuery.value) || 
      kb.description?.includes(searchQuery.value)
    )
  }
  
  return results
})

const myKnowledgeBases = computed(() => {
  const currentUsername = auth.userInfo?.username || ''
  let results = allKnowledgeBases.value.filter(kb => kb.createdBy === currentUsername)
  
  if (mySearchQuery.value) {
    results = results.filter(kb => 
      kb.name.includes(mySearchQuery.value)
    )
  }
  
  return results
})

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

const handleSearch = async () => {
  await loadAllKnowledgeBases()
}

const handleFilter = () => {
  handleSearch()
}

const handleMySearch = async () => {
  await loadAllKnowledgeBases()
}

const navigateToDetail = (id: string) => {
  router.push(`/chat/knowledge/${id}`)
}

const editKB = (kb: any) => {
  editingKBId.value = kb.id
  createForm.value = {
    name: kb.name,
    description: kb.description || '',
    readability: kb.readability || 'private'
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
    await loadAllKnowledgeBases()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('删除失败：', e)
      ElMessage.error('删除失败')
    }
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
        description: createForm.value.description
      })
      ElMessage.success('知识库更新成功')
    } else {
      // 创建模式
      await request.post('/knowledge-base', {
        name: createForm.value.name,
        description: createForm.value.description,
        readability: createForm.value.readability
      })
      ElMessage.success('知识库创建成功')
    }
    
    showCreateDialog.value = false
    editingKBId.value = null
    createForm.value = { name: '', description: '', readability: 'private' }
    await loadAllKnowledgeBases()
  } catch (e) {
    console.error('操作失败：', e)
    ElMessage.error('操作失败')
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
</style>