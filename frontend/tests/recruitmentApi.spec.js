import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/utils/request', () => ({
  default: {
    get: mocks.get,
  },
}))

import { recruitmentApi } from '@/api/recruitment'

describe('recruitmentApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    recruitmentApi.clearCache()
    mocks.get.mockResolvedValue({
      data: {
        data: { items: [], total: 0, page: 1, size: 30, hasMore: false },
      },
    })
  })

  it('normalizes job track, deadline window, university source and deadline sort', async () => {
    await recruitmentApi.list({
      jobTrack: 'AI应用/Agent',
      deadlineWithinDays: '14',
      sourceKind: 'UNIVERSITY',
      sort: 'deadline',
    })

    expect(mocks.get).toHaveBeenCalledTimes(1)
    expect(mocks.get.mock.calls[0][0]).toBe('/api/recruitments')
    expect(mocks.get.mock.calls[0][1].params).toEqual(
      expect.objectContaining({
        jobTrack: 'AI应用/Agent',
        deadlineWithinDays: 14,
        sourceKind: 'UNIVERSITY',
        sort: 'deadline',
      }),
    )
  })

  it('keeps the new filters in the response cache key', async () => {
    await recruitmentApi.list({
      jobTrack: 'AI应用/Agent',
      deadlineWithinDays: 7,
    })
    await recruitmentApi.list({
      jobTrack: 'AI应用/Agent',
      deadlineWithinDays: '7',
    })
    await recruitmentApi.list({
      jobTrack: '软件研发',
      deadlineWithinDays: 7,
    })
    await recruitmentApi.list({
      jobTrack: '软件研发',
      deadlineWithinDays: 14,
    })

    expect(mocks.get).toHaveBeenCalledTimes(3)
  })
})
