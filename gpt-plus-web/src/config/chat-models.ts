export interface ChatModelOption {
  id: number
  code: string
  label: string
}

export const CHAT_MODEL_OPTIONS: ChatModelOption[] = [
  { id: 1, code: 'deepseek-v4-flash', label: 'DeepSeek V4 Flash' },
  { id: 2, code: 'deepseek-v4-pro', label: 'DeepSeek V4 Pro' },
]

export const DEFAULT_CHAT_MODEL = CHAT_MODEL_OPTIONS[0]

export function findChatModelById(modelId: number | null | undefined) {
  return CHAT_MODEL_OPTIONS.find((option) => option.id === modelId) ?? null
}

export function findChatModelByCode(modelCode: string | null | undefined) {
  return CHAT_MODEL_OPTIONS.find((option) => option.code === modelCode) ?? null
}
