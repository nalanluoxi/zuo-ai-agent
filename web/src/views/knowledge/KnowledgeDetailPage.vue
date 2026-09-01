<template>
  <div class="knowledge-detail-page">
    <!-- 返回按钮和标题 -->
    <div class="detail-header">
      <el-button text @click="$router.back()">
        <el-icon><ArrowLeft /></el-icon>
        返回
      </el-button>
      <h1>{{ knowledgeBase.name }}</h1>
      <div class="header-actions">
        <el-button text @click="openEditDialog">编辑</el-button>
        <el-dropdown>
          <el-button text>
            更多
            <el-icon class="el-icon--right"><arrow-down /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="deleteKB">删除</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 知识库信息卡 -->
    <el-card class="info-card" v-loading="loading">
      <div class="info-row">
        <span class="label">描述：</span>
        <span class="value">{{ knowledgeBase.description }}</span>
      </div>

      <div class="info-grid">
        <div class="info-item">
          <span class="label">创建者：</span>
          <span class="value">{{ knowledgeBase.createdByUsername || knowledgeBase.createdBy || '-' }}</span>
        </div>
        <div class="info-item">
          <span class="label">创建时间：</span>
          <span class="value">{{ formatDate(knowledgeBase.createTime) }}</span>
        </div>
        <div class="info-item">
          <span class="label">文件数：</span>
          <span class="value">{{ files.length }}</span>
        </div>
        <div class="info-item">
          <span class="label">可读性：</span>
          <el-select v-model="knowledgeBase.readability" size="small" @change="updateReadability">
            <el-option label="私密" value="private" />
            <el-option label="团队" value="team" />
            <el-option label="公开" value="public" />
          </el-select>
        </div>
        <div class="info-item full-width" v-if="boundIntentNodeNames.length > 0">
          <span class="label">绑定意图节点：</span>
          <div class="intent-tags">
            <el-tag v-for="nodeName in boundIntentNodeNames" :key="nodeName" size="small" type="success" class="intent-tag">
              {{ nodeName }}
            </el-tag>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 选项卡 -->
    <el-tabs>
      <!-- Tab 1: Owner 管理 -->
      <el-tab-pane label="Owner 管理">
        <div class="owner-section">
          <div class="section-header">
            <h3>Owner 列表</h3>
            <el-button type="primary" size="small" @click="showAddOwnerDialog = true">
              <el-icon><Plus /></el-icon>
              添加 Owner
            </el-button>
          </div>

          <div class="owner-cards" v-loading="ownersLoading">
            <template v-if="owners.length > 0">
              <!-- 显示前2个卡片 -->
              <div
                v-for="owner in displayedOwners"
                :key="owner.ownerId"
                class="owner-card"
              >
                <div class="owner-card-content">
                  <div class="owner-info">
                    <div class="owner-name">{{ owner.username || '未知用户' }}</div>
                    <div class="owner-nickname" v-if="owner.nickname">{{ owner.nickname }}</div>
                  </div>
                  <el-button
                    type="danger"
                    size="small"
                    text
                    @click="removeOwner(owner.ownerId)"
                  >
                    删除
                  </el-button>
                </div>
              </div>

              <!-- 第3个及以后显示"+N 更多" -->
              <div
                v-if="remainingOwnersCount > 0 && !showAllOwners"
                class="owner-more"
                @click="showAllOwners = true"
              >
                +{{ remainingOwnersCount }} 更多
              </div>

              <!-- 展开后显示所有剩余卡片 -->
              <template v-if="showAllOwners">
                <div
                  v-for="owner in remainingOwners"
                  :key="owner.ownerId"
                  class="owner-card"
                >
                  <div class="owner-card-content">
                    <div class="owner-info">
                      <div class="owner-name">{{ owner.username || '未知用户' }}</div>
                      <div class="owner-nickname" v-if="owner.nickname">{{ owner.nickname }}</div>
                    </div>
                    <el-button
                      type="danger"
                      size="small"
                      text
                      @click="removeOwner(owner.ownerId)"
                    >
                      删除
                    </el-button>
                  </div>
                </div>

                <!-- 收起按钮 -->
                <div class="owner-more" @click="showAllOwners = false">
                  收起
                </div>
              </template>
            </template>

            <el-empty v-else description="暂无 Owner" />
          </div>
        </div>
      </el-tab-pane>

      <!-- Tab 2: 文件列表 -->
      <el-tab-pane label="文件列表">
        <div class="files-section">
          <div class="section-header">
            <h3>知识库文件</h3>
            <el-button type="primary" size="small" @click="showUploadDialog = true">
              <el-icon><Upload /></el-icon>
              上传文件
            </el-button>
          </div>

          <el-table :data="files" style="width: 100%" v-loading="filesLoading">
            <el-table-column prop="originalName" label="文件名" width="200" show-overflow-tooltip>
              <template #default="{ row }">{{ row.originalName || row.original_name || row.filename }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="150">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">
                  {{ getStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="chunks" label="块数" width="100" />
            <el-table-column label="上传时间" width="180">
              <template #default="{ row }">
                {{ formatDate(row.createdAt || row.created_at) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button text type="danger" size="small" @click="deleteFile(row.id)">
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="files.length === 0 && !filesLoading" description="暂无文件" />
        </div>
      </el-tab-pane>

      <!-- Tab 3: 统计信息 -->
      <el-tab-pane label="统计信息">
        <div class="stats-section">
          <el-row :gutter="20">
            <el-col :xs="24" :sm="12" :md="6">
              <el-statistic title="总文件数" :value="files.length" />
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-statistic title="总块数" :value="totalChunks" />
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-statistic title="总大小(MB)" :value="totalSizeMb" />
            </el-col>
            <el-col :xs="24" :sm="12" :md="6">
              <el-statistic title="Owner 数" :value="owners.length" />
            </el-col>
          </el-row>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="showEditDialog" title="编辑知识库" width="500px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="知识库名称">
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="editForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="可读性">
          <el-select v-model="editForm.readability">
            <el-option label="私密" value="private" />
            <el-option label="团队" value="team" />
            <el-option label="公开" value="public" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定意图节点">
          <el-select
            v-model="editForm.intentNodeIds"
            multiple
            filterable
            placeholder="选择要绑定的意图节点（可选）"
            style="width: 100%"
          >
            <el-option
              v-for="node in intentNodeList"
              :key="node.id"
              :label="node.label"
              :value="node.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 上传文件弹窗 -->
    <el-dialog v-model="showUploadDialog" title="上传文件" width="500px">
      <el-upload
        drag
        action="#"
        :auto-upload="false"
        :on-change="handleFileSelect"
        :file-list="uploadFileList"
        multiple
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
          拖拽文件到此或 <em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持 PDF、Word、Excel 等格式，单个文件不超过 100MB
          </div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="uploadFiles">上传</el-button>
      </template>
    </el-dialog>

    <!-- 添加 Owner 弹窗 -->
    <el-dialog v-model="showAddOwnerDialog" title="添加 Owner" width="400px">
      <el-form :model="ownerForm" label-width="80px">
        <el-form-item label="用户ID">
          <el-input v-model="ownerForm.ownerId" placeholder="请输入用户ID" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddOwnerDialog = false">取消</el-button>
        <el-button type="primary" @click="addOwner">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'

const route = useRoute()
const router = useRouter()
const kbId = route.params.id as string

const knowledgeBase = ref<any>({ name: '', description: '', readability: 'private' })
const owners = ref<any[]>([])
const files = ref<any[]>([])
const loading = ref(false)
const ownersLoading = ref(false)
const filesLoading = ref(false)

const showEditDialog = ref(false)
const showUploadDialog = ref(false)
const showAddOwnerDialog = ref(false)
const intentNodeList = ref<any[]>([])
const editForm = ref({ name: '', description: '', readability: '', intentNodeIds: [] as string[] })
const ownerForm = ref({ ownerId: '' })
const selectedFiles = ref<File[]>([])
const uploadFileList = ref<any[]>([])
const uploading = ref(false)

const totalChunks = computed(() => files.value.reduce((s, f) => s + (f.chunks || 0), 0))
const totalSizeMb = computed(() => {
  const bytes = files.value.reduce((s, f) => s + (f.fileSize || f.file_size || 0), 0)
  return Math.round((bytes / 1024 / 1024) * 100) / 100
})

// Owner 卡片展示逻辑
const showAllOwners = ref(false)
const displayedOwners = computed(() => owners.value.slice(0, 2))
const remainingOwnersCount = computed(() => Math.max(0, owners.value.length - 2))
const remainingOwners = computed(() => owners.value.slice(2))

// 绑定意图节点名称
const boundIntentNodeNames = computed(() => {
  if (!knowledgeBase.value.intentNodeLabels) return []
  return knowledgeBase.value.intentNodeLabels
})

onMounted(async () => {
  await Promise.all([loadDetail(), loadOwners(), loadFiles()])
})

async function loadDetail() {
  loading.value = true
  try {
    const res = await request.get(`/knowledge-base/${kbId}`) as any
    knowledgeBase.value = res.data || res || {}
  } catch (e) {
    console.error('加载知识库详情失败', e)
    ElMessage.error('加载知识库详情失败')
  } finally {
    loading.value = false
  }
}

async function loadOwners() {
  ownersLoading.value = true
  try {
    const res = await request.get(`/knowledge-base/${kbId}/owners`) as any
    owners.value = res.data || res || []
  } catch (e) {
    console.error('加载 Owner 列表失败', e)
  } finally {
    ownersLoading.value = false
  }
}

async function loadFiles() {
  filesLoading.value = true
  try {
    const res = await request.get(`/knowledge-base/${kbId}/files`, { params: { page: 1, size: 100 } }) as any
    const data = res.data || res
    files.value = data.list || data.records || data.content || []
  } catch (e) {
    console.error('加载文件列表失败', e)
  } finally {
    filesLoading.value = false
  }
}

const formatDate = (date: any): string => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

const getStatusType = (status: string) => {
  const typeMap: Record<string, string> = {
    QUEUED: 'info',
    JUST_UPLOADED: 'info',
    CHUNKING: 'warning',
    VECTORIZING: 'warning',
    VECTORIZE_FAILED: 'danger',
    VECTORIZED: 'success'
  }
  return typeMap[status] || 'info'
}

const getStatusLabel = (status: string) => {
  const labelMap: Record<string, string> = {
    QUEUED: '待处理',
    JUST_UPLOADED: '刚入库',
    CHUNKING: '分块中',
    VECTORIZING: '向量化中',
    VECTORIZE_FAILED: '失败',
    VECTORIZED: '已完成'
  }
  return labelMap[status] || status
}

const updateReadability = async () => {
  try {
    await request.put(`/knowledge-base/${kbId}/readability`, { readability: knowledgeBase.value.readability })
    ElMessage.success('可读性已更新')
  } catch {
    ElMessage.error('更新失败')
    await loadDetail()
  }
}

const openEditDialog = async () => {
  // 加载意图节点列表，并过滤出叶子节点
  try {
    const res = await request.get('/intent/nodes') as any
    const allNodes = res?.data || res || []

    // 找出所有父节点ID
    const parentIdSet = new Set(
      allNodes
        .map((n: any) => n.parentId)
        .filter((id: any) => id != null && id !== 0)
    )

    // 过滤出叶子节点（不在parentId集合中的节点）
    intentNodeList.value = allNodes.filter((n: any) => !parentIdSet.has(n.id))
  } catch {}

  // 加载当前知识库已绑定的节点
  // kbId 为雪花 ID 字符串，与 n.kbId 直接按字符串比较，避免 Number() 转换丢失精度
  let boundNodeIds: string[] = []
  try {
    const res = await request.get('/intent/nodes') as any
    const allNodes = res?.data || res || []
    boundNodeIds = allNodes
      .filter((n: any) => n.kbId === kbId)
      .map((n: any) => n.id)
  } catch {}

  editForm.value = {
    name: knowledgeBase.value.name,
    description: knowledgeBase.value.description,
    readability: knowledgeBase.value.readability,
    intentNodeIds: boundNodeIds
  }
  showEditDialog.value = true
}

const saveEdit = async () => {
  try {
    await request.put('/knowledge-base', {
      id: kbId,
      name: editForm.value.name,
      description: editForm.value.description,
      readability: editForm.value.readability,
      intentNodeIds: editForm.value.intentNodeIds
    })
    showEditDialog.value = false
    ElMessage.success('修改已保存')
    await loadDetail()
  } catch {
    ElMessage.error('保存失败')
  }
}

const removeOwner = async (ownerId: string) => {
  try {
    await ElMessageBox.confirm('确定移除该 Owner？')
    await request.delete(`/knowledge-base/${kbId}/owners/${ownerId}`)
    ElMessage.success('Owner 已删除')
    await loadOwners()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const addOwner = async () => {
  if (!ownerForm.value.ownerId) {
    ElMessage.warning('请输入用户ID')
    return
  }
  try {
    await request.post(`/knowledge-base/${kbId}/owners`, { ownerId: ownerForm.value.ownerId })
    ElMessage.success('Owner 已添加')
    showAddOwnerDialog.value = false
    ownerForm.value.ownerId = ''
    await loadOwners()
  } catch {
    ElMessage.error('添加失败')
  }
}

const deleteFile = async (id: string) => {
  try {
    await ElMessageBox.confirm('确定删除此文件？')
    await request.delete(`/knowledge-base/${kbId}/files/${id}`)
    ElMessage.success('文件已删除')
    await loadFiles()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const deleteKB = async () => {
  try {
    await ElMessageBox.confirm('确定删除此知识库？此操作不可恢复', '警告', { type: 'warning' })
    await request.delete(`/knowledge-base/${kbId}`)
    ElMessage.success('知识库已删除')
    router.push('/chat/knowledge')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const handleFileSelect = (_file: any, fileList: any[]) => {
  selectedFiles.value = fileList.map(f => f.raw).filter(Boolean)
  uploadFileList.value = fileList
}

const uploadFiles = async () => {
  if (selectedFiles.value.length === 0) {
    ElMessage.warning('请先选择文件')
    return
  }
  uploading.value = true
  try {
    for (const file of selectedFiles.value) {
      const fd = new FormData()
      fd.append('file', file)
      await request.post(`/knowledge-base/${kbId}/files/upload`, fd)
    }
    ElMessage.success('文件上传成功')
    showUploadDialog.value = false
    selectedFiles.value = []
    uploadFileList.value = []
    await loadFiles()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.knowledge-detail-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.detail-header h1 {
  flex: 1;
  margin: 0;
  font-size: 28px;
  color: #333;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.info-card {
  margin-bottom: 16px;
}

.info-row {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-top: 12px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.label {
  font-weight: 600;
  color: #666;
  font-size: 14px;
}

.value {
  color: #333;
  font-size: 14px;
}

.owner-section,
.files-section,
.stats-section {
  padding: 16px 0;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-header h3 {
  margin: 0;
  font-size: 16px;
  color: #333;
}

:deep(.el-tabs__content) {
  padding: 16px;
}

:deep(.el-statistic) {
  text-align: center;
}

.owner-cards {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: flex-start;
}

.owner-card {
  width: 240px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 16px;
  background: #fff;
  transition: all 0.3s;
}

.owner-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.owner-card-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.owner-info {
  flex: 1;
}

.owner-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.owner-nickname {
  font-size: 13px;
  color: #909399;
}

.owner-more {
  width: 240px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  color: #409eff;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s;
}

.owner-more:hover {
  border-color: #409eff;
  background: #ecf5ff;
}
</style>
