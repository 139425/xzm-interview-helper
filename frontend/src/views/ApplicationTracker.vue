<template>
  <WorkspaceFrame
    mode="applications"
    title="投递推进"
    eyebrow="CAREER PIPELINE"
    mark="投"
  >
    <template #actions>
      <router-link class="opportunity-link" to="/recruitment"
        >发现机会</router-link
      >
      <button
        type="button"
        class="primary workspace-primary"
        @click="openCreate"
      >
        ＋ 新增投递
      </button>
    </template>

    <main ref="applicationMain" class="application-main">
      <section class="pipeline-overview" aria-labelledby="pipeline-title">
        <div class="pipeline-overview__copy">
          <p>PROGRESS FIRST</p>
          <h1 id="pipeline-title">先看离 Offer 最近的机会</h1>
          <span>默认按进度倒序，同阶段再看最近更新。</span>
        </div>
        <dl class="pipeline-stats">
          <div class="is-offer">
            <dt>{{ summaryCount('OFFER') }}</dt>
            <dd>Offer</dd>
          </div>
          <div>
            <dt>{{ interviewCount }}</dt>
            <dd>面试中</dd>
          </div>
          <div>
            <dt>{{ activeCount }}</dt>
            <dd>推进中</dd>
          </div>
          <div>
            <dt>{{ summaryCount('TO_APPLY') }}</dt>
            <dd>未投递</dd>
          </div>
        </dl>
      </section>

      <section class="sheet-card" aria-label="投递记录表格">
        <div ref="sheetToolbar" class="sheet-toolbar">
          <div class="record-heading">
            <strong>投递清单</strong>
            <span class="record-count" aria-live="polite"
              >当前 {{ total }} 条</span
            >
          </div>
          <form class="sheet-filters" role="search" @submit.prevent="load">
            <label class="search-field">
              <span class="sr-only">搜索投递记录</span>
              <input
                v-model.trim="keyword"
                placeholder="搜索公司、岗位或备注"
              />
            </label>
            <details class="status-filter">
              <summary :aria-label="statusFilterLabel">
                <span>{{ statusFilterLabel }}</span>
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="m7 10 5 5 5-5" />
                </svg>
              </summary>
              <div class="status-filter__menu">
                <header>
                  <strong>多选投递进度</strong>
                  <button type="button" @click="clearStatuses">清空</button>
                </header>
                <div class="status-filter__options">
                  <label v-for="status in statusOptions" :key="status.value">
                    <input
                      v-model="selectedStatuses"
                      type="checkbox"
                      :value="status.value"
                      @change="load"
                    />
                    <i
                      :class="`status-dot--${status.tone}`"
                      aria-hidden="true"
                    ></i>
                    <span>{{ status.label }}</span>
                  </label>
                </div>
              </div>
            </details>
            <label class="sort-field">
              <span class="sr-only">投递记录排序</span>
              <select v-model="sortMode" @change="load">
                <option value="progress">进度优先</option>
                <option value="updated">最近更新</option>
                <option value="company">公司名称</option>
              </select>
            </label>
            <button type="submit">搜索</button>
            <button
              v-if="hasActiveFilters"
              type="button"
              class="quiet"
              @click="resetFilters"
            >
              清空
            </button>
          </form>
        </div>

        <div class="application-table-wrap">
          <table class="application-table">
            <thead>
              <tr>
                <th class="row-number">#</th>
                <th class="company-column">公司名</th>
                <th>投递链接</th>
                <th>投递状态</th>
                <th>岗位（选填）</th>
                <th>下一步 / 备注</th>
                <th>更新时间</th>
                <th class="actions-column">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="8" class="table-state">正在加载投递记录…</td>
              </tr>
              <tr v-else-if="!displayItems.length">
                <td colspan="8" class="table-state">
                  <strong>还没有投递记录</strong>
                  <span>点击右上角新增，或从秋招信息中一键录入。</span>
                </td>
              </tr>
              <tr
                v-for="(item, index) in displayItems"
                v-else
                :key="item.id"
                class="application-row"
              >
                <td class="row-number" data-label="优先级">
                  {{ String(index + 1).padStart(2, '0') }}
                </td>
                <td class="company-column" data-label="公司">
                  <strong :title="item.company">{{ item.company }}</strong>
                  <small v-if="item.location">{{ item.location }}</small>
                </td>
                <td class="link-cell" data-label="投递链接">
                  <a
                    v-if="item.applyUrl"
                    :href="item.applyUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    :title="item.applyUrl"
                  >
                    打开链接 <span aria-hidden="true">↗</span>
                  </a>
                  <span v-else class="muted">待补充</span>
                </td>
                <td data-label="进度">
                  <div
                    class="status-control"
                    :class="`status--${statusMeta(item.status).tone}`"
                  >
                    <i aria-hidden="true"></i>
                    <select
                      :value="item.status"
                      :aria-label="`修改 ${item.company} 的投递状态`"
                      :disabled="updatingStatusId === item.id"
                      @change="updateStatus(item, $event.target.value)"
                    >
                      <option
                        v-for="status in statusOptions"
                        :key="status.value"
                        :value="status.value"
                      >
                        {{ status.label }}
                      </option>
                    </select>
                  </div>
                </td>
                <td
                  class="text-cell"
                  data-label="岗位"
                  :title="item.roleName || ''"
                >
                  {{ item.roleName || '—' }}
                </td>
                <td
                  class="text-cell"
                  data-label="备注"
                  :title="item.nextAction || item.notes || ''"
                >
                  {{ item.nextAction || item.notes || '—' }}
                </td>
                <td class="updated-cell" data-label="更新时间">
                  {{ date(item.updatedAt) }}
                </td>
                <td class="actions-column" data-label="操作">
                  <button type="button" class="edit-button" @click="edit(item)">
                    编辑
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </main>

    <div v-if="dialogOpen" class="dialog-backdrop" @click.self="closeDialog">
      <form class="application-dialog" @submit.prevent="save">
        <header>
          <div>
            <small>{{ form.id ? 'EDIT RECORD' : 'NEW RECORD' }}</small>
            <h2>{{ form.id ? '编辑投递' : '新增投递' }}</h2>
          </div>
          <button type="button" aria-label="关闭" @click="closeDialog">
            ×
          </button>
        </header>

        <div class="core-fields">
          <label>
            <span>公司名 <b>*</b></span>
            <input
              v-model.trim="form.company"
              maxlength="200"
              required
              placeholder="例如：字节跳动"
            />
          </label>
          <label class="wide">
            <span>投递链接 <b>*</b></span>
            <input
              v-model.trim="form.applyUrl"
              maxlength="1024"
              type="url"
              required
              placeholder="https://"
            />
          </label>
          <label>
            <span>投递状态</span>
            <select v-model="form.status">
              <option
                v-for="status in statusOptions"
                :key="status.value"
                :value="status.value"
              >
                {{ status.label }}
              </option>
            </select>
          </label>
        </div>

        <details class="optional-fields">
          <summary>补充信息（选填）</summary>
          <div class="optional-grid">
            <label
              >岗位<input
                v-model.trim="form.roleName"
                maxlength="300"
                placeholder="例如：Java 后端"
            /></label>
            <label
              >城市<input
                v-model.trim="form.location"
                maxlength="300"
                placeholder="例如：北京"
            /></label>
            <label>截止时间<input v-model="form.deadline" type="date" /></label>
            <label
              >下一步行动<input
                v-model.trim="form.nextAction"
                maxlength="500"
                placeholder="例如：准备二面项目深挖"
            /></label>
            <label
              >行动时间<input v-model="form.nextActionAt" type="datetime-local"
            /></label>
            <label class="wide"
              >备注<textarea
                v-model.trim="form.notes"
                maxlength="10000"
                rows="3"
                placeholder="面试安排、准备事项等"
              ></textarea>
            </label>
          </div>
        </details>

        <footer>
          <button v-if="form.id" type="button" class="danger" @click="remove">
            删除
          </button>
          <span></span>
          <button type="button" class="quiet" @click="closeDialog">取消</button>
          <button type="submit" class="primary" :disabled="saving">
            {{ saving ? '保存中…' : '保存' }}
          </button>
        </footer>
      </form>
    </div>
  </WorkspaceFrame>
</template>

<script setup>
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
} from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { applicationApi } from '@/api/career'
import WorkspaceFrame from '@/components/WorkspaceFrame.vue'

const statusOptions = [
  { value: 'OFFER', label: 'Offer', tone: 'green', rank: 110 },
  { value: 'NEGOTIATION', label: '谈薪', tone: 'orange', rank: 100 },
  { value: 'HR_INTERVIEW', label: 'HR 面', tone: 'pink', rank: 90 },
  { value: 'INTERVIEW_FINAL', label: '终面', tone: 'purple', rank: 80 },
  { value: 'INTERVIEW_3', label: '三面', tone: 'purple', rank: 70 },
  { value: 'INTERVIEW_2', label: '二面', tone: 'indigo', rank: 60 },
  { value: 'INTERVIEW_1', label: '一面', tone: 'cyan', rank: 50 },
  { value: 'ASSESSMENT', label: '测评 / 笔试', tone: 'amber', rank: 40 },
  { value: 'APPLIED', label: '已投递', tone: 'blue', rank: 30 },
  { value: 'TO_APPLY', label: '未投递', tone: 'neutral', rank: 20 },
  { value: 'REJECTED', label: '未通过', tone: 'red', rank: 10 },
  { value: 'WITHDRAWN', label: '已放弃', tone: 'muted', rank: 0 },
]

const items = ref([])
const applicationSummary = ref({})
const keyword = ref('')
const selectedStatuses = ref([])
const sortMode = ref('progress')
const loading = ref(true)
const dialogOpen = ref(false)
const saving = ref(false)
const updatingStatusId = ref(null)
const applicationMain = ref(null)
const sheetToolbar = ref(null)
let toolbarResizeObserver
let loadRequestId = 0
const emptyForm = () => ({
  id: null,
  company: '',
  roleName: '',
  status: 'APPLIED',
  location: '',
  applyUrl: '',
  sourceUrl: '',
  deadline: '',
  nextAction: '',
  nextActionAt: '',
  notes: '',
})
const form = reactive(emptyForm())
const rankByStatus = new Map(
  statusOptions.map((status) => [status.value, status.rank]),
)
const displayItems = computed(() => {
  const selected = new Set(selectedStatuses.value)
  const filtered = selected.size
    ? items.value.filter((item) => selected.has(item.status))
    : [...items.value]

  return filtered.sort((left, right) => {
    if (sortMode.value === 'company') {
      return String(left.company || '').localeCompare(
        String(right.company || ''),
        'zh-CN',
      )
    }

    const updatedDiff = timestamp(right.updatedAt) - timestamp(left.updatedAt)
    if (sortMode.value === 'updated')
      return updatedDiff || Number(right.id || 0) - Number(left.id || 0)

    const progressDiff =
      (rankByStatus.get(right.status) || 0) -
      (rankByStatus.get(left.status) || 0)
    return (
      progressDiff ||
      updatedDiff ||
      Number(right.id || 0) - Number(left.id || 0)
    )
  })
})
const total = computed(() => displayItems.value.length)
const hasActiveFilters = computed(() =>
  Boolean(
    keyword.value ||
    selectedStatuses.value.length ||
    sortMode.value !== 'progress',
  ),
)
const statusFilterLabel = computed(() => {
  if (!selectedStatuses.value.length) return '全部状态'
  if (selectedStatuses.value.length === 1)
    return statusMeta(selectedStatuses.value[0]).label
  return `已选 ${selectedStatuses.value.length} 项状态`
})
const interviewStatuses = [
  'INTERVIEW_1',
  'INTERVIEW_2',
  'INTERVIEW_3',
  'INTERVIEW_FINAL',
  'HR_INTERVIEW',
]
const interviewCount = computed(() =>
  interviewStatuses.reduce((sum, status) => sum + summaryCount(status), 0),
)
const activeCount = computed(() =>
  ['APPLIED', 'ASSESSMENT', 'NEGOTIATION'].reduce(
    (sum, status) => sum + summaryCount(status),
    0,
  ),
)

function statusMeta(value) {
  return (
    statusOptions.find((status) => status.value === value) || statusOptions[0]
  )
}

function summaryCount(status) {
  return Number(applicationSummary.value?.[status] || 0)
}

function timestamp(value) {
  const parsed = new Date(value || 0).getTime()
  return Number.isNaN(parsed) ? 0 : parsed
}

async function load() {
  const requestId = ++loadRequestId
  loading.value = true
  try {
    const data = await applicationApi.list({
      keyword: keyword.value,
      statuses: selectedStatuses.value.join(','),
      sort: sortMode.value,
    })
    if (requestId !== loadRequestId) return
    items.value = data.items || []
    applicationSummary.value = data.summary || {}
  } catch (error) {
    if (requestId !== loadRequestId) return
    ElMessage.error(error.response?.data?.message || '投递记录加载失败')
  } finally {
    if (requestId === loadRequestId) loading.value = false
  }
}

function resetFilters() {
  keyword.value = ''
  selectedStatuses.value = []
  sortMode.value = 'progress'
  load()
}

function clearStatuses() {
  if (!selectedStatuses.value.length) return
  selectedStatuses.value = []
  load()
}

function openCreate() {
  Object.assign(form, emptyForm())
  dialogOpen.value = true
}

function edit(item) {
  Object.assign(form, emptyForm(), item, {
    deadline: item.deadline || '',
    nextActionAt: item.nextActionAt?.slice(0, 16) || '',
  })
  dialogOpen.value = true
}

function closeDialog() {
  dialogOpen.value = false
}

async function save() {
  saving.value = true
  try {
    const payload = {
      ...form,
      roleName: form.roleName || '',
      deadline: form.deadline || null,
      nextActionAt: form.nextActionAt || null,
    }
    if (form.id) {
      await applicationApi.update(form.id, payload)
    } else {
      await applicationApi.create(payload)
    }
    ElMessage.success('投递记录已保存')
    closeDialog()
    await load()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function updateStatus(item, status) {
  if (!item || item.status === status) return
  const previous = item.status
  item.status = status
  updatingStatusId.value = item.id
  try {
    const updated = await applicationApi.updateStatus(item.id, status)
    if (updated) Object.assign(item, updated)
    ElMessage.success('状态已更新')
  } catch (error) {
    item.status = previous
    ElMessage.error(error.response?.data?.message || '状态更新失败')
  } finally {
    updatingStatusId.value = null
  }
}

async function remove() {
  try {
    await ElMessageBox.confirm(
      '删除后不可恢复，确认删除这条投递记录？',
      '删除投递',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }
  await applicationApi.remove(form.id)
  closeDialog()
  await load()
  ElMessage.success('已删除')
}

function date(value) {
  if (!value) return '—'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(parsed)
}

function syncToolbarHeight() {
  if (!applicationMain.value || !sheetToolbar.value) return
  applicationMain.value.style.setProperty(
    '--sheet-toolbar-height',
    `${sheetToolbar.value.offsetHeight}px`,
  )
}

onMounted(async () => {
  load()
  await nextTick()
  syncToolbarHeight()

  if (typeof ResizeObserver !== 'undefined') {
    toolbarResizeObserver = new ResizeObserver(syncToolbarHeight)
    toolbarResizeObserver.observe(sheetToolbar.value)
  }
})

onBeforeUnmount(() => toolbarResizeObserver?.disconnect())
</script>

<style scoped>
.application-main {
  --sheet-sticky-inset: -12px;
  --sheet-toolbar-height: 64px;

  box-sizing: border-box;
  width: 100%;
  height: calc(100vh - 64px);
  height: calc(100dvh - 64px);
  padding: 10px 12px 12px;
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior-y: contain;
}

.sheet-card {
  display: flex;
  width: 100%;
  height: 100%;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--xzm-border-color);
  border-radius: 10px;
  background: var(--xzm-surface-elevated);
  box-shadow: 0 6px 18px rgba(31, 52, 81, 0.04);
}

.sheet-toolbar {
  display: flex;
  min-height: 52px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--xzm-border-color);
}

.record-count {
  flex: 0 0 auto;
  color: var(--xzm-text-tertiary);
  font-size: 0.7rem;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.sheet-filters {
  display: flex;
  min-width: 0;
  flex: 1;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.sheet-filters input,
.sheet-filters select,
.core-fields input,
.core-fields select,
.optional-grid input,
.optional-grid textarea {
  border: 1px solid var(--xzm-border-color);
  border-radius: 8px;
  color: var(--xzm-text-primary);
  background: var(--xzm-surface-1);
  outline: none;
}

.sheet-filters input:focus,
.sheet-filters select:focus,
.core-fields input:focus,
.core-fields select:focus,
.optional-grid input:focus,
.optional-grid textarea:focus {
  border-color: var(--xzm-brand);
  box-shadow: 0 0 0 3px var(--xzm-focus-ring-soft);
}

.sheet-filters input {
  width: min(300px, 28vw);
  height: 34px;
  padding: 0 11px;
}

.sheet-filters select {
  height: 34px;
  padding: 0 28px 0 10px;
}

.primary,
.sheet-filters button {
  height: 34px;
  padding: 0 14px;
  border: 1px solid var(--xzm-brand);
  border-radius: 8px;
  color: #fff;
  background: var(--xzm-brand);
  font: inherit;
  font-size: 0.73rem;
  font-weight: 700;
  cursor: pointer;
}

.workspace-primary {
  white-space: nowrap;
}

.sheet-filters .quiet,
.application-dialog .quiet {
  border-color: var(--xzm-border-color);
  color: var(--xzm-text-secondary);
  background: var(--xzm-surface-elevated);
}

.application-table-wrap {
  min-height: 0;
  flex: 1;
  overflow: visible;
}

.application-table {
  width: 100%;
  min-width: 1040px;
  border-collapse: separate;
  border-spacing: 0;
  table-layout: fixed;
}

.application-table th,
.application-table td {
  height: 52px;
  padding: 0 12px;
  border-bottom: 1px solid var(--xzm-border-color);
  color: var(--xzm-text-secondary);
  text-align: left;
  vertical-align: middle;
}

.application-table th {
  position: sticky;
  top: 0;
  z-index: 3;
  height: 40px;
  color: var(--xzm-text-tertiary);
  background: var(--xzm-surface-2);
  font-size: 0.66rem;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.application-row {
  transition: background-color 130ms ease-out;
}

.application-row:hover td {
  background: color-mix(
    in srgb,
    var(--xzm-brand) 4%,
    var(--xzm-surface-elevated)
  );
}

.application-table tbody tr:last-child td {
  border-bottom: 0;
}

.row-number {
  width: 48px;
  color: var(--xzm-text-tertiary) !important;
  text-align: center !important;
  font-size: 0.68rem;
  font-variant-numeric: tabular-nums;
}

.company-column {
  width: 180px;
}

td.company-column {
  display: grid;
  align-content: center;
  gap: 2px;
}

.company-column strong,
.company-column small,
.text-cell {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.company-column strong {
  color: var(--xzm-text-primary);
  font-size: 0.78rem;
}

.company-column small {
  color: var(--xzm-text-tertiary);
  font-size: 0.62rem;
}

.link-cell {
  width: 130px;
}

.link-cell a {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--xzm-brand);
  font-size: 0.72rem;
  font-weight: 650;
  text-decoration: none;
}

.muted {
  color: var(--xzm-text-tertiary);
  font-size: 0.7rem;
}

.status-control {
  --status-bg: #eef1f5;
  --status-text: #5e6978;
  --status-dot: #8b95a4;
  position: relative;
  display: inline-flex;
  max-width: 130px;
  align-items: center;
  border-radius: 999px;
  color: var(--status-text);
  background: var(--status-bg);
}

.status-control i {
  position: absolute;
  left: 10px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--status-dot);
  pointer-events: none;
}

.status-control select {
  width: 100%;
  height: 30px;
  padding: 0 24px 0 23px;
  border: 0;
  outline: 0;
  color: inherit;
  background: transparent;
  font: inherit;
  font-size: 0.68rem;
  font-weight: 700;
  cursor: pointer;
}

.status-control option {
  color: #263244;
  background: var(--xzm-surface-elevated);
}

.status--blue {
  --status-bg: #e8f1ff;
  --status-text: #215da8;
  --status-dot: #3b82f6;
}
.status--amber {
  --status-bg: #fff4d9;
  --status-text: #8d5e09;
  --status-dot: #e3a008;
}
.status--cyan {
  --status-bg: #e4f8fa;
  --status-text: #15727b;
  --status-dot: #20a7b5;
}
.status--indigo {
  --status-bg: #ebefff;
  --status-text: #4858a9;
  --status-dot: #6574d9;
}
.status--purple {
  --status-bg: #f0eaff;
  --status-text: #6947a8;
  --status-dot: #8b5bd4;
}
.status--pink {
  --status-bg: #fdebf3;
  --status-text: #9c426a;
  --status-dot: #d85c94;
}
.status--orange {
  --status-bg: #fff0df;
  --status-text: #9b5917;
  --status-dot: #ed8b2f;
}
.status--green {
  --status-bg: #e7f7ed;
  --status-text: #247146;
  --status-dot: #33a665;
}
.status--red {
  --status-bg: #fdecea;
  --status-text: #a0443d;
  --status-dot: #dc6259;
}
.status--muted {
  --status-bg: #f0f1f3;
  --status-text: #737b87;
  --status-dot: #9aa1ab;
}

.text-cell {
  width: 150px;
  font-size: 0.7rem;
}

.updated-cell {
  width: 112px;
  font-size: 0.67rem;
  font-variant-numeric: tabular-nums;
}

.actions-column {
  width: 72px;
  text-align: center !important;
}

.edit-button {
  height: 28px;
  padding: 0 9px;
  border: 1px solid var(--xzm-border-color);
  border-radius: 6px;
  color: var(--xzm-text-secondary);
  background: var(--xzm-surface-elevated);
  font: inherit;
  font-size: 0.67rem;
  cursor: pointer;
}

.edit-button:hover {
  border-color: var(--xzm-brand);
  color: var(--xzm-brand);
}

.table-state {
  height: 260px !important;
  text-align: center !important;
}

.table-state strong,
.table-state span {
  display: block;
}

.table-state strong {
  margin-bottom: 5px;
  color: var(--xzm-text-primary);
  font-size: 0.82rem;
}

.table-state span {
  color: var(--xzm-text-tertiary);
  font-size: 0.7rem;
}

.dialog-backdrop {
  position: fixed;
  inset: 0;
  z-index: calc(var(--gemini-z-modal) + 1);
  display: grid;
  place-items: center;
  padding: 16px;
  background: rgba(20, 29, 44, 0.34);
  backdrop-filter: blur(4px);
}

.application-dialog {
  width: min(620px, 100%);
  max-height: calc(100vh - 32px);
  overflow: auto;
  padding: 22px;
  border: 1px solid var(--xzm-border-color);
  border-radius: 16px;
  color: var(--xzm-text-primary);
  background: var(--xzm-surface-elevated);
  box-shadow: 0 24px 70px rgba(20, 33, 53, 0.2);
}

.application-dialog > header {
  display: flex;
  justify-content: space-between;
}

.application-dialog header small {
  display: block;
  margin: 0 0 4px;
  color: var(--xzm-brand);
  font-size: 0.62rem;
  font-weight: 800;
  letter-spacing: 0.15em;
}

.application-dialog h2 {
  margin: 0;
  font-size: 1.2rem;
}

.application-dialog > header button {
  border: 0;
  color: var(--xzm-text-tertiary);
  background: none;
  font-size: 1.6rem;
  cursor: pointer;
}

.core-fields,
.optional-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.core-fields {
  margin-top: 22px;
}

.core-fields label,
.optional-grid label {
  display: grid;
  gap: 7px;
  color: var(--xzm-text-secondary);
  font-size: 0.7rem;
}

.core-fields label > span b {
  color: var(--xzm-danger);
}

.core-fields input,
.core-fields select,
.optional-grid input {
  width: 100%;
  height: 40px;
  padding: 0 10px;
}

.core-fields .wide,
.optional-grid .wide {
  grid-column: 1 / -1;
}

.optional-fields {
  margin-top: 18px;
  border-top: 1px solid var(--xzm-border-color);
}

.optional-fields summary {
  padding: 16px 0;
  color: var(--xzm-text-secondary);
  font-size: 0.72rem;
  font-weight: 700;
  cursor: pointer;
}

.optional-grid textarea {
  width: 100%;
  padding: 10px;
  resize: vertical;
}

.application-dialog footer {
  display: flex;
  gap: 8px;
  margin-top: 20px;
}

.application-dialog footer span {
  flex: 1;
}

.application-dialog footer button {
  height: 36px;
  padding: 0 14px;
  border-radius: 8px;
  font: inherit;
  font-size: 0.72rem;
  cursor: pointer;
}

.danger {
  border: 1px solid
    color-mix(in srgb, var(--xzm-danger) 55%, var(--xzm-border-color));
  color: var(--xzm-danger);
  background: transparent;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
}

@media (max-width: 900px) {
  .sheet-toolbar {
    flex-wrap: wrap;
  }

  .sheet-filters input {
    width: 100%;
  }

  .search-field {
    flex: 1;
  }
}

@media (max-width: 760px) {
  .workspace-primary {
    height: 32px;
    padding: 0 9px;
    font-size: 0.68rem;
  }

  .application-main {
    height: calc(100vh - 60px);
    height: calc(100dvh - 60px);
    padding: 8px;
  }

  .sheet-filters {
    flex-wrap: wrap;
  }

  .search-field {
    flex-basis: 100%;
  }

  .sheet-filters select {
    min-width: 120px;
  }

  .core-fields,
  .optional-grid {
    grid-template-columns: 1fr;
  }

  .core-fields .wide,
  .optional-grid .wide {
    grid-column: auto;
  }
}

/* V4: progress-first operating sheet. */
.application-main {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 12px 14px 14px;
  scrollbar-gutter: stable;
}

.opportunity-link {
  display: inline-flex;
  min-height: 36px;
  align-items: center;
  padding: 0 12px;
  border: 1px solid var(--xzm-border-color);
  border-radius: 8px;
  color: var(--xzm-text-secondary);
  background: var(--xzm-surface-elevated);
  font-size: 0.72rem;
  font-weight: 700;
  text-decoration: none;
}

.opportunity-link:hover {
  border-color: var(--xzm-brand);
  color: var(--xzm-brand);
}

.pipeline-overview {
  position: relative;
  display: grid;
  grid-template-columns: minmax(300px, 1fr) minmax(430px, 0.82fr);
  min-height: 116px;
  flex: 0 0 auto;
  align-items: stretch;
  overflow: hidden;
  border: 1px solid
    color-mix(in srgb, var(--xzm-brand) 26%, var(--xzm-border-color));
  border-radius: 14px;
  color: #eefaf5;
  background:
    linear-gradient(118deg, rgba(221, 252, 116, 0.08), transparent 45%), #0a3c38;
  box-shadow: 0 15px 38px rgba(7, 72, 66, 0.1);
}

.pipeline-overview::after {
  content: '';
  position: absolute;
  right: 35%;
  bottom: -95px;
  width: 190px;
  height: 190px;
  border: 38px solid rgba(221, 252, 116, 0.055);
  border-radius: 50%;
}

.pipeline-overview__copy {
  position: relative;
  z-index: 1;
  align-self: center;
  padding: 20px 24px;
}

.pipeline-overview__copy p {
  margin: 0 0 6px;
  color: var(--xzm-signal);
  font: 800 0.57rem/1 var(--xzm-font-data);
  letter-spacing: 0.15em;
}

.pipeline-overview__copy h1 {
  margin: 0;
  font-family: var(--xzm-font-display);
  font-size: clamp(1.35rem, 2vw, 1.85rem);
  line-height: 1.15;
  letter-spacing: -0.04em;
}

.pipeline-overview__copy > span {
  display: block;
  margin-top: 7px;
  color: #a9c0b8;
  font-size: 0.67rem;
}

.pipeline-stats {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin: 0;
  border-left: 1px solid rgba(221, 252, 116, 0.14);
}

.pipeline-stats div {
  display: grid;
  place-content: center;
  gap: 5px;
  min-width: 0;
  text-align: center;
}

.pipeline-stats div + div {
  border-left: 1px solid rgba(221, 252, 116, 0.12);
}
.pipeline-stats dt {
  font: 750 1.5rem/1 var(--xzm-font-data);
}
.pipeline-stats dd {
  color: #8eaaa1;
  font-size: 0.63rem;
}
.pipeline-stats .is-offer {
  color: var(--xzm-signal);
  background: rgba(221, 252, 116, 0.06);
}
.pipeline-stats .is-offer dd {
  color: #c8dc85;
}

.sheet-card {
  height: auto;
  min-height: calc(100dvh - 90px);
  flex: 0 0 auto;
  overflow: visible;
  border-radius: 14px;
  background: color-mix(in srgb, var(--xzm-surface-elevated) 97%, transparent);
  box-shadow: 0 12px 36px rgba(19, 37, 34, 0.055);
}

.sheet-toolbar {
  position: sticky;
  top: var(--sheet-sticky-inset);
  z-index: var(--xzm-z-sticky);
  min-height: 64px;
  padding: 9px 13px;
  border-radius: 13px 13px 0 0;
  background: color-mix(in srgb, var(--xzm-surface-elevated) 96%, transparent);
  box-shadow: 0 10px 24px rgba(8, 61, 56, 0.07);
  backdrop-filter: blur(16px) saturate(130%);
}

.record-heading {
  display: grid;
  flex: 0 0 auto;
  gap: 2px;
}

.record-heading strong {
  color: var(--xzm-text-primary);
  font-size: 0.78rem;
}
.record-count {
  font-size: 0.62rem;
}

.sheet-filters input,
.sheet-filters select,
.status-filter > summary {
  height: 38px;
}

.sheet-filters input {
  width: min(300px, 24vw);
}

.status-filter {
  position: relative;
  flex: 0 0 auto;
}

.status-filter > summary {
  display: flex;
  min-width: 132px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0 10px 0 12px;
  border: 1px solid var(--xzm-border-color);
  border-radius: 8px;
  color: var(--xzm-text-secondary);
  background: var(--xzm-surface-1);
  font-size: 0.7rem;
  cursor: pointer;
  list-style: none;
}

.status-filter > summary::-webkit-details-marker {
  display: none;
}
.status-filter > summary svg {
  width: 15px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  transition: transform 0.16s;
}
.status-filter[open] > summary {
  border-color: var(--xzm-brand);
  box-shadow: 0 0 0 3px var(--xzm-focus-ring-soft);
}
.status-filter[open] > summary svg {
  transform: rotate(180deg);
}

.status-filter__menu {
  position: absolute;
  z-index: var(--xzm-z-popover);
  top: calc(100% + 8px);
  right: 0;
  width: 276px;
  overflow: hidden;
  border: 1px solid var(--xzm-border-color-strong);
  border-radius: 12px;
  background: var(--xzm-surface-elevated);
  box-shadow: var(--xzm-shadow-floating);
}

.status-filter__menu header {
  display: flex;
  min-height: 42px;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  border-bottom: 1px solid var(--xzm-border-color);
  color: var(--xzm-text-primary);
  font-size: 0.69rem;
}

.status-filter__menu header button {
  min-height: 30px;
  padding: 0 5px;
  border: 0;
  color: var(--xzm-brand);
  background: transparent;
  font-size: 0.65rem;
  cursor: pointer;
}

.status-filter__options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 3px;
  max-height: 290px;
  padding: 8px;
  overflow-y: auto;
}

.status-filter__options label {
  display: grid;
  grid-template-columns: 15px 7px minmax(0, 1fr);
  gap: 7px;
  align-items: center;
  min-height: 35px;
  padding: 0 7px;
  border-radius: 7px;
  color: var(--xzm-text-secondary);
  font-size: 0.66rem;
  cursor: pointer;
}

.status-filter__options label:hover {
  background: var(--xzm-hover-bg);
}
.status-filter__options input {
  width: 15px;
  height: 15px;
  accent-color: var(--xzm-brand);
}
.status-filter__options i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #8b95a4;
}
.status-filter__options .status-dot--blue {
  background: #3b82f6;
}
.status-filter__options .status-dot--amber {
  background: #e3a008;
}
.status-filter__options .status-dot--cyan {
  background: #20a7b5;
}
.status-filter__options .status-dot--indigo {
  background: #6574d9;
}
.status-filter__options .status-dot--purple {
  background: #8b5bd4;
}
.status-filter__options .status-dot--pink {
  background: #d85c94;
}
.status-filter__options .status-dot--orange {
  background: #ed8b2f;
}
.status-filter__options .status-dot--green {
  background: #33a665;
}
.status-filter__options .status-dot--red {
  background: #dc6259;
}

.sort-field select {
  min-width: 108px;
}
.primary,
.sheet-filters button {
  height: 38px;
  border-radius: 8px;
}
.workspace-primary {
  height: 36px;
  background: var(--xzm-brand-gradient);
  box-shadow: var(--xzm-shadow-brand);
}

.application-table th {
  top: calc(var(--sheet-toolbar-height) + var(--sheet-sticky-inset));
  z-index: calc(var(--xzm-z-sticky) - 1);
  height: 42px;
  background: color-mix(
    in srgb,
    var(--xzm-surface-2) 78%,
    var(--xzm-surface-elevated)
  );
  box-shadow: 0 1px 0 var(--xzm-border-color);
}
.application-table th,
.application-table td {
  padding-inline: 13px;
}
.application-row:hover td {
  background: color-mix(
    in srgb,
    var(--xzm-brand) 4.5%,
    var(--xzm-surface-elevated)
  );
}
.row-number {
  font-family: var(--xzm-font-data);
}

.status--green {
  --status-bg: var(--xzm-signal-soft);
  --status-text: #496000;
  --status-dot: #6f8f08;
  box-shadow: inset 0 0 0 1px rgba(111, 143, 8, 0.12);
}

.application-dialog {
  border-radius: 4px 18px 18px 18px;
}
.application-dialog header small {
  color: var(--xzm-brand);
  font-family: var(--xzm-font-data);
}
.application-dialog h2 {
  font-family: var(--xzm-font-display);
  font-size: 1.5rem;
  letter-spacing: -0.035em;
}

@media (max-width: 1100px) {
  .pipeline-overview {
    grid-template-columns: minmax(280px, 0.85fr) minmax(390px, 1fr);
  }
  .sheet-filters input {
    width: min(230px, 22vw);
  }
  .sort-field {
    display: none;
  }

  .application-table {
    min-width: 100%;
  }

  .application-table th,
  .application-table td {
    padding-inline: 8px;
  }

  .row-number {
    width: 36px;
  }

  .company-column {
    width: 130px;
  }

  .link-cell {
    width: 104px;
  }

  .text-cell {
    width: 112px;
  }

  .updated-cell {
    width: 88px;
  }

  .actions-column {
    width: 60px;
  }

  .status-control i {
    left: 8px;
  }

  .status-control select {
    padding-inline: 20px 18px;
  }

  .edit-button {
    padding-inline: 6px;
    white-space: nowrap;
  }
}

@media (min-width: 761px) and (max-width: 900px) {
  .application-table-wrap {
    overflow-x: auto;
    overflow-y: hidden;
  }

  .application-table {
    min-width: 760px;
  }
}

@media (max-width: 760px) {
  .opportunity-link {
    display: none;
  }
  .application-main {
    --sheet-sticky-inset: 0px;
    --sheet-toolbar-height: 0px;

    height: auto;
    min-height: calc(100dvh - 60px);
    padding: 9px;
    overflow: visible;
    scrollbar-gutter: auto;
  }
  .pipeline-overview {
    grid-template-columns: 1fr;
    min-height: auto;
  }
  .pipeline-overview__copy {
    padding: 17px 17px 15px;
  }
  .pipeline-overview__copy h1 {
    font-size: 1.28rem;
  }
  .pipeline-overview__copy > span {
    font-size: 0.63rem;
  }
  .pipeline-stats {
    min-height: 68px;
    border-top: 1px solid rgba(221, 252, 116, 0.14);
    border-left: 0;
  }
  .pipeline-stats dt {
    font-size: 1.15rem;
  }
  .pipeline-stats dd {
    font-size: 0.58rem;
  }
  .sheet-card {
    height: auto;
    min-height: 420px;
    overflow: visible;
  }
  .sheet-toolbar {
    position: static;
    display: grid;
    gap: 9px;
    padding: 11px;
    box-shadow: none;
    backdrop-filter: none;
  }
  .record-heading {
    grid-template-columns: 1fr auto;
    align-items: center;
  }
  .sheet-filters {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto;
    width: 100%;
  }
  .search-field {
    grid-column: 1 / -1;
  }
  .sheet-filters input {
    width: 100%;
    height: 44px;
  }
  .status-filter > summary,
  .sort-field select,
  .sheet-filters button {
    width: 100%;
    height: 42px;
    min-width: 0;
  }
  .sort-field {
    display: block;
  }
  .sheet-filters > button[type='submit'] {
    width: 48px;
    padding: 0 8px;
  }
  .sheet-filters .quiet {
    grid-column: 1 / -1;
  }
  .status-filter__menu {
    position: fixed;
    top: auto;
    right: 10px;
    bottom: 10px;
    left: 10px;
    width: auto;
  }
  .status-filter__options {
    grid-template-columns: repeat(2, 1fr);
    max-height: min(52vh, 340px);
  }
  .status-filter__options label {
    min-height: 42px;
  }
  .application-table-wrap {
    overflow: visible;
  }
  .application-table {
    display: block;
    min-width: 0;
  }
  .application-table thead {
    display: none;
  }
  .application-table tbody {
    display: grid;
    gap: 9px;
    padding: 10px;
  }
  .application-table tbody tr {
    display: block;
  }
  .application-row {
    display: grid !important;
    grid-template-columns: 34px minmax(0, 1fr) auto;
    grid-template-areas:
      'number company status'
      '. role status'
      '. note note'
      '. link actions'
      '. updated actions';
    gap: 5px 9px;
    padding: 13px 12px 12px;
    border: 1px solid var(--xzm-border-color);
    border-radius: 11px;
    background: var(--xzm-surface-elevated);
    box-shadow: 0 6px 18px rgba(19, 37, 34, 0.035);
  }
  .application-table .application-row td {
    display: flex;
    width: auto;
    height: auto;
    min-width: 0;
    align-items: center;
    padding: 0;
    border: 0;
    background: transparent !important;
  }
  .application-row .row-number {
    grid-area: number;
    align-self: start;
    padding-top: 4px;
  }
  .application-row .company-column {
    grid-area: company;
    display: grid;
    gap: 1px;
  }
  .application-row .link-cell {
    grid-area: link;
    margin-top: 6px;
  }
  .application-row td:nth-child(4) {
    grid-area: status;
    align-self: start;
  }
  .application-row td:nth-child(5) {
    grid-area: role;
    color: var(--xzm-text-tertiary);
  }
  .application-row td:nth-child(6) {
    grid-area: note;
    margin-top: 7px;
    padding: 8px 9px;
    border-radius: 7px;
    background: var(--xzm-surface-1) !important;
    white-space: normal;
  }
  .application-row td:nth-child(6)::before {
    content: '下一步 / ';
    margin-right: 4px;
    color: var(--xzm-brand);
    font-size: 0.6rem;
    font-weight: 750;
  }
  .application-row .updated-cell {
    grid-area: updated;
    color: var(--xzm-text-muted);
    font-size: 0.6rem;
  }
  .application-row .actions-column {
    grid-area: actions;
    align-self: end;
    justify-self: end;
  }
  .company-column strong {
    font-size: 0.84rem;
  }
  .status-control {
    max-width: 112px;
  }
  .status-control select {
    height: 32px;
    font-size: 0.63rem;
  }
  .edit-button {
    height: 34px;
  }
  .table-state {
    display: grid !important;
    min-height: 260px;
    place-content: center;
  }
}
</style>
