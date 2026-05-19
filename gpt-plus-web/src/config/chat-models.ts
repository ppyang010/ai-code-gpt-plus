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

// 接口异常或本地后端未启动时使用 fallback，保证页面仍能进入基础聊天界面。
export const FALLBACK_CHAT_MODEL_OPTIONS: ChatModelOption[] = [
  { id: 1, code: 'deepseek-v4-flash', label: 'DeepSeek V4 Flash' },
  { id: 2, code: 'deepseek-v4-pro', label: 'DeepSeek V4 Pro' },
]

export const DEFAULT_CHAT_MODEL = FALLBACK_CHAT_MODEL_OPTIONS[0]

export function findChatModelById(models: ChatModelOption[], modelId: number | null | undefined) {
  return models.find((option) => option.id === modelId) ?? null
}

export function findChatModelByCode(models: ChatModelOption[], modelCode: string | null | undefined) {
  return models.find((option) => option.code === modelCode) ?? null
}

export function firstAvailableModel(models: ChatModelOption[]) {
  return models[0] ?? DEFAULT_CHAT_MODEL
}
