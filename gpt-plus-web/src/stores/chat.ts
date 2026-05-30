import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

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
  FileAssetItem,
  PageResponse,
  WebSearchMode,
} from '@/types/chat'

type ChatModeCode = 'quick' | 'expert'

export interface SessionPreview {
  id: number
  /** 后端保存的原始标题，编辑弹窗使用它，避免把第一问摘要误当成用户标题提交。 */
  title: string
  /** 页面展示标题；当原始标题还是默认值时，用首条用户问题摘要兜底提升列表可识别性。 */
  displayTitle: string
  /** 后端返回的最近消息摘要，保留给后续列表副标题或预览使用。 */
  lastMessagePreview: string | null
  /** 首条用户问题摘要，默认标题兜底只使用它，避免展示 assistant 回答摘要。 */
  firstUserMessagePreview: string | null
  modeCode: ChatModeCode
  updatedAt: string
  updatedAtRaw: string
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
  attachments?: FileAssetItem[]
  /** 当前消息是否被后端判定为联网搜索回答。 */
  webSearchEnabled?: boolean
  /** 当前消息是否已经真实执行过搜索。 */
  webSearchExecuted?: boolean
  /** 当前消息的联网搜索状态，用于页面轻量提示。 */
  webSearchStatus?: string | null
  retryContent?: string
  retryAttachments?: FileAssetItem[]
}

const EMPTY_SESSION_TITLE = '新会话'
const DEFAULT_SESSION_TITLES = new Set([EMPTY_SESSION_TITLE, 'New Chat', '新对话'])
const CHAT_MODE_STORAGE_KEY = 'gpt-plus.chat.currentMode'
const CHAT_MODEL_STORAGE_KEY = 'gpt-plus.chat.currentModel'
const MESSAGE_STATUS_FAILED = 0
const MESSAGE_STATUS_INTERRUPTED = 2
const MESSAGE_STATUS_GENERATING = 3
const SESSION_PAGE_SIZE = 8

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

/**
 * 判断标题是否仍是系统默认标题，只有默认标题才会启用首条用户问题摘要兜底。
 */
function isDefaultSessionTitle(title: string | null | undefined) {
  return Boolean(title?.trim() && DEFAULT_SESSION_TITLES.has(title.trim()))
}

/**
 * 生成会话卡片展示标题：保留用户手动命名，默认标题优先展示首条用户问题摘要。
 */
function resolveSessionDisplayTitle(title: string | null | undefined, firstUserMessagePreview?: string | null) {
  const normalizedTitle = title?.trim() || '未命名会话'
  const normalizedPreview = firstUserMessagePreview?.trim()
  if (isDefaultSessionTitle(normalizedTitle) && normalizedPreview) {
    return normalizedPreview
  }

  return normalizedTitle
}

/**
 * 判断浏览器中保存的模式值是否仍属于当前支持的快速/思考模式。
 */
function isChatModeCode(value: string | null): value is ChatModeCode {
  return value === 'quick' || value === 'expert'
}

/**
 * 从浏览器本地存储恢复用户上次选择的聊天模式，异常或旧值统一回落到快速模式。
 */
function loadStoredChatMode(): ChatModeCode {
  if (typeof window === 'undefined') {
    return 'quick'
  }

  try {
    const storedMode = window.localStorage.getItem(CHAT_MODE_STORAGE_KEY)
    return isChatModeCode(storedMode) ? storedMode : 'quick'
  } catch {
    return 'quick'
  }
}

/**
 * 将用户选择的聊天模式写入浏览器本地存储，供下次重新进入页面时恢复。
 */
function saveStoredChatMode(mode: ChatModeCode) {
  if (typeof window === 'undefined') {
    return
  }

  try {
    window.localStorage.setItem(CHAT_MODE_STORAGE_KEY, mode)
  } catch {
    // localStorage 可能被隐私模式或浏览器策略禁用，此时只保留当前页面内状态。
  }
}

/**
 * 从浏览器本地存储恢复用户上次选择的模型编码，异常或空值统一回落到默认模型。
 */
function loadStoredChatModel() {
  if (typeof window === 'undefined') {
    return DEFAULT_CHAT_MODEL.code
  }

  try {
    return window.localStorage.getItem(CHAT_MODEL_STORAGE_KEY)?.trim() || DEFAULT_CHAT_MODEL.code
  } catch {
    return DEFAULT_CHAT_MODEL.code
  }
}

/**
 * 将用户选择的模型编码写入浏览器本地存储，供下次重新进入页面时恢复。
 */
function saveStoredChatModel(modelCode: string) {
  if (typeof window === 'undefined' || !modelCode.trim()) {
    return
  }

  try {
    window.localStorage.setItem(CHAT_MODEL_STORAGE_KEY, modelCode)
  } catch {
    // localStorage 可能被隐私模式或浏览器策略禁用，此时只保留当前页面内状态。
  }
}

function normalizeSession(session: ChatSessionListItem): SessionPreview {
  const rawTitle = session.title || '未命名会话'

  return {
    id: session.sessionId,
    title: rawTitle,
    displayTitle: resolveSessionDisplayTitle(rawTitle, session.firstUserMessagePreview),
    lastMessagePreview: session.lastMessagePreview,
    firstUserMessagePreview: session.firstUserMessagePreview,
    modeCode: session.modeCode,
    updatedAt: formatRelativeTime(session.lastMessageAt || session.createdAt),
    updatedAtRaw: session.lastMessageAt || session.createdAt,
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

function normalizeMessage(message: ChatMessageItem, retryContent?: string, retryAttachments?: FileAssetItem[]): ChatMessage {
  return {
    id: message.messageId,
    role: message.role,
    content: message.content,
    modelName: message.modelName,
    createdAt: formatRelativeTime(message.createdAt),
    status: normalizeMessageStatus(message.status),
    attachments: message.attachments || [],
    // 列表刷新时从后端 VO 恢复联网状态，保持页面刷新前后展示一致。
    webSearchEnabled: message.webSearchEnabled,
    webSearchExecuted: message.webSearchExecuted,
    webSearchStatus: message.webSearchStatus,
    retryContent,
    retryAttachments,
  }
}

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<SessionPreview[]>([])
  const currentSessionId = ref<number | null>(null)
  // 顶部模型选择也是用户操作偏好，优先从浏览器本地存储恢复。
  const currentModel = ref(loadStoredChatModel())
  // 顶部快速/思考是用户操作偏好，优先从浏览器本地存储恢复，而不是被历史会话模式覆盖。
  const currentMode = ref<ChatModeCode>(loadStoredChatMode())
  const currentWebSearchMode = ref<WebSearchMode>('auto')
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
  const pendingAttachments = ref<FileAssetItem[]>([])
  const isImageUploading = ref(false)
  const sessionTotal = ref(0)
  const sessionPageNo = ref(1)
  const sessionHasMore = ref(false)
  let activeStreamAbortController: AbortController | null = null
  let activeStreamSessionId: number | null = null
  let activeStreamMessageId: number | null = null
  let activeStopRequested = false

  const activeSession = computed(() => sessions.value.find((session) => session.id === currentSessionId.value) ?? null)
  const currentModelOption = computed(
    () => findChatModelByCode(availableModels.value, currentModel.value) || firstAvailableModel(availableModels.value),
  )

  watch(currentMode, (mode) => {
    saveStoredChatMode(mode)
  })

  watch(currentModel, (modelCode) => {
    saveStoredChatModel(modelCode)
  })

  /**
   * 会话列表刷新后同步当前页头标题，覆盖首条消息自动改名或摘要兜底后的最新展示值。
   */
  function syncCurrentSessionTitleFromList() {
    const currentSession = sessions.value.find((session) => session.id === currentSessionId.value)
    if (currentSession) {
      currentSessionTitle.value = currentSession.displayTitle
    }
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

  async function loadSessions(options?: { append?: boolean }) {
    isSessionsLoading.value = true
    errorMessage.value = ''
    const append = options?.append === true
    const requestPageNo = append ? sessionPageNo.value + 1 : 1
    const requestPageSize = append ? SESSION_PAGE_SIZE : Math.max(SESSION_PAGE_SIZE, sessions.value.length || 0)

    try {
      const response = await http.get<PageResponse<ChatSessionListItem>>('/chat/session/list', {
        pageNo: requestPageNo,
        pageSize: requestPageSize,
      })

      const nextSessions = response.list.map(normalizeSession)
      sessions.value = append ? [...sessions.value, ...nextSessions] : nextSessions
      sessionTotal.value = response.total
      sessionPageNo.value = append
        ? requestPageNo
        : Math.max(1, Math.ceil(Math.max(nextSessions.length, SESSION_PAGE_SIZE) / SESSION_PAGE_SIZE))
      sessionHasMore.value = sessions.value.length < response.total

      const hasCurrentSession = sessions.value.some((session) => session.id === currentSessionId.value)
      if (!hasCurrentSession) {
        currentSessionId.value = sessions.value[0]?.id ?? null
      }
      syncCurrentSessionTitleFromList()
    } catch (error) {
      errorMessage.value = formatErrorMessage(error, '会话列表加载失败')
      sessions.value = []
    } finally {
      isSessionsLoading.value = false
    }
  }

  async function loadMoreSessions() {
    if (isSessionsLoading.value || !sessionHasMore.value) {
      return
    }
    await loadSessions({ append: true })
  }

  async function loadMessages(sessionId: number) {
    isMessagesLoading.value = true
    errorMessage.value = ''

    try {
      const response = await http.get<ChatMessageListResponse>('/chat/message/list', {
        sessionId,
      })

      let lastUserContent = ''
      let lastUserAttachments: FileAssetItem[] = []
      messages.value = response.messageList.map((message) => {
        const retryContent = message.role === 'assistant' ? lastUserContent : message.content
        const retryAttachments = message.role === 'assistant' ? lastUserAttachments : message.attachments || []
        const normalizedMessage = normalizeMessage(message, retryContent, retryAttachments)
        if (message.role === 'user') {
          lastUserContent = message.content
          lastUserAttachments = message.attachments || []
        }
        return normalizedMessage
      })
      const matchedSession = sessions.value.find((session) => session.id === sessionId)
      // 消息列表接口不返回首条用户问题摘要，标题展示沿用会话列表的第一问兜底信息。
      currentSessionTitle.value = resolveSessionDisplayTitle(
        response.title || matchedSession?.title,
        matchedSession?.firstUserMessagePreview,
      )
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
        displayTitle: resolveSessionDisplayTitle(created.title || EMPTY_SESSION_TITLE),
        lastMessagePreview: null,
        firstUserMessagePreview: null,
        modeCode: created.modeCode,
        updatedAt: formatRelativeTime(created.createdAt),
        updatedAtRaw: created.createdAt,
        defaultModelId: created.defaultModelId ?? createdModel.id,
        defaultModelCode: createdModel.code,
        defaultModelName: createdModel.label,
      }

      sessions.value = [nextSession, ...sessions.value]
      sessionTotal.value += 1
      sessionHasMore.value = sessions.value.length < sessionTotal.value
      currentSessionId.value = created.sessionId
      currentSessionTitle.value = nextSession.displayTitle
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
              // 流式开始时立即带上联网状态，避免等刷新列表才出现提示。
              webSearchEnabled: startEvent.webSearchEnabled,
              webSearchExecuted: startEvent.webSearchExecuted,
              webSearchStatus: startEvent.webSearchStatus,
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
        // 新 assistant 占位消息也要保留后端搜索状态，供 ChatView 直接展示。
        webSearchEnabled: startEvent.webSearchEnabled,
        webSearchExecuted: startEvent.webSearchExecuted,
        webSearchStatus: startEvent.webSearchStatus,
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
    retryAttachments?: FileAssetItem[]
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
            retryAttachments: options.retryAttachments || [],
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

  async function sendMessage(content: string, attachments: FileAssetItem[] = pendingAttachments.value.slice()) {
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
    const attachmentIds = attachments.map((attachment) => attachment.fileId)
    pendingAttachments.value = []

    // 先乐观插入用户消息，保证按下发送后页面立即反馈，不等待后端 SSE 首包。
    messages.value = [
      ...messages.value,
      {
        id: userMessageId,
        role: 'user',
        content: trimmed,
        createdAt: 'just now',
        status: 'done',
        attachments,
        retryContent: trimmed,
        retryAttachments: attachments,
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
      attachmentIds,
      enableWebSearch:
        currentWebSearchMode.value === 'enabled' ? true : currentWebSearchMode.value === 'disabled' ? false : undefined,
      webSearchMode: currentWebSearchMode.value,
    }

    return streamAssistantResponse({
      path: '/chat/message/send',
      payload,
      sessionId,
      fallbackAssistantId,
      retryContent: trimmed,
      retryAttachments: attachments,
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

  async function retryMessage(content?: string, attachments?: FileAssetItem[]) {
    const nextContent = (content || lastRetryContent.value).trim()
    if (!nextContent) {
      return false
    }

    return sendMessage(nextContent, attachments || [])
  }

  async function uploadImage(file: File) {
    isImageUploading.value = true
    errorMessage.value = ''

    try {
      const formData = new FormData()
      formData.append('file', file)
      const uploaded = await http.upload<FileAssetItem>('/file/upload/image', formData)
      pendingAttachments.value = [...pendingAttachments.value, uploaded]
      return uploaded
    } catch (error) {
      errorMessage.value = formatErrorMessage(error, '图片上传失败')
      return null
    } finally {
      isImageUploading.value = false
    }
  }

  function removePendingAttachment(fileId: number) {
    pendingAttachments.value = pendingAttachments.value.filter((attachment) => attachment.fileId !== fileId)
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
              displayTitle: resolveSessionDisplayTitle(trimmedTitle, session.firstUserMessagePreview),
            }
          : session,
      )

      if (currentSessionId.value === sessionId) {
        currentSessionTitle.value = resolveSessionDisplayTitle(trimmedTitle, activeSession.value?.firstUserMessagePreview)
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
      sessionTotal.value = Math.max(0, sessionTotal.value - 1)
      sessionHasMore.value = sessions.value.length < sessionTotal.value

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
    currentWebSearchMode,
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
    sessionHasMore,
    sessionPageNo,
    sessionTotal,
    loadMessages,
    loadMoreSessions,
    loadModels,
    loadSessions,
    messages,
    pendingAttachments,
    isImageUploading,
    continueMessage,
    retryMessage,
    uploadImage,
    removePendingAttachment,
    sessionActionSessionId,
    selectSession,
    stopStreamingMessage,
    sendMessage,
    sessions,
    updateSessionTitle,
  }
})
