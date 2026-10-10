import axios, { type AxiosError } from 'axios'
import { ElMessage } from 'element-plus'

declare module 'axios' {
  interface AxiosRequestConfig {
    skipAuthRedirect?: boolean
    /** 静默请求：失败不弹提示也不跳登录页（版本检查用） */
    silent?: boolean
  }
}

export const http = axios.create({
  baseURL: '/api',
  timeout: 30_000,
  withCredentials: true,
})

/** 401 时通知外部跳登录页，避免在拦截器里直接依赖 router 造成循环引用 */
export const onUnauthorized = { handler: () => {} }

export interface ApiErrorBody {
  status?: number
  message?: string
}

/** 带上 HTTP 状态的错误，调用方需要区分具体原因时用得上（比如消息被拒收） */
export class ApiError extends Error {
  readonly status?: number

  constructor(message: string, status?: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorBody>) => {
    const status = error.response?.status
    const message = error.response?.data?.message ?? error.message ?? '请求失败'
    if (status === 401) {
      if (error.config?.silent) {
        return Promise.reject(new ApiError(message, status))
      }
      if (!error.config?.skipAuthRedirect) {
        onUnauthorized.handler()
      }
      return Promise.reject(new ApiError(message, status))
    }
    // 401 之外统一弹提示，调用方一般不需要再处理
    if (status !== 0 && !error.config?.silent) {
      ElMessage.error(message)
    }
    return Promise.reject(new ApiError(message, status))
  },
)
