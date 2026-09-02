<template>
  <div>
    <div class="header"><h3>意图路由</h3><el-button type="primary" @click="openCreateTop">新建顶级节点</el-button></div>
    <el-tree
      :data="treeData"
      :props="{ label: 'label', children: 'children' }"
      node-key="id"
      default-expand-all
      :expand-on-click-node="false"
    >
      <template #default="{ data }">
        <span class="tree-node" :class="{ disabled: data.enabled === 0 }">
          <span class="node-label">{{ data.label }}</span>
          <el-tag size="small" :type="data.isSystem === 1 ? 'warning' : 'info'" class="ml">
            {{ data.isSystem === 1 ? '系统' : '业务' }}
          </el-tag>
          <el-tag v-if="data.enabled === 0" size="small" type="danger" class="ml">已禁用</el-tag>
          <span v-if="data.parentLabel" class="parent-path">上级: {{ data.parentLabel }}</span>
          <span class="level-tag" v-if="data.level">L{{ data.level }}</span>
          <span class="actions">
            <span class="action-btn-wrapper">
              <el-button
                v-if="data.level < 3"
                text size="small" type="primary" @click.stop="openCreateChild(data)"
              >
                + 子节点
              </el-button>
            </span>
            <span class="action-btn-wrapper">
              <el-button v-if="data.enabled !== 0" text size="small" type="warning" @click.stop="disableNode(data.id)">禁用</el-button>
              <el-button v-else text size="small" type="success" @click.stop="enableNode(data.id)">启用</el-button>
            </span>
            <span class="action-btn-wrapper">
              <el-button text size="small" type="primary" @click.stop="editNode(data)">编辑</el-button>
            </span>
            <span class="action-btn-wrapper">
              <el-button text size="small" type="danger" @click.stop="deleteNode(data.id)">删除</el-button>
            </span>
          </span>
        </span>
      </template>
    </el-tree>

    <el-dialog v-model="showCreate" :title="dialogTitle" width="480px">
      <el-input v-model="form.label" placeholder="节点名称" class="mb" />
      <el-input v-model="form.description" type="textarea" placeholder="描述" class="mb" :rows="3" />
      <template #footer>
        <el-button @click="cancelDialog">取消</el-button>
        <el-button type="primary" @click="handleSave">{{ editingId ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import request from '../../api/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const treeData = ref<any[]>([])
const allNodes = ref<any[]>([])
const showCreate = ref(false)
const editingId = ref<string | null>(null)
const form = reactive({ label: '', description: '', parentId: null as string | null })
const createMode = ref<'top' | 'child'>('top')

onMounted(async () => { await loadTree() })

async function loadTree() {
  try {
    const res = await request.get('/intent/nodes') as any
    const nodes = res?.data || res || []
    allNodes.value = nodes
    treeData.value = buildTree(nodes, null)
  } catch {}
}

function buildTree(nodes: any[], parentId: string | null): any[] {
  return nodes.filter((n: any) => n.parentId === parentId).map((n: any) => {
    const parentNode = nodes.find(p => p.id === parentId)
    return {
      ...n,
      parentLabel: parentNode ? parentNode.label : null,
      children: buildTree(nodes, n.id)
    }
  })
}

const parentCandidates = computed(() => {
  if (!editingId.value) return allNodes.value
  const excludeIds = collectDescendantIds(editingId.value)
  excludeIds.add(editingId.value)
  return allNodes.value.filter(n => !excludeIds.has(n.id))
})

function collectDescendantIds(id: string): Set<string> {
  const result = new Set<string>()
  const children = allNodes.value.filter(n => n.parentId === id)
  for (const child of children) {
    result.add(child.id)
    const descendants = collectDescendantIds(child.id)
    descendants.forEach(d => result.add(d))
  }
  return result
}

const dialogTitle = computed(() => {
  if (editingId.value) return '编辑节点'
  return createMode.value === 'top' ? '新建顶级节点' : '新建子节点'
})

function openCreateTop() {
  editingId.value = null
  createMode.value = 'top'
  form.label = ''
  form.description = ''
  form.parentId = null
  showCreate.value = true
}

function openCreateChild(parent: any) {
  editingId.value = null
  createMode.value = 'child'
  form.label = ''
  form.description = ''
  form.parentId = parent.id
  showCreate.value = true
}

function cancelDialog() {
  showCreate.value = false
  editingId.value = null
  form.label = ''
  form.description = ''
  form.parentId = null
}

async function handleSave() {
  try {
    if (editingId.value) {
      const existingNode = allNodes.value.find(n => n.id === editingId.value)
      await request.put(`/intent/node/${editingId.value}`, {
        id: editingId.value,
        label: form.label,
        description: form.description,
        parentId: form.parentId,
        level: existingNode?.level,
        isSystem: existingNode?.isSystem,
        kbId: existingNode?.kbId,
        sortOrder: existingNode?.sortOrder,
        enabled: existingNode?.enabled
      })
    } else {
      await request.post('/intent/node', { label: form.label, description: form.description, parentId: form.parentId })
    }
    ElMessage.success(editingId.value ? '修改成功' : '创建成功')
    cancelDialog()
    loadTree()
  } catch (err: any) {
    if (err?.toString()?.includes('cancel')) return
    ElMessage.error(err?.response?.data?.message || '操作失败')
  }
}

function editNode(node: any) {
  editingId.value = node.id
  createMode.value = 'top'
  form.label = node.label
  form.description = node.description
  form.parentId = node.parentId ?? null
  showCreate.value = true
}

async function deleteNode(id: string) {
  try {
    await ElMessageBox.confirm('确定删除此节点？')
    await request.delete(`/intent/node/${id}`)
    ElMessage.success('删除成功')
    loadTree()
  } catch (err: any) {
    if (err?.toString()?.includes('cancel')) return
    const msg = err?.response?.data?.message || err?.message || '删除失败'
    ElMessage.error(msg)
  }
}

async function disableNode(id: string) {
  try {
    await ElMessageBox.confirm('禁用后，关联的知识库将自动路由到默认节点，确认禁用？')
    await request.put(`/intent/node/${id}/disable`)
    ElMessage.success('已禁用')
    loadTree()
  } catch {}
}

async function enableNode(id: string) {
  try { await request.put(`/intent/node/${id}/enable`); ElMessage.success('已启用'); loadTree() } catch {}
}
</script>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.tree-node { display: flex; align-items: center; flex: 1; gap: 6px; font-size: 15px; }
.tree-node.disabled { opacity: 0.5; }
.node-label { font-weight: 500; font-size: 15px; }
.ml { margin-left: 8px; }
.parent-path { font-size: 13px; color: #909399; margin-left: 8px; }
.level-tag { font-size: 13px; color: #409eff; margin-left: 8px; font-weight: 600; }
.actions { margin-left: auto; display: flex; gap: 6px; align-items: center; }
.action-btn-wrapper {
  display: inline-flex;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  overflow: hidden;
}
.mb { margin-bottom: 12px; width: 100%; }
</style>
