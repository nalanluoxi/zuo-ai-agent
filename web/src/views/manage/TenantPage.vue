<template>
  <div>
    <h3>租户管理 <el-tag size="small" type="info">组织架构</el-tag></h3>
    <el-tabs v-model="tab">
      <el-tab-pane label="我的信息" name="info">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="用户名">{{ auth.userInfo?.username }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ auth.userInfo?.nickname }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ auth.userInfo?.roles?.join(',') || '普通用户' }}</el-descriptions-item>
          <el-descriptions-item label="租户ID">{{ auth.userInfo?.tenantId }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
      <el-tab-pane label="团队架构" name="team">
        <el-button type="primary" size="small" class="mb" @click="addRootTeam">+ 新建根部门</el-button>
        <el-tree :data="teamTree" :props="{ label: 'teamName', children: 'children' }" node-key="id" default-expand-all :expand-on-click-node="false" highlight-current>
          <template #default="{ data }">
            <span class="tree-node">
              <span :class="{ 'node-disabled': data.enabled === 0 }">{{ data.teamName }}</span>
              <el-tag v-if="data.enabled === 0" size="small" type="danger" class="ml">已下线</el-tag>
              <span class="actions">
                <el-button text size="small" @click.stop="addChildTeam(data)">+子节点</el-button>
                <el-button text size="small" @click.stop="addSiblingTeam(data)">+平级</el-button>
                <el-button text size="small" type="primary" @click.stop="editTeam(data)">编辑</el-button>
                <el-button text size="small" :type="data.enabled === 0 ? 'success' : 'warning'" @click.stop="toggleTeam(data)">{{ data.enabled === 0 ? '启用' : '禁用' }}</el-button>
                <el-button text size="small" type="danger" @click.stop="handleDeleteTeam(data.id)">删除</el-button>
                <el-button text size="small" type="success" @click.stop="showLeaderDialog(data)">👤负责人</el-button>
                <el-button text size="small" type="info" @click.stop="showMemberDialog(data)">👥成员</el-button>
              </span>
            </span>
          </template>
        </el-tree>
      </el-tab-pane>
      <el-tab-pane label="成员管理" name="members">
        <el-select v-model="filterDept" placeholder="按部门筛选" clearable filterable class="mb" style="width:300px" @change="loadMembers">
          <el-option v-for="t in allTeams" :key="t.id" :label="t.teamName" :value="t.id" />
        </el-select>
        <el-input v-model="searchUser" placeholder="搜索用户名/账号" clearable class="mb" style="width:250px;margin-left:8px" @keyup.enter="loadMembers" />
        <el-button type="primary" size="small" class="mb" style="margin-left:8px" @click="loadMembers">搜索</el-button>
        <el-table :data="members" stripe>
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="nickname" label="昵称" />
          <el-table-column prop="roleInTeam" label="团队角色" width="120"><template #default="{ row }"><el-tag :type="row.roleInTeam === 'OWNER' ? 'warning' : 'info'">{{ row.roleInTeam === 'OWNER' ? '负责人' : '成员' }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="100"><template #default="{ row }"><el-button text size="small" type="danger" @click="removeMember(row.userId)">移除</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="角色权限" name="roles">
        <el-table :data="roles" stripe><el-table-column prop="roleCode" label="角色编码" /><el-table-column prop="roleName" label="角色名称" /><el-table-column prop="dataScope" label="数据范围" /></el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showTeamDialog" :title="editingTeamId ? '编辑团队' : '新建团队'">
      <el-input v-model="teamForm.name" placeholder="团队名称" class="mb" />
      <el-select v-model="teamForm.parentId" placeholder="父团队（留空=根）" clearable filterable class="mb" style="width:100%">
        <el-option v-for="t in allTeams" :key="t.id" :label="t.teamName" :value="t.id" />
      </el-select>
      <template #footer><el-button @click="showTeamDialog = false">取消</el-button><el-button type="primary" @click="handleSaveTeam">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="showLeaderDlg" title="负责人管理" width="400px">
      <div v-for="l in leaders" :key="l.userId" class="member-row">
        <span>{{ l.username }} ({{ l.nickname }})</span>
        <el-button text size="small" type="danger" @click="removeLeader(l.userId)">移除</el-button>
      </div>
      <el-divider />
      <el-select v-model="newLeaderId" placeholder="搜索用户" filterable remote :remote-method="searchUsersRemote" :loading="searchLoading" style="width:100%">
        <el-option v-for="u in searchResults" :key="u.id" :label="`${u.username} (${u.nickname})`" :value="u.id" />
      </el-select>
      <el-button type="primary" size="small" class="mt" @click="addLeader">新增负责人</el-button>
    </el-dialog>

    <el-dialog v-model="showMemberDlg" title="成员管理" width="400px">
      <div v-for="m in memberList" :key="m.userId" class="member-row">
        <span>{{ m.username }} ({{ m.nickname }})</span>
        <el-button text size="small" type="danger" @click="removeMemberItem(m.userId)">移除</el-button>
      </div>
      <el-divider />
      <el-select v-model="newMemberId" placeholder="搜索用户" filterable remote :remote-method="searchUsersRemote" :loading="searchLoading" style="width:100%">
        <el-option v-for="u in searchResults" :key="u.id" :label="`${u.username} (${u.nickname})`" :value="u.id" />
      </el-select>
      <el-button type="primary" size="small" class="mt" @click="addMemberItem">新增成员</el-button>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'

const auth = useAuthStore()
const tab = ref('team')
const teamTree = ref<any[]>([])
const allTeams = ref<any[]>([])
const roles = ref<any[]>([])
const members = ref<any[]>([])
const filterDept = ref<number | null>(null)
const searchUser = ref('')
const searchResults = ref<any[]>([])
const searchLoading = ref(false)

const showTeamDialog = ref(false)
const editingTeamId = ref<number | null>(null)
const teamForm = reactive({ name: '', parentId: null as number | null })

const showLeaderDlg = ref(false)
const showMemberDlg = ref(false)
const currentTeamId = ref<number | null>(null)
const leaders = ref<any[]>([])
const memberList = ref<any[]>([])
const newLeaderId = ref<number | null>(null)
const newMemberId = ref<number | null>(null)

// 统一走 request 实例（baseURL=/api），auth 服务前缀为 /auth
async function apiGet(path: string) {
  const res = await request.get('/auth' + path) as any
  return res.data ?? res
}

async function apiPost(path: string, body: any) {
  const res = await request.post('/auth' + path, body) as any
  return res.data ?? res
}

async function apiPut(path: string, body?: any) {
  const res = await request.put('/auth' + path, body) as any
  return res.data ?? res
}

async function apiDelete(path: string) {
  await request.delete('/auth' + path)
}

onMounted(async () => { await loadAll() })

async function loadAll() {
  try {
    allTeams.value = await apiGet('/team/all') || []
    teamTree.value = buildTree(allTeams.value, null)
    roles.value = await apiGet('/role/all') || []
  } catch {}
}

function buildTree(nodes: any[], parentId: number | null): any[] {
  return nodes.filter((n: any) => (n.parentId || null) === parentId).map((n: any) => ({
    ...n, children: buildTree(nodes, n.id)
  }))
}

function addRootTeam() { editingTeamId.value = null; teamForm.name = ''; teamForm.parentId = null; showTeamDialog.value = true }
function addChildTeam(parent: any) { editingTeamId.value = null; teamForm.name = ''; teamForm.parentId = parent.id; showTeamDialog.value = true }
function addSiblingTeam(node: any) { editingTeamId.value = null; teamForm.name = ''; teamForm.parentId = node.parentId || null; showTeamDialog.value = true }
function editTeam(node: any) { editingTeamId.value = node.id; teamForm.name = node.teamName; teamForm.parentId = node.parentId || null; showTeamDialog.value = true }

async function handleSaveTeam() {
  try {
    if (editingTeamId.value) {
      await apiPut(`/team/${editingTeamId.value}`, { name: teamForm.name, parentId: teamForm.parentId })
    } else {
      await apiPost('/team', { name: teamForm.name, parentId: teamForm.parentId, tenantId: auth.userInfo?.tenantId })
    }
    showTeamDialog.value = false; ElMessage.success('保存成功'); loadAll()
  } catch { ElMessage.error('操作失败') }
}

async function toggleTeam(node: any) {
  try { await apiPut(`/team/${node.id}/${node.enabled === 0 ? 'enable' : 'disable'}`); ElMessage.success(node.enabled === 0 ? '已启用' : '已禁用'); loadAll() } catch {}
}

async function handleDeleteTeam(id: number) {
  try {
    await ElMessageBox.confirm('确定删除此团队？')
    await apiDelete(`/team/${id}`)
    ElMessage.success('删除成功'); loadAll()
  } catch {}
}

async function searchUsersRemote(query: string) {
  searchLoading.value = true
  try {
    const res = await apiGet(`/user/page?current=1&size=20&keyword=${query}`)
    searchResults.value = res?.data?.records || res?.records || []
  } catch {} finally { searchLoading.value = false }
}

async function showLeaderDialog(node: any) {
  currentTeamId.value = node.id
  try { leaders.value = await apiGet(`/team/${node.id}/members`) || []; leaders.value = leaders.value.filter((m: any) => m.roleInTeam === 'OWNER') } catch {}
  showLeaderDlg.value = true
}

async function addLeader() {
  if (!currentTeamId.value || !newLeaderId.value) return
  try { await apiPost(`/team/${currentTeamId.value}/members`, { userId: newLeaderId.value, roleInTeam: 'OWNER' }); ElMessage.success('添加成功'); showLeaderDialog({ id: currentTeamId.value }) } catch {}
}

async function removeLeader(userId: number) {
  if (!currentTeamId.value) return
  try { await apiDelete(`/team/${currentTeamId.value}/members/${userId}`); ElMessage.success('移除成功'); showLeaderDialog({ id: currentTeamId.value }) } catch {}
}

async function showMemberDialog(node: any) {
  currentTeamId.value = node.id
  try { memberList.value = await apiGet(`/team/${node.id}/members`) || [] } catch {}
  showMemberDlg.value = true
}

async function addMemberItem() {
  if (!currentTeamId.value || !newMemberId.value) return
  try { await apiPost(`/team/${currentTeamId.value}/members`, { userId: newMemberId.value, roleInTeam: 'MEMBER' }); ElMessage.success('添加成功'); showMemberDialog({ id: currentTeamId.value }) } catch {}
}

async function removeMemberItem(userId: number) {
  if (!currentTeamId.value) return
  try { await apiDelete(`/team/${currentTeamId.value}/members/${userId}`); ElMessage.success('移除成功'); showMemberDialog({ id: currentTeamId.value }) } catch {}
}

async function loadMembers() {
  try {
    const res = await apiGet(`/user/page?current=1&size=50&keyword=${searchUser.value}`)
    members.value = res?.data?.records || res?.records || []
  } catch {}
}

async function removeMember(userId: number) {
  if (!filterDept.value) return
  try { await apiDelete(`/team/${filterDept.value}/members/${userId}`); ElMessage.success('移除成功'); loadMembers() } catch {}
}
</script>

<style scoped>
.tree-node { display: flex; align-items: center; flex: 1; flex-wrap: wrap; }
.ml { margin-left: 8px; }
.actions { margin-left: auto; display: flex; gap: 2px; flex-wrap: wrap; }
.mb { margin-bottom: 12px; }
.mt { margin-top: 12px; }
.member-row { display: flex; justify-content: space-between; align-items: center; padding: 6px 0; }
.node-disabled { color: #999; text-decoration: line-through; }
</style>