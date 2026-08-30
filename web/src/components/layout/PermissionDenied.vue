<template>
  <div class="denied-container">
    <el-result icon="warning" title="无权限访问" sub-title="您没有权限访问此页面">
      <template #extra>
        <p>当前角色：{{ userRole }}</p>
        <p>此页面需要：管理员权限</p>
        <el-button type="primary" @click="handleApply">申请管理员权限</el-button>
        <el-button @click="$router.push('/dashboard')">返回个人看板</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { ElMessage } from 'element-plus'

const props = defineProps<{ page: string }>()
const auth = useAuthStore()
const userRole = computed(() => auth.userInfo?.roles?.join(',') || '普通用户')

function handleApply() {
  ElMessage.success('权限申请已提交，请等待管理员审批')
}
</script>

<style scoped>
.denied-container { display: flex; justify-content: center; align-items: center; height: 60vh; }
</style>