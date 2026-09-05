<template>
  <main class="page scroll-page tracker-page">
    <AppHeader title="投递追踪" eyebrow="CAREER PIPELINE">
      <button class="primary-compact" type="button" @click="openCreate"><AppIcon name="plus" /> 新增</button>
    </AppHeader>

    <section class="pipeline-banner">
      <div><small>OFFER PROGRESS</small><h2>{{ activeCount }} 个机会正在推进</h2><p>更新状态，让下一步始终清楚。</p></div>
      <div class="offer-ring"><span>{{ offerCount }}</span><small>Offer</small></div>
    </section>

    <form class="mobile-search" @submit.prevent="load">
      <AppIcon name="search" /><input v-model.trim="keyword" placeholder="搜索公司、岗位或备注" /><button type="submit">搜索</button>
    </form>
    <div class="filter-chips">
      <button v-for="filter in quickFilters" :key="filter.value" type="button" :class="{ active: activeFilter === filter.value }" @click="selectFilter(filter.value)">{{ filter.label }}<b v-if="filter.value">{{ filterCount(filter.value) }}</b></button>
    </div>

    <div class="result-heading"><span>共 {{ items.length }} 条记录</span><select v-model="sort" @change="load"><option value="progress">进度优先</option><option value="updated">最近更新</option><option value="company">公司名称</option></select></div>
    <div v-if="loading" class="loading-stack"><i v-for="n in 4" :key="n"></i></div>
    <section v-else-if="items.length" class="application-cards">
      <article v-for="item in items" :key="item.id" @click="openEdit(item)">
        <div class="company-avatar" :style="avatarStyle(item.company)">{{ initials(item.company, '企') }}</div>
        <div class="application-main-info">
          <header><div><h3>{{ item.company }}</h3><p>{{ item.roleName || '岗位待补充' }}<span v-if="item.location"> · {{ item.location }}</span></p></div><span :class="`status-pill status-pill--${statusMeta(item.status).tone}`">{{ statusMeta(item.status).label }}</span></header>
          <div class="application-next"><span>下一步</span><p>{{ item.nextAction || item.notes || '暂未设置，点击补充行动计划' }}</p></div>
          <footer><small>更新于 {{ dateLabel(item.updatedAt) }}</small><div><button v-if="item.applyUrl" type="button" @click.stop="openLink(item.applyUrl)"><AppIcon name="link" />投递页</button><AppIcon name="arrow" /></div></footer>
        </div>
      </article>
    </section>
    <div v-else class="page-empty"><div><AppIcon name="pipeline" /></div><h3>还没有投递记录</h3><p>从第一份心仪岗位开始，记录每次推进。</p><button type="button" @click="openCreate">新增投递</button></div>

    <BottomSheet :open="editorOpen" :title="form.id ? '编辑投递' : '新增投递'" eyebrow="APPLICATION" @close="editorOpen = false">
      <form class="sheet-form" @submit.prevent="save">
        <label class="input-field"><span>公司名 *</span><input v-model.trim="form.company" required maxlength="200" placeholder="例如：字节跳动" /></label>
        <label class="input-field"><span>投递链接 *</span><input v-model.trim="form.applyUrl" required type="url" maxlength="1024" placeholder="https://" /></label>
        <label class="input-field"><span>投递进度</span><select v-model="form.status"><option v-for="statusItem in statuses" :key="statusItem.value" :value="statusItem.value">{{ statusItem.label }}</option></select></label>
        <div class="form-pair"><label class="input-field"><span>岗位</span><input v-model.trim="form.roleName" maxlength="300" placeholder="Java 后端" /></label><label class="input-field"><span>城市</span><input v-model.trim="form.location" maxlength="300" placeholder="北京" /></label></div>
        <label class="input-field"><span>下一步行动</span><input v-model.trim="form.nextAction" maxlength="500" placeholder="准备二面项目深挖" /></label>
        <div class="form-pair"><label class="input-field"><span>行动时间</span><input v-model="form.nextActionAt" type="datetime-local" /></label><label class="input-field"><span>截止日期</span><input v-model="form.deadline" type="date" /></label></div>
        <label class="input-field"><span>备注</span><textarea v-model.trim="form.notes" rows="3" maxlength="10000" placeholder="面试安排、联系人、准备重点等"></textarea></label>
        <div class="sheet-form-actions"><button v-if="form.id" class="danger-text" type="button" @click="removeCurrent"><AppIcon name="trash" /> 删除</button><span></span><button class="primary-action" type="submit" :disabled="saving">{{ saving ? '保存中…' : '保存投递' }}</button></div>
      </form>
    </BottomSheet>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { applicationApi, openExternal } from '@/lib/api'
import { dateLabel, initials } from '@/lib/format'
import AppHeader from '@/components/AppHeader.vue'
import AppIcon from '@/components/AppIcon.vue'
import BottomSheet from '@/components/BottomSheet.vue'

const emit = defineEmits(['toast'])
const statuses = [
  { value: 'OFFER', label: 'Offer', tone: 'green' }, { value: 'NEGOTIATION', label: '谈薪', tone: 'orange' },
  { value: 'HR_INTERVIEW', label: 'HR 面', tone: 'pink' }, { value: 'INTERVIEW_FINAL', label: '终面', tone: 'purple' },
  { value: 'INTERVIEW_3', label: '三面', tone: 'purple' }, { value: 'INTERVIEW_2', label: '二面', tone: 'indigo' },
  { value: 'INTERVIEW_1', label: '一面', tone: 'cyan' }, { value: 'ASSESSMENT', label: '测评 / 笔试', tone: 'amber' },
  { value: 'APPLIED', label: '已投递', tone: 'blue' }, { value: 'TO_APPLY', label: '未投递', tone: 'neutral' },
  { value: 'REJECTED', label: '未通过', tone: 'red' }, { value: 'WITHDRAWN', label: '已放弃', tone: 'muted' },
]
const filterGroups = {
  active: ['APPLIED', 'ASSESSMENT', 'INTERVIEW_1', 'INTERVIEW_2', 'INTERVIEW_3', 'INTERVIEW_FINAL', 'HR_INTERVIEW', 'NEGOTIATION'],
  interview: ['INTERVIEW_1', 'INTERVIEW_2', 'INTERVIEW_3', 'INTERVIEW_FINAL', 'HR_INTERVIEW'],
  offer: ['OFFER'], rejected: ['REJECTED', 'WITHDRAWN'],
}
const quickFilters = [{ value: '', label: '全部' }, { value: 'active', label: '推进中' }, { value: 'interview', label: '面试' }, { value: 'offer', label: 'Offer' }, { value: 'rejected', label: '已结束' }]
const items = ref([]); const summary = ref({}); const loading = ref(true); const saving = ref(false)
const editorOpen = ref(false); const keyword = ref(''); const activeFilter = ref(''); const sort = ref('progress')
const emptyForm = () => ({ id: null, company: '', roleName: '', status: 'APPLIED', location: '', applyUrl: '', sourceUrl: '', deadline: '', nextAction: '', nextActionAt: '', notes: '' })
const form = reactive(emptyForm())
const offerCount = computed(() => Number(summary.value.OFFER || 0))
const activeCount = computed(() => filterGroups.active.reduce((total, status) => total + Number(summary.value[status] || 0), 0))

function statusMeta(value) { return statuses.find((item) => item.value === value) || statuses[8] }
function filterCount(value) { return (filterGroups[value] || []).reduce((total, status) => total + Number(summary.value[status] || 0), 0) }
function avatarStyle(company) { const colors = ['#315AEF', '#7657D6', '#0A8F77', '#D76A24', '#C23D63']; const index = [...String(company || '')].reduce((n, char) => n + char.charCodeAt(0), 0) % colors.length; return { background: colors[index] } }
async function load() {
  loading.value = true
  try { const result = await applicationApi.list({ keyword: keyword.value, statuses: (filterGroups[activeFilter.value] || []).join(','), sort: sort.value }); items.value = result.items || []; summary.value = result.summary || {} }
  catch (error) { emit('toast', error.message, 'error') }
  finally { loading.value = false }
}
function selectFilter(value) { activeFilter.value = value; load() }
function openCreate() { Object.assign(form, emptyForm()); editorOpen.value = true }
function openEdit(item) { Object.assign(form, emptyForm(), item, { deadline: item.deadline || '', nextActionAt: item.nextActionAt?.slice(0, 16) || '' }); editorOpen.value = true }
async function save() {
  saving.value = true
  const payload = { ...form, roleName: form.roleName || '', deadline: form.deadline || null, nextActionAt: form.nextActionAt || null }
  delete payload.id
  try { if (form.id) await applicationApi.update(form.id, payload); else await applicationApi.create(payload); editorOpen.value = false; emit('toast', '投递记录已保存'); await load() }
  catch (error) { emit('toast', error.message, 'error') }
  finally { saving.value = false }
}
async function removeCurrent() {
  if (!window.confirm(`删除 ${form.company} 的投递记录？`)) return
  try { await applicationApi.remove(form.id); editorOpen.value = false; emit('toast', '投递记录已删除'); await load() }
  catch (error) { emit('toast', error.message, 'error') }
}
function openLink(url) { openExternal(url).catch((error) => emit('toast', error.message, 'error')) }
onMounted(load)
</script>
