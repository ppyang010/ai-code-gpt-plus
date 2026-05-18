import { apiBaseUrl, userIdHeaderValue } from '@/config/env'
import type { CommonResponse } from '@/types/chat'

type QueryValue = string | number | boolean | null | undefined

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

async function request<T>(path: string, init?: RequestInit, query?: Record<string, QueryValue>) {
  const response = await fetch(buildUrl(path, query), {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': userIdHeaderValue,
      ...(init?.headers ?? {}),
    },
  })

  if (!response.ok) {
    throw new Error(`HTTP_${response.status}`)
  }

  const payload = (await response.json()) as CommonResponse<T>
  if (payload.code !== 0) {
    throw new Error(payload.message || 'REQUEST_FAILED')
  }

  return payload.data
}

type StreamHandler = (event: string, payload: string) => void

async function stream(path: string, body: unknown, onEvent: StreamHandler) {
  const response = await fetch(buildUrl(path), {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      'X-User-Id': userIdHeaderValue,
    },
    body: JSON.stringify(body),
  })

  const contentType = response.headers.get('content-type') || ''

  if (!response.ok) {
    throw new Error(`HTTP_${response.status}`)
  }

  if (contentType.includes('application/json')) {
    const payload = (await response.json()) as CommonResponse<unknown>
    throw new Error(payload.message || 'STREAM_REQUEST_FAILED')
  }

  if (!response.body) {
    throw new Error('STREAM_BODY_MISSING')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done })

    let separatorIndex = buffer.indexOf('\n\n')
    while (separatorIndex >= 0) {
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
  stream,
}
