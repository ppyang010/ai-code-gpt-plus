import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { CHAT_MODEL_OPTIONS, DEFAULT_CHAT_MODEL, findChatModelByCode, findChatModelById } from '@/config/chat-models'
import { http } from '@/lib/http'
import type {
  ChatSessionDeleteRequest,
  ChatMessageItem,
  ChatMessageListResponse,
  ChatMessageSendRequest,
  ChatSessionUpdateTitleRequest,
  ChatStreamDeltaEvent,
  ChatStreamEndEvent,
  ChatStreamErrorEvent,
  ChatStreamStartEvent,
  ChatSessionCreateResponse,
  ChatSessionListItem,
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
  status?: 'streaming' | 'done' | 'error'
  retryContent?: string
}

const EMPTY_SESSION_TITLE = '新会话'

function resolveSessionModel(session: Pick<SessionPreview, 'defaultModelId' | 'defaultModelCode'>) {
  return (
    findChatModelById(session.defaultModelId) || findChatModelByCode(session.defaultModelCode) || DEFAULT_CHAT_MODEL
  )
}

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

function normalizeMessage(message: ChatMessageItem): ChatMessage {
  return {
    id: message.messageId,
    role: message.role,
    content: message.content,
    modelName: message.modelName,
    createdAt: formatRelativeTime(message.createdAt),
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
  const isCreatingSession = ref(false)
  const isUpdatingSessionTitle = ref(false)
  const isDeletingSession = ref(false)
  const sessionActionSessionId = ref<number | null>(null)
  const errorMessage = ref('')
  const messages = ref<ChatMessage[]>([])
  const currentSessionTitle = ref(EMPTY_SESSION_TITLE)
  const lastRetryContent = ref('')

  const activeSession = computed(() => sessions.value.find((session) => session.id === currentSessionId.value) ?? null)
  const availableModels = CHAT_MODEL_OPTIONS

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
      errorMessage.value = error instanceof Error ? error.message : 'LOAD_SESSIONS_FAILED'
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

      messages.value = response.messageList.map(normalizeMessage)
      currentSessionTitle.value = response.title || '未命名会话'
      currentMode.value = response.modeCode
      const matchedSession = sessions.value.find((session) => session.id === sessionId)
      currentModel.value = resolveSessionModel({
        defaultModelId: response.defaultModelId ?? matchedSession?.defaultModelId ?? null,
        defaultModelCode: matchedSession?.defaultModelCode ?? null,
      }).code
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'LOAD_MESSAGES_FAILED'
      messages.value = []
    } finally {
      isMessagesLoading.value = false
    }
  }

  async function createSession() {
    isCreatingSession.value = true
    errorMessage.value = ''
    const selectedModel = findChatModelByCode(currentModel.value) || DEFAULT_CHAT_MODEL

    try {
      const created = await http.post<ChatSessionCreateResponse>('/chat/session/create', {
        title: '新会话',
        modeCode: currentMode.value,
        defaultModelId: selectedModel.id,
      })

      const createdModel = findChatModelById(created.defaultModelId) || selectedModel

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
      errorMessage.value = error instanceof Error ? error.message : 'CREATE_SESSION_FAILED'
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

  async function sendMessage(content: string) {
    const trimmed = content.trim()
    if (!trimmed || isResponding.value) {
      return false
    }

    const sessionId = await ensureSessionId()
    if (!sessionId) {
      return false
    }

    isResponding.value = true
    errorMessage.value = ''
    lastRetryContent.value = trimmed

    const userMessageId = Date.now()
    const fallbackAssistantId = userMessageId + 1
    let assistantInserted = false

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
        findChatModelByCode(currentModel.value)?.id ?? activeSession.value?.defaultModelId ?? DEFAULT_CHAT_MODEL.id,
    }

    try {
      await http.stream('/chat/message/send', payload, (event, rawPayload) => {
        switch (event) {
          case 'message_start': {
            const startEvent = JSON.parse(rawPayload) as ChatStreamStartEvent
            assistantInserted = true
            messages.value = [
              ...messages.value,
              {
                id: startEvent.messageId || fallbackAssistantId,
                role: 'assistant',
                content: '',
                modelName: startEvent.modelName,
                createdAt: 'streaming',
                status: 'streaming',
                retryContent: trimmed,
              },
            ]
            break
          }
          case 'message_delta': {
            const deltaEvent = JSON.parse(rawPayload) as ChatStreamDeltaEvent
            messages.value = messages.value.map((message) =>
              message.id === deltaEvent.messageId
                ? {
                    ...message,
                    content: `${message.content}${deltaEvent.delta}`,
                  }
                : message,
            )
            break
          }
          case 'message_end': {
            const endEvent = JSON.parse(rawPayload) as ChatStreamEndEvent
            messages.value = messages.value.map((message) =>
              message.id === endEvent.messageId
                ? {
                    ...message,
                    status: 'done',
                    createdAt: formatRelativeTime(endEvent.createdAt),
                  }
                : message,
            )
            break
          }
          case 'message_error': {
            const errorEvent = JSON.parse(rawPayload) as ChatStreamErrorEvent
            const targetId = errorEvent.messageId || fallbackAssistantId
            messages.value = messages.value.map((message) =>
              message.id === targetId
                ? {
                    ...message,
                    status: 'error',
                    content: message.content || errorEvent.errorMessage,
                  }
                : message,
            )
            throw new Error(errorEvent.errorMessage || errorEvent.errorCode)
          }
          default:
            break
        }
      })

      await loadSessions()
      return true
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'SEND_MESSAGE_FAILED'
      if (!assistantInserted) {
        messages.value = [
          ...messages.value,
          {
            id: fallbackAssistantId,
            role: 'assistant',
            content: errorMessage.value,
            createdAt: 'error',
            status: 'error',
            retryContent: trimmed,
          },
        ]
      }
      return false
    } finally {
      isResponding.value = false
    }
  }

  async function retryMessage(content?: string) {
    const nextContent = (content || lastRetryContent.value).trim()
    if (!nextContent) {
      return false
    }

    return sendMessage(nextContent)
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
      errorMessage.value = error instanceof Error ? error.message : 'UPDATE_SESSION_TITLE_FAILED'
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
        currentModel.value = DEFAULT_CHAT_MODEL.code
        currentMode.value = 'quick'
      }

      return true
    } catch (error) {
      errorMessage.value = error instanceof Error ? error.message : 'DELETE_SESSION_FAILED'
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
    isResponding,
    isSessionsLoading,
    isUpdatingSessionTitle,
    loadMessages,
    loadSessions,
    messages,
    retryMessage,
    sessionActionSessionId,
    selectSession,
    sendMessage,
    sessions,
    updateSessionTitle,
  }
})
