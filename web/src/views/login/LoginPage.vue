<template>
  <div class="login-container">
    <el-card class="login-card">
      <h2>ZUO AI Agent</h2>
      <el-tabs v-model="mode">
        <el-tab-pane label="登录" name="login">
          <el-input v-model="loginForm.username" placeholder="用户名" class="mb" />
          <el-input v-model="loginForm.password" type="password" placeholder="密码" class="mb" show-password />
          <el-button type="primary" class="w-full" @click="handleLogin" :loading="loading">登录</el-button>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-input v-model="regForm.username" placeholder="用户名" class="mb" />
          <el-input v-model="regForm.nickname" placeholder="昵称" class="mb" />
          <el-input v-model="regForm.password" type="password" placeholder="密码" class="mb" show-password />
          <el-button type="primary" class="w-full" @click="handleRegister" :loading="loading">注册</el-button>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()
const auth = useAuthStore()
const mode = ref('login')
const loading = ref(false)
const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', password: '', nickname: '' })

async function handleLogin() {
  loading.value = true
  try { await auth.login(loginForm.username, loginForm.password); router.push('/chat') }
  catch { ElMessage.error('登录失败') }
  finally { loading.value = false }
}

async function handleRegister() {
  loading.value = true
  try { await auth.register(regForm.username, regForm.password, regForm.nickname); router.push('/chat') }
  catch { ElMessage.error('注册失败') }
  finally { loading.value = false }
}
</script>

<style scoped>
.login-container { display: flex; justify-content: center; align-items: center; height: 100vh; background: #f0f2f5; }
.login-card { width: 400px; }
.login-card h2 { text-align: center; margin-bottom: 20px; }
.mb { margin-bottom: 12px; }
.w-full { width: 100%; }
</style>