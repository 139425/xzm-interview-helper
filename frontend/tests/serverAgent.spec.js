import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessageBox } from 'element-plus'
import ServerAgent from '@/views/ServerAgent.vue'

const mocks = vi.hoisted(() => ({
  getStatus: vi.fn(),
  runAgent: vi.fn(),
  executeCommand: vi.fn(),
  executeTool: vi.fn(),
  approve: vi.fn(),
  getAudit: vi.fn(),
}))

vi.mock('@/api/serverAgent', () => ({
  serverAgentApi: mocks,
}))

const WorkspaceFrameStub = {
  template: '<main><slot name="status"/><slot name="actions"/><slot/></main>',
}

describe('ServerAgent admin console', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getStatus.mockResolvedValue({
      agentEnabled: true,
      hostname: 'prod-01',
      executionUser: 'www',
      cpuLoad: 0.18,
      memory: { physicalUsedBytes: 42, physicalTotalBytes: 100 },
      heapUsedBytes: 10,
      heapMaxBytes: 100,
      disk: [{ path: '/', totalBytes: 100, freeBytes: 64 }],
      capabilities: { readFile: true, writeFile: true, createSite: true, serviceStatus: true, serviceRestart: false },
      limits: { maxAgentSteps: 8 },
      uptimeSeconds: 172800,
    })
    mocks.getAudit.mockResolvedValue([])
    mocks.approve.mockResolvedValue({ approvalToken: 'one-use-token' })
  })

  const mountPage = () => mount(ServerAgent, {
    global: {
      stubs: {
        WorkspaceFrame: WorkspaceFrameStub,
        'el-icon': true,
      },
    },
  })

  it('shows live server metrics and runs a ReAct objective', async () => {
    mocks.runAgent.mockResolvedValue({
      status: 'COMPLETED',
      answer: '服务健康，目标已完成。',
      steps: [{ step: 1, rationale: '先检查服务', action: 'status', observation: 'active', status: 'EXECUTED' }],
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('prod-01')
    expect(wrapper.text()).toContain('Agent 已启用')
    expect(wrapper.text()).toContain('42%')
    expect(wrapper.text()).toContain('36%')

    await wrapper.get('.objective-panel textarea').setValue('检查应用健康状态')
    await wrapper.get('.primary-action').trigger('click')
    await flushPromises()

    expect(mocks.runAgent).toHaveBeenCalledWith({ objective: '检查应用健康状态', maxSteps: 6 })
    expect(wrapper.text()).toContain('服务健康，目标已完成。')
    expect(wrapper.text()).toContain('active')
  })

  it('requires one-time approval before retrying a dangerous command', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
    mocks.executeCommand
      .mockResolvedValueOnce({
        status: 'APPROVAL_REQUIRED',
        risk: 'DANGEROUS',
        approvalRequestId: 'approval-1',
        message: '需要确认',
      })
      .mockResolvedValueOnce({ status: 'EXECUTED', output: 'done', exitCode: 0 })

    const wrapper = mountPage()
    await flushPromises()
    await wrapper.findAll('.ops-tabs button')[1].trigger('click')
    await wrapper.get('.command-line input').setValue('systemctl restart demo')
    await wrapper.get('.command-line').trigger('submit')
    await flushPromises()

    expect(mocks.approve).toHaveBeenCalledWith('approval-1')
    expect(mocks.executeCommand).toHaveBeenNthCalledWith(2, {
      command: 'systemctl restart demo',
      timeoutSeconds: 45,
      approvalRequestId: 'approval-1',
      approvalToken: 'one-use-token',
    })
    expect(wrapper.text()).toContain('done')
  })

  it('resumes an approved Agent action with the original objective snapshot', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm')
    mocks.runAgent
      .mockResolvedValueOnce({
        status: 'AWAITING_APPROVAL',
        answer: '等待确认',
        steps: [],
        pendingApproval: {
          status: 'APPROVAL_REQUIRED',
          approvalRequestId: 'approval-agent-1',
          actionSummary: 'service: restart demo.service',
        },
      })
      .mockResolvedValueOnce({ status: 'COMPLETED', answer: '重启并验证完成', steps: [] })

    const wrapper = mountPage()
    await flushPromises()
    const objectiveInput = wrapper.get('.objective-panel textarea')
    await objectiveInput.setValue('重启并验证 demo 服务')
    await wrapper.get('.primary-action').trigger('click')
    await flushPromises()

    expect(objectiveInput.attributes('disabled')).toBeDefined()
    await wrapper.get('.approval-action').trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalledWith(
      expect.stringContaining('service: restart demo.service'),
      expect.any(String),
      expect.any(Object),
    )
    expect(mocks.runAgent).toHaveBeenNthCalledWith(2, {
      objective: '重启并验证 demo 服务',
      maxSteps: 6,
      approvalRequestId: 'approval-agent-1',
      approvalToken: 'one-use-token',
    })
  })
})
