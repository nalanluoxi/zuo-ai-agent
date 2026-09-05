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
  pagePermissions?: { pageCode: string; accessLevel: string }[]
  [key: string]: any
}

/** 访问级别权重：READ(只读) < WRITE(修改) < ADMIN(超级管理) */
const LEVEL_RANK: Record<string, number> = { READ: 1, WRITE: 2, ADMIN: 3 }

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('satoken') || '')
  // 刷新页面时从 localStorage 恢复 userInfo
  const savedUserInfo = localStorage.getItem('userInfo')
  const userInfo = ref<any>(savedUserInfo ? JSON.parse(savedUserInfo) : null)
  const tenantId = ref<number | null>(userInfo.value?.tenantId ?? null)

  /**
   * 判断当前用户对指定页面是否拥有不低于 minLevel 的访问级别
   * SUPER_ADMIN 角色直接放行
   */
  function hasPageAccess(pageCode: string, minLevel: 'READ' | 'WRITE' | 'ADMIN' = 'READ'): boolean {
    if (!userInfo.value) return false
    const roles = userInfo.value.roles || userInfo.value.permissions || []
    if (roles.includes('SUPER_ADMIN')) return true
    const perms: { pageCode: string; accessLevel: string }[] = userInfo.value.pagePermissions || []
    const level = perms.find(p => p.pageCode === pageCode)?.accessLevel
    if (!level) return false
    return (LEVEL_RANK[level] || 0) >= (LEVEL_RANK[minLevel] || 1)
  }

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
    } catch (e) {
      // /me 失败时不能保留旧用户的缓存信息，否则会出现"登录了 B 但显示 A 的数据"
      logout()
      throw new Error('登录失败：获取用户信息失败')
    }
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
    tenantId.value = null
    localStorage.removeItem('satoken')
    localStorage.removeItem('userInfo')
  }

  return { token, userInfo, tenantId, login, register, logout, hasPageAccess }
})