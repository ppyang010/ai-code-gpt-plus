import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import {
  DEFAULT_CHAT_MODEL,
  FALLBACK_CHAT_MODEL_OPTIONS,
  findChatModelByCode,
  findChatModelById,
  firstAvailableModel,
} from '@/config/chat-models'
import { formatErrorMessage } from '@/lib/error-handler'
import { http } from '@/lib/http'
import type {
  ChatMessageItem,
  ChatMessageListResponse,
  ChatMessageRegenerateRequest,
  ChatMessageSendRequest,
  ChatModelOption,
  ChatSessionCreateResponse,
  ChatSessionDeleteRequest,
  ChatSessionListItem,
  ChatSessionUpdateTitleRequest,
  ChatStreamDeltaEvent,
  ChatStreamEndEvent,
  ChatStreamErrorEvent,
  ChatStreamStartEvent,
  PageResponse,
} from '@/types/chat'

export interface SessionPreview {
  id: number
  title: string
  modeCode: 'quick' | 'expert'
  updatedAt: string
  defaultModelId: number | null
  defaultModelCode: string | null
  defaultModelName: string | null
}

export interface ChatMessage {
  id: number
  role: 'assistant' | 'user' | 'system'
  content: string
  modelName?: string | null
  createdAt: string
  status?: 'streaming' | 'done' | 'error' | 'interrupted'
  retryContent?: string
}

const EMPTY_SESSION_TITLE = '新会话'
const MESSAGE_STATUS_FAILED = 0
const MESSAGE_STATUS_INTERRUPTED = 2
const MESSAGE_STATUS_GENERATING = 3

function formatRelativeTime(value: string | null) {
  if (!value) {
    return 'just now'
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

function normalizeSession(session: ChatSessionListItem): SessionPreview {
  return {
    id: session.sessionId,
    title: session.title || '未命名会话',
    modeCode: session.modeCode,
    updatedAt: formatRelativeTime(session.lastMessageAt || session.createdAt),
    defaultModelId: session.defaultModelId,
    defaultModelCode: session.defaultModelCode,
    defaultModelName: session.defaultModelName,
  }
}

function normalizeMessageStatus(status: number): ChatMessage['status'] {
  switch (status) {
    case MESSAGE_STATUS_GENERATING:
      return 'streaming'
    case MESSAGE_STATUS_INTERRUPTED:
      return 'interrupted'
    case MESSAGE_STATUS_FAILED:
      return 'error'
    default:
      return 'done'
  }
}

function normalizeMessage(message: ChatMessageItem, retryContent?: string): ChatMessage {
  return {
    id: message.messageId,
    role: message.role,
    content: message.content,
    modelName: message.modelName,
    createdAt: formatRelativeTime(message.createdAt),
    status: normalizeMessageStatus(message.status),
    retryContent,
  }
}

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<SessionPreview[]>([])
  const currentSessionId = ref<number | null>(null)
  const currentModel = ref(DEFAULT_CHAT_MODEL.code)
  const currentMode = ref<'quick' | 'expert'>('quick')
  const isResponding = ref(false)
  const isBootstrapping = ref(false)
  const isSessionsLoading = ref(false)
  const isMessagesLoading = ref(false)
  const isModelsLoading = ref(false)
  const isCreatingSession = ref(false)
  const isUpdatingSessionTitle = ref(false)
  const isDeletingSession = ref(false)
  const sessionActionSessionId = ref<number | null>(null)
  const errorMessage = ref('')
  const messages = ref<ChatMessage[]>([])
  // 模型列表优先从后端加载；fallback 只用于后端未启动或模型接口异常时保持页面可用。
  const availableModels = ref<ChatModelOption[]>([...FALLBACK_CHAT_MODEL_OPTIONS])
  const currentSessionTitle = ref(EMPTY_SESSION_TITLE)
  const lastRetryContent = ref('')
  let activeStreamAbortController: AbortController | null = null
  let activeStreamSessionId: number | null = null
  let activeStreamMessageId: number | null = null
  let activeStopRequested = false

  const activeSession = computed(() => sessions.value.find((session) => session.id === currentSessionId.value) ?? null)
  const currentModelOption = computed(
    () => findChatModelByCode(availableModels.value, currentModel.value) || firstAvailableModel(availableModels.value),
  )

  function resolveSessionModel(session: Pick<SessionPreview, 'defaultModelId' | 'defaultModelCode'>) {
    return (
      findChatModelById(availableModels.value, session.defaultModelId) ||
      findChatModelByCode(availableModels.value, session.defaultModelCode) ||
      firstAvailableModel(availableModels.value)
    )
  }

  async function loadModels() {
    isModelsLoading.value = true

    try {
      const models = await http.get<ChatModelOption[]>('/model/list')
      if (models.length > 0) {
        availableModels.value = models
      }

      if (!findChatModelByCode(availableModels.value, currentModel.value)) {
        // 后端模型配置可能变化，当前选中的旧模型不存在时自动切到第一个可用模型。
        currentModel.value = firstAvailableModel(availableModels.value).code
      }
    } catch (error) {
      errorMessage.value = formatErrorMessage(error, '模型列表加载失败，已使用本地默认模型')
      availableModels.value = [...FALLBACK_CHAT_MODEL_OPTIONS]
      currentModel.value = firstAvailableModel(availableModels.value).code
    } finally {
      isModelsLoading.value = false
    }
  }

  async function loadSessions() {
    isSessionsLoading.value = true
    errorMessage.value = ''

    try {
      const response = await http.get<PageResponse<ChatSessionListItem>>('/chat/session/list', {
        pageNo: 1,
        pageSize: 50,
      })

      sessions.value = response.list.map(normalizeSession)

      const hasCurrentSession = sessions.value.some((session) => session.id === currentSessionId.value)
      if (!hasCurrentSession) {
        currentSessionId.value = sessions.value[0]?.id ?? null
      }
    } catch (error) {
      errorMessage.value = formatErrorMessage(error, '会话列表加载失败')
      sessions.value = []
    } finally {
      isSessionsLoading.value = false
    }
  }

  async function loadMessages(sessionId: number) {
    isMessagesLoading.value = true
    errorMessage.value = ''

    try {
      const response = await http.get<ChatMessageListResponse>('/chat/message/list', {
        sessionId,
      })

      let lastUserContent = ''
      messages.value = response.messageList.map((message) => {
        const retryContent = message.role === 'assistant' ? lastUserContent : message.content
        const normalizedMessage = normalizeMessage(message, retryContent)
        if (message.role === 'user') {
          lastUserContent = message.content
        }
        return normalizedMessage
      })
      currentSessionTitle.value = response.title || '未命名会话'
      currentMode.value = response.modeCode
      const matchedSession = sessions.value.find((session) => session.id === sessionId)
      // 消息列表接口只返回 defaultModelId，模型 code/name 优先从会话列表或模型接口缓存补齐。
      currentModel.value = resolveSessionModel({
        defaultModelId: response.defaultModelId ?? matchedSession?.defaultModelId ?? null,
        defaultModelCode: matchedSession?.defaultModelCode ?? null,
      }).code
    } catch (error) {
      errorMessage.value = formatErrorMessage(error, '消息列表加载失败')
      messages.value = []
    } finally {
      isMessagesLoading.value = false
    }
  }

  async function createSession() {
    isCreatingSession.value = true
    errorMessage.value = ''
    const selectedModel = currentModelOption.value

    try {
      const created = await http.post<ChatSessionCreateResponse>('/chat/session/create', {
        title: '新会话',
        modeCode: currentMode.value,
        defaultModelId: selectedModel.id,
      })

      const createdModel = findChatModelById(availableModels.value, created.defaultModelId) || selectedModel

      const nextSession: SessionPreview = {
        id: created.sessionId,
        title: created.title || EMPTY_SESSION_TITLE,
        modeCode: created.modeCode,
        updatedAt: formatRelativeTime(created.createdAt),
        defaultModelId: created.defaultModelId ?? createdModel.id,
        defaultModelCode: createdModel.code,
        defaultModelName: createdModel.label,
      }

      sessions.value = [nextSession, ...sessions.value]
      currentSessionId.value = created.sessionId
      currentSessionTitle.value = nextSession.title
      currentModel.value = createdModel.code
      messages.value = []
      await loadMessages(created.sessionId)
      return created.sessionId
    } catch (error) {
      errorMessage.value = formatErrorMessage(error, '创建会话失败')
      return null
    } finally {
      isCreatingSession.value = false
    }
  }

  async function selectSession(sessionId: number) {
    if (currentSessionId.value === sessionId && messages.value.length > 0) {
      return
    }

    currentSessionId.value = sessionId
    await loadMessages(sessionId)
  }

  async function bootstrap() {
    isBootstrapping.value = true
    // 启动顺序是模型 -> 会话 -> 消息，避免后续会话默认模型无法映射到下拉选项。
    await loadModels()
    await loadSessions()

    if (currentSessionId.value) {
      await loadMessages(currentSessionId.value)
    }

    isBootstrapping.value = false
  }

  async function ensureSessionId() {
    if (currentSessionId.value) {
      return currentSessionId.value
    }

    return createSession()
  }

  function isAbortError(error: unknown) {
    return error instanceof Error && error.name === 'AbortError'
  }

  function getActiveStreamMessage() {
    if (!activeStreamMessageId) {
      return null
    }
    return messages.value.find((message) => message.id === activeStreamMessageId) ?? null
  }

  function upsertAssistantStreamMessage(startEvent: ChatStreamStartEvent, fallbackAssistantId: number | null, retryContent?: string) {
    const nextMessageId = startEvent.messageId || fallbackAssistantId || Date.now()
    activeStreamMessageId = nextMessageId
    const existingMessage = messages.value.find((message) => message.id === nextMessageId)

    if (existingMessage) {
      messages.value = messages.value.map((message) =>
        message.id === nextMessageId
          ? {
              ...message,
              modelName: startEvent.modelName,
              createdAt: 'streaming',
              status: 'streaming',
            }
          : message,
      )
      return
    }

    messages.value = [
      ...messages.value,
      {
        id: nextMessageId,
        role: 'assistant',
        content: '',
        modelName: startEvent.modelName,
        createdAt: 'streaming',
        status: 'streaming',
        retryContent,
      },
    ]
  }

  function markAssistantMessageStatus(
    messageId: number,
    status: ChatMessage['status'],
    patch?: Partial<Pick<ChatMessage, 'content' | 'createdAt' | 'modelName'>>,
  ) {
    messages.value = messages.value.map((message) =>
      message.id === messageId
        ? {
            ...message,
            status,
            ...patch,
          }
        : message,
    )
  }

  function scheduleMessageRefresh(sessionId: number, delayMs = 450) {
    window.setTimeout(() => {
      if (currentSessionId.value === sessionId && !isMessagesLoading.value) {
        void loadMessages(sessionId)
      }
    }, delayMs)
  }

  async function streamAssistantResponse(options: {
    path: '/chat/message/send' | '/chat/message/regenerate'
    payload: ChatMessageSendRequest | ChatMessageRegenerateRequest
    sessionId: number
    fallbackAssistantId?: number | null
    retryContent?: string
  }) {
    isResponding.value = true
    errorMessage.value = ''
    activeStreamAbortController = new AbortController()
    activeStreamSessionId = options.sessionId
    activeStreamMessageId = options.fallbackAssistantId ?? null
    activeStopRequested = false

    let assistantInserted = false
    let streamErrored = false

    try {
      await http.stream(
        options.path,
        options.payload,
        (event, rawPayload) => {
          switch (event) {
            case 'message_start': {
              const startEvent = JSON.parse(rawPayload) as ChatStreamStartEvent
              assistantInserted = true
              upsertAssistantStreamMessage(startEvent, options.fallbackAssistantId ?? null, options.retryContent)
              break
            }
            case 'message_delta': {
              const deltaEvent = JSON.parse(rawPayload) as ChatStreamDeltaEvent
              activeStreamMessageId = deltaEvent.messageId
              messages.value = messages.value.map((message) =>
                message.id === deltaEvent.messageId
                  ? {
                      ...message,
                      status: 'streaming',
                      content: `${message.content}${deltaEvent.delta}`,
                    }
                  : message,
              )
              break
            }
            case 'message_end': {
              const endEvent = JSON.parse(rawPayload) as ChatStreamEndEvent
              activeStreamMessageId = endEvent.messageId
              markAssistantMessageStatus(endEvent.messageId, 'done', {
                createdAt: formatRelativeTime(endEvent.createdAt),
              })
              break
            }
            case 'message_error': {
              streamErrored = true
              const errorEvent = JSON.parse(rawPayload) as ChatStreamErrorEvent
              const targetId = errorEvent.messageId || activeStreamMessageId || options.fallbackAssistantId || Date.now()
              activeStreamMessageId = targetId
              markAssistantMessageStatus(targetId, 'error', {
                content: getActiveStreamMessage()?.content || errorEvent.errorMessage,
              })
              throw new Error(errorEvent.errorMessage || errorEvent.errorCode)
            }
            default:
              break
          }
        },
        activeStreamAbortController.signal,
      )

      await loadSessions()
      return true
    } catch (error) {
      const currentAssistantMessage = getActiveStreamMessage()
      const hasPartialContent = Boolean(currentAssistantMessage?.content?.trim())

      if (!streamErrored && (activeStopRequested || (assistantInserted && hasPartialContent))) {
        if (currentAssistantMessage) {
          markAssistantMessageStatus(currentAssistantMessage.id, 'interrupted')
        }
        scheduleMessageRefresh(options.sessionId)
        if (!activeStopRequested) {
          errorMessage.value = '回答连接已中断，可点击继续生成'
        }
        return false
      }

      errorMessage.value = formatErrorMessage(error, '消息发送失败')
      if (!assistantInserted && options.fallbackAssistantId) {
        messages.value = [
          ...messages.value,
          {
            id: options.fallbackAssistantId,
            role: 'assistant',
            content: errorMessage.value,
            createdAt: 'error',
            status: 'error',
            retryContent: options.retryContent,
          },
        ]
      }
      if (isAbortError(error) && activeStopRequested && activeStreamSessionId) {
        scheduleMessageRefresh(activeStreamSessionId)
      }
      return false
    } finally {
      isResponding.value = false
      activeStreamAbortController = null
      activeStreamSessionId = null
      activeStreamMessageId = null
      activeStopRequested = false
    }
  }

  async function sendMessage(content: string) {
    const trimmed = content.trim()
    if (!trimmed || isResponding.value) {
      return false
    }

    const sessionId = await ensureSessionId()
    if (!sessionId) {
      return false
    }

    errorMessage.value = ''
    lastRetryContent.value = trimmed

    const userMessageId = Date.now()
    const fallbackAssistantId = userMessageId + 1

    // 先乐观插入用户消息，保证按下发送后页面立即反馈，不等待后端 SSE 首包。
    messages.value = [
      ...messages.value,
      {
        id: userMessageId,
        role: 'user',
        content: trimmed,
        createdAt: 'just now',
        status: 'done',
        retryContent: trimmed,
      },
    ]

    const payload: ChatMessageSendRequest = {
      sessionId,
      content: trimmed,
      modeCode: currentMode.value,
      modelId:
        findChatModelByCode(availableModels.value, currentModel.value)?.id ??
        activeSession.value?.defaultModelId ??
        DEFAULT_CHAT_MODEL.id,
    }

    return streamAssistantResponse({
      path: '/chat/message/send',
      payload,
      sessionId,
      fallbackAssistantId,
      retryContent: trimmed,
    })
  }

  async function continueMessage(messageId: number) {
    if (isResponding.value || !currentSessionId.value) {
      return false
    }

    const payload: ChatMessageRegenerateRequest = {
      sessionId: currentSessionId.value,
      regenerateMessageId: messageId,
      modeCode: currentMode.value,
      modelId:
        findChatModelByCode(availableModels.value, currentModel.value)?.id ??
        activeSession.value?.defaultModelId ??
        DEFAULT_CHAT_MODEL.id,
    }

    const retryContent = messages.value.find((message) => message.id === messageId)?.retryContent
    return streamAssistantResponse({
      path: '/chat/message/regenerate',
      payload,
      sessionId: currentSessionId.value,
      fallbackAssistantId: messageId,
      retryContent,
    })
  }

  async function retryMessage(content?: string) {
    const nextContent = (content || lastRetryContent.value).trim()
    if (!nextContent) {
      return false
    }

    return sendMessage(nextContent)
  }

  function stopStreamingMessage() {
    if (!activeStreamAbortController || !isResponding.value) {
      return false
    }

    activeStopRequested = true
    activeStreamAbortController.abort()
    return true
  }

  function clearErrorMessage() {
    errorMessage.value = ''
  }

  async function updateSessionTitle(sessionId: number, title: string) {
    const trimmedTitle = title.trim()
    if (!trimmedTitle) {
      errorMessage.value = '会话标题不能为空'
      return false
    }

    isUpdatingSessionTitle.value = true
    sessionActionSessionId.value = sessionId
    errorMessage.value = ''

    const payload: ChatSessionUpdateTitleRequest = {
      sessionId,
      title: trimmedTitle,
    }

    try {
      await http.post<boolean>('/chat/session/update-title', payload)
      sessions.value = sessions.value.map((session) =>
        session.id === sessionId
          ? {
              ...session,
              title: trimmedTitle,
            }
          : session,
      )

      if (currentSessionId.value === sessionId) {
        currentSessionTitle.value = trimmedTitle
      }
      return true
    } catch (error) {
      errorMessage.value = formatErrorMessage(error, '更新会话标题失败')
      return false
    } finally {
      isUpdatingSessionTitle.value = false
      sessionActionSessionId.value = null
    }
  }

  async function deleteSession(sessionId: number) {
    isDeletingSession.value = true
    sessionActionSessionId.value = sessionId
    errorMessage.value = ''

    const payload: ChatSessionDeleteRequest = {
      sessionId,
    }

    try {
      await http.post<boolean>('/chat/session/delete', payload)

      const remainingSessions = sessions.value.filter((session) => session.id !== sessionId)
      const deletedCurrentSession = currentSessionId.value === sessionId

      sessions.value = remainingSessions

      if (!deletedCurrentSession) {
        return true
      }

      const nextSessionId = remainingSessions[0]?.id ?? null
      currentSessionId.value = nextSessionId

      if (nextSessionId) {
        await loadMessages(nextSessionId)
      } else {
        messages.value = []
        currentSessionTitle.value = EMPTY_SESSION_TITLE
        currentModel.value = firstAvailableModel(availableModels.value).code
        currentMode.value = 'quick'
      }

      return true
    } catch (error) {
      errorMessage.value = formatErrorMessage(error, '删除会话失败')
      return false
    } finally {
      isDeletingSession.value = false
      sessionActionSessionId.value = null
    }
  }

  return {
    activeSession,
    bootstrap,
    clearErrorMessage,
    createSession,
    currentMode,
    currentModel,
    currentSessionId,
    currentSessionTitle,
    deleteSession,
    errorMessage,
    availableModels,
    isBootstrapping,
    isCreatingSession,
    isDeletingSession,
    isMessagesLoading,
    isModelsLoading,
    isResponding,
    isSessionsLoading,
    isUpdatingSessionTitle,
    loadMessages,
    loadModels,
    loadSessions,
    messages,
    continueMessage,
    retryMessage,
    sessionActionSessionId,
    selectSession,
    stopStreamingMessage,
    sendMessage,
    sessions,
    updateSessionTitle,
  }
})
