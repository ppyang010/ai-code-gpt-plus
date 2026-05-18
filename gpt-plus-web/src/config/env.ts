const defaultApiBaseUrl = '/api'
const defaultUserId = '1'

export const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || defaultApiBaseUrl).trim()
export const userIdHeaderValue = (import.meta.env.VITE_USER_ID || defaultUserId).trim()
