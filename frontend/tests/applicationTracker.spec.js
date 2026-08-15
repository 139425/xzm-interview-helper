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
    mocks.list.mockResolvedValue({ items: records.map((item) => ({ ...item })) })
    mocks.updateStatus.mockResolvedValue(null)
    mocks.confirm.mockResolvedValue('confirm')
  })

  it('renders one application per table row instead of kanban columns', async () => {
    const wrapper = mount(ApplicationTracker, {
      global: { stubs: { WorkspaceFrame: WorkspaceFrameStub } },
    })
    await flushPromises()

    expect(wrapper.find('.pipeline').exists()).toBe(false)
    expect(wrapper.find('.application-heading').exists()).toBe(false)
    expect(wrapper.find('.sheet-card').attributes('aria-label')).toBe('投递记录表格')
    expect(wrapper.get('.record-count').text()).toBe('共 2 条')
    expect(wrapper.text()).not.toContain('APPLICATIONS')
    expect(wrapper.text()).not.toContain('我的投递')
    expect(wrapper.findAll('.application-row')).toHaveLength(2)
    expect(wrapper.get('.application-row').text()).toContain('星河云计算')
    expect(wrapper.get('.status-control').classes()).toContain('status--blue')
  })

  it('requires only company and apply URL in the primary form', async () => {
    const wrapper = mount(ApplicationTracker, {
      global: { stubs: { WorkspaceFrame: WorkspaceFrameStub } },
    })
    await flushPromises()

    await wrapper.get('.workspace-primary').trigger('click')

    const requiredInputs = wrapper.findAll('.core-fields input[required]')
    expect(requiredInputs).toHaveLength(2)
    expect(requiredInputs[0].attributes('placeholder')).toContain('字节跳动')
    expect(requiredInputs[1].attributes('type')).toBe('url')
    expect(wrapper.get('.optional-grid input').attributes('required')).toBeUndefined()
  })

  it('updates the colored status inline without requiring legacy optional fields', async () => {
    const wrapper = mount(ApplicationTracker, {
      global: { stubs: { WorkspaceFrame: WorkspaceFrameStub } },
    })
    await flushPromises()

    const statusSelect = wrapper.get('select[aria-label="修改 星河云计算 的投递状态"]')
    await statusSelect.setValue('INTERVIEW_2')
    await flushPromises()

    expect(mocks.updateStatus).toHaveBeenCalledWith(1, 'INTERVIEW_2')
    expect(wrapper.get('.status-control').classes()).toContain('status--indigo')
  })
})
