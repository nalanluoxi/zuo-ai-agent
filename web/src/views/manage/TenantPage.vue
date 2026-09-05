<template>
  <div>
    <h3>租户管理 <el-tag size="small" type="info">组织架构</el-tag></h3>
    <el-tabs v-model="tab">
      <el-tab-pane label="组织架构" name="team">
        <div class="mb" style="display:flex;gap:8px;align-items:center">
          <el-button v-if="canWrite" type="primary" size="small" @click="addRootTeam">+ 新建根部门</el-button>
          <el-button size="small" plain @click="toggleExpandAll">{{ expandAll ? '折叠全部' : '展开全部' }}</el-button>
        </div>
        <!-- :key 强制重建以应用展开/折叠状态；默认全部折叠只显示顶级部门 -->
        <el-tree :key="treeKey" :data="teamTree" :props="{ label: 'teamName', children: 'children' }" node-key="id" :default-expand-all="expandAll" :expand-on-click-node="true" highlight-current class="team-tree">
          <template #default="{ data }">
            <span class="tree-node">
              <span :class="{ 'node-disabled': data.status === 0 }">{{ data.teamName }}</span>
              <el-tag v-if="data.status === 0" size="small" type="danger" class="ml">已下线</el-tag>
              <!-- 成员摘要：负责人黄色标签优先，成员灰色标签，合计最多 4 个 -->
              <span class="member-summary" v-if="(data.memberCount || 0) > 0">
                <el-tag v-for="(n, i) in data.memberPreviews || []" :key="i" size="small"
                  :type="i < (data.leaderNames?.length || 0) ? 'warning' : 'info'" effect="plain">{{ n }}</el-tag>
                <span v-if="data.memberCount > 4" class="member-more">等 {{ data.memberCount }} 人</span>
              </span>
              <span class="actions">
                <template v-if="canWrite">
                  <el-button size="small" plain @click.stop="addChildTeam(data)">+子节点</el-button>
                  <el-button size="small" plain type="primary" @click.stop="editTeam(data)">编辑</el-button>
                  <el-button size="small" plain :type="data.status === 0 ? 'success' : 'warning'" @click.stop="toggleTeam(data)">{{ data.status === 0 ? '启用' : '禁用' }}</el-button>
                  <el-button size="small" plain type="danger" @click.stop="handleDeleteTeam(data.id)">删除</el-button>
                </template>
                <el-button size="small" plain type="primary" @click.stop="showMemberDialog(data)">成员管理</el-button>
              </span>
            </span>
          </template>
        </el-tree>
      </el-tab-pane>
      <el-tab-pane label="成员管理" name="members">
        <div class="mb" style="display:flex;gap:8px;align-items:center">
          <el-input v-model="searchUser" placeholder="搜索用户名/昵称" clearable style="width:250px" @keyup.enter="loadMembers" @clear="loadMembers" />
          <el-button type="primary" size="small" @click="loadMembers">搜索</el-button>
        </div>
        <el-table :data="members" stripe>
          <el-table-column prop="username" label="用户名" width="130" />
          <el-table-column prop="nickname" label="昵称" width="130" />
          <el-table-column label="组织架构" min-width="220">
            <template #default="{ row }">
              <template v-if="row.teamPaths && row.teamPaths.length">
                <!-- 默认只显示前 2 条，超出折叠为"还有 N 条" -->
                <div v-for="p in displayPaths(row)" :key="p" style="font-size:13px;line-height:1.8">{{ p }}</div>
                <el-button
                  v-if="row.teamPaths.length > 2"
                  text type="primary" size="small"
                  @click="togglePathExpand(row.id)"
                >{{ isPathExpanded(row.id) ? '收起' : `还有 ${row.teamPaths.length - 2} 条，点击展开` }}</el-button>
              </template>
              <span v-else style="color:#909399">默认</span>
            </template>
          </el-table-column>
          <el-table-column label="拥有角色权限" min-width="150">
            <template #default="{ row }">
              <template v-if="row.roles && row.roles.length">
                <el-tag v-for="r in row.roles" :key="r.roleId" size="small" type="success" style="margin-right:4px">{{ r.roleName }}</el-tag>
              </template>
              <span v-else style="color:#909399">无</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <div style="display:flex;gap:6px;flex-wrap:nowrap">
                <el-button size="small" :disabled="!canWrite" @click="openDeptDialog(row)">设置部门归属</el-button>
                <el-button size="small" type="primary" plain :disabled="!canWrite" @click="openAssignRoles(row)">编辑角色权限</el-button>
                <el-button
                  size="small"
                  :type="row.status === 0 ? 'success' : 'danger'"
                  plain
                  :disabled="!canWrite || row.id === auth.userInfo?.id"
                  @click="toggleUserStatus(row)"
                >{{ row.status === 0 ? '启用' : '禁用' }}</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="角色权限" name="roles">
        <div class="mb" style="display:flex;justify-content:space-between;align-items:center">
          <span style="color:#909399;font-size:13px">配置每个角色对各页面的访问级别（不可读/只读/修改/超级管理），新注册用户默认拥有「普通用户」角色</span>
          <el-button type="primary" size="small" :disabled="!canWrite" @click="openCreateRole">+ 新建角色</el-button>
        </div>
        <el-table :data="roles" stripe>
          <el-table-column prop="roleName" label="角色名称" width="160" />
          <el-table-column label="权限范围" min-width="320">
            <template #default="{ row }">
              <template v-if="row.pagePerms && row.pagePerms.length">
                <el-tag
                  v-for="p in row.pagePerms" :key="p.pageCode" size="small"
                  :type="p.accessLevel === 'ADMIN' ? 'danger' : p.accessLevel === 'WRITE' ? 'warning' : 'info'"
                  style="margin:2px 4px 2px 0"
                >{{ p.pageName }} {{ levelLabel(p.accessLevel) }}</el-tag>
              </template>
              <span v-else style="color:#909399">全部不可读</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button text size="small" type="primary" :disabled="!canWrite" @click="openRolePermDialog(row)">编辑</el-button>
              <el-button
                v-if="!row.builtin"
                text size="small" type="danger" :disabled="!canWrite"
                @click="handleDeleteRole(row)"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="我的信息" name="info">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="账号">{{ auth.userInfo?.username }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ auth.userInfo?.nickname }}</el-descriptions-item>
          <el-descriptions-item label="权限角色">
            <template v-if="auth.userInfo?.roleNames?.length">
              <el-tag v-for="r in auth.userInfo.roleNames" :key="r" size="small" type="success" style="margin-right:4px">{{ r }}</el-tag>
            </template>
            <span v-else style="color:#909399">无</span>
          </el-descriptions-item>
          <el-descriptions-item label="隶属部门">
            <template v-if="auth.userInfo?.teamPaths?.length">
              <div v-for="p in auth.userInfo.teamPaths" :key="p" style="font-size:13px">{{ p }}</div>
            </template>
            <span v-else style="color:#909399">默认</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
    </el-tabs>

    <!-- 设置部门归属对话框：复选框树，勾选=加入，取消勾选=移出 -->
    <!-- destroy-on-close：关闭销毁内容，确保下次打开树重新挂载、勾选状态正确应用 -->
    <el-dialog v-model="showDeptDlg" :title="`设置部门归属 - ${deptTarget?.nickname || deptTarget?.username || ''}`" width="460px" destroy-on-close>
      <div style="margin-bottom:8px;color:#909399;font-size:13px">勾选部门即加入，取消勾选即移出（可多选）</div>
      <el-tree
        ref="deptTreeRef"
        :data="teamTree"
        :props="{ label: 'teamName', children: 'children' }"
        node-key="id"
        show-checkbox
        check-strictly
        default-expand-all
        :default-checked-keys="deptCheckedIds"
        style="max-height:360px;overflow:auto;border:1px solid #e4e7ed;border-radius:4px;padding:8px"
      />
      <template #footer>
        <el-button @click="showDeptDlg = false">取消</el-button>
        <el-button type="primary" :loading="deptSaving" @click="saveDepartment">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新建角色对话框：角色名称 + 页面权限矩阵 一步完成 -->
    <el-dialog v-model="showRoleDialog" title="新建角色" width="560px">
      <el-form label-width="80px">
        <el-form-item label="角色名称" required>
          <el-input v-model="roleForm.roleName" placeholder="如 DB管理员" />
        </el-form-item>
        <el-form-item label="访问页面">
          <el-table :data="createPermMatrix" stripe max-height="360" size="small">
            <el-table-column prop="pageName" label="页面" width="140" />
            <el-table-column label="访问级别">
              <template #default="{ row }">
                <el-select v-model="row.accessLevel" size="small" style="width:140px">
                  <el-option label="不可读" value="" />
                  <el-option label="只读" value="READ" />
                  <el-option label="修改" value="WRITE" />
                  <el-option label="超级管理" value="ADMIN" />
                </el-select>
              </template>
            </el-table-column>
          </el-table>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRoleDialog = false">取消</el-button>
        <el-button type="primary" :loading="roleSaving" @click="handleCreateRole">创建</el-button>
      </template>
    </el-dialog>

    <!-- 页面权限矩阵对话框 -->
    <el-dialog v-model="showPermDialog" :title="`编辑页面权限 - ${currentRole?.roleName || ''}`" width="560px">
      <el-table :data="permMatrix" stripe max-height="420">
        <el-table-column prop="pageName" label="页面" width="160" />
        <el-table-column prop="pageCode" label="页面编码" width="160">
          <template #default="{ row }"><span style="font-family:monospace;font-size:12px">{{ row.pageCode }}</span></template>
        </el-table-column>
        <el-table-column label="访问级别">
          <template #default="{ row }">
            <el-select v-model="row.accessLevel" size="small" style="width:140px" :disabled="!canWrite">
              <el-option label="不可读" value="" />
              <el-option label="只读" value="READ" />
              <el-option label="修改" value="WRITE" />
              <el-option label="超级管理" value="ADMIN" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showPermDialog = false">取消</el-button>
        <el-button type="primary" :loading="permSaving" :disabled="!canWrite" @click="saveRolePerms">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色对话框 -->
    <el-dialog v-model="showAssignDlg" :title="`分配角色 - ${assignTarget?.nickname || assignTarget?.username || ''}`" width="420px">
      <el-select v-model="assignRoleIds" multiple style="width:100%" placeholder="选择角色">
        <el-option v-for="r in roles" :key="r.id" :label="`${r.roleName} (${r.roleCode})`" :value="r.id" />
      </el-select>
      <template #footer>
        <el-button @click="showAssignDlg = false">取消</el-button>
        <el-button type="primary" :loading="assignSaving" :disabled="!canWrite" @click="saveUserRoles">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showTeamDialog" :title="teamDialogTitle" width="420px">
      <!-- 子部门创建/编辑时父部门只读展示，不支持修改 -->
      <div v-if="teamParentPath" class="parent-path-bar">
        <span style="color:#909399">父部门：</span><span>{{ teamParentPath }}</span>
      </div>
      <el-input v-model="teamForm.name" placeholder="部门名称" class="mb" />
      <template #footer><el-button @click="showTeamDialog = false">取消</el-button><el-button type="primary" @click="handleSaveTeam">保存</el-button></template>
    </el-dialog>


    <el-dialog v-model="showMemberDlg" title="成员管理" width="560px">
      <!-- 当前部门：从顶级部门逐层展示 -->
      <div class="dept-path-bar">
        <span style="color:#909399">当前部门：</span>
        <template v-for="(name, idx) in currentDeptPath" :key="idx">
          <span class="dept-path-node">{{ name }}</span>
          <span v-if="idx < currentDeptPath.length - 1" class="dept-path-sep">/</span>
        </template>
      </div>
      <!-- 当前部门成员列表 -->
      <el-table :data="memberList" stripe height="300" class="mb">
        <el-table-column prop="username" label="用户名" width="120" />
        <el-table-column prop="nickname" label="昵称" width="120" />
        <el-table-column label="角色" width="90">
          <template #default="{ row }">
            <el-tag :type="row.roleInTeam === 'OWNER' ? 'warning' : 'info'" size="small">
              {{ row.roleInTeam === 'OWNER' ? '负责人' : '成员' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190">
          <template #default="{ row }">
            <div style="display:flex;gap:6px;flex-wrap:nowrap">
              <el-button size="small" :type="row.roleInTeam === 'OWNER' ? 'warning' : 'primary'" plain :disabled="!canManageTeam" @click="toggleLeader(row)">
                {{ row.roleInTeam === 'OWNER' ? '取消负责人' : '设为负责人' }}
              </el-button>
              <el-button size="small" type="danger" plain :disabled="!canManageTeam" @click="removeMemberItem(row.userId)">移除部门</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty><el-empty description="该部门暂无成员" :image-size="60" /></template>
      </el-table>
      <el-divider />
      <!-- 输入名字实时匹配用户，选中即添加 -->
      <div style="display:flex;gap:8px">
        <el-select
          v-model="newMemberId"
          placeholder="输入名字搜索用户"
          filterable remote clearable
          :remote-method="searchUsersRemote"
          :loading="searchLoading"
          style="flex:1"
        >
          <el-option v-for="u in searchResults" :key="u.id" :label="`${u.nickname || ''} (${u.username})`" :value="u.id" />
        </el-select>
        <el-button type="primary" :disabled="!canManageTeam || !newMemberId" @click="addMemberItem">添加成员</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../../api/request'

const auth = useAuthStore()
const tab = ref('team')
const teamTree = ref<any[]>([])
const allTeams = ref<any[]>([])
const roles = ref<any[]>([])
const members = ref<any[]>([])
const searchUser = ref('')
const searchResults = ref<any[]>([])
const searchLoading = ref(false)

// 当前用户是否拥有租户管理页面的"修改"权限
const canWrite = computed(() => auth.hasPageAccess('manage:tenants', 'WRITE'))

// 访问级别中文标签
function levelLabel(level: string): string {
  return { READ: '只读', WRITE: '修改', ADMIN: '超级管理' }[level] || '不可读'
}

// ── 角色权限配置 ──
const showRoleDialog = ref(false)
const roleForm = reactive({ roleName: '' })
const createPermMatrix = ref<any[]>([])
const roleSaving = ref(false)
const showPermDialog = ref(false)
const currentRole = ref<any>(null)
const permMatrix = ref<any[]>([])
const permSaving = ref(false)

// ── 分配角色 ──
const showAssignDlg = ref(false)
const assignTarget = ref<any>(null)
const assignRoleIds = ref<(string | number)[]>([])
const assignSaving = ref(false)

const showTeamDialog = ref(false)
// 组织架构树：默认全部折叠（只显示顶级部门），可一键展开/折叠
const expandAll = ref(false)
const treeKey = ref(0)
function toggleExpandAll() {
  expandAll.value = !expandAll.value
  treeKey.value++ // 强制重建树以应用展开状态
}

// 团队弹窗标题
const teamDialogTitle = computed(() => {
  if (editingTeamId.value) return '编辑部门'
  return teamForm.parentId ? '新建子部门' : '新建根部门'
})

// 父部门完整路径（只读展示）
const teamParentPath = computed(() => {
  if (!teamForm.parentId) return ''
  const byId = new Map(allTeams.value.map((t: any) => [String(t.id), t]))
  const path: string[] = []
  let cur = byId.get(String(teamForm.parentId))
  let guard = 0
  while (cur && guard++ < 20) {
    path.unshift(cur.teamName)
    const pid = cur.parentId
    cur = (pid === null || pid === undefined || pid === 0 || pid === '0') ? undefined : byId.get(String(pid))
  }
  return path.join(' / ')
})
const editingTeamId = ref<string | number | null>(null)
const teamForm = reactive({ name: '', parentId: null as string | number | null })

const showMemberDlg = ref(false)
const currentTeamId = ref<string | number | null>(null)
const memberList = ref<any[]>([])
const newMemberId = ref<string | number | null>(null)

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

// 提取后端返回的失败原因（保护策略拦截时给出提示）
function errMsg(e: any, fallback: string) {
  return e?.response?.data?.message || fallback
}

onMounted(async () => { await loadAll(); await loadMembers() })

async function loadAll() {
  try {
    allTeams.value = await apiGet('/team/all') || []
    teamTree.value = buildTree(allTeams.value, null)
    roles.value = await apiGet('/role/with-page-perms') || []
  } catch {}
}

function buildTree(nodes: any[], parentId: any): any[] {
  return nodes.filter((n: any) => {
    const pid = n.parentId
    // parentId 为空/0/'0' 均视为根节点；其余按字符串比较（Long id 序列化为字符串防精度丢失）
    const isRoot = pid === null || pid === undefined || pid === 0 || pid === '0'
    return parentId === null ? isRoot : String(pid) === String(parentId)
  }).map((n: any) => ({
    ...n, children: buildTree(nodes, n.id)
  }))
}

function addRootTeam() { editingTeamId.value = null; teamForm.name = ''; teamForm.parentId = null; showTeamDialog.value = true }
function addChildTeam(parent: any) { editingTeamId.value = null; teamForm.name = ''; teamForm.parentId = parent.id; showTeamDialog.value = true }
function editTeam(node: any) { editingTeamId.value = node.id; teamForm.name = node.teamName; teamForm.parentId = node.parentId || null; showTeamDialog.value = true }

async function handleSaveTeam() {
  try {
    if (editingTeamId.value) {
      await apiPut(`/team/${editingTeamId.value}`, { name: teamForm.name, parentId: teamForm.parentId })
    } else {
      await apiPost('/team', { name: teamForm.name, parentId: teamForm.parentId, tenantId: auth.userInfo?.tenantId })
    }
    showTeamDialog.value = false; ElMessage.success('保存成功'); loadAll()
  } catch (e: any) { ElMessage.error(errMsg(e, '操作失败')) }
}

async function toggleTeam(node: any) {
  try { await apiPut(`/team/${node.id}/${node.status === 0 ? 'enable' : 'disable'}`); ElMessage.success(node.status === 0 ? '已启用' : '已禁用'); loadAll() }
  catch (e: any) { ElMessage.error(errMsg(e, '操作失败')) }
}

async function handleDeleteTeam(id: string | number) {
  try {
    await ElMessageBox.confirm('确定删除此部门？如部门内有成员，删除后成员将自动移出该部门。', '删除部门', { type: 'warning' })
    await apiDelete(`/team/${id}`)
    ElMessage.success('删除成功'); loadAll()
  } catch (e: any) {
    // 用户点取消不提示；后端返回的失败原因（如存在子部门）要展示
    if (e !== 'cancel') {
      ElMessage.error(e?.response?.data?.message || '删除失败')
    }
  }
}

async function searchUsersRemote(query: string) {
  searchLoading.value = true
  try {
    const res = await apiGet(`/user/page?current=1&size=20&keyword=${query}`)
    searchResults.value = res?.data?.records || res?.records || []
  } catch {} finally { searchLoading.value = false }
}

// 当前部门完整路径（顶级 → 当前）
const currentDeptPath = computed<string[]>(() => {
  if (!currentTeamId.value) return []
  const path: string[] = []
  const byId = new Map(allTeams.value.map((t: any) => [String(t.id), t]))
  let cur = byId.get(String(currentTeamId.value))
  let guard = 0
  while (cur && guard++ < 20) {
    path.unshift(cur.teamName)
    const pid = cur.parentId
    cur = (pid === null || pid === undefined || pid === 0 || pid === '0') ? undefined : byId.get(String(pid))
  }
  return path
})

// 是否可管理当前部门成员：页面超级管理(ADMIN)，或本部门/任意上级部门的负责人（权限继承）
const canManageTeam = computed(() => {
  if (auth.hasPageAccess('manage:tenants', 'ADMIN')) return true
  const myOwnerIds = new Set(
    (auth.userInfo?.myTeams || [])
      .filter((t: any) => t.roleInTeam === 'OWNER')
      .map((t: any) => String(t.teamId))
  )
  if (myOwnerIds.size === 0 || !currentTeamId.value) return false
  // 沿部门链向上找：本部门或任意祖先部门是我负责的即可
  const byId = new Map(allTeams.value.map((t: any) => [String(t.id), t]))
  let cur = byId.get(String(currentTeamId.value))
  let guard = 0
  while (cur && guard++ < 20) {
    if (myOwnerIds.has(String(cur.id))) return true
    const pid = cur.parentId
    cur = (pid === null || pid === undefined || pid === 0 || pid === '0') ? undefined : byId.get(String(pid))
  }
  return false
})

// 设为负责人 / 取消负责人
async function toggleLeader(row: any) {
  if (!currentTeamId.value) return
  const targetRole = row.roleInTeam === 'OWNER' ? 'MEMBER' : 'OWNER'
  try {
    await apiPost(`/team/${currentTeamId.value}/members`, { userId: row.userId, roleInTeam: targetRole })
    ElMessage.success(targetRole === 'OWNER' ? '已设为负责人' : '已取消负责人')
    await showMemberDialog({ id: currentTeamId.value })
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  }
}

async function showMemberDialog(node: any) {
  currentTeamId.value = node.id
  newMemberId.value = null
  searchResults.value = []
  try { memberList.value = await apiGet(`/team/${node.id}/members`) || [] } catch {}
  showMemberDlg.value = true
}

async function addMemberItem() {
  if (!currentTeamId.value || !newMemberId.value) return
  try {
    await apiPost(`/team/${currentTeamId.value}/members`, { userId: newMemberId.value, roleInTeam: 'MEMBER' })
    ElMessage.success('添加成功')
    newMemberId.value = null
    searchResults.value = []
    await showMemberDialog({ id: currentTeamId.value })
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '添加失败')
  }
}

async function removeMemberItem(userId: string | number) {
  if (!currentTeamId.value) return
  try { await apiDelete(`/team/${currentTeamId.value}/members/${userId}`); ElMessage.success('移除成功'); showMemberDialog({ id: currentTeamId.value }) }
  catch (e: any) { ElMessage.error(errMsg(e, '移除失败')) }
}

async function loadMembers() {
  try {
    const data = await apiGet(`/user/manage-list?keyword=${encodeURIComponent(searchUser.value || '')}`)
    members.value = Array.isArray(data) ? data : (data?.data || [])
  } catch { members.value = [] }
}

// 禁用/启用账号
async function toggleUserStatus(row: any) {
  const targetStatus = row.status === 0 ? 1 : 0
  const action = targetStatus === 0 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定${action}账号「${row.username}」？`, `${action}账号`, { type: 'warning' })
    await apiPut(`/user/${row.id}`, { status: targetStatus })
    ElMessage.success(`${action}成功`)
    loadMembers()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(errMsg(e, `${action}失败`))
  }
}

// ── 设置部门归属（复选框树：勾选=加入，取消勾选=移出） ──
const showDeptDlg = ref(false)
const deptTarget = ref<any>(null)
const deptCheckedIds = ref<(string | number)[]>([])
const deptTreeRef = ref()
const deptSaving = ref(false)

// 组织架构列折叠：记录展开的用户 id
const expandedPathRows = ref<Set<string>>(new Set())

function isPathExpanded(userId: any): boolean {
  return expandedPathRows.value.has(String(userId))
}

function togglePathExpand(userId: any) {
  const key = String(userId)
  if (expandedPathRows.value.has(key)) {
    expandedPathRows.value.delete(key)
  } else {
    expandedPathRows.value.add(key)
  }
}

function displayPaths(row: any): string[] {
  const paths: string[] = row.teamPaths || []
  return isPathExpanded(row.id) ? paths : paths.slice(0, 2)
}

async function openDeptDialog(row: any) {
  deptTarget.value = row
  // 实时查询该用户最新归属，避免使用列表缓存数据
  try {
    const ids = await apiGet(`/user/${row.id}/departments`)
    deptCheckedIds.value = Array.isArray(ids) ? ids : []
  } catch {
    deptCheckedIds.value = (row.teams || []).map((t: any) => t.teamId)
  }
  showDeptDlg.value = true
  // default-checked-keys 只在树挂载时生效，这里显式再设一次兜底
  await nextTick()
  deptTreeRef.value?.setCheckedKeys(deptCheckedIds.value)
}

async function saveDepartment() {
  if (!deptTarget.value) return
  deptSaving.value = true
  try {
    const checked = deptTreeRef.value?.getCheckedKeys() || []
    await apiPost(`/user/${deptTarget.value.id}/department`, { teamIds: checked })
    ElMessage.success('保存成功')
    showDeptDlg.value = false
    loadMembers()
  } catch (e: any) {
    ElMessage.error(errMsg(e, '保存失败'))
  } finally {
    deptSaving.value = false
  }
}

// ═══════════════ 角色权限配置 ═══════════════

async function openCreateRole() {
  roleForm.roleName = ''
  // 加载页面清单，构建权限矩阵（默认无权限）
  try {
    const pages = await apiGet('/role/page-list') || []
    createPermMatrix.value = pages.map((p: any) => ({
      permissionId: p.id,
      pageCode: p.permCode || p.perm_code,
      pageName: p.permName || p.perm_name,
      accessLevel: ''
    }))
  } catch {
    createPermMatrix.value = []
  }
  showRoleDialog.value = true
}

async function handleCreateRole() {
  if (!roleForm.roleName.trim()) {
    ElMessage.warning('角色名称不能为空')
    return
  }
  roleSaving.value = true
  try {
    // 1. 创建角色（roleCode 由后端自动生成）
    const role = await apiPost('/role', {
      roleName: roleForm.roleName.trim(),
      scopeType: 'TENANT',
      dataScope: 'SELF'
    })
    // 2. 保存页面权限矩阵
    const items = createPermMatrix.value
      .filter((r: any) => r.accessLevel)
      .map((r: any) => ({ permissionId: r.permissionId, accessLevel: r.accessLevel }))
    if (role?.id && items.length > 0) {
      await apiPost(`/role/${role.id}/page-permissions`, { items })
    }
    ElMessage.success('创建成功')
    showRoleDialog.value = false
    roles.value = await apiGet('/role/with-page-perms') || []
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '创建失败')
  } finally {
    roleSaving.value = false
  }
}

async function handleDeleteRole(row: any) {
  try {
    await ElMessageBox.confirm(`确定删除角色「${row.roleName}」？删除后已分配该角色的用户将失去对应权限。`, '删除角色', { type: 'warning' })
    await apiDelete(`/role/${row.id}`)
    ElMessage.success('删除成功')
    roles.value = await apiGet('/role/with-page-perms') || []
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(errMsg(e, '删除失败'))
  }
}

async function openRolePermDialog(row: any) {
  currentRole.value = row
  try {
    const pages = await apiGet('/role/page-list') || []
    const granted = await apiGet(`/role/${row.id}/page-permissions`) || []
    const grantedMap = new Map(granted.map((g: any) => [g.permissionId, g.accessLevel]))
    permMatrix.value = pages.map((p: any) => ({
      permissionId: p.id,
      pageCode: p.permCode || p.perm_code,
      pageName: p.permName || p.perm_name,
      accessLevel: grantedMap.get(p.id) || ''
    }))
    showPermDialog.value = true
  } catch {
    ElMessage.error('加载页面权限失败')
  }
}

async function saveRolePerms() {
  if (!currentRole.value) return
  permSaving.value = true
  try {
    const items = permMatrix.value
      .filter((r: any) => r.accessLevel)
      .map((r: any) => ({ permissionId: r.permissionId, accessLevel: r.accessLevel }))
    await apiPost(`/role/${currentRole.value.id}/page-permissions`, { items })
    ElMessage.success('保存成功')
    showPermDialog.value = false
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    permSaving.value = false
  }
}

// ═══════════════ 分配角色 ═══════════════

async function openAssignRoles(row: any) {
  // 成员管理表格行：可能来自 team members（userId 字段）或 user page（id 字段）
  assignTarget.value = row
  const userId = row.userId ?? row.id
  try {
    const roleIds = await apiGet(`/user/${userId}/roles`) || []
    assignRoleIds.value = roleIds
  } catch {
    assignRoleIds.value = []
  }
  showAssignDlg.value = true
}

async function saveUserRoles() {
  if (!assignTarget.value) return
  const userId = assignTarget.value.userId ?? assignTarget.value.id
  assignSaving.value = true
  try {
    await apiPost(`/user/${userId}/roles`, { roleIds: assignRoleIds.value })
    ElMessage.success('分配成功')
    showAssignDlg.value = false
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '分配失败')
  } finally {
    assignSaving.value = false
  }
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

.member-summary { display: inline-flex; align-items: center; gap: 4px; margin-left: 12px; flex-wrap: wrap; }
.member-more { font-size: 12px; color: #909399; margin-left: 4px; }
.parent-path-bar { background: #f5f7fa; border-radius: 4px; padding: 8px 12px; margin-bottom: 12px; font-size: 13px; }

/* 成员管理弹窗：当前部门路径 */
.dept-path-bar {
  margin-bottom: 12px;
  padding: 8px 12px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 14px;
}
.dept-path-node { color: #303133; font-weight: 500; }
.dept-path-sep { color: #c0c4cc; margin: 0 6px; }

/* 团队架构树：放大字体与行高，提升可读性 */
.team-tree :deep(.el-tree-node__content) {
  height: 44px;
  font-size: 15px;
}
.team-tree :deep(.el-tree-node__label) {
  font-size: 15px;
}
.team-tree :deep(.el-button) {
  font-size: 13px;
}
</style>