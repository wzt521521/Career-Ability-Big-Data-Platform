import client from '../utils/request'

const BASE = '/reports'

export function getReportTemplates() {
  return client.get(`${BASE}/templates`)
}

export function generateReport(data) {
  return client.post(`${BASE}/generate`, data)
}

export function getReports(params = {}) {
  return client.get(BASE, { params })
}

export function getReportStatus(id) {
  return client.get(`${BASE}/${id}/status`)
}

export function getReportDownloadUrl(id) {
  // window.open 直接使用浏览器原生请求，Axios baseURL (/api) 不会自动附加
  // 因此需要显式加上 /api 前缀，与 Nginx 反向代理规则匹配
  const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api'
  return `${API_BASE}${BASE}/${id}/download`
}

export function getReportPreviewUrl(id) {
  const API_BASE = import.meta.env.VITE_API_BASE_URL || '/api'
  return `${API_BASE}${BASE}/${id}/preview`
}

export function deleteReport(id) {
  return client.delete(`${BASE}/${id}`)
}
