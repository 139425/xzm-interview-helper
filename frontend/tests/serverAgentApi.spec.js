import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))

vi.mock('@/utils/request', () => ({ default: mocks }))

import { serverAgentApi } from '@/api/serverAgent'

describe('serverAgentApi', () => {
  beforeEach(() => vi.clearAllMocks())

  it('uses an operation-specific timeout for a multi-step Agent run', async () => {
    mocks.post.mockResolvedValue({ data: { code: 200, data: { status: 'COMPLETED' } } })

    await serverAgentApi.runAgent({ objective: '检查服务', maxSteps: 8 })

    expect(mocks.post).toHaveBeenCalledWith(
      '/admin/server-agent/run',
      { objective: '检查服务', maxSteps: 8 },
      { timeout: 22 * 60 * 1000 },
    )
  })
})
