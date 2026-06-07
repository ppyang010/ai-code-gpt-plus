import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { clearStoredAdminAuth, loadStoredAdminAuth, saveStoredAdminAuth } from '@/config/admin-auth'
import { formatErrorMessage } from '@/lib/error-handler'
import { adminHttp } from '@/lib/admin-http'
import type { AdminAuthInfo, AdminLoginRequest } from '@/types/admin'

/**
 * 兼容当前 TypeScript 目标版本的错误包装器，同时保留原始异常供调试和错误上报使用。
 */
function buildWrappedError(message: string, cause: unknown) {
  const wrappedError = new Error(message) as Error & { cause?: unknown }
  wrappedError.cause = cause
  return wrappedError
}

/**
 * 后台登录态 store，负责统一管理管理员信息、本地 token 和登录态恢复。
 */
export const useAdminAuthStore = defineStore('admin-auth', () => {
  /** 当前登录管理员的后台鉴权信息。 */
  const authInfo = ref<AdminAuthInfo | null>(loadStoredAdminAuth())
  /** 登录态恢复或网络请求过程中的 loading 状态。 */
  const isLoading = ref(false)
  /** 当前是否已经拥有可用后台 token。 */
  const isAuthenticated = computed(() => Boolean(authInfo.value?.token))
  /** 当前管理员展示名称，优先昵称，其次用户名。 */
  const displayName = computed(() => authInfo.value?.nickname || authInfo.value?.username || '管理员')

  /**
   * 从浏览器本地存储恢复后台登录态，供页面刷新或首次进入后台时复用。
   */
  function restoreStoredAuth() {
    authInfo.value = loadStoredAdminAuth()
  }

  /**
   * 执行后台管理员登录，请求成功后同时刷新内存态和本地存储。
   */
  async function login(request: AdminLoginRequest) {
    isLoading.value = true
    try {
      const nextAuthInfo = await adminHttp.post<AdminAuthInfo>('/admin/auth/login', request)
      authInfo.value = nextAuthInfo
      saveStoredAdminAuth(nextAuthInfo)
      return nextAuthInfo
    } catch (error) {
      throw buildWrappedError(formatErrorMessage(error, '后台登录失败，请稍后重试'), error)
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 刷新当前管理员信息；若 token 失效则同步清理本地登录态。
   */
  async function fetchCurrentAdmin() {
    if (!authInfo.value?.token) {
      return null
    }

    isLoading.value = true
    try {
      const latestAuthInfo = await adminHttp.get<AdminAuthInfo>('/admin/auth/me')
      authInfo.value = {
        ...latestAuthInfo,
        token: latestAuthInfo.token || authInfo.value.token,
        tokenType: latestAuthInfo.tokenType || authInfo.value.tokenType,
      }
      saveStoredAdminAuth(authInfo.value)
      return authInfo.value
    } catch (error) {
      clearAuth()
      throw buildWrappedError(formatErrorMessage(error, '后台登录已失效，请重新登录'), error)
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 执行后台登出；当前后端采用无状态 token，因此失败时也要保证前端本地状态被清掉。
   */
  async function logout() {
    try {
      if (authInfo.value?.token) {
        await adminHttp.post<boolean>('/admin/auth/logout')
      }
    } finally {
      clearAuth()
    }
  }

  /**
   * 清理当前后台登录态，供登出、token 失效和强制回到登录页时复用。
   */
  function clearAuth() {
    authInfo.value = null
    clearStoredAdminAuth()
  }

  return {
    authInfo,
    isLoading,
    isAuthenticated,
    displayName,
    restoreStoredAuth,
    login,
    fetchCurrentAdmin,
    logout,
    clearAuth,
  }
})
