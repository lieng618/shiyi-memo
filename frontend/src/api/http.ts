import axios, { type AxiosError } from 'axios'
import { ElMessage } from 'element-plus'

declare module 'axios' {
  interface AxiosRequestConfig {
    skipAuthRedirect?: boolean
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

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorBody>) => {
    const status = error.response?.status
    const message = error.response?.data?.message ?? error.message ?? '请求失败'
    if (status === 401) {
      if (!error.config?.skipAuthRedirect) {
        onUnauthorized.handler()
      }
      return Promise.reject(new Error(message))
    }
    // 401 之外统一弹提示，调用方一般不需要再处理
    if (status !== 0) {
      ElMessage.error(message)
    }
    return Promise.reject(new Error(message))
  },
)
