import { MessagePlugin } from 'tdesign-vue-next'
import type { App } from 'vue'

const DEFAULT_ERROR_MESSAGE = '操作失败，请稍后重试'

// store、请求层和全局异常都走同一个格式化入口，避免页面出现英文兜底错误码。
export function formatErrorMessage(error: unknown, fallback = DEFAULT_ERROR_MESSAGE) {
  if (error instanceof Error && error.message) {
    return error.message
  }

  if (typeof error === 'string' && error.trim()) {
    return error
  }

  return fallback
}

export function registerGlobalErrorHandler(app: App) {
  // 这里兜住组件生命周期中的未捕获异常，普通业务失败仍由 store 写入 errorMessage。
  app.config.errorHandler = (error) => {
    MessagePlugin.error(formatErrorMessage(error, '页面运行异常，请稍后重试'))
  }

  window.addEventListener('unhandledrejection', (event) => {
    MessagePlugin.error(formatErrorMessage(event.reason, '请求异常，请稍后重试'))
  })

  window.addEventListener('error', (event) => {
    MessagePlugin.error(event.message || '页面资源加载异常')
  })
}
