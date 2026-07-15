import { beforeEach, describe, expect, it, vi } from 'vitest'

const client = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('../utils/request.js', () => ({ default: client }))

import { comparePositions, getPosition, getPositions, getSuggestions } from './positions.js'

describe('position API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    client.get.mockResolvedValue({ data: [] })
  })

  it('uses the bounded comparison endpoint with selected ids', async () => {
    await comparePositions([11, 22])

    expect(client.get).toHaveBeenCalledWith('/positions/compare', { params: { ids: '11,22' } })
  })

  it('keeps search, detail, and suggestion requests on the public position contract', async () => {
    await getPositions({ city: '上海', page: 2 })
    await getPosition(11)
    await getSuggestions('Java')

    expect(client.get).toHaveBeenCalledWith('/positions', { params: { city: '上海', page: 2 } })
    expect(client.get).toHaveBeenCalledWith('/positions/11')
    expect(client.get).toHaveBeenCalledWith('/positions/search/suggest', { params: { keyword: 'Java' } })
  })
})
