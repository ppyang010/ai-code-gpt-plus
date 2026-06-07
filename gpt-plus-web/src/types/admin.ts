import type { PageResponse } from '@/types/chat'

/** 后台列表查询统一复用的分页入参。 */
export interface AdminPageQuery {
  pageNo?: number
  pageSize?: number
}

/** 后台管理员登录请求。 */
export interface AdminLoginRequest {
  username: string
  password: string
}

/** 后台管理员鉴权信息。 */
export interface AdminAuthInfo {
  adminUserId: number
  username: string
  nickname: string | null
  email: string | null
  mobile: string | null
  status: number
  lastLoginAt: string | null
  token: string
  tokenType: string
}

/** 后台管理员列表项。 */
export interface AdminUser {
  adminUserId: number
  username: string
  nickname: string | null
  email: string | null
  mobile: string | null
  status: number
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
}

/** 普通用户后台管理列表项。 */
export interface AppUserAdminItem {
  userId: number
  username: string
  nickname: string | null
  email: string | null
  mobile: string | null
  status: number
  balanceAmount?: string | null
  lastLoginAt: string | null
  lastRequestAt?: string | null
  totalTokens?: number | null
  estimatedCost?: string | null
  createdAt: string
  updatedAt: string
}

/** 模型供应商后台管理项。 */
export interface AdminModelProvider {
  providerId: number
  providerCode: string
  providerName: string
  baseUrl: string | null
  apiKeyConfigured: boolean
  apiKeyMaskedPreview?: string | null
  status: number
  sortNo: number
  remark: string | null
  updatedAt: string
}

/** 模型配置后台管理项。 */
export interface AdminModelConfig {
  modelId: number
  providerId: number
  providerCode: string
  providerName: string
  modelCode: string
  modelName: string
  modelType: string
  supportStream: boolean
  supportThinking: boolean
  supportJsonOutput: boolean
  supportVision: boolean
  supportFile: boolean
  contextWindow?: number | null
  maxOutputTokens?: number | null
  temperatureDefault?: number | null
  topPDefault?: number | null
  status: number
  sortNo: number
  updatedAt: string
}

/** 通用附加提示词模板项。 */
export interface PromptTemplate {
  templateId: number
  templateCode: string
  templateName: string
  templateScope: string
  templateContent: string
  status: number
  sortNo: number
  remark: string | null
  updatedAt: string
}

/** 模型调用日志后台列表项。 */
export interface ApiCallLogItem {
  logId: number
  userId: number
  sessionId?: number | null
  messageId?: number | null
  requestId?: string | null
  providerId: number
  providerName?: string | null
  modelId: number
  modelCode?: string | null
  modelName?: string | null
  successFlag: boolean
  httpStatus?: number | null
  latencyMs?: number | null
  promptTokens: number
  completionTokens: number
  totalTokens: number
  estimatedCost: string
  errorCode?: string | null
  errorMessage?: string | null
  requestPayload?: string | null
  responsePayload?: string | null
  createdAt: string
}

/** 每日用量聚合项。 */
export interface UsageDailyItem {
  statDate: string
  userCount: number
  requestCount: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  estimatedCost: string
}

/** 单个用户的用量汇总。 */
export interface UserUsageSummary {
  userId: number
  totalPromptTokens: number
  totalCompletionTokens: number
  totalTokens: number
  estimatedCost: string
  lastRequestAt?: string | null
}

/** 后台分页响应仍沿用现有通用分页结构。 */
export type AdminPageResponse<T> = PageResponse<T>
