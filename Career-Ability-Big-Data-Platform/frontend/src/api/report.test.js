import { beforeEach, describe, expect, it, vi } from 'vitest'

const client = vi.hoisted(() => ({
  delete: vi.fn(),
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('../utils/request', () => ({ default: client }))

import {
  deleteReport,
  downloadReportFile,
  generateReport,
  getReportDownloadUrl,
  getReportPreviewUrl,
  getReportStatus,
  getReportTemplates,
  getReports,
  previewReportFile,
} from './report'

describe('report API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('sends report list, generation, and deletion requests to their scoped endpoints', () => {
    const payload = { templateId: 3, title: 'Monthly report' }

    getReports({ page: 2, size: 10, status: 'COMPLETED' })
    generateReport(payload)
    deleteReport(9)

    getReportTemplates()
    getReportStatus(9)
    expect(client.get).toHaveBeenCalledWith('/reports', {
      params: { page: 2, size: 10, status: 'COMPLETED' },
    })
    expect(client.get).toHaveBeenCalledWith('/reports/templates')
    expect(client.get).toHaveBeenCalledWith('/reports/9/status')
    expect(client.post).toHaveBeenCalledWith('/reports/generate', payload)
    expect(client.delete).toHaveBeenCalledWith('/reports/9')
  })

  it('builds browser URLs with the API prefix for binary report actions', () => {
    expect(getReportDownloadUrl(9)).toBe('/api/reports/9/download')
    expect(getReportPreviewUrl(9)).toBe('/api/reports/9/preview')
  })

  it('requests report binaries through the authenticated client instead of window URLs', () => {
    downloadReportFile(9)
    previewReportFile(9)

    expect(client.get).toHaveBeenCalledWith('/reports/9/download', {
      responseType: 'blob',
      suppressErrorMessage: true,
    })
    expect(client.get).toHaveBeenCalledWith('/reports/9/preview', {
      responseType: 'blob',
      suppressErrorMessage: true,
    })
  })
})
