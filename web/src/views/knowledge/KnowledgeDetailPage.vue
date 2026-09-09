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
        <el-dropdown trigger="click">
          <el-button text>
            更多
            <el-icon class="el-icon--right"><arrow-down /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="openEditDialog">
                <el-icon><Edit /></el-icon> 编辑
              </el-dropdown-item>
              <el-dropdown-item @click="showAddOwnerDialog = true">
                <el-icon><Plus /></el-icon> 添加管理员
              </el-dropdown-item>
              <el-dropdown-item @click="deleteKB">
                <el-icon><Delete /></el-icon> 删除
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 知识库信息卡 -->
    <el-card class="info-card" v-loading="loading">
      <!-- 第一行：描述（左标签，右侧可展开内容） -->
      <div class="info-row-description">
        <span class="label">描述：</span>
        <div class="description-content">
          <span
            class="description-text"
            :class="{ 'description-ellipsis': !descExpanded }"
            @click="descExpanded = !descExpanded"
            :title="knowledgeBase.description || '-'"
          >
            {{ knowledgeBase.description || '-' }}
          </span>
          <el-button
            v-if="hasLongDescription"
            text
            size="small"
            type="primary"
            @click="descExpanded = !descExpanded"
          >
            {{ descExpanded ? '收起' : '详情' }}
          </el-button>
        </div>
      </div>

      <!-- 第二行：创建者/创建时间/Owner/可读性 -->
      <div class="info-row-compact">
        <div class="info-cell">
          <span class="label">创建者：</span>
          <span class="value">{{ createdByDisplay || '-' }}</span>
        </div>
        <div class="info-cell">
          <span class="label">创建时间：</span>
          <span class="value">{{ formatDate(knowledgeBase.createTime) }}</span>
        </div>
        <div class="info-cell owner-cell">
          <span class="label">管理员：</span>
          <span class="owner-link" @click="showOwnerDialog = true" v-if="owners.length > 0">
            {{ owners[0].nickname || owners[0].username || '未知' }}
            <el-icon style="margin-left:2px"><arrow-down /></el-icon>
          </span>
          <span class="value" v-else>-</span>
        </div>
        <div class="info-cell">
          <span class="label">可读性：</span>
          <span class="value">{{ readabilityLabel(knowledgeBase.readability) }}</span>
        </div>
        <div class="info-cell full-width" v-if="boundIntentNodeNames.length > 0">
          <span class="label">绑定意图节点：</span>
          <div class="intent-tags">
            <el-tag v-for="nodeName in boundIntentNodeNames" :key="nodeName" size="small" type="success">
              {{ nodeName }}
            </el-tag>
          </div>
        </div>
      </div>

      <!-- 第三行：统计信息 -->
      <el-row :gutter="24" class="stats-row">
        <el-col :span="6">
          <div class="stat-cell">
            <div class="stat-label">文件数</div>
            <div class="stat-value">{{ files.length }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-cell">
            <div class="stat-label">分块数</div>
            <div class="stat-value">{{ totalChunks }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-cell">
            <div class="stat-label">总大小(MB)</div>
            <div class="stat-value">{{ totalSizeMb }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 选项卡 -->
    <el-tabs v-model="activeTab">
      <el-tab-pane label="文件列表" name="files">
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
              <template #default="{ row }">{{ row.docName || row.originalName || row.original_name || row.filename }}</template>
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
                {{ formatDate(row.createTime || row.createdAt || row.created_at) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="280">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="previewFile(row.id)">查看</el-button>
                <el-button text type="primary" size="small" @click="downloadFile(row.id)">下载</el-button>
                <el-button text type="primary" size="small" @click="reIngestFile(row.id)">重新入库</el-button>
                <el-button text type="danger" size="small" @click="deleteFile(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="files.length === 0 && !filesLoading" description="暂无文件" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="showEditDialog" title="编辑知识库" width="560px">
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
          <el-cascader
            v-model="editForm.intentNodeIds"
            :options="cascaderOptions"
            :props="{ multiple: true, checkStrictly: false, emitPath: false }"
            placeholder="选择要绑定的意图节点（可选）"
            style="width: 100%"
            filterable
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 上传文件弹窗 -->
    <el-dialog v-model="showUploadDialog" title="上传文件" width="520px" @close="resetUploadState">
      <el-upload
        drag
        action="#"
        :auto-upload="false"
        :on-change="handleFileSelect"
        :on-remove="handleFileRemove"
        :file-list="uploadFileList"
        :limit="1"
        :on-exceed="handleFileExceed"
        :before-upload="beforeUpload"
        accept=".pdf,.txt,.md"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">拖拽文件到此或 <em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">支持 PDF、TXT、Markdown 格式，单个文件不超过 100MB</div>
        </template>
      </el-upload>

      <!-- 文件名编辑区域 -->
      <div v-if="uploadFileName" class="upload-filename-area">
        <div class="filename-label">文档名称：</div>
        <el-input v-model="uploadFileName" placeholder="输入文档名称" clearable />
        <div class="filename-info">
          <span>文件大小：{{ formatFileSize(uploadFileSize) }}</span>
        </div>
      </div>

      <!-- 冲突提示 -->
      <div v-if="docConflictInfo.exists" class="conflict-notice" :class="conflictType">
        <el-icon><WarningFilled /></el-icon>
        <span v-if="conflictType === 'same-content'">
          已存在同名文档且内容相同，将自动创建引用关系。
        </span>
        <span v-else>
          已存在同名文档但内容不同（{{ docConflictInfo.status === 'success' ? '已入库' : docConflictInfo.status }}），确认后将覆盖旧文档。
        </span>
      </div>

      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" :loading="uploading" :disabled="!uploadFileName" @click="uploadFiles">
          {{ uploading ? '上传中...' : '上传' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 添加 Owner 弹窗 -->
    <el-dialog v-model="showAddOwnerDialog" title="添加管理员" width="400px">
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

    <!-- Owner 列表弹窗（点击管理员名字后显示） -->
    <el-dialog v-model="showOwnerDialog" title="知识库管理者" width="400px">
      <el-table :data="owners" size="small">
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="username" label="账号" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button text type="danger" size="small" @click="removeOwner(row.ownerId)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
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
const filesLoading = ref(false)
const activeTab = ref('files')

const showEditDialog = ref(false)
const showUploadDialog = ref(false)
const showAddOwnerDialog = ref(false)
const showOwnerDialog = ref(false)
const cascaderOptions = ref<any[]>([])
const editForm = ref({ name: '', description: '', readability: '', intentNodeIds: [] as string[] })
const ownerForm = ref({ ownerId: '' })
const selectedFiles = ref<File[]>([])
const uploadFileList = ref<any[]>([])
const uploading = ref(false)
const descExpanded = ref(false)

// 上传弹窗新状态
const uploadFileName = ref('')
const uploadFileSize = ref(0)
const uploadFileRaw = ref<File | null>(null)
const docConflictInfo = ref<{ exists: boolean; docId?: number; contentMd5?: string; status?: string }>({ exists: false })
const conflictType = computed<string>(() => {
  if (!docConflictInfo.value.exists) return ''
  // 比较 MD5：前端无法计算文件 MD5，所以只要有同名就视为"不同内容"让用户确认
  return 'different-content'
})

const totalChunks = computed(() => files.value.reduce((s, f) => s + Number(f.chunks || 0), 0))
const totalSizeMb = computed(() => {
  const bytes = files.value.reduce((s, f) => s + Number(f.fileSize || f.file_size || 0), 0)
  return Math.round((bytes / 1024 / 1024) * 100) / 100
})

const hasLongDescription = computed(() => {
  const desc = knowledgeBase.value.description
  return desc && desc.length > 50
})

const createdByDisplay = computed(() => {
  return knowledgeBase.value.createdByUsername || '-'
})

const boundIntentNodeNames = computed(() => {
  if (!knowledgeBase.value.intentNodeLabels) return []
  return knowledgeBase.value.intentNodeLabels
})

const readabilityLabel = (r: string) => {
  const m: Record<string, string> = { private: '私密', team: '团队', public: '公开' }
  return m[r] || r || '私密'
}

onMounted(async () => {
  await Promise.all([loadDetail(), loadOwners(), loadFiles()])
})

async function loadDetail() {
  loading.value = true
  try {
    const res = await request.get(`/knowledge-base/${kbId}`) as any
    knowledgeBase.value = res.data || res || {}
  } catch {
    ElMessage.error('加载知识库详情失败')
  } finally {
    loading.value = false
  }
}

async function loadOwners() {
  try {
    const res = await request.get(`/knowledge-base/${kbId}/owners`) as any
    owners.value = res.data || res || []
  } catch {}
}

async function loadFiles() {
  filesLoading.value = true
  try {
    const res = await request.get(`/knowledge-base/${kbId}/docs`, { params: { current: 1, pageSize: 100 } }) as any
    const data = res.data || res
    files.value = data.list || data.records || data.content || []
  } catch {
    ElMessage.error('加载文件列表失败')
  } finally {
    filesLoading.value = false
  }
}

async function loadIntentNodesForCascader() {
  try {
    const res = await request.get('/intent/nodes') as any
    const allNodes = res?.data || res || []
    cascaderOptions.value = buildCascaderTree(allNodes, null)
  } catch {}
}

function buildCascaderTree(nodes: any[], parentId: string | null): any[] {
  return nodes
    .filter((n: any) => String(n.parentId) === String(parentId))
    .map((n: any) => {
      const children = buildCascaderTree(nodes, n.id)
      return { value: n.id, label: n.label, children: children.length > 0 ? children : undefined }
    })
}

const formatDate = (date: any): string => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

const getStatusType = (status: string) => {
  const typeMap: Record<string, string> = {
    pending: 'info', success: 'success', failed: 'danger',
    QUEUED: 'info', JUST_UPLOADED: 'info', CHUNKING: 'warning',
    VECTORIZING: 'warning', VECTORIZE_FAILED: 'danger', VECTORIZED: 'success'
  }
  return typeMap[status] || 'info'
}

const getStatusLabel = (status: string) => {
  const labelMap: Record<string, string> = {
    pending: '待处理', success: '已完成', failed: '失败',
    QUEUED: '待处理', JUST_UPLOADED: '刚入库', CHUNKING: '分块中',
    VECTORIZING: '向量化中', VECTORIZE_FAILED: '失败', VECTORIZED: '已完成'
  }
  return labelMap[status] || status
}

const openEditDialog = async () => {
  await loadIntentNodesForCascader()
  let boundNodeIds: string[] = []
  try {
    const res = await request.get('/intent/nodes') as any
    const allNodes = res?.data || res || []
    boundNodeIds = allNodes
      .filter((n: any) => String(n.kbId) === String(kbId))
      .map((n: any) => String(n.id))
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
    await ElMessageBox.confirm('确定移除该管理员？')
    await request.delete(`/knowledge-base/${kbId}/owners/${ownerId}`)
    ElMessage.success('已移除')
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
    ElMessage.success('管理员已添加')
    showAddOwnerDialog.value = false
    ownerForm.value.ownerId = ''
    await loadOwners()
  } catch {
    ElMessage.error('添加失败')
  }
}

const deleteFile = async (id: string) => {
  try {
    await ElMessageBox.confirm('确定删除此文件？删除后将同时清理向量库数据，不可恢复。', '警告', { type: 'warning' })
    await request.delete(`/knowledge-base/docs/${id}`)
    ElMessage.success('文件已删除，向量库数据已清理')
    await loadFiles()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const reIngestFile = async (id: string) => {
  try {
    await ElMessageBox.confirm('确定重新入库此文件？将清理旧向量数据并重新执行 ETL 流程。', '提示', { type: 'info' })
    await request.post(`/knowledge-base/docs/${id}/re-ingest`)
    ElMessage.success('已触发重新入库，请稍后刷新查看状态')
    await loadFiles()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('重新入库失败')
  }
}

const previewFile = (id: string) => {
  // 在新窗口打开预览/下载；浏览器原生请求不带 header，token 走 query 参数
  const token = localStorage.getItem('satoken') || ''
  const url = `${request.defaults.baseURL}/knowledge-base/docs/${id}/download?satoken=${encodeURIComponent(token)}`
  window.open(url, '_blank')
}

const downloadFile = (id: string) => {
  // 触发浏览器下载；浏览器原生请求不带 header，token 走 query 参数
  const token = localStorage.getItem('satoken') || ''
  const url = `${request.defaults.baseURL}/knowledge-base/docs/${id}/download?satoken=${encodeURIComponent(token)}`
  const link = document.createElement('a')
  link.href = url
  link.download = ''
  link.target = '_blank'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
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

const handleFileSelect = async (file: any) => {
  const rawFile = file.raw || file
  // 校验文件格式
  const name = (rawFile.name || '').toLowerCase()
  const ext = name.substring(name.lastIndexOf('.'))
  if (!ALLOWED_EXTENSIONS.includes(ext)) {
    ElMessage.error('不支持的文件格式，仅支持 PDF、TXT、Markdown')
    return
  }
  uploadFileRaw.value = rawFile
  uploadFileSize.value = rawFile.size || 0

  // 自动识别文件名（去掉扩展名作为默认文档名）
  const originalName = rawFile.name || ''
  const dotIndex = originalName.lastIndexOf('.')
  uploadFileName.value = dotIndex > 0 ? originalName.substring(0, dotIndex) : originalName

  uploadFileList.value = [file]

  // 调用 check-name 检查冲突
  await checkDocNameConflict()
}

const handleFileRemove = () => {
  uploadFileRaw.value = null
  uploadFileName.value = ''
  uploadFileSize.value = 0
  uploadFileList.value = []
  docConflictInfo.value = { exists: false }
}

const handleFileExceed = () => {
  ElMessage.warning('一次只能上传一个文件，请先移除已选文件')
}

const ALLOWED_EXTENSIONS = ['.pdf', '.txt', '.md', '.markdown']
const beforeUpload = (file: any) => {
  const rawFile = file.raw || file
  const name = (rawFile.name || '').toLowerCase()
  const ext = name.substring(name.lastIndexOf('.'))
  if (!ALLOWED_EXTENSIONS.includes(ext)) {
    ElMessage.error('不支持的文件格式，仅支持 PDF、TXT、Markdown')
    return false
  }
  return true
}

const checkDocNameConflict = async () => {
  if (!uploadFileName.value.trim()) return
  try {
    const res = await request.get(`/knowledge-base/${kbId}/docs/check-name`, {
      params: { docName: uploadFileName.value.trim() }
    }) as any
    const data = res.data || res
    docConflictInfo.value = data
  } catch {
    docConflictInfo.value = { exists: false }
  }
}

const resetUploadState = () => {
  uploadFileRaw.value = null
  uploadFileName.value = ''
  uploadFileSize.value = 0
  uploadFileList.value = []
  selectedFiles.value = []
  docConflictInfo.value = { exists: false }
  uploading.value = false
}

const formatFileSize = (bytes: number): string => {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

const uploadFiles = async () => {
  if (!uploadFileRaw.value || !uploadFileName.value.trim()) {
    ElMessage.warning('请先选择文件并填写文档名称')
    return
  }

  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', uploadFileRaw.value)
    fd.append('docName', uploadFileName.value.trim())

    // 根据冲突状态决定模式
    if (docConflictInfo.value.exists && conflictType.value === 'different-content') {
      // 覆盖模式
      const confirmMsg = `已存在同名文档"${uploadFileName.value.trim()}"，是否覆盖旧文档？覆盖后将清理旧向量数据并重新入库。`
      await ElMessageBox.confirm(confirmMsg, '确认覆盖', { type: 'warning', confirmButtonText: '覆盖', cancelButtonText: '取消' })
      fd.append('mode', 'overwrite')
    } else {
      fd.append('mode', 'new')
    }

    await request.post(`/knowledge-base/${kbId}/docs/upload`, fd)
    ElMessage.success('文件上传成功，正在处理中...')
    showUploadDialog.value = false
    resetUploadState()
    await loadFiles()
  } catch (e: any) {
    if (e !== 'cancel' && e?.toString?.() !== 'cancel') {
      ElMessage.error(e?.response?.data?.message || '上传失败')
    }
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
  min-height: 100%;
  height: calc(100vh - 120px);
}

/* 让 Tabs 区域填充剩余空间 */
.knowledge-detail-page :deep(.el-tabs) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.knowledge-detail-page :deep(.el-tabs__content) {
  flex: 1;
  min-height: 0;
  overflow: auto;
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

.info-row-description {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 12px;
}

.info-row-description .label {
  font-weight: 600;
  color: #666;
  font-size: 14px;
  white-space: nowrap;
  flex-shrink: 0;
}

.description-content {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.description-text {
  color: #333;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.description-text.description-ellipsis {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

.description-text:not(.description-ellipsis) {
  cursor: pointer;
}

.description-text:hover {
  color: #409eff;
}

.info-row-compact {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  align-items: flex-end;
  margin-bottom: 16px;
}

.info-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 140px;
}

.info-cell.full-width {
  flex-basis: 100%;
}

.owner-cell .owner-link {
  color: #409eff;
  cursor: pointer;
  font-size: 14px;
  display: inline-flex;
  align-items: center;
}

.owner-cell .owner-link:hover {
  text-decoration: underline;
}

.stats-row {
  padding: 12px 0;
  border-top: 1px solid #f0f0f0;
  border-bottom: 1px solid #f0f0f0;
}

.stat-cell {
  text-align: center;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
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

.files-section {
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

.intent-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.upload-filename-area {
  margin-top: 16px;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 6px;
}

.filename-label {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
  font-weight: 500;
}

.filename-info {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

.conflict-notice {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 13px;
}

.conflict-notice.same-content {
  background: #f0f9eb;
  color: #67c23a;
  border: 1px solid #e1f3d8;
}

.conflict-notice.different-content {
  background: #fdf6ec;
  color: #e6a23c;
  border: 1px solid #faecd8;
}
</style>
