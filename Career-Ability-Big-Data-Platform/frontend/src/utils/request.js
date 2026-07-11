import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  } else if ((import.meta.env.DEV || true) && (import.meta.env.VITE_DEV_USERNAME || 'admin') && (import.meta.env.VITE_DEV_PASSWORD || 'admin123')) {
    const credentials = btoa(`${import.meta.env.VITE_DEV_USERNAME || 'admin'}:${import.meta.env.VITE_DEV_PASSWORD || 'admin123'}`)
    config.headers.Authorization = `Basic ${credentials}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (payload?.code !== 200) {
      return Promise.reject(new Error(payload?.message || '请求失败'))
    }
    return payload.data
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      localStorage.removeItem('accessToken')
      window.dispatchEvent(new CustomEvent('auth:expired'))
    }
    const message = error.response?.data?.message || error.message || '网络连接异常'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default request
