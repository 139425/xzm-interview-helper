<template>
  <WorkspaceFrame mode="applications" title="投递追踪" eyebrow="APPLICATION SHEET" mark="投">
    <template #actions>
      <button type="button" class="primary workspace-primary" @click="openCreate">
        ＋ 新增投递
      </button>
    </template>

    <main class="application-main">
      <header class="application-heading">
        <div>
          <p>APPLICATIONS</p>
          <h1>投递记录</h1>
        </div>
        <span>共 {{ total }} 条</span>
      </header>

      <section class="sheet-card" aria-labelledby="application-sheet-title">
        <div class="sheet-toolbar">
          <div>
            <h2 id="application-sheet-title">我的投递</h2>
            <span>公司名和投递链接为必填项</span>
          </div>
          <form class="sheet-filters" role="search" @submit.prevent="load">
            <label class="search-field">
              <span class="sr-only">搜索投递记录</span>
              <input v-model.trim="keyword" placeholder="搜索公司、岗位或备注">
            </label>
            <label>
              <span class="sr-only">筛选投递状态</span>
              <select v-model="statusFilter" @change="load">
                <option value="">全部状态</option>
                <option v-for="status in statusOptions" :key="status.value" :value="status.value">
                  {{ status.label }}
                </option>
              </select>
            </label>
            <button type="submit">搜索</button>
            <button
              v-if="keyword || statusFilter"
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
                <th>备注（选填）</th>
                <th>更新时间</th>
                <th class="actions-column">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="8" class="table-state">正在加载投递记录…</td>
              </tr>
              <tr v-else-if="!items.length">
                <td colspan="8" class="table-state">
                  <strong>还没有投递记录</strong>
                  <span>点击右上角新增，或从秋招信息中一键录入。</span>
                </td>
              </tr>
              <tr v-for="(item, index) in items" v-else :key="item.id" class="application-row">
                <td class="row-number">{{ index + 1 }}</td>
                <td class="company-column">
                  <strong :title="item.company">{{ item.company }}</strong>
                  <small v-if="item.location">{{ item.location }}</small>
                </td>
                <td class="link-cell">
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
                <td>
                  <div class="status-control" :class="`status--${statusMeta(item.status).tone}`">
                    <i aria-hidden="true"></i>
                    <select
                      :value="item.status"
                      :aria-label="`修改 ${item.company} 的投递状态`"
                      :disabled="updatingStatusId === item.id"
                      @change="updateStatus(item, $event.target.value)"
                    >
                      <option v-for="status in statusOptions" :key="status.value" :value="status.value">
                        {{ status.label }}
                      </option>
                    </select>
                  </div>
                </td>
                <td class="text-cell" :title="item.roleName || ''">{{ item.roleName || '—' }}</td>
                <td class="text-cell" :title="item.notes || ''">{{ item.notes || '—' }}</td>
                <td class="updated-cell">{{ date(item.updatedAt) }}</td>
                <td class="actions-column">
                  <button type="button" class="edit-button" @click="edit(item)">编辑</button>
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
          <button type="button" aria-label="关闭" @click="closeDialog">×</button>
        </header>

        <div class="core-fields">
          <label>
            <span>公司名 <b>*</b></span>
            <input v-model.trim="form.company" maxlength="200" required placeholder="例如：字节跳动">
          </label>
          <label class="wide">
            <span>投递链接 <b>*</b></span>
            <input v-model.trim="form.applyUrl" maxlength="1024" type="url" required placeholder="https://">
          </label>
          <label>
            <span>投递状态</span>
            <select v-model="form.status">
              <option v-for="status in statusOptions" :key="status.value" :value="status.value">
                {{ status.label }}
              </option>
            </select>
          </label>
        </div>

        <details class="optional-fields">
          <summary>补充信息（选填）</summary>
          <div class="optional-grid">
            <label>岗位<input v-model.trim="form.roleName" maxlength="300" placeholder="例如：Java 后端"></label>
            <label>城市<input v-model.trim="form.location" maxlength="300" placeholder="例如：北京"></label>
            <label>截止时间<input v-model="form.deadline" type="date"></label>
            <label class="wide">备注<textarea v-model.trim="form.notes" maxlength="10000" rows="3" placeholder="面试安排、准备事项等"></textarea></label>
          </div>
        </details>

        <footer>
          <button v-if="form.id" type="button" class="danger" @click="remove">删除</button>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { applicationApi } from '@/api/career'
import WorkspaceFrame from '@/components/WorkspaceFrame.vue'

const statusOptions = [
  { value: 'TO_APPLY', label: '待投递', tone: 'neutral' },
  { value: 'APPLIED', label: '已投递', tone: 'blue' },
  { value: 'ASSESSMENT', label: '测评 / 笔试', tone: 'amber' },
  { value: 'INTERVIEW_1', label: '一面', tone: 'cyan' },
  { value: 'INTERVIEW_2', label: '二面', tone: 'indigo' },
  { value: 'INTERVIEW_3', label: '三面', tone: 'purple' },
  { value: 'INTERVIEW_FINAL', label: '终面', tone: 'purple' },
  { value: 'HR_INTERVIEW', label: 'HR 面', tone: 'pink' },
  { value: 'NEGOTIATION', label: '谈薪', tone: 'orange' },
  { value: 'OFFER', label: 'Offer', tone: 'green' },
  { value: 'REJECTED', label: '未通过', tone: 'red' },
  { value: 'WITHDRAWN', label: '已放弃', tone: 'muted' },
]

const items = ref([])
const keyword = ref('')
const statusFilter = ref('')
const loading = ref(true)
const dialogOpen = ref(false)
const saving = ref(false)
const updatingStatusId = ref(null)
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
const total = computed(() => items.value.length)

function statusMeta(value) {
  return statusOptions.find((status) => status.value === value) || statusOptions[0]
}

async function load() {
  loading.value = true
  try {
    const data = await applicationApi.list({
      keyword: keyword.value,
      status: statusFilter.value,
    })
    items.value = data.items || []
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '投递记录加载失败')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  keyword.value = ''
  statusFilter.value = ''
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
    await ElMessageBox.confirm('删除后不可恢复，确认删除这条投递记录？', '删除投递', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
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

onMounted(load)
</script>

<style scoped>
.application-main {
  width: min(1320px, calc(100% - 40px));
  margin: 24px auto 48px;
}

.application-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.application-heading p {
  margin: 0 0 4px;
  color: var(--xzm-brand);
  font-size: 0.63rem;
  font-weight: 800;
  letter-spacing: 0.16em;
}

.application-heading h1 {
  margin: 0;
  color: var(--xzm-text-primary);
  font-size: clamp(1.45rem, 2.5vw, 2rem);
  line-height: 1.2;
  letter-spacing: -0.035em;
}

.application-heading > span {
  color: var(--xzm-text-tertiary);
  font-size: 0.76rem;
}

.sheet-card {
  overflow: hidden;
  border: 1px solid var(--xzm-border-color);
  border-radius: 14px;
  background: var(--xzm-surface-elevated);
  box-shadow: 0 10px 30px rgba(31, 52, 81, 0.05);
}

.sheet-toolbar {
  display: flex;
  min-height: 64px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--xzm-border-color);
}

.sheet-toolbar > div {
  display: grid;
  gap: 2px;
}

.sheet-toolbar h2 {
  margin: 0;
  color: var(--xzm-text-primary);
  font-size: 0.88rem;
}

.sheet-toolbar > div span {
  color: var(--xzm-text-tertiary);
  font-size: 0.66rem;
}

.sheet-filters {
  display: flex;
  align-items: center;
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
  width: min(260px, 24vw);
  height: 36px;
  padding: 0 11px;
}

.sheet-filters select {
  height: 36px;
  padding: 0 28px 0 10px;
}

.primary,
.sheet-filters button {
  height: 36px;
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
  max-height: calc(100vh - 230px);
  overflow: auto;
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
  height: 58px;
  padding: 0 14px;
  border-bottom: 1px solid var(--xzm-border-color);
  color: var(--xzm-text-secondary);
  text-align: left;
  vertical-align: middle;
}

.application-table th {
  position: sticky;
  top: 0;
  z-index: 3;
  height: 42px;
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
  background: color-mix(in srgb, var(--xzm-brand) 4%, var(--xzm-surface-elevated));
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
  background: #fff;
}

.status--blue { --status-bg: #e8f1ff; --status-text: #215da8; --status-dot: #3b82f6; }
.status--amber { --status-bg: #fff4d9; --status-text: #8d5e09; --status-dot: #e3a008; }
.status--cyan { --status-bg: #e4f8fa; --status-text: #15727b; --status-dot: #20a7b5; }
.status--indigo { --status-bg: #ebefff; --status-text: #4858a9; --status-dot: #6574d9; }
.status--purple { --status-bg: #f0eaff; --status-text: #6947a8; --status-dot: #8b5bd4; }
.status--pink { --status-bg: #fdebf3; --status-text: #9c426a; --status-dot: #d85c94; }
.status--orange { --status-bg: #fff0df; --status-text: #9b5917; --status-dot: #ed8b2f; }
.status--green { --status-bg: #e7f7ed; --status-text: #247146; --status-dot: #33a665; }
.status--red { --status-bg: #fdecea; --status-text: #a0443d; --status-dot: #dc6259; }
.status--muted { --status-bg: #f0f1f3; --status-text: #737b87; --status-dot: #9aa1ab; }

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
  border: 1px solid color-mix(in srgb, var(--xzm-danger) 55%, var(--xzm-border-color));
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
    align-items: stretch;
    flex-direction: column;
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
    width: calc(100% - 24px);
    margin-top: 18px;
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

  .application-table-wrap {
    max-height: calc(100vh - 280px);
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
</style>
