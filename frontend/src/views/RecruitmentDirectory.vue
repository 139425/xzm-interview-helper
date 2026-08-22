<template>
  <WorkspaceFrame
    mode="recruitment"
    title="秋招信息"
    eyebrow="CAREER DIRECTORY"
    mark="职"
  >
    <template #status>
      <div class="jobs-sync" :class="{ 'is-running': summary.running }">
        <i aria-hidden="true"></i>
        {{ syncText }}
      </div>
    </template>
    <template #actions>
      <router-link class="jobs-tracker" to="/applications"
        >投递追踪</router-link
      >
    </template>

    <main class="jobs-main">
      <section class="jobs-overview" aria-label="招聘信息概览">
        <div class="jobs-overview__title">
          <h1>招聘信息汇总</h1>
          <span>每日自动更新</span>
        </div>
        <dl class="jobs-stats">
          <div>
            <dt>{{ number(summary.newToday) }}</dt>
            <dd>今日新增</dd>
          </div>
          <div>
            <dt>{{ number(summary.newWeek) }}</dt>
            <dd>近 7 天</dd>
          </div>
          <div>
            <dt>{{ number(summary.total) }}</dt>
            <dd>全部岗位</dd>
          </div>
          <div>
            <dt>{{ number(summary.sourceCount) }}</dt>
            <dd>信息来源</dd>
          </div>
        </dl>
      </section>

      <section class="jobs-controls" aria-label="招聘信息筛选">
        <div class="jobs-tabs" role="tablist" aria-label="行业分类">
          <button
            v-for="category in categories"
            :key="category.value"
            type="button"
            role="tab"
            :aria-selected="filters.industry === category.value"
            :class="{ 'is-active': filters.industry === category.value }"
            @click="setIndustry(category.value)"
          >
            {{ category.label }}
          </button>
        </div>

        <form
          class="jobs-filterbar"
          role="search"
          @submit.prevent="applyKeyword"
        >
          <label class="jobs-search">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="11" cy="11" r="7" />
              <path d="m20 20-4-4" />
            </svg>
            <span class="sr-only">搜索企业或岗位</span>
            <input
              v-model.trim="draftKeyword"
              type="search"
              autocomplete="off"
              placeholder="搜索企业或岗位"
            />
          </label>
          <label>
            <span class="sr-only">工作城市</span>
            <select v-model="filters.city">
              <option value="">全部城市</option>
              <option
                v-for="item in facet('cities')"
                :key="item.value"
                :value="item.value"
              >
                {{ item.value }}
              </option>
            </select>
          </label>
          <label>
            <span class="sr-only">招聘批次</span>
            <select v-model="filters.recruitmentType">
              <option value="">全部批次</option>
              <option
                v-for="item in facet('recruitmentTypes')"
                :key="item.value"
                :value="item.value"
              >
                {{ item.value }}
              </option>
            </select>
          </label>
          <label>
            <span class="sr-only">企业性质</span>
            <select v-model="filters.companyType">
              <option value="">企业性质</option>
              <option
                v-for="item in facet('companyTypes')"
                :key="item.value"
                :value="item.value"
              >
                {{ item.value }}
              </option>
            </select>
          </label>
          <label>
            <span class="sr-only">岗位方向</span>
            <select v-model="filters.jobTrack">
              <option value="">全部方向</option>
              <option
                v-for="item in facet('jobTracks')"
                :key="item.value"
                :value="item.value"
              >
                {{ item.value }}
              </option>
            </select>
          </label>
          <label>
            <span class="sr-only">截止时间</span>
            <select v-model="filters.deadlineWithinDays">
              <option value="">全部截止时间</option>
              <option value="7">7 天内截止</option>
              <option value="14">14 天内截止</option>
              <option value="30">30 天内截止</option>
            </select>
          </label>
          <label>
            <span class="sr-only">信息来源</span>
            <select v-model="filters.sourceKind">
              <option value="">全部来源</option>
              <option
                v-for="item in sourceOptions"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </option>
            </select>
          </label>
          <label class="jobs-check">
            <input v-model="filters.freshOnly" type="checkbox" />
            <span aria-hidden="true"></span>
            今日新增
          </label>
          <button type="submit" class="jobs-submit">搜索</button>
          <button
            v-if="hasFilters"
            type="button"
            class="jobs-reset"
            @click="resetFilters"
          >
            重置（{{ activeFilterCount }}）
          </button>
        </form>
      </section>

      <section class="jobs-results" aria-labelledby="jobs-result-title">
        <header class="jobs-results__head">
          <h2 id="jobs-result-title">共 {{ number(total) }} 条</h2>
          <label>
            <span>排序</span>
            <select v-model="filters.sort">
              <option value="latest">公告最新</option>
              <option value="newlyAdded">收录最新</option>
              <option value="authority">官网优先</option>
              <option value="deadline">截止最近</option>
            </select>
          </label>
        </header>

        <div class="sr-only" role="status" aria-live="polite">
          {{ liveMessage }}
        </div>

        <div v-if="error" class="jobs-state" role="alert">
          <strong>招聘信息暂时加载失败</strong>
          <button type="button" @click="load(true)">重新加载</button>
        </div>

        <div v-else class="jobs-table-wrap">
          <div class="jobs-table" role="table" aria-label="招聘信息列表">
            <div class="jobs-table__header" role="row">
              <span role="columnheader">更新</span>
              <span role="columnheader">企业</span>
              <span role="columnheader">招聘信息</span>
              <span role="columnheader">行业</span>
              <span role="columnheader">工作城市</span>
              <span role="columnheader">批次 / 对象</span>
              <span role="columnheader">截止</span>
              <span role="columnheader">来源</span>
              <span role="columnheader">操作</span>
            </div>

            <div v-if="loading" class="jobs-loading" aria-hidden="true">
              <span v-for="index in 8" :key="index"></span>
            </div>

            <div v-else-if="!items.length" class="jobs-state">
              <strong>没有符合条件的信息</strong>
              <button type="button" @click="resetFilters">查看全部</button>
            </div>

            <article
              v-for="item in items"
              v-else
              :key="item.id"
              class="jobs-row"
              role="row"
            >
              <div class="jobs-date" role="cell" data-label="更新">
                <time :datetime="item.publishedDate || item.firstSeenAt">{{
                  shortDate(item.publishedDate || item.firstSeenAt)
                }}</time>
                <span v-if="isNew(item)">NEW</span>
              </div>
              <div class="jobs-company" role="cell" data-label="企业">
                <span class="jobs-company__mark" aria-hidden="true">{{
                  companyMark(item.company)
                }}</span>
                <strong :title="item.company">{{ item.company }}</strong>
              </div>
              <div class="jobs-position" role="cell" data-label="招聘信息">
                <strong :title="item.title">{{ item.title }}</strong>
                <span :title="item.positions">{{
                  item.positions || "岗位以原始公告为准"
                }}</span>
                <div v-if="item.jobTrack" class="jobs-position__meta">
                  <span class="jobs-track">{{ item.jobTrack }}</span>
                </div>
              </div>
              <div class="jobs-industry" role="cell" data-label="行业">
                {{ item.industry || "其他行业" }}
              </div>
              <div
                class="jobs-location"
                role="cell"
                data-label="工作城市"
                :title="item.locations"
              >
                {{ item.locations || "以公告为准" }}
              </div>
              <div class="jobs-batch" role="cell" data-label="批次 / 对象">
                <strong>{{ item.recruitmentType || "校园招聘" }}</strong>
                <span>{{ item.targetGraduates || "应届毕业生" }}</span>
              </div>
              <div
                class="jobs-deadline"
                role="cell"
                data-label="截止"
                :class="{
                  'is-urgent': isUrgent(item.deadlineDate || item.deadline),
                }"
              >
                {{ item.deadline || item.deadlineDate || "以公告为准" }}
              </div>
              <div class="jobs-source" role="cell" data-label="来源">
                <span :class="`is-${sourceMeta(item.sourceKind).tone}`">{{
                  sourceMeta(item.sourceKind).short
                }}</span>
                <small :title="item.sourceName">{{ item.sourceName }}</small>
              </div>
              <div class="jobs-actions" role="cell" data-label="操作">
                <a
                  v-if="safeUrl(item.announcementUrl)"
                  :href="safeUrl(item.announcementUrl)"
                  target="_blank"
                  rel="noopener noreferrer"
                  >公告</a
                >
                <a
                  v-if="safeUrl(item.applyUrl || item.announcementUrl)"
                  class="is-primary"
                  :href="safeUrl(item.applyUrl || item.announcementUrl)"
                  target="_blank"
                  rel="noopener noreferrer"
                  >投递</a
                >
                <button
                  type="button"
                  :disabled="addingId === item.id"
                  @click="addToTracker(item)"
                >
                  {{ addingId === item.id ? "录入中" : "＋录入" }}
                </button>
              </div>
            </article>
          </div>
        </div>

        <nav
          v-if="totalPages > 1"
          class="jobs-pagination"
          aria-label="招聘信息分页"
        >
          <button
            type="button"
            :disabled="page <= 1 || loading"
            @click="goPage(page - 1)"
          >
            上一页
          </button>
          <span>{{ page }} / {{ totalPages }}</span>
          <button
            type="button"
            :disabled="page >= totalPages || loading"
            @click="goPage(page + 1)"
          >
            下一页
          </button>
        </nav>
      </section>
    </main>
  </WorkspaceFrame>
</template>

<script setup>
import {
  computed,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from "vue";
import { useRoute, useRouter } from "vue-router";
import { recruitmentApi } from "@/api/recruitment";
import { applicationApi } from "@/api/career";
import WorkspaceFrame from "@/components/WorkspaceFrame.vue";
import { ElMessage } from "element-plus";

const route = useRoute();
const router = useRouter();
const addingId = ref(null);
const PAGE_SIZE = 30;
const categories = [
  { label: "最新招聘", value: "" },
  { label: "互联网 / AI", value: "IT/互联网" },
  { label: "硬件 / 半导体", value: "硬件/半导体" },
  { label: "国企央企", value: "国企央企" },
  { label: "金融", value: "金融行业" },
  { label: "汽车", value: "汽车/自动驾驶" },
  { label: "游戏", value: "游戏" },
  { label: "制造", value: "机械/制造业" },
  { label: "消费", value: "消费生活" },
  { label: "医疗", value: "医疗健康" },
];
const sourceKinds = {
  OFFICIAL: { label: "企业官网", short: "官网", tone: "official" },
  GOVERNMENT: { label: "政府部门", short: "政府", tone: "government" },
  PUBLIC_EMPLOYMENT: { label: "公共就业平台", short: "公共", tone: "public" },
  AGGREGATOR: { label: "求职平台", short: "平台", tone: "aggregate" },
  UNIVERSITY: { label: "高校就业网", short: "高校", tone: "university" },
  WECHAT: { label: "微信公众号", short: "公众号", tone: "wechat" },
  WEB_SEARCH: { label: "公开检索", short: "检索", tone: "search" },
};

const filters = reactive({
  keyword: String(route.query.keyword || ""),
  industry: String(route.query.industry || ""),
  city: String(route.query.city || ""),
  recruitmentType: String(route.query.recruitmentType || ""),
  companyType: String(route.query.companyType || ""),
  jobTrack: String(route.query.jobTrack || ""),
  deadlineWithinDays: String(route.query.deadlineWithinDays || ""),
  sourceKind: String(route.query.sourceKind || ""),
  freshOnly: route.query.freshOnly === "true",
  sort: String(route.query.sort || "latest"),
});
const draftKeyword = ref(filters.keyword);
const items = ref([]);
const total = ref(0);
const page = ref(Math.max(1, Number(route.query.page || 1)));
const facets = ref({});
const summary = reactive({
  total: 0,
  newToday: 0,
  newWeek: 0,
  sourceCount: 0,
  running: false,
  lastUpdated: null,
});
const loading = ref(true);
const error = ref("");
const liveMessage = ref("正在加载招聘信息");
let requestController = null;
let filterTimer = null;
let initialized = false;

const totalPages = computed(() =>
  Math.max(1, Math.ceil(total.value / PAGE_SIZE)),
);
const sourceOptions = computed(() =>
  facet("sourceKinds").map((item) => ({
    value: item.value,
    label: sourceMeta(item.value).label,
  })),
);
const activeFilterCount = computed(
  () =>
    [
      filters.keyword,
      filters.industry,
      filters.city,
      filters.recruitmentType,
      filters.companyType,
      filters.jobTrack,
      filters.deadlineWithinDays,
      filters.sourceKind,
      filters.freshOnly,
    ].filter(Boolean).length,
);
const hasFilters = computed(() => activeFilterCount.value > 0);
const syncText = computed(() => {
  if (summary.running) return "正在更新";
  if (!summary.lastUpdated) return "等待首次同步";
  return `更新于 ${dateTime(summary.lastUpdated)}`;
});

function facet(key) {
  return Array.isArray(facets.value[key]) ? facets.value[key] : [];
}

function sourceMeta(kind) {
  return sourceKinds[kind] || sourceKinds.WEB_SEARCH;
}

function params() {
  return { page: page.value, size: PAGE_SIZE, ...filters };
}

function syncUrl() {
  const query = {};
  Object.entries({ page: page.value, ...filters }).forEach(([key, value]) => {
    if (
      value !== "" &&
      value !== false &&
      !(key === "page" && value === 1) &&
      !(key === "sort" && value === "latest")
    ) {
      query[key] = String(value);
    }
  });
  router.replace({ query }).catch(() => {});
}

async function load(force = false) {
  requestController?.abort();
  const controller = new AbortController();
  requestController = controller;
  loading.value = true;
  error.value = "";
  try {
    const data = await recruitmentApi.list(params(), {
      signal: controller.signal,
      force,
    });
    items.value = data.items || [];
    total.value = Number(data.total || 0);
    Object.assign(summary, data.summary || {});
    liveMessage.value = `已加载第 ${page.value} 页，共 ${total.value} 条招聘信息`;
  } catch (requestError) {
    if (
      requestError?.name === "CanceledError" ||
      requestError?.name === "AbortError"
    )
      return;
    error.value = "load_failed";
    liveMessage.value = "招聘信息加载失败";
  } finally {
    if (requestController === controller) loading.value = false;
  }
}

async function loadFacets() {
  try {
    facets.value = await recruitmentApi.facets({
      signal: requestController?.signal,
    });
  } catch {
    facets.value = {};
  }
}

function applyKeyword() {
  filters.keyword = draftKeyword.value;
}

function setIndustry(value) {
  filters.industry = value;
}

function resetFilters() {
  Object.assign(filters, {
    keyword: "",
    industry: "",
    city: "",
    recruitmentType: "",
    companyType: "",
    jobTrack: "",
    deadlineWithinDays: "",
    sourceKind: "",
    freshOnly: false,
    sort: "latest",
  });
  draftKeyword.value = "";
}

function goPage(value) {
  page.value = value;
  window.scrollTo({ top: 0, behavior: "smooth" });
  load();
  syncUrl();
}

function number(value) {
  return new Intl.NumberFormat("zh-CN").format(Number(value || 0));
}

function shortDate(value) {
  if (!value) return "--";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "--";
  return `${String(date.getMonth() + 1).padStart(2, "0")}.${String(date.getDate()).padStart(2, "0")}`;
}

function dateTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "--";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(date);
}

function companyMark(company) {
  return (
    String(company || "企")
      .replace(/[^\p{L}\p{N}]/gu, "")
      .slice(0, 1) || "企"
  );
}

function isNew(item) {
  if (!item.firstSeenAt) return false;
  const seen = new Date(item.firstSeenAt).getTime();
  return Number.isFinite(seen) && Date.now() - seen < 24 * 60 * 60 * 1000;
}

function isUrgent(deadline) {
  if (!deadline) return false;
  const match = String(deadline).match(
    /(20\d{2})[-/.年](\d{1,2})[-/.月](\d{1,2})/,
  );
  if (!match) return false;
  const end = new Date(
    Number(match[1]),
    Number(match[2]) - 1,
    Number(match[3]),
    23,
    59,
    59,
  );
  const days = (end.getTime() - Date.now()) / 86400000;
  return days >= 0 && days <= 7;
}

function safeUrl(value) {
  try {
    const url = new URL(value);
    return ["http:", "https:"].includes(url.protocol) ? url.href : "";
  } catch {
    return "";
  }
}

async function addToTracker(item) {
  addingId.value = item.id;
  try {
    await applicationApi.addFromRecruitment(item.id);
    ElMessage.success("已录入投递表");
  } catch (requestError) {
    if (requestError?.response?.status === 401) {
      ElMessage.warning("请先登录后再添加投递追踪");
      return;
    }
    ElMessage.error(
      requestError?.response?.data?.message || "添加失败，请稍后重试",
    );
  } finally {
    addingId.value = null;
  }
}

watch(
  filters,
  () => {
    if (!initialized) return;
    clearTimeout(filterTimer);
    filterTimer = setTimeout(() => {
      page.value = 1;
      syncUrl();
      load();
    }, 180);
  },
  { deep: true },
);

onMounted(async () => {
  await Promise.all([load(), loadFacets()]);
  initialized = true;
});

onBeforeUnmount(() => {
  requestController?.abort();
  clearTimeout(filterTimer);
});
</script>

<style scoped>
.jobs-tracker {
  display: inline-flex;
  align-items: center;
  height: 34px;
  padding: 0 12px;
  border: 1px solid #cfdcf2;
  border-radius: 8px;
  color: #1769e0;
  background: #f7faff;
  font-size: 11px;
  font-weight: 700;
  text-decoration: none;
}
.jobs-sync {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #667386;
  font-size: 12px;
}
.jobs-sync i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #26a269;
  box-shadow: 0 0 0 4px #e9f7f0;
}
.jobs-sync.is-running i {
  animation: jobs-pulse 1s ease-in-out infinite;
}
.jobs-main {
  width: min(1480px, calc(100% - 40px));
  margin: 26px auto 50px;
}
.jobs-overview,
.jobs-controls,
.jobs-results {
  border: 1px solid var(--xzm-border-color);
  background: var(--xzm-surface-elevated);
}
.jobs-overview {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 104px;
  padding: 0 28px;
  border-radius: 12px;
}
.jobs-overview__title {
  display: flex;
  align-items: baseline;
  gap: 13px;
}
.jobs-overview h1 {
  margin: 0;
  font-size: 23px;
  letter-spacing: -0.035em;
}
.jobs-overview__title span {
  color: #718096;
  font-size: 12px;
}
.jobs-stats {
  display: grid;
  grid-template-columns: repeat(4, 116px);
  margin: 0;
}
.jobs-stats div {
  text-align: center;
  border-left: 1px solid #edf0f4;
}
.jobs-stats dt {
  color: #1769e0;
  font-size: 22px;
  font-weight: 760;
  font-variant-numeric: tabular-nums;
}
.jobs-stats dd {
  margin: 5px 0 0;
  color: #7a8697;
  font-size: 11px;
}
.jobs-controls {
  margin-top: 16px;
  border-radius: 12px;
  overflow: hidden;
}
.jobs-tabs {
  display: flex;
  gap: 3px;
  padding: 0 15px;
  overflow-x: auto;
  border-bottom: 1px solid #e6eaf0;
  scrollbar-width: none;
}
.jobs-tabs::-webkit-scrollbar {
  display: none;
}
.jobs-tabs button {
  flex: 0 0 auto;
  height: 53px;
  padding: 0 15px;
  border: 0;
  border-bottom: 2px solid transparent;
  color: #4e5a6b;
  background: transparent;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.jobs-tabs button:hover {
  color: #1769e0;
}
.jobs-tabs button.is-active {
  color: #1769e0;
  border-bottom-color: #1769e0;
  font-weight: 700;
}
.jobs-filterbar {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 15px;
}
.jobs-filterbar select,
.jobs-search {
  height: 38px;
  border: 1px solid var(--xzm-border-color);
  border-radius: 7px;
  background: var(--xzm-surface-control);
}
.jobs-filterbar select {
  min-width: 118px;
  padding: 0 31px 0 11px;
  color: #3f4b5e;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.jobs-search {
  display: flex;
  flex: 1 1 240px;
  min-width: 190px;
  align-items: center;
  gap: 8px;
  padding: 0 11px;
}
.jobs-search:focus-within,
.jobs-filterbar select:focus {
  border-color: #1769e0;
  outline: 3px solid #e7f0ff;
}
.jobs-search svg {
  width: 17px;
  flex: 0 0 auto;
  fill: none;
  stroke: #8792a3;
  stroke-width: 1.8;
}
.jobs-search input {
  width: 100%;
  border: 0;
  outline: 0;
  color: #172033;
  background: transparent;
  font: inherit;
  font-size: 13px;
}
.jobs-search input::placeholder {
  color: #9aa4b2;
}
.jobs-check {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 7px;
  color: #4c5869;
  font-size: 12px;
  cursor: pointer;
}
.jobs-check input {
  position: absolute;
  opacity: 0;
}
.jobs-check span {
  width: 15px;
  height: 15px;
  border: 1px solid #cfd6df;
  border-radius: 4px;
}
.jobs-check input:checked + span {
  border-color: #1769e0;
  background: #1769e0
    url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 16 16'%3E%3Cpath fill='none' stroke='white' stroke-width='2' d='m3 8 3 3 7-7'/%3E%3C/svg%3E")
    center/13px;
}
.jobs-submit,
.jobs-reset {
  height: 38px;
  padding: 0 16px;
  border-radius: 7px;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.jobs-submit {
  border: 1px solid #1769e0;
  color: #fff;
  background: #1769e0;
  font-weight: 700;
}
.jobs-submit:hover {
  background: #0f59c5;
}
.jobs-reset {
  border: 0;
  color: #728096;
  background: transparent;
}
.jobs-reset:hover {
  color: #1769e0;
}
.jobs-results {
  margin-top: 16px;
  border-radius: 12px;
  overflow: hidden;
}
.jobs-results__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 55px;
  padding: 0 18px;
  border-bottom: 1px solid #e5e9ef;
}
.jobs-results__head h2 {
  margin: 0;
  font-size: 13px;
  font-weight: 650;
}
.jobs-results__head label {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #8690a0;
  font-size: 11px;
}
.jobs-results__head select {
  border: 0;
  color: #445063;
  background: transparent;
  font: inherit;
  font-size: 12px;
  outline: 0;
}
.jobs-table-wrap {
  overflow-x: auto;
}
.jobs-table {
  min-width: 1160px;
}
.jobs-table__header,
.jobs-row {
  display: grid;
  grid-template-columns: 70px 130px minmax(
      250px,
      1.9fr
    ) 105px 130px 120px 100px 105px 92px;
  column-gap: 14px;
  align-items: center;
  padding: 0 17px;
}
.jobs-table__header {
  min-height: 48px;
  color: #69768a;
  background: #f8f9fb;
  font-size: 11px;
  font-weight: 650;
}
.jobs-row {
  min-height: 88px;
  border-top: 1px solid #edf0f4;
  font-size: 12px;
  transition: background-color 0.14s ease;
}
.jobs-row:first-of-type {
  border-top: 0;
}
.jobs-row:hover {
  background: #f8fbff;
}
.jobs-date {
  color: #59667a;
  font-variant-numeric: tabular-nums;
}
.jobs-date span {
  display: block;
  width: fit-content;
  margin-top: 5px;
  padding: 2px 5px;
  border-radius: 3px;
  color: #d14427;
  background: #fff0eb;
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.06em;
}
.jobs-company {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  align-items: center;
  gap: 9px;
  min-width: 0;
}
.jobs-company__mark {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border: 1px solid #dfe5ed;
  border-radius: 8px;
  color: #1769e0;
  background: #f4f8ff;
  font-weight: 750;
}
.jobs-company strong,
.jobs-position strong,
.jobs-position span,
.jobs-location,
.jobs-source small {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.jobs-company strong {
  font-size: 13px;
}
.jobs-position {
  display: grid;
  min-width: 0;
  gap: 7px;
}
.jobs-position strong {
  color: #253146;
  font-size: 12px;
}
.jobs-position span {
  color: #7a8596;
  font-size: 11px;
}
.jobs-position__meta {
  display: flex;
  min-width: 0;
}
.jobs-position .jobs-track {
  width: fit-content;
  max-width: 100%;
  padding: 2px 6px;
  border: 1px solid #dce8fb;
  border-radius: 4px;
  color: #315a8d;
  background: #f2f7ff;
  font-size: 9px;
  font-weight: 700;
  line-height: 1.35;
}
.jobs-industry {
  color: #59667a;
}
.jobs-location {
  color: #4e5b6e;
}
.jobs-batch {
  display: grid;
  gap: 5px;
}
.jobs-batch strong {
  font-size: 11px;
}
.jobs-batch span {
  color: #7a8596;
  font-size: 10px;
}
.jobs-deadline {
  color: #687589;
  font-size: 11px;
}
.jobs-deadline.is-urgent {
  color: #c23c20;
  font-weight: 700;
}
.jobs-source {
  display: grid;
  min-width: 0;
  gap: 5px;
}
.jobs-source > span {
  width: fit-content;
  padding: 2px 6px;
  border-radius: 4px;
  color: #315a8d;
  background: #eaf2fd;
  font-size: 9px;
  font-weight: 700;
}
.jobs-source > span.is-official {
  color: #17633c;
  background: #e7f5ed;
}
.jobs-source > span.is-government,
.jobs-source > span.is-public {
  color: #8b5412;
  background: #fff2dd;
}
.jobs-source > span.is-wechat {
  color: #087941;
  background: #e7f7ef;
}
.jobs-source > span.is-university {
  color: #5b4a94;
  background: #f0edfb;
}
.jobs-source small {
  color: #8993a2;
  font-size: 9px;
}
.jobs-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.jobs-actions a,
.jobs-actions button {
  display: grid;
  min-width: 36px;
  height: 28px;
  padding: 0 7px;
  place-items: center;
  border: 1px solid #d8dfe8;
  border-radius: 6px;
  color: #566276;
  background: var(--xzm-surface-control);
  font: inherit;
  font-size: 10px;
  text-decoration: none;
  cursor: pointer;
}
.jobs-actions a:hover,
.jobs-actions button:hover {
  border-color: #1769e0;
  color: #1769e0;
}
.jobs-actions a.is-primary {
  border-color: #1769e0;
  color: #fff;
  background: #1769e0;
}
.jobs-actions button:disabled {
  cursor: wait;
  opacity: 0.55;
}
.jobs-loading {
  padding: 5px 17px 16px;
}
.jobs-loading span {
  display: block;
  height: 74px;
  margin-top: 10px;
  border-radius: 5px;
  background: linear-gradient(90deg, #f2f4f7 25%, #fafbfc 50%, #f2f4f7 75%);
  background-size: 200% 100%;
  animation: jobs-shimmer 1.2s infinite;
}
.jobs-state {
  display: flex;
  min-height: 220px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 13px;
  color: #69768a;
}
.jobs-state strong {
  color: #354156;
  font-size: 14px;
}
.jobs-state button {
  border: 0;
  color: #1769e0;
  background: transparent;
  cursor: pointer;
}
.jobs-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  min-height: 64px;
  border-top: 1px solid #edf0f4;
  color: #7b8798;
  font-size: 11px;
}
.jobs-pagination button {
  height: 32px;
  padding: 0 13px;
  border: 1px solid #dbe1e9;
  border-radius: 6px;
  color: #425066;
  background: var(--xzm-surface-control);
  cursor: pointer;
}
.jobs-pagination button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
@keyframes jobs-shimmer {
  to {
    background-position: -200% 0;
  }
}
@keyframes jobs-pulse {
  50% {
    transform: scale(0.72);
    opacity: 0.45;
  }
}
@media (max-width: 1280px) {
  .jobs-filterbar {
    flex-wrap: wrap;
  }
  .jobs-search {
    flex-basis: 100%;
  }
}
@media (max-width: 1120px) {
  .jobs-stats {
    grid-template-columns: repeat(4, 92px);
  }
}
@media (max-width: 760px) {
  .jobs-sync {
    display: none;
  }
  .jobs-tracker {
    height: 32px;
    padding: 0 9px;
  }
  .jobs-main {
    width: calc(100% - 24px);
    margin-top: 12px;
  }
  .jobs-overview {
    display: block;
    padding: 18px;
  }
  .jobs-overview__title {
    justify-content: space-between;
  }
  .jobs-overview h1 {
    font-size: 20px;
  }
  .jobs-stats {
    grid-template-columns: repeat(4, 1fr);
    margin-top: 19px;
  }
  .jobs-stats div:first-child {
    border-left: 0;
  }
  .jobs-stats dt {
    font-size: 18px;
  }
  .jobs-filterbar > label:not(.jobs-search):not(.jobs-check) {
    flex: 1 1 calc(50% - 9px);
    min-width: 0;
  }
  .jobs-filterbar select {
    width: 100%;
    min-width: 0;
  }
  .jobs-check {
    margin-right: auto;
  }
  .jobs-submit {
    min-width: 74px;
  }
  .jobs-table {
    min-width: 0;
  }
  .jobs-table__header {
    display: none;
  }
  .jobs-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 14px 16px;
    min-height: 0;
    padding: 18px;
  }
  .jobs-row > div {
    min-width: 0;
  }
  .jobs-row > div::before {
    display: block;
    margin-bottom: 6px;
    color: #98a1af;
    font-size: 9px;
    content: attr(data-label);
  }
  .jobs-company,
  .jobs-position,
  .jobs-actions {
    grid-column: 1 / -1;
  }
  .jobs-company::before {
    display: none !important;
  }
  .jobs-position strong,
  .jobs-position span,
  .jobs-location {
    white-space: normal;
  }
  .jobs-position .jobs-track {
    white-space: nowrap;
  }
  .jobs-actions {
    justify-content: flex-end;
  }
  .jobs-actions a {
    min-width: 54px;
    height: 34px;
  }
  .jobs-source small {
    white-space: normal;
  }
  .jobs-date span {
    display: inline-block;
    margin: 0 0 0 5px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .jobs-loading span,
  .jobs-sync.is-running i {
    animation: none;
  }
  .jobs-row {
    transition: none;
  }
}

/* V4 token bridge: keep the directory in the same candidate field system. */
.jobs-overview h1 {
  font-family: var(--xzm-font-display);
  font-size: 25px;
}
.jobs-stats dt {
  color: var(--xzm-brand);
  font-family: var(--xzm-font-data);
}
.jobs-tracker {
  border-color: color-mix(
    in srgb,
    var(--xzm-brand) 30%,
    var(--xzm-border-color)
  );
  color: var(--xzm-brand);
  background: var(--xzm-brand-soft);
}
.jobs-tabs button:hover,
.jobs-tabs button.is-active,
.jobs-reset:hover,
.jobs-state button {
  color: var(--xzm-brand);
}
.jobs-tabs button.is-active {
  border-bottom-color: var(--xzm-brand);
}
.jobs-search:focus-within,
.jobs-filterbar select:focus {
  border-color: var(--xzm-brand);
  outline-color: var(--xzm-focus-ring-soft);
}
.jobs-check input:checked + span {
  border-color: var(--xzm-brand);
  background-color: var(--xzm-brand);
}
.jobs-submit {
  border-color: var(--xzm-brand);
  background: var(--xzm-brand);
}
.jobs-submit:hover {
  background: var(--xzm-brand-hover);
}
.jobs-company__mark {
  color: var(--xzm-brand);
  background: var(--xzm-brand-soft);
}
.jobs-actions a:hover,
.jobs-actions button:hover {
  border-color: var(--xzm-brand);
  color: var(--xzm-brand);
}
.jobs-actions a.is-primary {
  border-color: var(--xzm-brand);
  background: var(--xzm-brand);
}
</style>
