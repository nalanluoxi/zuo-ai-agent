<template>
  <div>
    <h3>审批中心</h3>
    <el-tabs v-model="tab">
      <el-tab-pane label="待我审批" name="pending">
        <el-table :data="pendingList" stripe>
          <el-table-column prop="applyType" label="类型" width="120" />
          <el-table-column prop="applicantId" label="申请人" width="100" />
          <el-table-column prop="targetName" label="目标" />
          <el-table-column prop="applyTime" label="时间" width="160" />
          <el-table-column label="操作" width="180"><template #default="{ row }"><el-button text type="success" @click="approve(row.id)">通过</el-button><el-button text type="danger" @click="reject(row.id)">拒绝</el-button></template></el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="我发起的" name="mine">
        <el-table :data="myList" stripe>
          <el-table-column prop="applyType" label="类型" width="120" />
          <el-table-column prop="targetName" label="目标" />
          <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'APPROVED' ? 'success' : row.status === 'REJECTED' ? 'danger' : 'warning'">{{ row.status === 'APPROVED' ? '已通过' : row.status === 'REJECTED' ? '已拒绝' : '待审批' }}</el-tag></template></el-table-column>
          <el-table-column prop="applyTime" label="时间" width="160" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
    <el-button type="primary" class="mt" @click="showApply = true">发起申请</el-button>
    <el-dialog v-model="showApply" title="发起申请" width="400px">
      <el-select v-model="applyForm.type" placeholder="申请类型" class="mb" style="width:100%">
        <el-option label="申请知识库访问" value="KB_ACCESS" />
        <el-option label="申请加入团队" value="TEAM_JOIN" />
        <el-option label="申请角色权限" value="ROLE_APPLY" />
      </el-select>
      <el-input v-model="applyForm.targetName" placeholder="目标名称" class="mb" />
      <el-input v-model="applyForm.reason" type="textarea" placeholder="申请理由" class="mb" />
      <template #footer><el-button @click="showApply = false">取消</el-button><el-button type="primary" @click="submitApply">提交</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../../api/request'

const tab = ref('pending')
const pendingList = ref<any[]>([])
const myList = ref<any[]>([])
const showApply = ref(false)
const applyForm = reactive({ type: 'KB_ACCESS', targetName: '', reason: '' })

async function apiGet(path: string) {
  try { 
    const res = await request.get('/auth' + path) as any
    return res.data ?? res
  } catch { return { records: [] } }
}

async function apiPost(path: string, body?: any) {
  try { 
    const res = await request.post('/auth' + path, body) as any
    return res.data ?? res
  } catch { return {} }
}

onMounted(async () => {
  try { const res = await apiGet('/approval/pending?current=1&size=50'); pendingList.value = res?.records || [] } catch {}
  try { const res = await apiGet('/approval/my?current=1&size=50'); myList.value = res?.records || [] } catch {}
})

async function approve(id: number) {
  try { await apiPost(`/approval/${id}/approve`); ElMessage.success('已通过'); pendingList.value = pendingList.value.filter((a: any) => a.id !== id) } catch { ElMessage.error('操作失败') }
}

async function reject(id: number) {
  try { await apiPost(`/approval/${id}/reject`, { reason: '拒绝' }); ElMessage.success('已拒绝'); pendingList.value = pendingList.value.filter((a: any) => a.id !== id) } catch { ElMessage.error('操作失败') }
}

async function submitApply() {
  try { await apiPost('/approval/submit', { applyType: applyForm.type, targetType: applyForm.type, targetId: 1, targetName: applyForm.targetName, approverType: 'RESOURCE_OWNER', approverId: 1 }); showApply.value = false; ElMessage.success('申请已提交') } catch {}
}
</script>

<style scoped>
.mb { margin-bottom: 12px; }
.mt { margin-top: 16px; }
</style>