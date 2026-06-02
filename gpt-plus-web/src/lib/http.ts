import { apiBaseUrl, userIdHeaderValue } from '@/config/env'
import type { CommonResponse } from '@/types/chat'

type QueryValue = string | number | boolean | null | undefined

export class ApiError extends Error {
  readonly code?: number
  readonly httpStatus?: number

  constructor(message: string, code?: number, httpStatus?: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
  }
}

function buildUrl(path: string, query?: Record<string, QueryValue>) {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  // Vite 本地开发时 apiBaseUrl=/api，会走 dev server proxy；生产环境也保留同源部署能力。
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

async function request<T>(path: string, init?: RequestInit, query?: Record<string, QueryValue>) {
  const headers = new Headers(init?.headers ?? {})
  headers.set('X-User-Id', userIdHeaderValue)
  if (!(init?.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(buildUrl(path, query), {
    ...init,
    headers,
  })

  if (!response.ok) {
    // 后端异常通常仍会返回 CommonResponse，优先使用中文 message 展示给用户。
    const payload = await parseJsonSafely<CommonResponse<unknown>>(response)
    throw new ApiError(payload?.message || `请求失败（${response.status}）`, payload?.code, response.status)
  }

  const payload = (await response.json()) as CommonResponse<T>
  if (payload.code !== 0) {
    throw new ApiError(payload.message || '请求失败，请稍后重试', payload.code, response.status)
  }

  return payload.data
}

type StreamHandler = (event: string, payload: string) => void

async function stream(path: string, body: unknown, onEvent: StreamHandler, signal?: AbortSignal) {
  const response = await fetch(buildUrl(path), {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      'X-User-Id': userIdHeaderValue,
    },
    signal,
    body: JSON.stringify(body),
  })

  const contentType = response.headers.get('content-type') || ''

  if (!response.ok) {
    const payload = await parseJsonSafely<CommonResponse<unknown>>(response)
    throw new ApiError(payload?.message || `请求失败（${response.status}）`, payload?.code, response.status)
  }

  if (contentType.includes('application/json')) {
    const payload = (await response.json()) as CommonResponse<unknown>
    throw new ApiError(payload.message || '流式请求失败，请稍后重试', payload.code, response.status)
  }

  if (!response.body) {
    throw new ApiError('流式响应为空，请稍后重试', undefined, response.status)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })

    let separatorIndex = buffer.indexOf('\n\n')
    while (separatorIndex >= 0) {
      // SSE 事件可能被网络分片，只有读到完整空行分隔符后才解析。
      const rawEvent = buffer.slice(0, separatorIndex)
      buffer = buffer.slice(separatorIndex + 2)
      parseSseEvent(rawEvent, onEvent)
      separatorIndex = buffer.indexOf('\n\n')
    }

    if (done) {
      if (buffer.trim()) {
        parseSseEvent(buffer, onEvent)
      }
      break
    }
  }
}

async function parseJsonSafely<T>(response: Response) {
  try {
    return (await response.clone().json()) as T
  } catch {
    return null
  }
}

function parseSseEvent(rawEvent: string, onEvent: StreamHandler) {
  const lines = rawEvent
    .split('\n')
    .map((line) => line.trimEnd())
    .filter(Boolean)

  if (lines.length === 0) {
    return
  }

  let eventName = 'message'
  const dataLines: string[] = []

  // 当前后端使用命名事件：message_start / message_reasoning_delta / message_delta / message_end / message_error。
  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
      continue
    }

    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }

  if (dataLines.length > 0) {
    onEvent(eventName, dataLines.join('\n'))
  }
}

export const http = {
  get<T>(path: string, query?: Record<string, QueryValue>) {
    return request<T>(path, { method: 'GET' }, query)
  },
  post<T>(path: string, body?: unknown) {
    return request<T>(path, {
      method: 'POST',
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  },
  upload<T>(path: string, formData: FormData) {
    return request<T>(path, {
      method: 'POST',
      body: formData,
    })
  },
  stream,
}
