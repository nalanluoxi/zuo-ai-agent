import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '../api/index'

interface LoginResponse {
  code: number
  data: {
    token: string
    loginId: number
    username: string
    nickname: string
  }
  message: string
}

interface UserInfo {
  id: number
  tenantId: number
  roles: string[]
  [key: string]: any
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('satoken') || '')
  const userInfo = ref<any>(null)
  const tenantId = ref<number | null>(null)

  async function login(username: string, password: string) {
    const res = (await authApi.login(username, password)) as unknown as LoginResponse
    const tokenValue = res.data.token
    token.value = tokenValue
    localStorage.setItem('satoken', tokenValue)
    try {
      const meRes = (await authApi.me(tokenValue)) as unknown as { code: number; data: UserInfo }
      const me = meRes.data
      // 使用后端返回的角色列表，不再硬编码判断
      const roles = (me as any).roles || (me as any).permissions || []
      const loginId = (me as any).loginId || me.id
      const info = { ...me, roles, permissions: roles, id: loginId }
      userInfo.value = info
      tenantId.value = me.tenantId
      localStorage.setItem('userInfo', JSON.stringify(info))
    } catch {}
  }

  async function register(username: string, password: string, nickname: string) {
    const res = (await authApi.register(username, password, nickname)) as unknown as LoginResponse
    const tokenValue = res.data.token
    token.value = tokenValue
    localStorage.setItem('satoken', tokenValue)
    const meRes = (await authApi.me(tokenValue)) as unknown as { code: number; data: UserInfo }
    const me = meRes.data
    // 使用后端返回的角色列表，不再硬编码判断
    const roles = (me as any).roles || (me as any).permissions || []
    const loginId = (me as any).loginId || me.id
    userInfo.value = { ...me, roles, permissions: roles, id: loginId }
    tenantId.value = me.tenantId
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('satoken')
  }

  return { token, userInfo, tenantId, login, register, logout }
})