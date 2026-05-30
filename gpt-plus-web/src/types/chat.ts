export interface CommonResponse<T> {
  code: number
  message: string
  data: T
}

export type WebSearchMode = 'disabled' | 'enabled' | 'auto'

export interface FileAssetItem {
  fileId: number
  fileName: string
  contentType: string
  fileSize: number
  fileUrl: string
  thumbnailUrl?: string | null
}

export interface PageResponse<T> {
  list: T[]
  total: number
  pageNo: number
  pageSize: number
}

export interface ChatModelOption {
  id: number
  code: string
  label: string
  modelType?: string | null
  supportStream?: boolean
  supportThinking?: boolean
  supportJsonOutput?: boolean
  supportVision?: boolean
  supportFile?: boolean
  contextWindow?: number | null
  maxOutputTokens?: number | null
}

export interface ChatSessionListItem {
  sessionId: number
  title: string
  modeCode: 'quick' | 'expert'
  defaultModelId: number | null
  defaultModelCode: string | null
  defaultModelName: string | null
  lastMessagePreview: string | null
  /** 首条用户问题摘要，用于默认标题会话的标题展示兜底。 */
  firstUserMessagePreview: string | null
  lastMessageAt: string | null
  createdAt: string
}

export interface ChatSessionCreateRequest {
  title: string
  modeCode: 'quick' | 'expert'
  defaultModelId?: number | null
  systemPrompt?: string | null
}

export interface ChatSessionCreateResponse {
  sessionId: number
  title: string
  modeCode: 'quick' | 'expert'
  defaultModelId: number | null
  createdAt: string
}

export interface ChatSessionUpdateTitleRequest {
  sessionId: number
  title: string
}

export interface ChatSessionDeleteRequest {
  sessionId: number
}

export interface ChatMessageItem {
  messageId: number
  role: 'assistant' | 'user' | 'system'
  content: string
  contentFormat: string
  seqNo: number
  modelId: number | null
  modelCode: string | null
  modelName: string | null
  finishReason: string | null
  status: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  attachments?: FileAssetItem[]
  /** 后端从 assistant metadata 解析出的联网搜索开关状态。 */
  webSearchEnabled?: boolean
  /** 后端从 assistant metadata 解析出的真实搜索执行状态。 */
  webSearchExecuted?: boolean
  /** 联网搜索状态，用于消息列表展示轻量提示。 */
  webSearchStatus?: string | null
  createdAt: string
}

export interface ChatMessageListResponse {
  sessionId: number
  title: string
  modeCode: 'quick' | 'expert'
  defaultModelId: number | null
  messageList: ChatMessageItem[]
}

export interface ChatMessageSendRequest {
  sessionId: number
  content: string
  modelId?: number | null
  modeCode?: 'quick' | 'expert'
  systemPrompt?: string | null
  attachmentIds?: number[]
  enableWebSearch?: boolean
  webSearchMode?: WebSearchMode
}

export interface ChatMessageRegenerateRequest {
  sessionId: number
  regenerateMessageId: number
  modelId?: number | null
  modeCode?: 'quick' | 'expert'
}

export interface ChatStreamStartEvent {
  sessionId: number
  messageId: number
  role: 'assistant'
  modelId: number | null
  modelCode: string | null
  modelName: string | null
  /** 流式开始时同步的联网搜索开关状态。 */
  webSearchEnabled?: boolean
  /** 流式开始时同步的真实搜索执行状态。 */
  webSearchExecuted?: boolean
  /** 流式开始时同步的联网搜索状态。 */
  webSearchStatus?: string | null
}

export interface ChatStreamDeltaEvent {
  messageId: number
  delta: string
}

export interface ChatStreamEndEvent {
  messageId: number
  finishReason: string | null
  promptTokens: number
  completionTokens: number
  totalTokens: number
  createdAt: string
}

export interface ChatStreamErrorEvent {
  messageId: number | null
  errorCode: string
  errorMessage: string
}
