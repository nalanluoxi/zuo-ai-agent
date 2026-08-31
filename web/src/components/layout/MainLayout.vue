<template>
  <el-container class="layout-container">
    <!-- 顶部头部 -->
    <el-header class="layout-header">
      <div class="header-left">
        <!-- Logo 菜单 -->
        <el-popover placement="bottom" :width="800" trigger="click">
          <template #reference>
            <div class="logo">
              <el-icon><DataAnalysis /></el-icon>
              <span>ZWD-ai</span>
            </div>
          </template>
          <div class="logo-menu">
            <div class="menu-item" @click="navigateTo('/chat')">
              <el-icon><ChatDotSquare /></el-icon>
              <span>RAG 对话</span>
            </div>
            <div class="menu-item" @click="navigateTo('/manage/dashboard')">
              <el-icon><DataAnalysis /></el-icon>
              <span>全局看板</span>
            </div>
            <div class="menu-item" @click="navigateTo('/manage/tenants')" v-if="hasPermission('SUPER_ADMIN')">
              <el-icon><OfficeBuilding /></el-icon>
              <span>租户管理</span>
            </div>
            <div class="menu-item" @click="navigateTo('/monitor/redis')">
              <el-icon><Coin /></el-icon>
              <span>Redis 监控</span>
            </div>
            <div class="menu-item" @click="navigateTo('/monitor/db')">
              <el-icon><DataAnalysis /></el-icon>
              <span>DB 监控</span>
            </div>
            <div class="menu-item" @click="navigateTo('/monitor/logs')">
              <el-icon><Document /></el-icon>
              <span>日志系统</span>
            </div>
            <div class="menu-item" @click="navigateTo('/manage/approvals')" v-if="hasPermission('SUPER_ADMIN')">
              <el-icon><DocumentChecked /></el-icon>
              <span>审批中心</span>
            </div>
          </div>
        </el-popover>

        <!-- 当前页面标题 -->
        <div class="page-title">
          {{ currentPageTitle }}
        </div>
      </div>

      <!-- 右侧用户信息 -->
      <div class="header-right">
        <el-avatar :size="32" icon="UserFilled" style="cursor:pointer" @click="showProfile = true" />
        <span class="username">{{ auth.userInfo?.nickname || '用户' }}</span>
        <el-button text @click="handleLogout">退出</el-button>
      </div>
    </el-header>

    <!-- 内容区域 -->
    <el-main class="layout-main">
      <router-view v-if="allowed" />
      <PermissionDenied v-else :page="route.path" />
    </el-main>

    <!-- 个人信息弹窗 -->
    <el-dialog v-model="showProfile" title="个人信息" width="400px">
      <div class="profile-dialog">
        <el-avatar :size="80" icon="UserFilled" />
        <el-descriptions :column="1" border class="mt">
          <el-descriptions-item label="用户名">{{ auth.userInfo?.username }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ auth.userInfo?.nickname }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ auth.userInfo?.roles?.join(',') || '普通用户' }}</el-descriptions-item>
          <el-descriptions-item label="租户ID">{{ auth.userInfo?.tenantId }}</el-descriptions-item>
        </el-descriptions>
        <el-button type="primary" class="mt" @click="showPwd = true">修改密码</el-button>
      </div>
    </el-dialog>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="showPwd" title="修改密码" width="350px" append-to-body>
      <el-input v-model="pwdForm.oldPwd" type="password" placeholder="旧密码" class="mb" />
      <el-input v-model="pwdForm.newPwd" type="password" placeholder="新密码" class="mb" />
      <template #footer>
        <el-button @click="showPwd = false">取消</el-button>
        <el-button type="primary" @click="changePwd">确认</el-button>
      </template>
    </el-dialog>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import PermissionDenied from './PermissionDenied.vue'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const showProfile = ref(false)
const showPwd = ref(false)
const pwdForm = ref({ oldPwd: '', newPwd: '' })

// 权限检查
const allowed = computed(() => {
  const requiredPermission = route.meta.permission as string
  if (!requiredPermission) return true
  return hasPermission(requiredPermission)
})

// 当前页面标题
const currentPageTitle = computed(() => {
  return (route.meta.title as string) || '首页'
})

function hasPermission(permission: string): boolean {
  if (!auth.userInfo) return false
  const perms = auth.userInfo.permissions || auth.userInfo.roles || []
  // SUPER_ADMIN 有所有权限
  if (perms.includes('SUPER_ADMIN')) return true
  return perms.includes(permission)
}

function navigateTo(path: string) {
  router.push(path)
}

function handleLogout() {
  auth.logout()
  router.push('/login')
}

function changePwd() {
  showPwd.value = false
  ElMessage.success('密码修改成功')
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

.layout-header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 20px;
  height: 60px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 20px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.logo:hover {
  background: #f0f2f5;
}

.logo-menu {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  padding: 12px 0;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  cursor: pointer;
  border-radius: 4px;
  font-size: 14px;
  transition: background 0.2s;
}

.menu-item:hover {
  background: #f0f2f5;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.username {
  font-size: 14px;
  color: #666;
}

.layout-main {
  flex: 1;
  padding: 20px;
  background: #f5f7fa;
  overflow-y: auto;
}

.profile-dialog {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.mt {
  margin-top: 16px;
}

.mb {
  margin-bottom: 12px;
}
</style>