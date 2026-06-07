import { apiBaseUrl } from '@/config/env'
import { loadStoredAdminAuth } from '@/config/admin-auth'
import type { CommonResponse } from '@/types/chat'

type QueryValue = string | number | boolean | null | undefined

export class AdminApiError extends Error {
  readonly code?: number
  readonly httpStatus?: number

  constructor(message: string, code?: number, httpStatus?: number) {
    super(message)
    this.name = 'AdminApiError'
    this.code = code
    this.httpStatus = httpStatus
  }
}

function buildUrl(path: string, query?: Record<string, QueryValue>) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  const url = new URL(`${apiBaseUrl}${normalizedPath}`, window.location.origin)

  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== null && value !== undefined && value !== '') {
        url.searchParams.set(key, String(value))
      }
    }
  }

  return url.toString()
}

/**
 * 后台请求统一从本地登录态读取 Bearer token，和用户侧 `X-User-Id` 方案彻底隔离。
 */
function buildHeaders(init?: RequestInit) {
  const headers = new Headers(init?.headers ?? {})
  headers.set('Content-Type', 'application/json')
  const storedAuth = loadStoredAdminAuth()
  if (storedAuth?.token) {
    headers.set('Authorization', `${storedAuth.tokenType || 'Bearer'} ${storedAuth.token}`)
  }
  return headers
}

async function parseJsonSafely<T>(response: Response) {
  try {
    return (await response.clone().json()) as T
  } catch {
    return null
  }
}

async function request<T>(path: string, init?: RequestInit, query?: Record<string, QueryValue>) {
  const response = await fetch(buildUrl(path, query), {
    ...init,
    headers: buildHeaders(init),
  })

  if (!response.ok) {
    const payload = await parseJsonSafely<CommonResponse<unknown>>(response)
    throw new AdminApiError(payload?.message || `请求失败（${response.status}）`, payload?.code, response.status)
  }

  const payload = (await response.json()) as CommonResponse<T>
  if (payload.code !== 0) {
    throw new AdminApiError(payload.message || '请求失败，请稍后重试', payload.code, response.status)
  }

  return payload.data
}

export const adminHttp = {
  get<T>(path: string, query?: Record<string, QueryValue>) {
    return request<T>(path, { method: 'GET' }, query)
  },
  post<T>(path: string, body?: unknown) {
    return request<T>(path, {
      method: 'POST',
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  },
}
