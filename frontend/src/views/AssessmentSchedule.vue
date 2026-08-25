<template>
  <WorkspaceFrame
    mode="schedule"
    title="笔面测待办"
    eyebrow="CAREER CALENDAR"
    mark="程"
  >
    <template #status>
      <span class="topbar-status">
        <i aria-hidden="true"></i>
        {{ pendingItems.length ? `下一场 ${nextTaskLabel}` : "当前没有待办" }}
      </span>
    </template>
    <template #actions>
      <router-link class="back-link" to="/applications">查看投递</router-link>
    </template>

    <main class="schedule-main">
      <section class="schedule-hero" aria-labelledby="schedule-title">
        <div class="schedule-hero__copy">
          <p>NEXT UP</p>
          <h1 id="schedule-title">下一场，别错过。</h1>
          <span>笔试、面试、测评集中记录，临近安排自动排在最前。</span>
        </div>
        <dl class="schedule-stats" aria-label="日程概览">
          <div>
            <dt>{{ pendingItems.length }}</dt>
            <dd>待完成</dd>
          </div>
          <div :class="{ 'has-attention': dueTodayCount }">
            <dt>{{ dueTodayCount }}</dt>
            <dd>今天</dd>
          </div>
          <div>
            <dt>{{ upcomingWeekCount }}</dt>
            <dd>近 7 天</dd>
          </div>
          <div>
            <dt>{{ completedItems.length }}</dt>
            <dd>已完成</dd>
          </div>
        </dl>
      </section>

      <div class="schedule-layout">
        <aside class="entry-panel" aria-labelledby="entry-title">
          <header class="panel-heading">
            <div>
              <small>QUICK ENTRY</small>
              <h2 id="entry-title">录入新安排</h2>
            </div>
            <span class="step-mark">01</span>
          </header>

          <form class="schedule-form" @submit.prevent="createSchedule">
            <fieldset class="type-fieldset">
              <legend>安排类型</legend>
              <div class="type-picker">
                <label
                  v-for="type in eventTypes"
                  :key="type.value"
                  :class="[
                    `tone-${type.tone}`,
                    { active: form.eventType === type.value },
                  ]"
                >
                  <input
                    v-model="form.eventType"
                    type="radio"
                    :value="type.value"
                  />
                  <span class="type-icon" aria-hidden="true">{{
                    type.mark
                  }}</span>
                  <strong>{{ type.label }}</strong>
                </label>
              </div>
            </fieldset>

            <label class="form-field">
              <span>公司名 <b>*</b></span>
              <input
                v-model.trim="form.company"
                maxlength="200"
                required
                autocomplete="organization"
                placeholder="例如：字节跳动"
              />
            </label>

            <label class="form-field">
              <span>岗位名称 <em>选填</em></span>
              <input
                v-model.trim="form.roleName"
                maxlength="300"
                placeholder="例如：后端开发工程师"
              />
            </label>

            <fieldset class="time-fieldset">
              <div class="field-title">
                <legend>日期与时间 <b>*</b></legend>
                <div class="time-mode" aria-label="选择时间形式">
                  <label :class="{ active: form.timeMode === 'point' }">
                    <input v-model="form.timeMode" type="radio" value="point" />
                    时间点
                  </label>
                  <label :class="{ active: form.timeMode === 'range' }">
                    <input v-model="form.timeMode" type="radio" value="range" />
                    时间段
                  </label>
                </div>
              </div>
              <label class="datetime-field">
                <span>{{ form.timeMode === "range" ? "开始" : "时间" }}</span>
                <input v-model="form.startAt" type="datetime-local" required />
              </label>
              <label v-if="form.timeMode === 'range'" class="datetime-field">
                <span>结束</span>
                <input v-model="form.endAt" type="datetime-local" required />
              </label>
            </fieldset>

            <label class="form-field">
              <span>备注 <em>选填</em></span>
              <textarea
                v-model.trim="form.notes"
                maxlength="1000"
                rows="3"
                placeholder="会议链接、地点或需要准备的内容"
              ></textarea>
            </label>

            <button class="submit-button" type="submit" :disabled="saving">
              <span>{{ saving ? "正在添加…" : "添加到日程" }}</span>
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M5 12h14M13 6l6 6-6 6" />
              </svg>
            </button>
            <p class="form-hint">添加后会自动按临近程度排序。</p>
          </form>
        </aside>

        <section class="timeline-panel" aria-labelledby="timeline-title">
          <header class="panel-heading timeline-heading">
            <div>
              <small>UPCOMING</small>
              <h2 id="timeline-title">接下来</h2>
            </div>
            <span class="sort-note"><i aria-hidden="true"></i> 最近优先</span>
          </header>

          <div v-if="loading" class="schedule-state" aria-live="polite">
            <span class="loading-ring" aria-hidden="true"></span>
            <strong>正在整理日程</strong>
            <p>稍等一下，很快就好。</p>
          </div>

          <div
            v-else-if="!pendingItems.length"
            class="schedule-state empty-state"
          >
            <span class="empty-calendar" aria-hidden="true">
              <i></i><b>✓</b>
            </span>
            <strong>接下来暂时没有安排</strong>
            <p>从左侧录入一场笔试、面试或测评吧。</p>
          </div>

          <ol v-else class="schedule-list">
            <li
              v-for="item in pendingItems"
              :key="item.id"
              :class="{ overdue: isOverdue(item), urgent: isUrgent(item) }"
            >
              <div class="date-block" aria-hidden="true">
                <span>{{ monthLabel(item.startAt) }}</span>
                <strong>{{ dayLabel(item.startAt) }}</strong>
                <small>{{ weekdayLabel(item.startAt) }}</small>
              </div>

              <article class="schedule-card">
                <div class="schedule-card__content">
                  <div class="schedule-tags">
                    <span
                      :class="`event-tag tone-${eventMeta(item.eventType).tone}`"
                    >
                      {{ eventMeta(item.eventType).label }}
                    </span>
                    <span :class="['proximity-tag', proximityMeta(item).tone]">
                      {{ proximityMeta(item).label }}
                    </span>
                  </div>
                  <h3>{{ item.company }}</h3>
                  <p v-if="item.roleName" class="role-name">
                    {{ item.roleName }}
                  </p>
                  <p class="schedule-time">
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <circle cx="12" cy="12" r="8.5" />
                      <path d="M12 7v5l3.5 2" />
                    </svg>
                    {{ formatScheduleTime(item) }}
                  </p>
                  <p v-if="item.notes" class="schedule-notes">
                    {{ item.notes }}
                  </p>
                </div>

                <div class="schedule-card__actions">
                  <button
                    type="button"
                    class="complete-button"
                    :disabled="busyId === item.id"
                    :aria-label="`完成 ${item.company} 的${eventMeta(item.eventType).label}`"
                    @click="setCompleted(item, true)"
                  >
                    <span aria-hidden="true">✓</span>
                    完成
                  </button>
                  <button
                    type="button"
                    class="delete-button"
                    :disabled="busyId === item.id"
                    :aria-label="`删除 ${item.company} 的日程`"
                    @click="removeSchedule(item)"
                  >
                    删除
                  </button>
                </div>
              </article>
            </li>
          </ol>

          <details v-if="completedItems.length" class="completed-section">
            <summary>
              <span>已完成</span>
              <b>{{ completedItems.length }}</b>
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="m7 10 5 5 5-5" />
              </svg>
            </summary>
            <ul>
              <li v-for="item in completedItems" :key="item.id">
                <span class="completed-check" aria-hidden="true">✓</span>
                <div>
                  <strong>{{ item.company }}</strong>
                  <small>
                    {{ eventMeta(item.eventType).label }} ·
                    {{ shortTime(item.startAt) }}
                  </small>
                </div>
                <button
                  type="button"
                  :disabled="busyId === item.id"
                  @click="setCompleted(item, false)"
                >
                  恢复
                </button>
              </li>
            </ul>
          </details>
        </section>
      </div>
    </main>
  </WorkspaceFrame>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { scheduleApi } from "@/api/career";
import WorkspaceFrame from "@/components/WorkspaceFrame.vue";

const eventTypes = [
  { value: "WRITTEN_TEST", label: "笔试", mark: "笔", tone: "amber" },
  { value: "INTERVIEW", label: "面试", mark: "面", tone: "teal" },
  { value: "ASSESSMENT", label: "测评", mark: "测", tone: "purple" },
];

const items = ref([]);
const loading = ref(true);
const saving = ref(false);
const busyId = ref(null);

function nextHourValue() {
  const date = new Date();
  date.setMinutes(0, 0, 0);
  date.setHours(date.getHours() + 1);
  return localDateTimeValue(date);
}

function emptyForm() {
  return {
    company: "",
    roleName: "",
    eventType: "INTERVIEW",
    timeMode: "point",
    startAt: nextHourValue(),
    endAt: "",
    notes: "",
  };
}

const form = reactive(emptyForm());

const pendingItems = computed(() =>
  items.value
    .filter((item) => !item.completedAt)
    .sort((left, right) => {
      const leftOverdue = isOverdue(left);
      const rightOverdue = isOverdue(right);
      if (leftOverdue !== rightOverdue) return leftOverdue ? -1 : 1;
      const difference = timestamp(left.startAt) - timestamp(right.startAt);
      return leftOverdue ? -difference : difference;
    }),
);

const completedItems = computed(() =>
  items.value
    .filter((item) => item.completedAt)
    .sort(
      (left, right) =>
        timestamp(right.completedAt) - timestamp(left.completedAt),
    ),
);

const dueTodayCount = computed(
  () =>
    pendingItems.value.filter((item) => isSameDay(item.startAt, new Date()))
      .length,
);

const upcomingWeekCount = computed(() => {
  const now = Date.now();
  const week = now + 7 * 24 * 60 * 60 * 1000;
  return pendingItems.value.filter((item) => {
    const time = timestamp(item.startAt);
    return time >= now && time < week;
  }).length;
});

const nextTaskLabel = computed(() => {
  const next =
    pendingItems.value.find((item) => !isOverdue(item)) ||
    pendingItems.value[0];
  return next ? proximityMeta(next).label : "";
});

watch(
  () => form.timeMode,
  (mode) => {
    if (mode === "point") {
      form.endAt = "";
      return;
    }
    if (!form.endAt || timestamp(form.endAt) <= timestamp(form.startAt)) {
      form.endAt = localDateTimeValue(
        new Date(timestamp(form.startAt) + 60 * 60 * 1000),
      );
    }
  },
);

watch(
  () => form.startAt,
  (startAt) => {
    if (form.timeMode !== "range" || !startAt) return;
    if (!form.endAt || timestamp(form.endAt) <= timestamp(startAt)) {
      form.endAt = localDateTimeValue(
        new Date(timestamp(startAt) + 60 * 60 * 1000),
      );
    }
  },
);

async function load() {
  loading.value = true;
  try {
    const data = await scheduleApi.list();
    items.value = data.items || [];
  } catch (error) {
    ElMessage.error(error.response?.data?.message || "日程加载失败");
  } finally {
    loading.value = false;
  }
}

async function createSchedule() {
  if (
    form.timeMode === "range" &&
    timestamp(form.endAt) <= timestamp(form.startAt)
  ) {
    ElMessage.warning("结束时间需要晚于开始时间");
    return;
  }
  saving.value = true;
  try {
    const created = await scheduleApi.create({
      company: form.company,
      roleName: form.roleName || "",
      eventType: form.eventType,
      startAt: form.startAt,
      endAt: form.timeMode === "range" ? form.endAt : null,
      notes: form.notes || "",
    });
    if (created) items.value.push(created);
    const previousType = form.eventType;
    Object.assign(form, emptyForm(), { eventType: previousType });
    ElMessage.success("已添加到日程");
  } catch (error) {
    ElMessage.error(error.response?.data?.message || "日程添加失败");
  } finally {
    saving.value = false;
  }
}

async function setCompleted(item, completed) {
  busyId.value = item.id;
  try {
    const updated = await scheduleApi.setCompleted(item.id, completed);
    if (updated) Object.assign(item, updated);
    ElMessage.success(completed ? "已完成，辛苦了" : "已恢复到待办");
  } catch (error) {
    ElMessage.error(error.response?.data?.message || "状态更新失败");
  } finally {
    busyId.value = null;
  }
}

async function removeSchedule(item) {
  try {
    await ElMessageBox.confirm(
      `确认删除「${item.company}」的${eventMeta(item.eventType).label}日程？`,
      "删除日程",
      {
        confirmButtonText: "删除",
        cancelButtonText: "取消",
        type: "warning",
      },
    );
  } catch {
    return;
  }

  busyId.value = item.id;
  try {
    await scheduleApi.remove(item.id);
    items.value = items.value.filter((entry) => entry.id !== item.id);
    ElMessage.success("日程已删除");
  } catch (error) {
    ElMessage.error(error.response?.data?.message || "删除失败");
  } finally {
    busyId.value = null;
  }
}

function eventMeta(value) {
  return eventTypes.find((item) => item.value === value) || eventTypes[1];
}

function timestamp(value) {
  const parsed = new Date(value || 0).getTime();
  return Number.isNaN(parsed) ? 0 : parsed;
}

function isOverdue(item) {
  return timestamp(item.endAt || item.startAt) < Date.now();
}

function isUrgent(item) {
  const distance = timestamp(item.startAt) - Date.now();
  return distance >= 0 && distance <= 24 * 60 * 60 * 1000;
}

function isSameDay(value, other) {
  const date = new Date(value);
  return (
    date.getFullYear() === other.getFullYear() &&
    date.getMonth() === other.getMonth() &&
    date.getDate() === other.getDate()
  );
}

function proximityMeta(item) {
  if (isOverdue(item)) return { label: "已逾期", tone: "is-overdue" };
  const date = new Date(item.startAt);
  const today = new Date();
  const tomorrow = new Date(today);
  tomorrow.setDate(today.getDate() + 1);
  if (isSameDay(date, today)) return { label: "今天", tone: "is-today" };
  if (isSameDay(date, tomorrow)) return { label: "明天", tone: "is-tomorrow" };
  const startOfToday = new Date(
    today.getFullYear(),
    today.getMonth(),
    today.getDate(),
  );
  const startOfDate = new Date(
    date.getFullYear(),
    date.getMonth(),
    date.getDate(),
  );
  const days = Math.round((startOfDate - startOfToday) / 86_400_000);
  return days <= 7
    ? { label: `${days} 天后`, tone: "is-soon" }
    : { label: "待进行", tone: "is-normal" };
}

function monthLabel(value) {
  return new Intl.DateTimeFormat("zh-CN", { month: "short" }).format(
    new Date(value),
  );
}

function dayLabel(value) {
  return new Intl.DateTimeFormat("zh-CN", { day: "2-digit" }).format(
    new Date(value),
  );
}

function weekdayLabel(value) {
  return new Intl.DateTimeFormat("zh-CN", { weekday: "short" }).format(
    new Date(value),
  );
}

function shortTime(value) {
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(value));
}

function formatScheduleTime(item) {
  const start = new Date(item.startAt);
  const startText = new Intl.DateTimeFormat("zh-CN", {
    month: "long",
    day: "numeric",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(start);
  if (!item.endAt) return startText;
  const end = new Date(item.endAt);
  const endText = new Intl.DateTimeFormat("zh-CN", {
    ...(isSameDay(end, start) ? {} : { month: "long", day: "numeric" }),
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(end);
  return `${startText} — ${endText}`;
}

function localDateTimeValue(date) {
  if (!(date instanceof Date) || Number.isNaN(date.getTime())) return "";
  const pad = (value) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

onMounted(load);
</script>

<style scoped>
.schedule-main {
  box-sizing: border-box;
  width: min(1380px, 100%);
  min-height: calc(100dvh - 64px);
  margin: 0 auto;
  padding: 16px 18px 30px;
}

.topbar-status,
.back-link {
  display: inline-flex;
  align-items: center;
}

.topbar-status {
  gap: 7px;
  font-size: 0.7rem;
}

.topbar-status i,
.sort-note i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #7c9b10;
  box-shadow: 0 0 0 4px rgba(124, 155, 16, 0.1);
}

.back-link {
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid var(--xzm-border-color);
  border-radius: 8px;
  color: var(--xzm-text-secondary);
  background: var(--xzm-surface-elevated);
  font-size: 0.72rem;
  font-weight: 700;
  text-decoration: none;
}

.back-link:hover {
  border-color: var(--xzm-brand);
  color: var(--xzm-brand);
}

.schedule-hero {
  position: relative;
  display: grid;
  grid-template-columns: minmax(350px, 1fr) minmax(440px, 0.8fr);
  min-height: 122px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--xzm-brand) 30%, transparent);
  border-radius: 16px;
  color: #f2fbf7;
  background:
    radial-gradient(
      circle at 44% 120%,
      rgba(216, 246, 115, 0.12),
      transparent 36%
    ),
    #093c37;
  box-shadow: 0 16px 42px rgba(8, 61, 56, 0.11);
}

.schedule-hero::after {
  content: "";
  position: absolute;
  top: -82px;
  right: 31%;
  width: 170px;
  height: 170px;
  border: 34px solid rgba(216, 246, 115, 0.045);
  border-radius: 50%;
}

.schedule-hero__copy {
  z-index: 1;
  align-self: center;
  padding: 22px 26px;
}

.schedule-hero__copy p,
.panel-heading small {
  margin: 0 0 6px;
  color: var(--xzm-signal);
  font: 800 0.58rem/1 var(--xzm-font-data);
  letter-spacing: 0.15em;
}

.schedule-hero__copy h1 {
  margin: 0;
  font-family: var(--xzm-font-display);
  font-size: clamp(1.55rem, 2.4vw, 2.05rem);
  line-height: 1.12;
  letter-spacing: -0.045em;
}

.schedule-hero__copy span {
  display: block;
  margin-top: 8px;
  color: #abc2ba;
  font-size: 0.7rem;
}

.schedule-stats {
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin: 0;
  border-left: 1px solid rgba(216, 246, 115, 0.14);
}

.schedule-stats div {
  display: grid;
  min-width: 0;
  place-content: center;
  gap: 6px;
  text-align: center;
}

.schedule-stats div + div {
  border-left: 1px solid rgba(216, 246, 115, 0.11);
}

.schedule-stats dt {
  font: 760 1.55rem/1 var(--xzm-font-data);
}

.schedule-stats dd {
  color: #91ada4;
  font-size: 0.63rem;
}

.schedule-stats .has-attention {
  color: var(--xzm-signal);
  background: rgba(216, 246, 115, 0.055);
}

.schedule-stats .has-attention dd {
  color: #cadc91;
}

.schedule-layout {
  display: grid;
  grid-template-columns: minmax(320px, 372px) minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  margin-top: 16px;
}

.entry-panel,
.timeline-panel {
  border: 1px solid var(--xzm-border-color);
  border-radius: 15px;
  background: color-mix(in srgb, var(--xzm-surface-elevated) 97%, transparent);
  box-shadow: 0 12px 34px rgba(19, 37, 34, 0.05);
}

.entry-panel {
  position: sticky;
  top: 80px;
  overflow: hidden;
}

.timeline-panel {
  min-height: 566px;
  padding-bottom: 10px;
}

.panel-heading {
  display: flex;
  min-height: 70px;
  align-items: center;
  justify-content: space-between;
  padding: 14px 17px;
  border-bottom: 1px solid var(--xzm-border-color);
}

.panel-heading small {
  display: block;
  margin-bottom: 4px;
  color: var(--xzm-brand);
}

.panel-heading h2 {
  margin: 0;
  color: var(--xzm-text-primary);
  font-family: var(--xzm-font-display);
  font-size: 1.04rem;
  letter-spacing: -0.025em;
}

.step-mark {
  color: color-mix(in srgb, var(--xzm-brand) 45%, var(--xzm-text-muted));
  font: 760 0.68rem/1 var(--xzm-font-data);
}

.sort-note {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--xzm-text-tertiary);
  font-size: 0.66rem;
}

.schedule-form {
  display: grid;
  gap: 16px;
  padding: 18px;
}

.schedule-form fieldset {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}

.schedule-form legend,
.form-field > span {
  display: block;
  margin-bottom: 8px;
  color: var(--xzm-text-secondary);
  font-size: 0.69rem;
  font-weight: 720;
}

.schedule-form b {
  color: var(--xzm-danger);
  font-style: normal;
}

.schedule-form em {
  margin-left: 4px;
  color: var(--xzm-text-muted);
  font-size: 0.58rem;
  font-style: normal;
  font-weight: 500;
}

.type-picker {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 7px;
}

.type-picker label {
  --type-color: #8a6608;
  --type-bg: #fff8e2;
  display: grid;
  min-height: 56px;
  grid-template-columns: 26px 1fr;
  align-items: center;
  gap: 7px;
  padding: 7px;
  border: 1px solid var(--xzm-border-color);
  border-radius: 10px;
  color: var(--xzm-text-secondary);
  background: var(--xzm-surface-1);
  cursor: pointer;
  transition:
    border-color 140ms ease,
    background-color 140ms ease,
    transform 140ms ease;
}

.type-picker label:hover {
  transform: translateY(-1px);
}

.type-picker label.active {
  border-color: color-mix(
    in srgb,
    var(--type-color) 45%,
    var(--xzm-border-color)
  );
  color: var(--type-color);
  background: var(--type-bg);
  box-shadow: inset 0 0 0 1px
    color-mix(in srgb, var(--type-color) 9%, transparent);
}

.type-picker input,
.time-mode input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.type-picker strong {
  color: inherit;
  font-size: 0.69rem;
}

.type-icon {
  display: grid;
  width: 26px;
  height: 26px;
  place-items: center;
  border-radius: 7px;
  color: var(--type-color);
  background: var(--type-bg);
  font: 780 0.61rem/1 var(--xzm-font-data);
}

.tone-teal {
  --type-color: #116e66 !important;
  --type-bg: #e5f5f1 !important;
}

.tone-purple {
  --type-color: #6e54a2 !important;
  --type-bg: #f1ebfb !important;
}

.form-field input,
.form-field textarea,
.datetime-field input {
  box-sizing: border-box;
  width: 100%;
  border: 1px solid var(--xzm-border-color);
  border-radius: 9px;
  color: var(--xzm-text-primary);
  background: var(--xzm-surface-1);
  font: inherit;
  font-size: 0.74rem;
  outline: none;
  transition:
    border-color 140ms ease,
    box-shadow 140ms ease,
    background-color 140ms ease;
}

.form-field input,
.datetime-field input {
  height: 43px;
  padding: 0 11px;
}

.form-field textarea {
  min-height: 70px;
  padding: 10px 11px;
  line-height: 1.55;
  resize: vertical;
}

.form-field input:focus,
.form-field textarea:focus,
.datetime-field input:focus {
  border-color: var(--xzm-brand);
  background: var(--xzm-surface-elevated);
  box-shadow: 0 0 0 3px var(--xzm-focus-ring-soft);
}

.field-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 9px;
}

.field-title legend {
  margin: 0;
}

.time-mode {
  display: inline-flex;
  padding: 2px;
  border: 1px solid var(--xzm-border-color);
  border-radius: 7px;
  background: var(--xzm-surface-2);
}

.time-mode label {
  padding: 5px 8px;
  border-radius: 5px;
  color: var(--xzm-text-tertiary);
  font-size: 0.6rem;
  cursor: pointer;
}

.time-mode label.active {
  color: var(--xzm-brand);
  background: var(--xzm-surface-elevated);
  box-shadow: 0 1px 4px rgba(19, 37, 34, 0.08);
  font-weight: 700;
}

.datetime-field {
  display: grid;
  grid-template-columns: 34px 1fr;
  gap: 8px;
  align-items: center;
  margin-top: 7px;
}

.datetime-field > span {
  color: var(--xzm-text-tertiary);
  font-size: 0.63rem;
}

.submit-button {
  display: flex;
  width: 100%;
  height: 44px;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px 0 16px;
  border: 0;
  border-radius: 9px;
  color: var(--xzm-text-on-brand) !important;
  background: var(--xzm-brand-gradient);
  box-shadow: var(--xzm-shadow-brand);
  font: inherit;
  font-size: 0.73rem;
  font-weight: 750;
  cursor: pointer;
}

.submit-button:hover:not(:disabled) {
  box-shadow: var(--xzm-shadow-brand-hover);
  transform: translateY(-1px);
}

.submit-button:disabled,
.schedule-card button:disabled,
.completed-section button:disabled {
  cursor: wait;
  opacity: 0.58;
}

.submit-button svg {
  width: 17px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.form-hint {
  margin: -9px 0 0;
  color: var(--xzm-text-muted);
  font-size: 0.59rem;
  text-align: center;
}

.schedule-state {
  display: grid;
  min-height: 390px;
  place-content: center;
  justify-items: center;
  padding: 28px;
  text-align: center;
}

.schedule-state strong {
  margin-top: 15px;
  color: var(--xzm-text-primary);
  font-size: 0.82rem;
}

.schedule-state p {
  margin: 6px 0 0;
  color: var(--xzm-text-tertiary);
  font-size: 0.68rem;
}

.loading-ring {
  width: 30px;
  height: 30px;
  border: 2px solid var(--xzm-border-color);
  border-top-color: var(--xzm-brand);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.empty-calendar {
  position: relative;
  display: grid;
  width: 64px;
  height: 60px;
  place-items: center;
  border: 1px solid
    color-mix(in srgb, var(--xzm-brand) 28%, var(--xzm-border-color));
  border-radius: 13px;
  color: var(--xzm-brand);
  background: var(--xzm-brand-soft);
}

.empty-calendar::before,
.empty-calendar::after {
  content: "";
  position: absolute;
  top: -5px;
  width: 7px;
  height: 13px;
  border-radius: 4px;
  background: var(--xzm-brand);
}

.empty-calendar::before {
  left: 15px;
}
.empty-calendar::after {
  right: 15px;
}
.empty-calendar i {
  position: absolute;
  top: 13px;
  right: 0;
  left: 0;
  height: 1px;
  background: color-mix(in srgb, var(--xzm-brand) 24%, transparent);
}
.empty-calendar b {
  margin-top: 10px;
  font-size: 1.2rem;
}

.schedule-list {
  position: relative;
  display: grid;
  gap: 11px;
  margin: 0;
  padding: 15px 16px 17px;
  list-style: none;
}

.schedule-list::before {
  content: "";
  position: absolute;
  top: 22px;
  bottom: 24px;
  left: 57px;
  width: 1px;
  background: color-mix(in srgb, var(--xzm-brand) 18%, var(--xzm-border-color));
}

.schedule-list > li {
  position: relative;
  display: grid;
  grid-template-columns: 82px minmax(0, 1fr);
  gap: 12px;
}

.date-block {
  z-index: 1;
  display: grid;
  width: 58px;
  height: 70px;
  align-content: center;
  justify-self: center;
  border: 1px solid var(--xzm-border-color);
  border-radius: 11px;
  color: var(--xzm-text-secondary);
  background: var(--xzm-surface-elevated);
  text-align: center;
  box-shadow: 0 5px 14px rgba(19, 37, 34, 0.04);
}

.date-block span,
.date-block small {
  color: var(--xzm-text-tertiary);
  font-size: 0.57rem;
}

.date-block strong {
  margin: 2px 0 1px;
  color: var(--xzm-text-primary);
  font: 760 1.15rem/1 var(--xzm-font-data);
}

.urgent .date-block {
  border-color: color-mix(in srgb, #d8f673 44%, var(--xzm-border-color));
  color: #587000;
  background: color-mix(
    in srgb,
    var(--xzm-signal-soft) 72%,
    var(--xzm-surface-elevated)
  );
}

.overdue .date-block {
  border-color: color-mix(
    in srgb,
    var(--xzm-danger) 28%,
    var(--xzm-border-color)
  );
}

.schedule-card {
  display: flex;
  min-width: 0;
  min-height: 124px;
  align-items: stretch;
  justify-content: space-between;
  overflow: hidden;
  border: 1px solid var(--xzm-border-color);
  border-radius: 13px;
  background: var(--xzm-surface-elevated);
  box-shadow: 0 7px 20px rgba(19, 37, 34, 0.035);
  transition:
    border-color 140ms ease,
    box-shadow 140ms ease,
    transform 140ms ease;
}

.schedule-card:hover {
  border-color: color-mix(
    in srgb,
    var(--xzm-brand) 28%,
    var(--xzm-border-color)
  );
  box-shadow: 0 11px 28px rgba(19, 37, 34, 0.07);
  transform: translateY(-1px);
}

.schedule-card__content {
  min-width: 0;
  flex: 1;
  padding: 14px 16px;
}

.schedule-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}

.event-tag,
.proximity-tag {
  display: inline-flex;
  min-height: 21px;
  align-items: center;
  padding: 0 7px;
  border-radius: 999px;
  font-size: 0.58rem;
  font-weight: 760;
}

.event-tag {
  color: var(--type-color);
  background: var(--type-bg);
}

.tone-amber {
  --type-color: #8a6608;
  --type-bg: #fff8e2;
}

.proximity-tag {
  color: var(--xzm-text-tertiary);
  background: var(--xzm-surface-2);
}

.proximity-tag.is-today,
.proximity-tag.is-tomorrow {
  color: #587000;
  background: var(--xzm-signal-soft);
}

.proximity-tag.is-overdue {
  color: var(--xzm-danger);
  background: color-mix(
    in srgb,
    var(--xzm-danger) 9%,
    var(--xzm-surface-elevated)
  );
}

.proximity-tag.is-soon {
  color: var(--xzm-brand);
  background: var(--xzm-brand-soft);
}

.schedule-card h3 {
  overflow: hidden;
  margin: 10px 0 0;
  color: var(--xzm-text-primary);
  font-family: var(--xzm-font-display);
  font-size: 1.02rem;
  line-height: 1.25;
  letter-spacing: -0.025em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-name {
  overflow: hidden;
  margin: 3px 0 0;
  color: var(--xzm-text-secondary);
  font-size: 0.67rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.schedule-time {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 10px 0 0;
  color: var(--xzm-brand);
  font-size: 0.68rem;
  font-weight: 680;
  font-variant-numeric: tabular-nums;
}

.schedule-time svg {
  width: 15px;
  flex: 0 0 15px;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
}

.schedule-notes {
  margin: 9px 0 0;
  padding: 7px 9px;
  border-left: 2px solid color-mix(in srgb, var(--xzm-brand) 34%, transparent);
  border-radius: 0 6px 6px 0;
  color: var(--xzm-text-tertiary);
  background: var(--xzm-surface-1);
  font-size: 0.64rem;
  line-height: 1.5;
  white-space: pre-wrap;
}

.schedule-card__actions {
  display: grid;
  width: 86px;
  flex: 0 0 86px;
  grid-template-rows: 1fr auto;
  align-items: center;
  padding: 12px;
  border-left: 1px solid var(--xzm-border-color);
  background: color-mix(in srgb, var(--xzm-surface-1) 68%, transparent);
}

.complete-button,
.delete-button,
.completed-section button {
  border: 0;
  font: inherit;
  cursor: pointer;
}

.complete-button {
  display: grid;
  justify-items: center;
  gap: 6px;
  color: var(--xzm-brand) !important;
  background: transparent;
  font-size: 0.62rem;
  font-weight: 750;
}

.complete-button span {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 1px solid
    color-mix(in srgb, var(--xzm-brand) 40%, var(--xzm-border-color));
  border-radius: 50%;
  background: var(--xzm-surface-elevated);
  font-size: 0.86rem;
  transition:
    color 140ms ease,
    background-color 140ms ease,
    transform 140ms ease;
}

.complete-button:hover span {
  color: var(--xzm-text-on-brand);
  background: var(--xzm-brand);
  transform: scale(1.05);
}

.delete-button {
  padding: 5px;
  color: var(--xzm-text-muted) !important;
  background: transparent;
  font-size: 0.58rem;
}

.delete-button:hover {
  color: var(--xzm-danger) !important;
}

.completed-section {
  margin: 0 16px 8px 110px;
  border-top: 1px solid var(--xzm-border-color);
}

.completed-section summary {
  display: flex;
  min-height: 49px;
  align-items: center;
  gap: 7px;
  color: var(--xzm-text-secondary);
  font-size: 0.69rem;
  font-weight: 700;
  cursor: pointer;
  list-style: none;
}

.completed-section summary::-webkit-details-marker {
  display: none;
}
.completed-section summary b {
  display: grid;
  min-width: 21px;
  height: 21px;
  place-items: center;
  border-radius: 999px;
  color: var(--xzm-text-tertiary);
  background: var(--xzm-surface-2);
  font-size: 0.58rem;
}
.completed-section summary svg {
  width: 15px;
  margin-left: auto;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  transition: transform 140ms ease;
}
.completed-section[open] summary svg {
  transform: rotate(180deg);
}

.completed-section ul {
  display: grid;
  gap: 6px;
  margin: 0 0 10px;
  padding: 0;
  list-style: none;
}

.completed-section li {
  display: grid;
  grid-template-columns: 26px 1fr auto;
  gap: 9px;
  align-items: center;
  min-height: 50px;
  padding: 7px 9px;
  border-radius: 9px;
  background: var(--xzm-surface-1);
}

.completed-check {
  display: grid;
  width: 24px;
  height: 24px;
  place-items: center;
  border-radius: 50%;
  color: #587000;
  background: var(--xzm-signal-soft);
  font-size: 0.66rem;
}

.completed-section li > div {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.completed-section li strong {
  overflow: hidden;
  color: var(--xzm-text-secondary);
  font-size: 0.69rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.completed-section li small {
  color: var(--xzm-text-muted);
  font-size: 0.58rem;
}

.completed-section button {
  padding: 6px 7px;
  border-radius: 6px;
  color: var(--xzm-brand) !important;
  background: transparent;
  font-size: 0.6rem;
}

.completed-section button:hover {
  background: var(--xzm-brand-soft);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1050px) {
  .schedule-hero {
    grid-template-columns: minmax(300px, 0.8fr) minmax(400px, 1fr);
  }
  .schedule-layout {
    grid-template-columns: minmax(300px, 340px) minmax(0, 1fr);
  }
  .schedule-card__actions {
    width: 78px;
    flex-basis: 78px;
  }
}

@media (max-width: 860px) {
  .schedule-hero {
    grid-template-columns: 1fr;
  }
  .schedule-stats {
    min-height: 72px;
    border-top: 1px solid rgba(216, 246, 115, 0.14);
    border-left: 0;
  }
  .schedule-layout {
    grid-template-columns: 1fr;
  }
  .entry-panel {
    position: static;
  }
  .schedule-form {
    grid-template-columns: 1fr 1fr;
  }
  .type-fieldset,
  .time-fieldset,
  .schedule-form .submit-button,
  .form-hint {
    grid-column: 1 / -1;
  }
}

@media (max-width: 760px) {
  .back-link {
    min-height: 32px;
    padding-inline: 9px;
    font-size: 0.65rem;
  }
  .schedule-main {
    min-height: calc(100dvh - 60px);
    padding: 9px 9px 24px;
  }
  .schedule-hero {
    border-radius: 13px;
  }
  .schedule-hero__copy {
    padding: 18px 17px 16px;
  }
  .schedule-hero__copy h1 {
    font-size: 1.42rem;
  }
  .schedule-hero__copy span {
    max-width: 290px;
    font-size: 0.64rem;
    line-height: 1.5;
  }
  .schedule-stats {
    min-height: 66px;
  }
  .schedule-stats dt {
    font-size: 1.18rem;
  }
  .schedule-stats dd {
    font-size: 0.56rem;
  }
  .schedule-layout {
    gap: 10px;
    margin-top: 10px;
  }
  .entry-panel,
  .timeline-panel {
    border-radius: 12px;
  }
  .panel-heading {
    min-height: 62px;
    padding: 11px 13px;
  }
  .schedule-form {
    grid-template-columns: 1fr;
    gap: 14px;
    padding: 14px;
  }
  .type-fieldset,
  .time-fieldset,
  .schedule-form .submit-button,
  .form-hint {
    grid-column: auto;
  }
  .form-field input,
  .datetime-field input {
    height: 45px;
  }
  .submit-button {
    height: 46px;
  }
  .timeline-panel {
    min-height: 420px;
  }
  .schedule-list {
    gap: 9px;
    padding: 11px;
  }
  .schedule-list::before {
    display: none;
  }
  .schedule-list > li {
    grid-template-columns: 50px minmax(0, 1fr);
    gap: 8px;
  }
  .date-block {
    width: 48px;
    height: 64px;
    border-radius: 9px;
  }
  .date-block strong {
    font-size: 1.02rem;
  }
  .schedule-card {
    display: grid;
    min-height: 0;
  }
  .schedule-card__content {
    padding: 12px;
  }
  .schedule-card h3 {
    font-size: 0.93rem;
  }
  .schedule-time {
    align-items: flex-start;
    line-height: 1.5;
  }
  .schedule-card__actions {
    display: flex;
    width: auto;
    min-height: 45px;
    grid-template-rows: none;
    justify-content: flex-end;
    gap: 6px;
    padding: 6px 9px;
    border-top: 1px solid var(--xzm-border-color);
    border-left: 0;
  }
  .complete-button {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 0 7px;
  }
  .complete-button span {
    width: 25px;
    height: 25px;
  }
  .delete-button {
    padding-inline: 8px;
  }
  .completed-section {
    margin: 0 11px 6px 69px;
  }
}

@media (max-width: 420px) {
  .type-picker {
    gap: 5px;
  }
  .type-picker label {
    grid-template-columns: 24px 1fr;
    gap: 5px;
    padding: 5px;
  }
  .type-icon {
    width: 24px;
    height: 24px;
  }
  .schedule-card__content {
    padding: 11px;
  }
  .schedule-time {
    font-size: 0.63rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .type-picker label,
  .submit-button,
  .schedule-card,
  .complete-button span {
    transition: none;
  }
  .loading-ring {
    animation-duration: 1.5s;
  }
}
</style>
