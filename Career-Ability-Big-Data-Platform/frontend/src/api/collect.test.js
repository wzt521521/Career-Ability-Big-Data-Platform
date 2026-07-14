import { beforeEach, describe, expect, it, vi } from 'vitest'

const client = vi.hoisted(() => ({
  delete: vi.fn(),
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
}))

vi.mock('../utils/request.js', () => ({ default: client }))

import { listTaskLogs, pauseTask, resumeTask, runTask } from './collect.js'

describe('collection runtime API', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    client.delete.mockResolvedValue({ data: null })
    client.get.mockResolvedValue({ data: [] })
    client.post.mockResolvedValue({ data: {} })
    client.put.mockResolvedValue({ data: {} })
  })

  it('uses explicit runtime commands instead of a task status PUT', async () => {
    await runTask(7)
    await pauseTask(7)
    await resumeTask(7)

    expect(client.post).toHaveBeenCalledWith('/collect/task/7/run')
    expect(client.post).toHaveBeenCalledWith('/collect/task/7/pause')
    expect(client.post).toHaveBeenCalledWith('/collect/task/7/resume')
    expect(client.put).not.toHaveBeenCalled()
  })

  it('reads a task-specific bounded log list', async () => {
    await listTaskLogs(7, { page: 2, size: 25 })

    expect(client.get).toHaveBeenCalledWith('/collect/task/7/logs', {
      params: { page: 2, size: 25 },
    })
  })
})
