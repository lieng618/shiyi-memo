import axios, { type AxiosError } from 'axios'

/** 后台控制台自己的 API 客户端：baseURL 和 Cookie 都和备忘录前台分开 */
export const adminHttp = axios.create({
  baseURL: '/api/admin',
  timeout: 30_000,
  withCredentials: true,
})

export interface ApiErrorBody {
  status?: number
  message?: string
}

export const adminEvents = {
  onUnauthorized: () => {},
}

adminHttp.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorBody>) => {
    const status = error.response?.status
    const message = error.response?.data?.message ?? error.message ?? '请求失败'
    if (status === 401) {
      adminEvents.onUnauthorized()
    }
    return Promise.reject(new Error(message))
  },
)
