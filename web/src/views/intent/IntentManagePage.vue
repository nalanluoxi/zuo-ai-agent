<template>
  <div>
    <div class="header"><h3>意图路由</h3><el-button type="primary" @click="openCreate">新建节点</el-button></div>
    <el-tree :data="treeData" :props="{ label: 'label', children: 'children' }" node-key="id" default-expand-all :expand-on-click-node="false">
      <template #default="{ data }">
        <span class="tree-node" :class="{ disabled: data.enabled === 0 }">
          <span class="node-label">{{ data.label }}</span>
          <el-tag size="small" :type="data.isSystem === 1 ? 'warning' : 'info'" class="ml">{{ data.isSystem === 1 ? '系统' : '业务' }}</el-tag>
          <el-tag v-if="data.enabled === 0" size="small" type="danger" class="ml">已禁用</el-tag>
          <span v-if="data.parentLabel" class="parent-path">上级: {{ data.parentLabel }}</span>
          <span class="actions">
            <el-button v-if="data.enabled !== 0" text size="small" type="warning" @click.stop="disableNode(data.id)">禁用</el-button>
            <el-button v-else text size="small" type="success" @click.stop="enableNode(data.id)">启用</el-button>
            <el-button text size="small" type="primary" @click.stop="editNode(data)">编辑</el-button>
            <el-button text size="small" type="danger" @click.stop="deleteNode(data.id)">删除</el-button>
          </span>
        </span>
      </template>
    </el-tree>
    <el-dialog v-model="showCreate" :title="editingId ? '编辑节点' : '新建节点'" width="480px">
      <el-input v-model="form.label" placeholder="节点名称" class="mb" />
      <el-input v-model="form.description" type="textarea" placeholder="描述" class="mb" :rows="3" />
      <el-select v-model="form.parentId" placeholder="父节点（留空=根节点）" clearable filterable class="mb" style="width:100%">
        <el-option v-for="n in parentCandidates" :key="n.id" :label="n.label" :value="n.id" />
      </el-select>
      <template #footer><el-button @click="cancelDialog">取消</el-button><el-button type="primary" @click="handleSave">{{ editingId ? '保存' : '创建' }}</el-button></template>
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
const editingId = ref<number | null>(null)
const form = reactive({ label: '', description: '', parentId: null as number | null })

onMounted(async () => { await loadTree() })

async function loadTree() {
  try {
    const res = await request.get('/intent/nodes') as any
    const nodes = res?.data || res || []
    allNodes.value = nodes
    treeData.value = buildTree(nodes, null)
  } catch {}
}

function buildTree(nodes: any[], parentId: number | null): any[] {
  return nodes.filter((n: any) => n.parentId === parentId).map((n: any) => {
    const parentNode = nodes.find(p => p.id === parentId)
    return {
      ...n,
      parentLabel: parentNode ? parentNode.label : null,
      children: buildTree(nodes, n.id)
    }
  })
}

// 编辑时排除当前节点及其所有子孙节点（避免循环引用）
const parentCandidates = computed(() => {
  if (!editingId.value) return allNodes.value
  const excludeIds = collectDescendantIds(editingId.value)
  excludeIds.add(editingId.value)
  return allNodes.value.filter(n => !excludeIds.has(n.id))
})

function collectDescendantIds(id: number): Set<number> {
  const result = new Set<number>()
  const children = allNodes.value.filter(n => n.parentId === id)
  for (const child of children) {
    result.add(child.id)
    const descendants = collectDescendantIds(child.id)
    descendants.forEach(d => result.add(d))
  }
  return result
}

function openCreate() {
  editingId.value = null
  form.label = ''; form.description = ''; form.parentId = null
  showCreate.value = true
}

function cancelDialog() {
  showCreate.value = false
  editingId.value = null
  form.label = ''; form.description = ''; form.parentId = null
}

async function handleSave() {
  try {
    if (editingId.value) {
      await request.put(`/intent/node/${editingId.value}`, { label: form.label, description: form.description, parentId: form.parentId })
    } else {
      await request.post('/intent/node', { label: form.label, description: form.description, parentId: form.parentId })
    }
    ElMessage.success(editingId.value ? '修改成功' : '创建成功')
    cancelDialog()
    loadTree()
  } catch { ElMessage.error('操作失败') }
}

function editNode(node: any) {
  editingId.value = node.id
  form.label = node.label
  form.description = node.description
  form.parentId = node.parentId
  showCreate.value = true
}

async function deleteNode(id: number) {
  try {
    await ElMessageBox.confirm('确定删除此节点？')
    await request.delete(`/intent/node/${id}`)
    ElMessage.success('删除成功')
    loadTree()
  } catch {}
}

async function disableNode(id: number) {
  try {
    await ElMessageBox.confirm('禁用后，关联的知识库将自动路由到默认节点，确认禁用？')
    await request.put(`/intent/node/${id}/disable`)
    ElMessage.success('已禁用')
    loadTree()
  } catch {}
}

async function enableNode(id: number) {
  try { await request.put(`/intent/node/${id}/enable`); ElMessage.success('已启用'); loadTree() } catch {}
}
</script>

<style scoped>
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.tree-node { display: flex; align-items: center; flex: 1; gap: 4px; }
.tree-node.disabled { opacity: 0.5; }
.node-label { font-weight: 500; }
.ml { margin-left: 8px; }
.parent-path { font-size: 12px; color: #909399; margin-left: 8px; }
.actions { margin-left: auto; }
.mb { margin-bottom: 12px; width: 100%; }
</style>