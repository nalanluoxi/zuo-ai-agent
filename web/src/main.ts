import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import { useAuthStore } from './stores/auth'
import { authApi } from './api/index'

const app = createApp(App)
app.use(ElementPlus)
const pinia = createPinia()
app.use(pinia)
app.use(router)

const auth = useAuthStore()
const token = localStorage.getItem('satoken')
if (token) {
  auth.token = token
  const cached = localStorage.getItem('userInfo')
  if (cached) {
    try {
      const info = JSON.parse(cached)
      // 兼容旧缓存：如果没有 permissions 字段，从 roles 补上
      if (!info.permissions && info.roles) {
        info.permissions = info.roles
      }
      // 兼容旧缓存：如果没有 id 字段，从 loginId 补上
      if (!info.id && info.loginId) {
        info.id = info.loginId
      }
      auth.userInfo = info
    } catch {}
  }
  // 重新获取用户信息以确保权限正确（修复旧缓存角色不正确的问题）
  authApi.me(token).then((res: any) => {
    const me = res?.data || res
    // 使用后端返回的角色列表，不再硬编码判断
    const roles = me.roles || me.permissions || []
    const loginId = me.loginId || me.id
    const info = { ...me, roles, permissions: roles, id: loginId }
    auth.userInfo = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }).catch(() => {
    // 刷新失败说明 token 已失效或用户信息异常，清理过期缓存，避免一直展示旧用户数据
    auth.logout()
  })
}

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}
app.mount('#app')