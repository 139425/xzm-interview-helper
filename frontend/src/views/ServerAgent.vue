<template>
  <WorkspaceFrame
    mode="serverAgent"
    eyebrow="ADMIN OPERATIONS"
    title="服务器 Agent"
    mark="OP"
  >
    <template #status>
      <span class="live-status" :class="{ online: status.agentEnabled, error: statusError }">
        <i></i>
        {{ statusError ? '服务器不可达' : (status.agentEnabled ? 'Agent 已启用' : 'Agent 已锁定') }}
      </span>
    </template>

    <template #actions>
      <button class="icon-action" type="button" :disabled="statusLoading" title="刷新状态" aria-label="刷新服务器状态" @click="refreshAll">
        <el-icon :class="{ 'is-loading': statusLoading }"><Refresh /></el-icon>
      </button>
    </template>

    <main class="ops-page">
      <section class="ops-hero">
        <div class="hero-copy">
          <span class="security-kicker"><el-icon><Lock /></el-icon> 仅管理员可访问</span>
          <h1>把目标交给 Agent，<br /><em>让服务器完成工作。</em></h1>
          <p>观察、推理、执行、校验都留有记录。高风险动作必须由你二次确认。</p>
        </div>

        <div class="host-plate" aria-label="服务器运行状态">
          <div class="host-plate__head">
            <div>
              <small>ACTIVE HOST</small>
              <strong>{{ status.hostname || '等待连接' }}</strong>
            </div>
            <span class="host-chip">{{ status.executionUser || '受限进程' }}</span>
          </div>

          <div class="metric-grid">
            <article>
              <span>CPU LOAD</span>
              <strong>{{ formatPercent(status.cpuLoad) }}</strong>
              <i><b :style="metricWidth(status.cpuLoad)"></b></i>
            </article>
            <article>
              <span>MEMORY</span>
              <strong>{{ formatPercent(memoryPercent) }}</strong>
              <i><b :style="metricWidth(memoryPercent)"></b></i>
            </article>
            <article>
              <span>DISK</span>
              <strong>{{ formatPercent(diskPercent) }}</strong>
              <i><b :style="metricWidth(diskPercent)"></b></i>
            </article>
            <article>
              <span>UPTIME</span>
              <strong>{{ formatUptime(status.uptimeSeconds) }}</strong>
              <i class="uptime-line"><b></b></i>
            </article>
          </div>
        </div>
      </section>

      <section v-if="statusLoaded && statusError" class="locked-banner error-banner" role="alert">
        <el-icon><Warning /></el-icon>
        <div>
          <strong>暂时无法连接服务器控制面</strong>
          <p>{{ statusError }}。页面不会把连接失败误判为功能关闭，请刷新后重试。</p>
        </div>
      </section>

      <section v-else-if="statusLoaded && !status.agentEnabled" class="locked-banner" role="status">
        <el-icon><Lock /></el-icon>
        <div>
          <strong>服务器执行能力当前处于关闭状态</strong>
          <p>页面仍可查看，但执行请求会被服务端拒绝。需由部署环境显式开启，不能由浏览器自行解锁。</p>
        </div>
      </section>

      <nav class="ops-tabs" role="tablist" aria-label="服务器控制台">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.id"
          :class="{ active: activeTab === tab.id }"
          @click="activeTab = tab.id"
        >
          <span>{{ tab.index }}</span>{{ tab.label }}
        </button>
      </nav>

      <section v-if="activeTab === 'agent'" class="agent-workbench">
        <div class="panel objective-panel">
          <header class="panel-heading">
            <div>
              <span>REACT RUN</span>
              <h2>告诉 Agent 最终目标</h2>
            </div>
            <span class="step-limit">最多 {{ maxSteps }} 步</span>
          </header>

          <textarea
            v-model.trim="objective"
            :disabled="agentRunning || Boolean(agentPendingApproval)"
            rows="6"
            maxlength="2000"
            placeholder="例如：检查当前应用健康状态，定位最近一次失败原因，修复后重新验证。"
            @keydown.ctrl.enter.prevent="runAgent()"
            @keydown.meta.enter.prevent="runAgent()"
          ></textarea>

          <div class="objective-footer">
            <label>
              <span>执行步数</span>
              <input v-model.number="maxSteps" type="range" min="2" :max="maxAgentSteps" />
              <b>{{ maxSteps }}</b>
            </label>
            <button class="primary-action" type="button" :disabled="agentRunning || Boolean(agentPendingApproval) || !objective || !status.agentEnabled" @click="runAgent()">
              <el-icon v-if="agentRunning" class="is-loading"><Loading /></el-icon>
              <el-icon v-else><Promotion /></el-icon>
              {{ agentRunning ? 'Agent 正在执行' : '开始执行' }}
            </button>
          </div>

          <div class="guardrails">
            <span><i></i> 命令限时</span>
            <span><i></i> 输出脱敏</span>
            <span><i></i> 单次审批</span>
            <span><i></i> 全程审计</span>
          </div>
        </div>

        <div class="panel trace-panel">
          <header class="panel-heading">
            <div>
              <span>EXECUTION TRACE</span>
              <h2>推理与执行轨迹</h2>
            </div>
            <span v-if="agentResult?.status" class="run-state" :data-state="agentResult.status">
              {{ agentStateLabel(agentResult.status) }}
            </span>
          </header>

          <div v-if="agentRunning" class="agent-thinking">
            <span class="orbit"><i></i></span>
            <div><strong>正在观察服务器并规划下一步</strong><small>请保持页面打开，过程完成后会自动写入审计记录。</small></div>
          </div>

          <ol v-else-if="agentSteps.length" class="trace-list">
            <li v-for="(step, index) in agentSteps" :key="`${index}-${step.action || 'step'}`">
              <div class="trace-index">{{ String(index + 1).padStart(2, '0') }}</div>
              <div class="trace-body">
                <div class="trace-meta">
                  <span>{{ step.status || 'OBSERVED' }}</span>
                  <small>{{ step.tool || step.action || 'Agent step' }}</small>
                </div>
                <p v-if="step.rationale">{{ step.rationale }}</p>
                <pre v-if="step.observation">{{ step.observation }}</pre>
              </div>
            </li>
          </ol>

          <div v-else class="empty-trace">
            <span>01</span><i></i><span>02</span><i></i><span>03</span>
            <strong>执行轨迹会显示在这里</strong>
            <p>每一步都包含思考依据、实际动作与服务器返回结果。</p>
          </div>

          <div v-if="agentResult?.answer" class="agent-answer">
            <span>FINAL ANSWER</span>
            <p>{{ agentResult.answer }}</p>
          </div>

          <button
            v-if="agentPendingApproval"
            class="approval-action"
            type="button"
            :disabled="approvalLoading"
            @click="approveAgentRun"
          >
            <el-icon><Warning /></el-icon>
            {{ approvalLoading ? '正在确认…' : '审阅并批准下一步' }}
          </button>
        </div>
      </section>

      <section v-else-if="activeTab === 'tools'" class="tools-layout">
        <div class="panel terminal-panel">
          <header class="panel-heading">
            <div><span>DIRECT COMMAND</span><h2>受控终端</h2></div>
            <span class="terminal-path">server / shell</span>
          </header>

          <div class="terminal-screen">
            <div class="terminal-dots"><i></i><i></i><i></i><span>{{ status.hostname || 'server' }}</span></div>
            <pre>{{ commandOutput || '等待命令。输出中的令牌、密码和密钥将被自动遮盖。' }}</pre>
          </div>

          <form class="command-line" @submit.prevent="executeCommand">
            <span>$</span>
            <input v-model="command" autocomplete="off" spellcheck="false" placeholder="输入服务器命令" />
            <button type="submit" aria-label="执行服务器命令" :disabled="commandRunning || !command || !status.agentEnabled">
              <el-icon v-if="commandRunning" class="is-loading"><Loading /></el-icon>
              <el-icon v-else><Promotion /></el-icon>
            </button>
          </form>
        </div>

        <div class="panel tool-panel">
          <header class="panel-heading">
            <div><span>STRUCTURED TOOLS</span><h2>文件、站点与服务</h2></div>
          </header>

          <div class="tool-picker">
            <button
              v-for="tool in tools"
              :key="tool.id"
              type="button"
              :class="{ active: toolMode === tool.id }"
              :disabled="!toolAvailable(tool.id)"
              @click="selectTool(tool.id)"
            >
              <el-icon><component :is="tool.icon" /></el-icon>
              <span><strong>{{ tool.label }}</strong><small>{{ tool.caption }}</small></span>
            </button>
          </div>

          <form class="tool-form" @submit.prevent="executeTool">
            <template v-if="toolMode === 'READ_FILE' || toolMode === 'WRITE_FILE'">
              <label><span>文件路径</span><input v-model.trim="toolForm.path" required placeholder="/www/wwwroot/example/index.html" /></label>
              <label v-if="toolMode === 'WRITE_FILE'"><span>文件内容</span><textarea v-model="toolForm.content" required rows="5" placeholder="输入要写入的内容"></textarea></label>
            </template>

            <template v-else-if="toolMode === 'CREATE_SITE'">
              <label><span>站点名称</span><input v-model.trim="toolForm.siteName" required placeholder="portfolio" /></label>
              <label><span>index.html 源码</span><textarea v-model="toolForm.content" required rows="5" placeholder="粘贴完整 HTML；自然语言建站请使用左侧 AI Agent"></textarea></label>
            </template>

            <template v-else-if="toolMode === 'SERVICE'">
              <label><span>服务名</span><input v-model.trim="toolForm.service" required placeholder="spring_xzm_interview_helper.service" /></label>
              <label><span>操作</span><select v-model="toolForm.action"><option value="STATUS">查看状态</option><option value="RESTART" :disabled="!capabilities.serviceRestart">重启</option><option value="START" :disabled="!capabilities.serviceRestart">启动</option><option value="STOP" :disabled="!capabilities.serviceRestart">停止</option></select></label>
              <small v-if="!capabilities.serviceRestart" class="capability-note">当前低权限服务账号仅支持查看状态，启动、停止与重启已禁用。</small>
            </template>

            <div class="tool-form__footer">
              <small>危险操作不会直接执行，而是生成一次性审批请求。</small>
              <button class="secondary-action" type="submit" :disabled="toolRunning || !status.agentEnabled || !selectedToolAvailable">
                <el-icon v-if="toolRunning" class="is-loading"><Loading /></el-icon>
                {{ toolRunning ? '执行中' : '执行工具' }}
              </button>
            </div>
          </form>

          <pre v-if="toolOutput" class="tool-output">{{ toolOutput }}</pre>
        </div>
      </section>

      <section v-else class="panel audit-panel">
        <header class="panel-heading">
          <div><span>IMMUTABLE TRAIL</span><h2>最近操作审计</h2></div>
          <button class="text-action" type="button" :disabled="auditLoading" @click="loadAudit">刷新记录</button>
        </header>

        <div v-if="auditLoading" class="audit-empty"><el-icon class="is-loading"><Loading /></el-icon> 正在读取记录</div>
        <div v-else-if="!auditEntries.length" class="audit-empty">暂无服务器操作记录</div>
        <div v-else class="audit-table-wrap">
          <table>
            <thead><tr><th>时间</th><th>操作者</th><th>动作</th><th>风险</th><th>结果</th><th>耗时</th></tr></thead>
            <tbody>
              <tr v-for="(entry, index) in auditEntries" :key="entry.id || index">
                <td>{{ formatDate(entry.timestamp || entry.createdAt) }}</td>
                <td>{{ entry.username || entry.actor || '管理员' }}</td>
                <td class="audit-action">
                  <code>{{ entry.operation || entry.action || entry.tool || '-' }}</code>
                  <small v-if="entry.target">{{ entry.target }}</small>
                </td>
                <td><span class="risk-pill" :data-risk="entry.risk">{{ entry.risk || 'LOW' }}</span></td>
                <td>{{ entry.status || entry.result || '-' }}</td>
                <td>{{ entry.durationMs != null ? `${entry.durationMs} ms` : '-' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </main>
  </WorkspaceFrame>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Collection,
  Files,
  Loading,
  Lock,
  Monitor,
  Promotion,
  Refresh,
  Setting,
  Warning,
} from '@element-plus/icons-vue'
import WorkspaceFrame from '@/components/WorkspaceFrame.vue'
import { serverAgentApi } from '@/api/serverAgent'

const tabs = [
  { id: 'agent', index: '01', label: 'AI Agent' },
  { id: 'tools', index: '02', label: '直接工具' },
  { id: 'audit', index: '03', label: '审计日志' },
]
const tools = [
  { id: 'READ_FILE', label: '读取文件', caption: '安全查看', icon: Files },
  { id: 'WRITE_FILE', label: '写入文件', caption: '受控修改', icon: Collection },
  { id: 'CREATE_SITE', label: '创建站点', caption: '生成并发布', icon: Monitor },
  { id: 'SERVICE', label: '管理服务', caption: '状态与重启', icon: Setting },
]

const activeTab = ref('agent')
const statusLoading = ref(false)
const statusLoaded = ref(false)
const statusError = ref('')
const status = reactive({
  agentEnabled: false,
  hostname: '',
  executionUser: '',
  uptimeSeconds: 0,
  cpuLoad: 0,
  memory: null,
  heapUsedBytes: 0,
  heapMaxBytes: 0,
  disk: [],
})

const objective = ref('')
const maxSteps = ref(6)
const agentRunning = ref(false)
const agentResult = ref(null)
const lastAgentPayload = ref(null)
const approvalLoading = ref(false)

const command = ref('')
const commandRunning = ref(false)
const commandOutput = ref('')

const toolMode = ref('READ_FILE')
const toolRunning = ref(false)
const toolOutput = ref('')
const toolForm = reactive({ path: '', content: '', siteName: '', service: '', action: 'STATUS' })

const auditLoading = ref(false)
const auditEntries = ref([])

const ratioFrom = (value) => {
  if (value == null) return 0
  if (typeof value === 'number') return value <= 1 ? value * 100 : value
  if (typeof value === 'object') {
    if (Number.isFinite(value.percent)) return ratioFrom(value.percent)
    const used = Number(value.used ?? value.usedBytes ?? value.physicalUsedBytes)
    const total = Number(value.total ?? value.totalBytes ?? value.physicalTotalBytes)
    if (Number.isFinite(used) && Number.isFinite(total) && total > 0) return (used / total) * 100
  }
  const parsed = Number.parseFloat(String(value))
  return Number.isFinite(parsed) ? parsed : 0
}

const memoryPercent = computed(() => ratioFrom(
  status.memory
  ?? status.memoryPercent
  ?? { used: status.heapUsedBytes, total: status.heapMaxBytes },
))
const diskPercent = computed(() => {
  if (!Array.isArray(status.disk)) return ratioFrom(status.disk ?? status.diskPercent)
  const totals = status.disk.reduce(
    (summary, disk) => ({
      total: summary.total + Number(disk?.totalBytes || 0),
      free: summary.free + Number(disk?.freeBytes || 0),
    }),
    { total: 0, free: 0 },
  )
  return totals.total > 0 ? ((totals.total - totals.free) / totals.total) * 100 : 0
})
const agentSteps = computed(() => Array.isArray(agentResult.value?.steps) ? agentResult.value.steps : [])
const agentPendingApproval = computed(() => agentResult.value?.pendingApproval || (agentResult.value?.status === 'AWAITING_APPROVAL' ? agentResult.value : null))
const capabilities = computed(() => status.capabilities || {})
const maxAgentSteps = computed(() => Math.max(2, Math.min(20, Number(status.limits?.maxAgentSteps) || 12)))
const toolAvailable = (tool) => {
  if (tool === 'READ_FILE') return capabilities.value.readFile !== false
  if (tool === 'WRITE_FILE') return capabilities.value.writeFile !== false
  if (tool === 'CREATE_SITE') return capabilities.value.createSite !== false
  if (tool === 'SERVICE') return capabilities.value.serviceStatus !== false
  return true
}
const selectedToolAvailable = computed(() => {
  if (!toolAvailable(toolMode.value)) return false
  if (toolMode.value === 'SERVICE' && toolForm.action !== 'STATUS') return capabilities.value.serviceRestart !== false
  return true
})

const formatPercent = (value) => `${Math.round(Math.max(0, Math.min(100, ratioFrom(value))))}%`
const metricWidth = (value) => ({ width: formatPercent(value) })
const formatUptime = (seconds) => {
  const value = Number(seconds)
  if (!Number.isFinite(value) || value <= 0) return '--'
  const days = Math.floor(value / 86400)
  const hours = Math.floor((value % 86400) / 3600)
  return days > 0 ? `${days}天 ${hours}时` : `${hours}时`
}
const formatDate = (value) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
const agentStateLabel = (value) => ({ COMPLETED: '已完成', AWAITING_APPROVAL: '等待审批', STEP_LIMIT: '达到步数上限', FAILED: '执行失败' }[value] || value)
const resultText = (result) => result?.output || result?.message || (result ? JSON.stringify(result, null, 2) : '')

const loadStatus = async () => {
  statusLoading.value = true
  statusError.value = ''
  try {
    Object.assign(status, await serverAgentApi.getStatus())
    maxSteps.value = Math.min(maxSteps.value, maxAgentSteps.value)
  } catch (error) {
    statusError.value = error?.response?.data?.message || '无法读取服务器状态'
    ElMessage.error(statusError.value)
  } finally {
    statusLoading.value = false
    statusLoaded.value = true
  }
}

const loadAudit = async () => {
  auditLoading.value = true
  try {
    const data = await serverAgentApi.getAudit(100)
    auditEntries.value = Array.isArray(data) ? data : (data?.items || data?.records || [])
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '无法读取审计日志')
  } finally {
    auditLoading.value = false
  }
}

const refreshAll = async () => Promise.all([loadStatus(), loadAudit()])

const runAgent = async (extra = {}) => {
  if (!objective.value || agentRunning.value) return
  const isResume = Boolean(extra?.approvalRequestId && extra?.approvalToken)
  const basePayload = isResume && lastAgentPayload.value
    ? { ...lastAgentPayload.value }
    : { objective: objective.value, maxSteps: maxSteps.value }
  const payload = { ...basePayload, ...extra }
  if (!isResume) lastAgentPayload.value = { ...basePayload }
  agentRunning.value = true
  try {
    agentResult.value = await serverAgentApi.runAgent(payload)
    if (agentResult.value?.status === 'COMPLETED') ElMessage.success('Agent 已完成目标')
    await loadAudit()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || 'Agent 执行失败')
  } finally {
    agentRunning.value = false
  }
}

const approvalCopy = (pending) => {
  const action = pending?.actionSummary || pending?.message || pending?.action || pending?.tool || '高风险服务器操作'
  return `即将批准：${action}\n\n批准令牌仅对本次完整操作有效，过期或修改动作后会自动失效。`
}

const requestApproval = async (pending) => {
  const id = pending?.approvalRequestId || pending?.id
  if (!id) throw new Error('服务端未返回审批编号')
  await ElMessageBox.confirm(approvalCopy(pending), '高风险操作确认', {
    confirmButtonText: '确认并生成一次性令牌',
    cancelButtonText: '取消',
    type: 'warning',
    distinguishCancelAndClose: true,
    customClass: 'server-agent-approval',
  })
  return serverAgentApi.approve(id)
}

const approveAgentRun = async () => {
  const pending = agentPendingApproval.value
  approvalLoading.value = true
  try {
    const approval = await requestApproval(pending)
    await runAgent({
      approvalRequestId: pending.approvalRequestId || pending.id,
      approvalToken: approval?.approvalToken || approval?.token,
    })
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || '审批未完成')
  } finally {
    approvalLoading.value = false
  }
}

const executeWithApproval = async (executor, payload) => {
  let result = await executor(payload)
  if (result?.status !== 'APPROVAL_REQUIRED') return result
  const approval = await requestApproval(result)
  result = await executor({
    ...payload,
    approvalRequestId: result.approvalRequestId,
    approvalToken: approval?.approvalToken || approval?.token,
  })
  return result
}

const executeCommand = async () => {
  if (!command.value || commandRunning.value) return
  commandRunning.value = true
  commandOutput.value = `$ ${command.value}\n\n正在执行…`
  try {
    const result = await executeWithApproval(serverAgentApi.executeCommand, { command: command.value, timeoutSeconds: 45 })
    commandOutput.value = `$ ${command.value}\n\n${resultText(result)}`
    if (result?.status === 'EXECUTED') ElMessage.success('命令执行完成')
    await loadAudit()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      commandOutput.value = `$ ${command.value}\n\n已取消审批，命令未执行。`
    } else {
      commandOutput.value = `$ ${command.value}\n\n${error?.response?.data?.message || error?.message || '执行失败'}`
      ElMessage.error('命令未执行')
    }
  } finally {
    commandRunning.value = false
  }
}

const selectTool = (id) => {
  toolMode.value = id
  toolOutput.value = ''
}

const toolPayload = () => {
  const payload = { tool: toolMode.value }
  if (['READ_FILE', 'WRITE_FILE'].includes(toolMode.value)) payload.path = toolForm.path
  if (toolMode.value === 'WRITE_FILE') payload.content = toolForm.content
  if (toolMode.value === 'CREATE_SITE') Object.assign(payload, { siteName: toolForm.siteName, content: toolForm.content })
  if (toolMode.value === 'SERVICE') Object.assign(payload, { service: toolForm.service, action: toolForm.action })
  return payload
}

const executeTool = async () => {
  if (toolRunning.value) return
  toolRunning.value = true
  try {
    const result = await executeWithApproval(serverAgentApi.executeTool, toolPayload())
    toolOutput.value = resultText(result)
    if (result?.status === 'EXECUTED') ElMessage.success('工具执行完成')
    await loadAudit()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      toolOutput.value = '已取消审批，工具未执行。'
    } else {
      toolOutput.value = error?.response?.data?.message || error?.message || '执行失败'
      ElMessage.error('工具执行失败')
    }
  } finally {
    toolRunning.value = false
  }
}

onMounted(refreshAll)
</script>

<style scoped>
.ops-page {
  --ops-ink: #102b38;
  --ops-muted: #617784;
  --ops-line: color-mix(in srgb, var(--xzm-border-color) 78%, #9fb9c1);
  --ops-teal: #0c8292;
  --ops-teal-bright: #39c6c1;
  --ops-lime: #a5de72;
  width: 100%;
  min-height: calc(100vh - 64px);
  padding: clamp(18px, 2.6vw, 38px);
  color: var(--ops-ink);
  background:
    linear-gradient(rgba(20, 75, 87, 0.035) 1px, transparent 1px),
    linear-gradient(90deg, rgba(20, 75, 87, 0.035) 1px, transparent 1px),
    #eef4f3;
  background-size: 28px 28px;
}

.ops-hero,
.agent-workbench,
.tools-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.08fr) minmax(380px, 0.92fr);
  gap: clamp(18px, 2.3vw, 32px);
}

.ops-hero {
  width: min(1480px, 100%);
  margin: 0 auto 24px;
  align-items: stretch;
}

.hero-copy {
  display: flex;
  min-height: 254px;
  flex-direction: column;
  justify-content: center;
  padding: 24px clamp(4px, 1vw, 14px);
}

.security-kicker,
.live-status,
.host-chip,
.step-limit,
.run-state,
.terminal-path {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 7px;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 760;
  letter-spacing: 0.04em;
}

.security-kicker { color: var(--ops-teal); }
.hero-copy h1 {
  margin: 15px 0 12px;
  color: var(--ops-ink);
  font-size: clamp(2.15rem, 4.2vw, 4.65rem);
  font-weight: 760;
  letter-spacing: -0.065em;
  line-height: 0.98;
}
.hero-copy h1 em { color: var(--ops-teal); font-family: Georgia, 'Times New Roman', serif; font-weight: 500; }
.hero-copy p { max-width: 650px; color: var(--ops-muted); font-size: 0.92rem; line-height: 1.75; }

.host-plate,
.panel {
  border: 1px solid rgba(76, 116, 126, 0.2);
  border-radius: 18px;
  background: rgba(252, 255, 254, 0.88);
  box-shadow: 0 16px 50px rgba(34, 75, 80, 0.08);
  backdrop-filter: blur(16px);
}
.host-plate { padding: clamp(20px, 2.5vw, 32px); color: #e9f7f4; background: #132f38; box-shadow: 0 25px 60px rgba(15, 45, 54, 0.2); }
.host-plate__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.host-plate__head div { display: grid; gap: 5px; }
.host-plate__head small { color: #7f9fa4; font: 700 0.63rem/1 monospace; letter-spacing: 0.16em; }
.host-plate__head strong { overflow-wrap: anywhere; font-size: 1.2rem; letter-spacing: -0.02em; }
.host-chip { padding: 6px 10px; color: #bce9e2; background: rgba(57, 198, 193, 0.13); font-family: monospace; }
.metric-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 26px 30px; margin-top: 36px; }
.metric-grid article { display: grid; grid-template-columns: 1fr auto; gap: 8px; align-items: end; }
.metric-grid span { color: #77959c; font: 700 0.62rem/1 monospace; letter-spacing: 0.11em; }
.metric-grid strong { color: #f6fffd; font-size: 0.9rem; }
.metric-grid i { grid-column: 1 / -1; height: 3px; overflow: hidden; border-radius: 9px; background: rgba(255,255,255,.11); }
.metric-grid i b { display: block; height: 100%; min-width: 2px; border-radius: inherit; background: linear-gradient(90deg, var(--ops-teal-bright), var(--ops-lime)); }
.metric-grid .uptime-line b { width: 72%; }

.live-status { padding: 6px 10px; color: #8b5c20; background: #fff3dc; }
.live-status i { width: 7px; height: 7px; border-radius: 50%; background: #d5983d; }
.live-status.online { color: #177168; background: #e2f5ef; }
.live-status.online i { background: #20a98f; box-shadow: 0 0 0 4px rgba(32,169,143,.12); }
.live-status.error { color:#9b3e31; background:#fde8e4; }
.live-status.error i { background:#d65d4c; }
.icon-action { display: grid; width: 36px; height: 36px; place-items: center; border: 1px solid var(--xzm-border-color); border-radius: 10px; color: var(--xzm-text-secondary); background: var(--xzm-surface-elevated); cursor: pointer; }

.locked-banner { display: flex; width: min(1480px, 100%); margin: 0 auto 18px; padding: 14px 17px; align-items: flex-start; gap: 12px; border: 1px solid #e4bd78; border-radius: 12px; color: #704917; background: #fff7e7; }
.locked-banner strong { font-size: .84rem; }
.locked-banner p { margin-top: 3px; font-size: .72rem; line-height: 1.55; }
.error-banner { border-color:#e4a89f; color:#843b30; background:#fff0ed; }

.ops-tabs { display: flex; width: min(1480px, 100%); margin: 0 auto 16px; gap: 4px; border-bottom: 1px solid rgba(47, 94, 105, .17); }
.ops-tabs button { position: relative; display: flex; gap: 9px; padding: 11px 17px 13px; border: 0; color: #71838b; background: transparent; font-size: .78rem; font-weight: 710; cursor: pointer; }
.ops-tabs button span { color: #9aabb0; font-family: monospace; font-size: .65rem; }
.ops-tabs button.active { color: var(--ops-ink); }
.ops-tabs button.active::after { position: absolute; right: 13px; bottom: -1px; left: 13px; height: 2px; background: var(--ops-teal); content: ''; }

.agent-workbench,
.tools-layout,
.audit-panel { width: min(1480px, 100%); margin: 0 auto; }
.panel { min-width: 0; padding: clamp(18px, 2vw, 27px); }
.panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.panel-heading > div { display: grid; gap: 5px; }
.panel-heading span { color: #7d9299; font: 750 .61rem/1 monospace; letter-spacing: .14em; }
.panel-heading h2 { color: var(--ops-ink); font-size: 1.05rem; letter-spacing: -.02em; }
.step-limit,.run-state,.terminal-path { padding: 6px 9px; color: #45717a !important; background: #eaf4f2; font-family: inherit !important; letter-spacing: 0 !important; }
.run-state[data-state='COMPLETED'] { color: #1c7368 !important; background: #ddf3ec; }
.run-state[data-state='AWAITING_APPROVAL'] { color: #9a641b !important; background: #fff0d5; }

.objective-panel textarea,
.tool-form textarea,
.tool-form input,
.tool-form select,
.command-line input {
  width: 100%;
  border: 1px solid var(--ops-line);
  outline: 0;
  color: var(--ops-ink);
  background: rgba(247, 251, 250, .85);
}
.objective-panel textarea { min-height: 154px; padding: 17px; border-radius: 12px; font-size: .92rem; line-height: 1.7; resize: vertical; }
.objective-panel textarea:focus,.tool-form input:focus,.tool-form textarea:focus,.tool-form select:focus { border-color: var(--ops-teal); box-shadow: 0 0 0 3px rgba(12,130,146,.1); }
.objective-footer { display: flex; margin-top: 14px; align-items: center; justify-content: space-between; gap: 18px; }
.objective-footer label { display: flex; min-width: 220px; align-items: center; gap: 10px; color: var(--ops-muted); font-size: .7rem; }
.objective-footer input[type='range'] { flex: 1; accent-color: var(--ops-teal); }
.objective-footer b { display: grid; width: 25px; height: 25px; place-items: center; border-radius: 7px; color: var(--ops-teal); background: #e5f3f1; }
.primary-action,.secondary-action,.approval-action { display: inline-flex; min-height: 40px; align-items: center; justify-content: center; gap: 8px; border: 0; border-radius: 10px; font-weight: 740; cursor: pointer; }
.primary-action { padding: 0 18px; color: #f4fffc; background: #123b43; box-shadow: 0 8px 20px rgba(18,59,67,.18); }
.primary-action:hover:not(:disabled) { background: #0a6c76; transform: translateY(-1px); }
button:disabled { opacity: .5; cursor: not-allowed; }
.guardrails { display: flex; margin-top: 19px; padding-top: 15px; flex-wrap: wrap; gap: 13px 22px; border-top: 1px dashed var(--ops-line); color: #778b91; font-size: .65rem; }
.guardrails span { display: flex; align-items: center; gap: 6px; }
.guardrails i { width: 5px; height: 5px; border-radius: 50%; background: var(--ops-teal); }

.trace-panel { display: flex; min-height: 420px; flex-direction: column; }
.agent-thinking { display: flex; min-height: 235px; align-items: center; justify-content: center; gap: 18px; color: var(--ops-ink); }
.agent-thinking > div { display: grid; gap: 5px; }
.agent-thinking small { color: var(--ops-muted); font-size: .68rem; }
.orbit { position: relative; width: 40px; height: 40px; border: 1px solid #add6d4; border-radius: 50%; animation: spin 1.2s linear infinite; }
.orbit i { position: absolute; top: -3px; left: 17px; width: 6px; height: 6px; border-radius: 50%; background: var(--ops-teal); }
.empty-trace { display: flex; min-height: 245px; flex-wrap: wrap; align-content: center; align-items: center; justify-content: center; color: #9aadb2; text-align: center; }
.empty-trace > span { display: grid; width: 32px; height: 32px; place-items: center; border: 1px solid #bdd0d2; border-radius: 50%; font: .65rem/1 monospace; }
.empty-trace > i { width: 45px; height: 1px; background: #cad9da; }
.empty-trace strong,.empty-trace p { width: 100%; }
.empty-trace strong { margin-top: 22px; color: var(--ops-ink); font-size: .86rem; }
.empty-trace p { margin-top: 5px; font-size: .68rem; }
.trace-list { max-height: 440px; overflow-y: auto; padding: 0; list-style: none; }
.trace-list li { display: grid; grid-template-columns: 35px minmax(0,1fr); gap: 12px; padding: 13px 0; border-top: 1px solid rgba(54,96,104,.1); }
.trace-index { display: grid; width: 31px; height: 31px; place-items: center; border-radius: 50%; color: #eafffa; background: var(--ops-teal); font: .65rem/1 monospace; }
.trace-meta { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.trace-meta span { color: var(--ops-teal); font: 750 .59rem/1 monospace; }
.trace-meta small { overflow: hidden; color: #7f9297; font: .63rem/1 monospace; text-overflow: ellipsis; white-space: nowrap; }
.trace-body p { margin-top: 7px; color: var(--ops-ink); font-size: .76rem; line-height: 1.55; }
.trace-body pre,.agent-answer p { overflow: auto; margin-top: 8px; padding: 10px; border-radius: 8px; color: #d8efeb; background: #17343b; font: .67rem/1.55 ui-monospace, monospace; white-space: pre-wrap; }
.agent-answer { margin-top: auto; padding-top: 16px; border-top: 1px solid var(--ops-line); }
.agent-answer span { color: var(--ops-teal); font: 750 .58rem/1 monospace; letter-spacing: .14em; }
.agent-answer p { color: #314f57; background: #edf5f3; font-family: inherit; }
.approval-action { width: 100%; margin-top: 12px; color: #714815; background: #ffedca; }

.terminal-panel { background: #112d35; }
.terminal-panel .panel-heading h2 { color: #effbf8; }
.terminal-panel .panel-heading span { color: #78989e; }
.terminal-screen { min-height: 280px; overflow: hidden; border: 1px solid rgba(158,210,207,.13); border-radius: 10px; background: #0b2228; }
.terminal-dots { display: flex; height: 38px; padding: 0 13px; align-items: center; gap: 6px; border-bottom: 1px solid rgba(158,210,207,.1); color: #66868c; font: .62rem/1 monospace; }
.terminal-dots i { width: 7px; height: 7px; border-radius: 50%; background: #c9695b; }.terminal-dots i:nth-child(2){background:#d9ad55}.terminal-dots i:nth-child(3){background:#65ac82}.terminal-dots span{margin-left:auto}
.terminal-screen pre { max-height: 330px; overflow: auto; padding: 16px; color: #b9d9d5; font: .72rem/1.65 ui-monospace, monospace; white-space: pre-wrap; }
.command-line { display: grid; grid-template-columns: auto minmax(0,1fr) 40px; margin-top: 12px; align-items: center; gap: 8px; color: var(--ops-teal-bright); }
.command-line input { padding: 11px 4px; border: 0; border-bottom: 1px solid rgba(159,211,207,.19); color: #e7f8f5; background: transparent; font-family: ui-monospace, monospace; }
.command-line button { display:grid; height: 38px; place-items:center; border:0; border-radius:9px; color:#0d3037; background:var(--ops-lime); cursor:pointer; }
.tool-picker { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 8px; }
.tool-picker button { display: flex; min-width: 0; padding: 12px; align-items: center; gap: 10px; border: 1px solid var(--ops-line); border-radius: 10px; color: var(--ops-muted); background: #f8fbfa; text-align: left; cursor: pointer; }
.tool-picker button.active { border-color: rgba(12,130,146,.4); color: var(--ops-teal); background: #e8f5f3; box-shadow: inset 3px 0 var(--ops-teal); }
.tool-picker button > span { display: grid; min-width: 0; gap: 2px; }.tool-picker strong{font-size:.75rem}.tool-picker small{color:#899a9f;font-size:.61rem}
.tool-form { display: grid; margin-top: 18px; gap: 12px; }
.tool-form label { display: grid; gap: 6px; color: var(--ops-muted); font-size: .67rem; font-weight: 690; }
.tool-form input,.tool-form select,.tool-form textarea { padding: 10px 11px; border-radius: 8px; font-size: .76rem; resize: vertical; }
.tool-form__footer { display: flex; align-items: center; justify-content: space-between; gap: 14px; }
.tool-form__footer small { max-width: 320px; color: #84969b; font-size: .61rem; line-height: 1.5; }
.capability-note { padding: 9px 10px; border-radius: 8px; color: #805719; background: #fff3dc; font-size: .65rem; line-height: 1.5; }
.secondary-action { padding: 0 15px; color: #eafffb; background: var(--ops-teal); }
.tool-output { max-height: 220px; overflow:auto; margin-top:14px; padding:12px; border-radius:8px; color:#bedbd7; background:#14323a; font:.67rem/1.55 ui-monospace,monospace; white-space:pre-wrap; }

.audit-panel { min-height: 430px; }
.text-action { border:0; color:var(--ops-teal); background:transparent; font-size:.72rem; font-weight:720; cursor:pointer; }
.audit-table-wrap { overflow-x: auto; }
.audit-panel table { width:100%; border-collapse:collapse; font-size:.72rem; }
.audit-panel th { padding:10px 12px; color:#809399; font-size:.61rem; letter-spacing:.08em; text-align:left; }
.audit-panel td { padding:13px 12px; border-top:1px solid rgba(55,96,104,.1); color:#425f67; }
.audit-panel code { color:#245a64; background:#eaf3f1; }
.audit-action { display:grid; min-width:180px; gap:4px; }
.audit-action code { width:fit-content; }
.audit-action small { max-width:320px; overflow:hidden; color:#82959b; font-size:.6rem; text-overflow:ellipsis; white-space:nowrap; }
.risk-pill { padding:4px 7px; border-radius:999px; color:#29776d; background:#e0f3ed; font-size:.58rem; font-weight:800; }
.risk-pill[data-risk='HIGH'],.risk-pill[data-risk='CRITICAL'] { color:#9b551a; background:#ffecd2; }
.audit-empty { display:flex; min-height:270px; align-items:center; justify-content:center; gap:8px; color:#82959b; font-size:.75rem; }

:global(.server-agent-approval .el-message-box__message p) {
  max-height: 50vh;
  overflow: auto;
  overflow-wrap: anywhere;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: .74rem;
  line-height: 1.55;
  white-space: pre-wrap;
}

@keyframes spin { to { transform: rotate(360deg); } }

:global([data-theme='dark']) .ops-page { --ops-ink:#e4f2f0; --ops-muted:#8eaaa9; --ops-line:#2b4c52; background-color:#10272e; background-image:linear-gradient(rgba(147,202,198,.045) 1px,transparent 1px),linear-gradient(90deg,rgba(147,202,198,.045) 1px,transparent 1px); }
:global([data-theme='dark']) .panel { background:rgba(17,45,53,.9); border-color:#294b51; }
:global([data-theme='dark']) .objective-panel textarea,:global([data-theme='dark']) .tool-form input,:global([data-theme='dark']) .tool-form textarea,:global([data-theme='dark']) .tool-form select,:global([data-theme='dark']) .tool-picker button { color:#dceceb; background:#102a31; border-color:#315159; }
:global([data-theme='dark']) .tool-picker button.active { color:#6bdad2; background:#163940; }

@media (max-width: 1080px) { .ops-hero,.agent-workbench,.tools-layout { grid-template-columns: 1fr; }.hero-copy{min-height:auto}.host-plate{min-height:250px} }
@media (max-width: 680px) { .ops-page{min-height:calc(100vh - 60px);padding:14px}.ops-hero{gap:12px}.hero-copy{padding:13px 2px}.hero-copy h1{font-size:2.25rem}.host-plate,.panel{border-radius:14px}.metric-grid{gap:20px}.ops-tabs{overflow-x:auto}.ops-tabs button{flex:0 0 auto}.objective-footer,.tool-form__footer{align-items:stretch;flex-direction:column}.objective-footer label{min-width:0}.primary-action,.secondary-action{width:100%}.tool-picker{grid-template-columns:1fr}.panel{padding:17px}.audit-panel{padding:14px}.audit-panel th,.audit-panel td{white-space:nowrap} }
@media (prefers-reduced-motion: reduce) { .orbit { animation-duration: 1ms; } }
</style>
