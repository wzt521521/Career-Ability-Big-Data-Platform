import { beforeEach, describe, expect, it, vi } from 'vitest'

const client = vi.hoisted(() => ({
  get: vi.fn(),
  put: vi.fn(),
}))

vi.mock('../utils/request', () => ({ default: client }))

import { getMyProfile, saveMyProfile } from './profile'
import { getGapAnalysis, getRecommendations } from './recommend'

describe('recommend API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('requests the current user recommendations with the supplied pagination', () => {
    getRecommendations({ page: 2, size: 20 })

    expect(client.get).toHaveBeenCalledWith('/recommend', {
      params: { page: 2, size: 20 },
    })
  })

  it('uses the gap analysis route for a selected position', () => {
    getGapAnalysis(42)

    expect(client.get).toHaveBeenCalledWith('/recommend/42/gap-analysis')
  })

  it('reads and saves the current users profile through the profile endpoint', () => {
    const profile = { major: 'Computer Science', skills: ['Java'] }

    getMyProfile()
    saveMyProfile(profile)

    expect(client.get).toHaveBeenCalledWith('/profile')
    expect(client.put).toHaveBeenCalledWith('/profile', profile)
  })
})
