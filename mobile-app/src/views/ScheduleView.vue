<template>
  <main class="page scroll-page schedule-page">
    <AppHeader title="笔面测待办" eyebrow="NEXT UP">
      <button class="primary-compact" type="button" @click="openCreate"><AppIcon name="plus" /> 新增</button>
    </AppHeader>

    <section class="schedule-hero">
      <div><span>{{ nextItem ? relativeTime(nextItem.startAt) : '日程清空' }}</span><h2>{{ nextItem ? nextItem.company : '暂时没有安排' }}</h2><p>{{ nextItem ? `${eventMeta(nextItem.eventType).label} · ${dateLabel(nextItem.startAt, true)}` : '给自己留一点复盘与休息的时间。' }}</p></div>
      <div class="hero-date"><small>{{ nextItem ? month(nextItem.startAt) : 'ALL' }}</small><b>{{ nextItem ? day(nextItem.startAt) : '✓' }}</b></div>
    </section>

    <section class="mini-stats">
      <div><span class="stat-dot stat-dot--blue"></span><b>{{ pendingItems.length }}</b><small>待完成</small></div>
      <div><span class="stat-dot stat-dot--orange"></span><b>{{ todayCount }}</b><small>今天</small></div>
      <div><span class="stat-dot stat-dot--red"></span><b>{{ overdueCount }}</b><small>已逾期</small></div>
    </section>

    <div class="section-heading schedule-heading">
      <div><h2>接下来</h2><span>按时间顺序</span></div>
      <div class="schedule-heading-actions">
        <button class="trash-entry" type="button" @click="trashOpen = true"><AppIcon name="trash" /><span>回收站</span><b v-if="trashItems.length">{{ trashItems.length }}</b></button>
        <button type="button" aria-label="刷新日程" @click="load"><AppIcon name="refresh" /></button>
      </div>
    </div>

    <div v-if="loading" class="loading-stack"><i v-for="n in 3" :key="n"></i></div>
    <section v-else-if="pendingItems.length" class="schedule-list-mobile">
      <article v-for="item in pendingItems" :key="item.id" :class="{ overdue: isOverdue(item) }">
        <button class="schedule-check" type="button" :disabled="busyId === item.id" aria-label="标记为已完成" @click="complete(item)"><AppIcon name="check" /></button>
        <div class="schedule-card-main" @click="openEdit(item)">
          <div class="card-meta"><span :class="`event-chip event-chip--${eventMeta(item.eventType).tone}`">{{ eventMeta(item.eventType).label }}</span><em>{{ relativeTime(item.startAt) }}</em></div>
          <h3>{{ item.company }}</h3><p>{{ item.roleName || '岗位待补充' }}</p>
          <div class="schedule-time-row"><strong>{{ dateLabel(item.startAt, true) }}</strong><span v-if="item.endAt">至 {{ timeOnly(item.endAt) }}</span></div>
          <p v-if="item.notes" class="card-note">{{ item.notes }}</p>
        </div>
        <div class="schedule-card-actions">
          <button v-if="item.eventUrl" class="card-link-button" type="button" aria-label="打开笔面试链接" @click="openLink(item.eventUrl)"><AppIcon name="link" /></button>
          <button class="card-edit-button" type="button" :disabled="busyId === item.id" :aria-label="`编辑 ${item.company} 的日程`" @click="openEdit(item)"><AppIcon name="edit" /></button>
          <button class="card-delete-button" type="button" :disabled="busyId === item.id" :aria-label="`删除 ${item.company} 的日程`" @click="removeItem(item)"><AppIcon name="trash" /></button>
        </div>
      </article>
    </section>
    <div v-else class="page-empty"><div><AppIcon name="calendar" /></div><h3>日程已经清空</h3><p>新增下一场笔试、面试或测评，避免错过重要时间。</p><button type="button" @click="openCreate">添加一项安排</button></div>

    <details v-if="completedItems.length" class="completed-panel">
      <summary>已完成 <b>{{ completedItems.length }}</b><span>查看</span></summary>
      <div>
        <div v-for="item in completedItems.slice(0, 8)" :key="item.id" class="completed-item">
          <AppIcon name="check" />
          <span><strong>{{ item.company }}</strong><small>{{ eventMeta(item.eventType).label }} · {{ dateLabel(item.startAt) }}</small></span>
          <div><button type="button" @click="openEdit(item)">编辑</button><button type="button" :disabled="busyId === item.id" @click="restoreCompleted(item)">恢复</button></div>
        </div>
      </div>
    </details>

    <BottomSheet :open="editorOpen" :title="form.id ? '编辑日程' : '新增日程'" eyebrow="SCHEDULE" @close="editorOpen = false">
      <form class="sheet-form" @submit.prevent="save">
        <fieldset class="choice-row"><legend>安排类型</legend><label v-for="type in eventTypes" :key="type.value" :class="{ active: form.eventType === type.value }"><input v-model="form.eventType" type="radio" :value="type.value" />{{ type.label }}</label></fieldset>
        <label class="input-field"><span>公司名 *</span><input v-model.trim="form.company" required maxlength="200" placeholder="例如：字节跳动" /></label>
        <label class="input-field"><span>岗位名称</span><input v-model.trim="form.roleName" maxlength="300" placeholder="例如：Java 后端" /></label>
        <div class="form-pair">
          <label class="input-field"><span>开始时间 *</span><input v-model="form.startAt" required type="datetime-local" /></label>
          <label class="input-field"><span>结束时间</span><input v-model="form.endAt" type="datetime-local" /></label>
        </div>
        <label class="input-field"><span>在线链接</span><input v-model.trim="form.eventUrl" type="url" maxlength="2048" placeholder="https://" /></label>
        <label class="input-field"><span>备注</span><textarea v-model.trim="form.notes" maxlength="1000" rows="3" placeholder="准备重点、联系人等"></textarea></label>
        <div class="sheet-form-actions">
          <button v-if="form.id" class="danger-text" type="button" @click="removeCurrent"><AppIcon name="trash" /> 删除</button>
          <span></span><button class="primary-action" type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存日程' }}</button>
        </div>
      </form>
    </BottomSheet>

    <BottomSheet :open="trashOpen" title="日程回收站" eyebrow="RECYCLE BIN" @close="trashOpen = false">
      <div class="trash-sheet">
        <p class="trash-intro">删除的日程会保留 14 天，可随时恢复。</p>
        <div v-if="!trashItems.length" class="empty-mini"><AppIcon name="trash" /><p>回收站是空的</p></div>
        <article v-for="item in trashItems" :key="item.id">
          <div><strong>{{ item.company }}</strong><small>{{ eventMeta(item.eventType).label }} · {{ dateLabel(item.startAt) }}</small></div>
          <div><button type="button" :disabled="busyId === item.id" @click="restoreTrash(item)"><AppIcon name="undo" />恢复</button><button class="permanent-delete" type="button" :disabled="busyId === item.id" @click="permanentRemove(item)"><AppIcon name="trash" />永久删除</button></div>
        </article>
      </div>
    </BottomSheet>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { openExternal, scheduleApi } from '@/lib/api'
import { dateLabel, relativeTime } from '@/lib/format'
import AppHeader from '@/components/AppHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import BottomSheet from '@/components/BottomSheet.vue'

const emit = defineEmits(['toast'])
const items = ref([])
const trashItems = ref([])
const loading = ref(true)
const saving = ref(false)
const busyId = ref(null)
const editorOpen = ref(false)
const trashOpen = ref(false)
const eventTypes = [
  { value: 'WRITTEN_TEST', label: '笔试', tone: 'amber' },
  { value: 'INTERVIEW', label: '面试', tone: 'teal' },
  { value: 'ASSESSMENT', label: '测评', tone: 'purple' },
]

function localValue(date) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}
function emptyForm() {
  const date = new Date(); date.setMinutes(0, 0, 0); date.setHours(date.getHours() + 1)
  return { id: null, company: '', roleName: '', eventType: 'INTERVIEW', startAt: localValue(date), endAt: '', eventUrl: '', notes: '' }
}
const form = reactive(emptyForm())
const pendingItems = computed(() => items.value.filter((item) => !item.completedAt).sort((a, b) => new Date(a.startAt) - new Date(b.startAt)))
const completedItems = computed(() => items.value.filter((item) => item.completedAt).sort((a, b) => new Date(b.completedAt) - new Date(a.completedAt)))
const nextItem = computed(() => pendingItems.value.find((item) => !isOverdue(item)) || pendingItems.value[0])
const todayCount = computed(() => pendingItems.value.filter((item) => new Date(item.startAt).toDateString() === new Date().toDateString()).length)
const overdueCount = computed(() => pendingItems.value.filter(isOverdue).length)

function eventMeta(value) { return eventTypes.find((item) => item.value === value) || eventTypes[1] }
function isOverdue(item) { return new Date(item.startAt).getTime() < Date.now() && !item.completedAt }
function month(value) { return `${new Date(value).getMonth() + 1}月` }
function day(value) { return String(new Date(value).getDate()).padStart(2, '0') }
function timeOnly(value) { return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value)) }

async function load() {
  loading.value = true
  try {
    const data = await scheduleApi.list()
    items.value = data.items || []
    trashItems.value = data.trash || []
  }
  catch (error) { emit('toast', error.message, 'error') }
  finally { loading.value = false }
}
function openCreate() { Object.assign(form, emptyForm()); editorOpen.value = true }
function openEdit(item) {
  Object.assign(form, emptyForm(), item, { startAt: item.startAt?.slice(0, 16) || '', endAt: item.endAt?.slice(0, 16) || '' })
  editorOpen.value = true
}
async function save() {
  if (form.endAt && new Date(form.endAt) <= new Date(form.startAt)) { emit('toast', '结束时间需要晚于开始时间', 'error'); return }
  saving.value = true
  const payload = { company: form.company, roleName: form.roleName || '', eventType: form.eventType, startAt: form.startAt, endAt: form.endAt || null, eventUrl: form.eventUrl || '', notes: form.notes || '' }
  try {
    if (form.id) await scheduleApi.update(form.id, payload); else await scheduleApi.create(payload)
    editorOpen.value = false; emit('toast', '日程已保存'); await load()
  } catch (error) { emit('toast', error.message, 'error') }
  finally { saving.value = false }
}
async function complete(item) {
  busyId.value = item.id
  try { await scheduleApi.complete(item.id, true); emit('toast', '已完成，辛苦了'); await load() }
  catch (error) { emit('toast', error.message, 'error') }
  finally { busyId.value = null }
}
async function restoreCompleted(item) {
  busyId.value = item.id
  try { await scheduleApi.complete(item.id, false); emit('toast', '已恢复到待办'); await load() }
  catch (error) { emit('toast', error.message, 'error') }
  finally { busyId.value = null }
}
async function removeItem(item, closeEditor = false) {
  if (!window.confirm(`删除 ${item.company} 的日程？删除后可在回收站恢复。`)) return
  busyId.value = item.id
  try {
    await scheduleApi.remove(item.id)
    if (closeEditor) editorOpen.value = false
    emit('toast', '日程已移入回收站')
    await load()
  } catch (error) { emit('toast', error.message, 'error') }
  finally { busyId.value = null }
}
async function removeCurrent() {
  await removeItem({ id: form.id, company: form.company }, true)
}
async function restoreTrash(item) {
  busyId.value = item.id
  try { await scheduleApi.restore(item.id); emit('toast', '日程已恢复'); await load() }
  catch (error) { emit('toast', error.message, 'error') }
  finally { busyId.value = null }
}
async function permanentRemove(item) {
  if (!window.confirm(`永久删除 ${item.company} 的日程？此操作无法恢复。`)) return
  busyId.value = item.id
  try { await scheduleApi.permanentRemove(item.id); emit('toast', '日程已永久删除'); await load() }
  catch (error) { emit('toast', error.message, 'error') }
  finally { busyId.value = null }
}
function openLink(url) { openExternal(url).catch((error) => emit('toast', error.message, 'error')) }
onMounted(load)
</script>
