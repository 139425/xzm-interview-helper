import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ApplicationTracker from '@/views/ApplicationTracker.vue'

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  updateStatus: vi.fn(),
  remove: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
  confirm: vi.fn(),
}))

vi.mock('@/api/career', () => ({
  applicationApi: {
    list: mocks.list,
    create: mocks.create,
    update: mocks.update,
    updateStatus: mocks.updateStatus,
    remove: mocks.remove,
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: {
    success: mocks.success,
    error: mocks.error,
  },
  ElMessageBox: {
    confirm: mocks.confirm,
  },
}))

const WorkspaceFrameStub = {
  template: '<div><slot name="actions"></slot><slot></slot></div>',
}
const RouterLinkStub = {
  template: '<a><slot></slot></a>',
}

const records = [
  {
    id: 1,
    company: '星河云计算',
    roleName: 'Java 后端工程师',
    applyUrl: 'https://jobs.example.com/apply',
    status: 'APPLIED',
    notes: '官网投递',
    updatedAt: '2026-08-14T12:00:00Z',
  },
  {
    id: 2,
    company: '开源智造',
    roleName: '',
    applyUrl: 'https://jobs.example.com/testing',
    status: 'ASSESSMENT',
    notes: '',
    updatedAt: '2026-08-13T12:00:00Z',
  },
]

describe('ApplicationTracker table view', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.list.mockResolvedValue({
      items: records.map((item) => ({ ...item })),
    })
    mocks.updateStatus.mockResolvedValue(null)
    mocks.confirm.mockResolvedValue('confirm')
  })

  it('renders one application per table row instead of kanban columns', async () => {
    const wrapper = mount(ApplicationTracker, {
      global: {
        stubs: {
          WorkspaceFrame: WorkspaceFrameStub,
          RouterLink: RouterLinkStub,
        },
      },
    })
    await flushPromises()

    expect(wrapper.find('.pipeline').exists()).toBe(false)
    expect(wrapper.find('.application-heading').exists()).toBe(false)
    expect(wrapper.find('.sheet-card').attributes('aria-label')).toBe(
      '投递记录表格',
    )
    expect(wrapper.get('.record-count').text()).toBe('当前 2 条')
    expect(wrapper.text()).not.toContain('APPLICATIONS')
    expect(wrapper.text()).not.toContain('我的投递')
    expect(wrapper.findAll('.application-row')).toHaveLength(2)
    expect(wrapper.get('.application-row').text()).toContain('开源智造')
    expect(wrapper.get('.status-control').classes()).toContain('status--amber')
    expect(wrapper.get('.pipeline-overview__copy').text()).toContain(
      '默认按进度倒序',
    )
  })

  it('requires only company and apply URL in the primary form', async () => {
    const wrapper = mount(ApplicationTracker, {
      global: {
        stubs: {
          WorkspaceFrame: WorkspaceFrameStub,
          RouterLink: RouterLinkStub,
        },
      },
    })
    await flushPromises()

    await wrapper.get('.workspace-primary').trigger('click')

    const requiredInputs = wrapper.findAll('.core-fields input[required]')
    expect(requiredInputs).toHaveLength(2)
    expect(requiredInputs[0].attributes('placeholder')).toContain('字节跳动')
    expect(requiredInputs[1].attributes('type')).toBe('url')
    expect(
      wrapper.get('.optional-grid input').attributes('required'),
    ).toBeUndefined()
  })

  it('updates the colored status inline without requiring legacy optional fields', async () => {
    const wrapper = mount(ApplicationTracker, {
      global: {
        stubs: {
          WorkspaceFrame: WorkspaceFrameStub,
          RouterLink: RouterLinkStub,
        },
      },
    })
    await flushPromises()

    const statusSelect = wrapper.get(
      'select[aria-label="修改 星河云计算 的投递状态"]',
    )
    await statusSelect.setValue('INTERVIEW_2')
    await flushPromises()

    expect(mocks.updateStatus).toHaveBeenCalledWith(1, 'INTERVIEW_2')
    expect(wrapper.get('.status-control').classes()).toContain('status--indigo')
  })

  it('supports multi-select status filters and sends all selected stages', async () => {
    const wrapper = mount(ApplicationTracker, {
      global: {
        stubs: {
          WorkspaceFrame: WorkspaceFrameStub,
          RouterLink: RouterLinkStub,
        },
      },
    })
    await flushPromises()

    await wrapper.get('input[type="checkbox"][value="APPLIED"]').setValue(true)
    await flushPromises()
    await wrapper
      .get('input[type="checkbox"][value="ASSESSMENT"]')
      .setValue(true)
    await flushPromises()

    expect(wrapper.get('.status-filter > summary').text()).toContain(
      '已选 2 项状态',
    )
    expect(mocks.list).toHaveBeenLastCalledWith({
      keyword: '',
      statuses: 'APPLIED,ASSESSMENT',
      sort: 'progress',
    })
    expect(wrapper.findAll('.application-row')).toHaveLength(2)
  })

  it('ignores an older filter response when rapid multi-select requests finish out of order', async () => {
    const wrapper = mount(ApplicationTracker, {
      global: {
        stubs: {
          WorkspaceFrame: WorkspaceFrameStub,
          RouterLink: RouterLinkStub,
        },
      },
    })
    await flushPromises()

    let resolveOlder
    let resolveLatest
    mocks.list
      .mockImplementationOnce(
        () => new Promise((resolve) => { resolveOlder = resolve }),
      )
      .mockImplementationOnce(
        () => new Promise((resolve) => { resolveLatest = resolve }),
      )

    await wrapper.get('input[type="checkbox"][value="APPLIED"]').setValue(true)
    await wrapper.get('input[type="checkbox"][value="ASSESSMENT"]').setValue(true)

    resolveLatest({ items: records.map((item) => ({ ...item })) })
    await flushPromises()
    resolveOlder({ items: [{ ...records[0] }] })
    await flushPromises()

    expect(wrapper.findAll('.application-row')).toHaveLength(2)
    expect(wrapper.text()).toContain('开源智造')
  })
})
