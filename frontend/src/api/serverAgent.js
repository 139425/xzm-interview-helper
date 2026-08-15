import request from '@/utils/request'

const unwrap = (response) => response?.data?.data ?? response?.data

export const serverAgentApi = {
  async getStatus() {
    return unwrap(await request.get('/admin/server-agent/status'))
  },

  async runAgent(payload) {
    // A bounded ReAct run can legitimately span several AI decisions and commands.
    // Keep this isolated from the normal 90-second API timeout.
    return unwrap(await request.post('/admin/server-agent/run', payload, { timeout: 22 * 60 * 1000 }))
  },

  async executeCommand(payload) {
    return unwrap(await request.post('/admin/server-agent/command', payload))
  },

  async executeTool(payload) {
    return unwrap(await request.post('/admin/server-agent/tools', payload))
  },

  async approve(approvalRequestId) {
    return unwrap(
      await request.post(`/admin/server-agent/approvals/${approvalRequestId}/approve`, {
        confirm: true,
      }),
    )
  },

  async getAudit(limit = 100) {
    return unwrap(await request.get('/admin/server-agent/audit', { params: { limit } }))
  },
}
