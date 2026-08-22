import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  facets: vi.fn(),
  addFromRecruitment: vi.fn(),
  routerReplace: vi.fn(),
  routeQuery: {},
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
}))

vi.mock('@/api/recruitment', () => ({
  recruitmentApi: {
    list: mocks.list,
    facets: mocks.facets,
  },
}))

vi.mock('@/api/career', () => ({
  applicationApi: {
    addFromRecruitment: mocks.addFromRecruitment,
  },
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: mocks.routeQuery }),
  useRouter: () => ({ replace: mocks.routerReplace }),
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: mocks.success,
    error: mocks.error,
    warning: mocks.warning,
  },
}))

vi.mock('@/components/WorkspaceFrame.vue', () => ({
  default: {
    template: '<div><slot name="status"></slot><slot name="actions"></slot><slot></slot></div>',
  },
}))

import RecruitmentDirectory from '@/views/RecruitmentDirectory.vue'

const RouterLinkStub = {
  template: '<a><slot></slot></a>',
}

function mountDirectory() {
  return mount(RecruitmentDirectory, {
    global: {
      stubs: {
        RouterLink: RouterLinkStub,
      },
    },
  })
}

function selectWithOption(wrapper, value) {
  return wrapper
    .findAll('select')
    .find((select) => select.find(`option[value="${value}"]`).exists())
}

describe('RecruitmentDirectory incremental job filters', () => {
  beforeEach(() => {
    vi.useRealTimers()
    vi.clearAllMocks()
    mocks.routeQuery = {
      jobTrack: 'AI应用/Agent',
      deadlineWithinDays: '14',
      sourceKind: 'UNIVERSITY',
      sort: 'deadline',
    }
    mocks.routerReplace.mockResolvedValue(undefined)
    mocks.facets.mockResolvedValue({
      jobTracks: [
        { value: 'AI应用/Agent', count: 12 },
        { value: '软件研发', count: 18 },
      ],
      sourceKinds: [{ value: 'UNIVERSITY', count: 6 }],
      cities: [],
      recruitmentTypes: [],
      companyTypes: [],
    })
    mocks.list.mockResolvedValue({
      items: [
        {
          id: 1,
          company: '星河智能',
          title: '2027 届校园招聘',
          positions: 'AI 应用开发工程师',
          jobTrack: 'AI应用/Agent',
          industry: 'IT/互联网',
          locations: '北京',
          recruitmentType: '秋季校园招聘',
          targetGraduates: '2027 届',
          deadlineDate: '2026-09-05',
          sourceKind: 'UNIVERSITY',
          sourceName: '示例大学就业网',
          announcementUrl: 'https://career.example.edu.cn/jobs/1',
        },
      ],
      total: 1,
      summary: {
        total: 1,
        newToday: 1,
        newWeek: 1,
        sourceCount: 1,
      },
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('hydrates the new query filters without changing the four-stat, nine-column layout', async () => {
    const wrapper = mountDirectory()
    await flushPromises()

    expect(mocks.list).toHaveBeenCalledWith(
      expect.objectContaining({
        jobTrack: 'AI应用/Agent',
        deadlineWithinDays: '14',
        sourceKind: 'UNIVERSITY',
        sort: 'deadline',
      }),
      expect.any(Object),
    )
    expect(selectWithOption(wrapper, 'AI应用/Agent').element.value).toBe(
      'AI应用/Agent',
    )
    expect(selectWithOption(wrapper, '14').element.value).toBe('14')
    expect(selectWithOption(wrapper, 'deadline').element.value).toBe(
      'deadline',
    )
    expect(wrapper.findAll('.jobs-stats > div')).toHaveLength(4)
    expect(wrapper.findAll('[role="columnheader"]')).toHaveLength(9)
    expect(wrapper.get('.jobs-track').text()).toBe('AI应用/Agent')
    expect(wrapper.get('.jobs-source > span').text()).toBe('高校')
    expect(wrapper.get('.jobs-reset').text()).toContain('3')

    wrapper.unmount()
  })

  it('resets the new filters, count and URL query together', async () => {
    const wrapper = mountDirectory()
    await flushPromises()
    vi.useFakeTimers()

    await wrapper.get('.jobs-reset').trigger('click')
    await vi.advanceTimersByTimeAsync(200)
    await flushPromises()

    expect(mocks.routerReplace).toHaveBeenLastCalledWith({ query: {} })
    expect(mocks.list).toHaveBeenLastCalledWith(
      expect.objectContaining({
        jobTrack: '',
        deadlineWithinDays: '',
        sourceKind: '',
        sort: 'latest',
      }),
      expect.any(Object),
    )
    expect(wrapper.find('.jobs-reset').exists()).toBe(false)

    wrapper.unmount()
  })
})
