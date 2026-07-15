import { beforeEach, describe, expect, it, vi } from 'vitest'

const client = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('../utils/request.js', () => ({ default: client }))

import {
  getCityStats,
  getCompanyStats,
  getDashboard,
  getEducationStats,
  getOverview,
  getPositionStats,
  getSalaryStats,
  getSkillStats,
  getTrendStats,
} from './analytics.js'

describe('analytics API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    client.get.mockResolvedValue({ data: {} })
  })

  it('uses the stable statistics and dashboard endpoints', async () => {
    await Promise.all([
      getOverview(), getDashboard(), getPositionStats(), getSalaryStats(), getSkillStats(),
      getEducationStats(), getCityStats(), getCompanyStats(), getTrendStats(),
    ])

    expect(client.get).toHaveBeenCalledWith('/stats/overview')
    expect(client.get).toHaveBeenCalledWith('/dashboard/all')
    expect(client.get).toHaveBeenCalledWith('/stats/positions')
    expect(client.get).toHaveBeenCalledWith('/stats/salary')
    expect(client.get).toHaveBeenCalledWith('/stats/skills')
    expect(client.get).toHaveBeenCalledWith('/stats/education')
    expect(client.get).toHaveBeenCalledWith('/stats/city')
    expect(client.get).toHaveBeenCalledWith('/stats/company')
    expect(client.get).toHaveBeenCalledWith('/stats/trends')
  })
})
