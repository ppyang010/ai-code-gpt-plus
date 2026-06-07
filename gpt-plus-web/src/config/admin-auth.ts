import type { AdminAuthInfo } from '@/types/admin'

export const ADMIN_AUTH_STORAGE_KEY = 'gpt-plus.admin.auth'

/**
 * 从浏览器本地存储恢复后台登录态；解析失败时回退为空，避免坏数据卡死后台入口。
 */
export function loadStoredAdminAuth() {
  if (typeof window === 'undefined') {
    return null
  }

  try {
    const rawValue = window.localStorage.getItem(ADMIN_AUTH_STORAGE_KEY)
    if (!rawValue) {
      return null
    }
    return JSON.parse(rawValue) as AdminAuthInfo
  } catch {
    return null
  }
}

/**
 * 将后台登录态写入浏览器本地存储，供刷新和重新进入后台时恢复。
 */
export function saveStoredAdminAuth(authInfo: AdminAuthInfo) {
  if (typeof window === 'undefined') {
    return
  }

  try {
    window.localStorage.setItem(ADMIN_AUTH_STORAGE_KEY, JSON.stringify(authInfo))
  } catch {
    // localStorage 不可用时只保留当前页内存状态，不额外打断登录流程。
  }
}

/**
 * 清理浏览器本地存储里的后台登录态，供登出或 token 失效后统一复用。
 */
export function clearStoredAdminAuth() {
  if (typeof window === 'undefined') {
    return
  }

  try {
    window.localStorage.removeItem(ADMIN_AUTH_STORAGE_KEY)
  } catch {
    // localStorage 不可用时无需额外处理。
  }
}
