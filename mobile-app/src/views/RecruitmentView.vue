<template>
  <main class="page scroll-page recruitment-page">
    <AppHeader title="秋招信息" eyebrow="2027 CAREER DIRECTORY">
      <button class="icon-button" type="button" aria-label="刷新" @click="refresh"><AppIcon name="refresh" /></button>
    </AppHeader>

    <section class="jobs-summary">
      <div><small>DAILY UPDATE</small><h2>今天，多 {{ number(summary.newToday) }} 个机会</h2><p>多来源聚合去重，持续追踪 2027 届秋招。</p></div>
      <dl><div><dt>{{ number(summary.total || total) }}</dt><dd>岗位</dd></div><div><dt>{{ number(summary.newWeek) }}</dt><dd>近 7 天</dd></div></dl>
    </section>

    <form class="mobile-search jobs-search-mobile" @submit.prevent="search"><AppIcon name="search" /><input v-model.trim="draftKeyword" type="search" placeholder="搜索企业、岗位或城市" /><button type="submit">搜索</button></form>
    <div class="category-scroll">
      <button v-for="category in categories" :key="category.value" type="button" :class="{ active: industry === category.value }" @click="setIndustry(category.value)">{{ category.label }}</button>
    </div>
    <div class="jobs-toolbar"><p>找到 <b>{{ number(total) }}</b> 条机会</p><label><input v-model="freshOnly" type="checkbox" @change="refresh" /><span></span>今日新增</label><select v-model="sort" @change="refresh"><option value="latest">公告最新</option><option value="newlyAdded">收录最新</option><option value="authority">官网优先</option><option value="deadline">截止最近</option></select></div>

    <div v-if="loading && !items.length" class="loading-stack"><i v-for="n in 5" :key="n"></i></div>
    <section v-else-if="items.length" class="job-cards">
      <article v-for="item in items" :key="item.id">
        <header><div class="company-avatar jobs-avatar">{{ initials(item.company, '企') }}</div><div><h3>{{ item.company }}</h3><p>{{ item.industry || '行业待确认' }}<span v-if="item.locations"> · {{ item.locations }}</span></p></div><span v-if="isNew(item)" class="new-tag">NEW</span></header>
        <h4>{{ item.title }}</h4><p class="job-positions">{{ item.positions || '岗位以原始公告为准' }}</p>
        <div class="job-tags"><span v-if="item.recruitmentType">{{ item.recruitmentType }}</span><span v-if="item.jobTrack">{{ item.jobTrack }}</span><span>{{ item.targetGraduates || '2027届' }}</span></div>
        <div class="job-deadline"><span>截止</span><strong :class="{ urgent: isUrgent(item.deadlineDate || item.deadline) }">{{ item.deadline || item.deadlineDate || '以公告为准' }}</strong><small>{{ item.sourceName || sourceLabel(item.sourceKind) }}</small></div>
        <footer>
          <button type="button" :disabled="addingId === item.id" @click="addToTracker(item)"><AppIcon name="plus" />{{ addingId === item.id ? '录入中' : '加入追踪' }}</button>
          <button class="apply-button" type="button" @click="openJob(item)">查看并投递<AppIcon name="arrow" /></button>
        </footer>
      </article>
    </section>
    <div v-else class="page-empty"><div><AppIcon name="jobs" /></div><h3>没有符合条件的机会</h3><p>换个关键词或分类试试。</p><button type="button" @click="reset">查看全部</button></div>
    <button v-if="hasMore" class="load-more" type="button" :disabled="loading" @click="loadMore">{{ loading ? '加载中…' : '继续加载' }}</button>
    <p v-else-if="items.length" class="list-end">— 已经到底了 —</p>
  </main>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { applicationApi, openExternal, recruitmentApi } from '@/lib/api'
import { initials } from '@/lib/format'
import AppHeader from '@/components/AppHeader.vue'
import AppIcon from '@/components/AppIcon.vue'

const emit = defineEmits(['toast', 'go-tracker'])
const categories = [
  { label: '最新', value: '' }, { label: '互联网 / AI', value: 'IT/互联网' }, { label: '硬件', value: '硬件/半导体' },
  { label: '国企央企', value: '国企央企' }, { label: '金融', value: '金融行业' }, { label: '汽车', value: '汽车/自动驾驶' },
  { label: '游戏', value: '游戏' }, { label: '制造', value: '机械/制造业' },
]
const items = ref([]); const total = ref(0); const page = ref(1); const hasMore = ref(false); const loading = ref(false)
const summary = ref({}); const draftKeyword = ref(''); const keyword = ref(''); const industry = ref(''); const freshOnly = ref(false); const sort = ref('latest'); const addingId = ref(null)

async function load(append = false) {
  loading.value = true
  try {
    const result = await recruitmentApi.list({ page: page.value, size: 20, keyword: keyword.value, industry: industry.value, freshOnly: freshOnly.value, sort: sort.value, targetGraduates: '2027届' })
    items.value = append ? [...items.value, ...(result.items || [])] : result.items || []
    total.value = Number(result.total || 0); hasMore.value = Boolean(result.hasMore ?? items.value.length < total.value); summary.value = result.summary || {}
  } catch (error) { emit('toast', error.message, 'error') }
  finally { loading.value = false }
}
function refresh() { page.value = 1; load(false) }
function search() { keyword.value = draftKeyword.value; refresh() }
function setIndustry(value) { industry.value = value; refresh() }
function reset() { draftKeyword.value = ''; keyword.value = ''; industry.value = ''; freshOnly.value = false; sort.value = 'latest'; refresh() }
function loadMore() { page.value += 1; load(true) }
function number(value) { return new Intl.NumberFormat('zh-CN').format(Number(value || 0)) }
function isNew(item) { const time = new Date(item.firstSeenAt).getTime(); return Number.isFinite(time) && Date.now() - time < 86_400_000 }
function isUrgent(value) { const time = new Date(value).getTime(); if (!Number.isFinite(time)) return false; const days = (time - Date.now()) / 86_400_000; return days >= 0 && days <= 7 }
function sourceLabel(kind) { return ({ OFFICIAL: '企业官网', GOVERNMENT: '政府部门', PUBLIC_EMPLOYMENT: '公共就业平台', AGGREGATOR: '求职平台', UNIVERSITY: '高校就业网', WECHAT: '微信公众号' })[kind] || '公开信息' }
function openJob(item) { openExternal(item.applyUrl || item.announcementUrl).catch((error) => emit('toast', error.message, 'error')) }
async function addToTracker(item) {
  addingId.value = item.id
  try { await applicationApi.fromRecruitment(item.id); emit('toast', '已加入投递追踪') }
  catch (error) { emit('toast', error.message, 'error') }
  finally { addingId.value = null }
}
onMounted(refresh)
</script>
