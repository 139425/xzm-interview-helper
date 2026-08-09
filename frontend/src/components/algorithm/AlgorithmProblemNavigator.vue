<template>
  <div class="problem-navigator">
    <header class="navigator-header">
      <div>
        <span class="navigator-kicker">PROBLEM SET</span>
        <h2>算法题库</h2>
      </div>
      <span class="result-count" :aria-label="`当前显示 ${filteredProblems.length} 道题`">
        {{ filteredProblems.length }}
      </span>
    </header>

    <label class="problem-search">
      <el-icon aria-hidden="true"><Search /></el-icon>
      <span class="sr-only">搜索算法题</span>
      <input
        v-model.trim="keyword"
        type="search"
        autocomplete="off"
        placeholder="题号、名称或关键词"
      />
      <button
        v-if="keyword"
        type="button"
        aria-label="清空搜索"
        title="清空搜索"
        @click="keyword = ''"
      >
        ×
      </button>
    </label>

    <div class="filter-row">
      <label>
        <span>难度</span>
        <select v-model="difficultyFilter" aria-label="按难度筛选">
          <option value="">全部难度</option>
          <option value="EASY">简单</option>
          <option value="MEDIUM">中等</option>
          <option value="HARD">困难</option>
        </select>
      </label>
      <label>
        <span>来源</span>
        <select v-model="sourceFilter" aria-label="按来源筛选">
          <option value="">全部来源</option>
          <option
            v-for="source in sourceOptions"
            :key="source.value"
            :value="source.value"
          >
            {{ source.label }}
          </option>
        </select>
      </label>
    </div>

    <div class="navigator-rule">
      <span>{{ activeFilterSummary }}</span>
      <button
        v-if="hasActiveFilters"
        type="button"
        @click="resetFilters"
      >
        重置
      </button>
    </div>

    <nav
      class="problem-list gemini-smooth-scroll"
      aria-label="算法题列表"
      :aria-busy="loading"
    >
      <template v-if="loading">
        <div v-for="index in 6" :key="index" class="problem-skeleton" aria-hidden="true">
          <i></i><span></span>
        </div>
        <span class="sr-only">题库加载中</span>
      </template>

      <button
        v-for="problem in filteredProblems"
        v-else
        :key="problem.slug"
        type="button"
        class="problem-row"
        :class="{ active: selectedSlug === problem.slug }"
        :disabled="isLocked(problem)"
        :aria-current="selectedSlug === problem.slug ? 'page' : undefined"
        :aria-label="problemAriaLabel(problem)"
        @click="selectProblem(problem)"
      >
        <span class="problem-rank">{{ rankOf(problem) }}</span>
        <span class="problem-copy">
          <strong>{{ problem.title }}</strong>
          <small>
            <i :class="`difficulty-${difficultyOf(problem).toLowerCase()}`">
              {{ difficultyLabel(problem.difficulty) }}
            </i>
            <em v-if="problem.judgeable">可判题</em>
            <em v-if="lockedSlug === problem.slug" class="locked-mark">
              <el-icon><Lock /></el-icon>面试题
            </em>
          </small>
        </span>
        <span class="row-arrow" aria-hidden="true">›</span>
      </button>

      <div v-if="!loading && filteredProblems.length === 0" class="empty-state">
        <span aria-hidden="true">∅</span>
        <strong>没有匹配的题目</strong>
        <p>换个关键词，或清除筛选条件。</p>
        <button type="button" @click="resetFilters">清除筛选</button>
      </div>
    </nav>

    <footer v-if="lockedSlug" class="lock-notice">
      <el-icon aria-hidden="true"><Lock /></el-icon>
      <span>面试中仅可作答已分配题目</span>
    </footer>
  </div>
</template>

<script setup>
import { computed, ref } from "vue";
import { Lock, Search } from "@element-plus/icons-vue";

const props = defineProps({
  problems: {
    type: Array,
    default: () => [],
  },
  selectedSlug: {
    type: String,
    default: "",
  },
  lockedSlug: {
    type: String,
    default: "",
  },
  loading: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(["select"]);

const keyword = ref("");
const difficultyFilter = ref("");
const sourceFilter = ref("");

const sourceLabels = {
  HOT100: "LeetCode Hot 100",
  CODETOP: "CodeTop Top 100",
  JUDGEABLE: "可自动判题",
};

const sourceOptions = computed(() => {
  const sources = new Set();
  let hasJudgeable = false;

  props.problems.forEach((problem) => {
    (problem.sources || []).forEach((source) => {
      if (source) sources.add(String(source).toUpperCase());
    });
    if (problem.judgeable) hasJudgeable = true;
  });

  const options = [...sources]
    .sort((left, right) => left.localeCompare(right))
    .map((value) => ({ value, label: sourceLabels[value] || value }));

  if (hasJudgeable && !sources.has("JUDGEABLE")) {
    options.push({ value: "JUDGEABLE", label: sourceLabels.JUDGEABLE });
  }
  return options;
});

const filteredProblems = computed(() => {
  const needle = keyword.value.toLocaleLowerCase("zh-CN");
  return props.problems.filter((problem) => {
    const difficultyMatches = !difficultyFilter.value ||
      difficultyOf(problem) === difficultyFilter.value;
    const sourceMatches = !sourceFilter.value ||
      (sourceFilter.value === "JUDGEABLE"
        ? Boolean(problem.judgeable)
        : (problem.sources || []).some(
          (source) => String(source).toUpperCase() === sourceFilter.value,
        ));
    const searchableText = [
      problem.frontendId,
      problem.title,
      problem.slug,
      ...(problem.tags || []),
    ].filter(Boolean).join(" ").toLocaleLowerCase("zh-CN");

    return difficultyMatches && sourceMatches &&
      (!needle || searchableText.includes(needle));
  });
});

const hasActiveFilters = computed(
  () => Boolean(keyword.value || difficultyFilter.value || sourceFilter.value),
);

const activeFilterSummary = computed(() => {
  if (!hasActiveFilters.value) return "全部题目";
  const parts = [];
  if (difficultyFilter.value) {
    parts.push(difficultyLabel(difficultyFilter.value));
  }
  if (sourceFilter.value) {
    parts.push(sourceLabels[sourceFilter.value] || sourceFilter.value);
  }
  if (keyword.value) parts.push(`“${keyword.value}”`);
  return parts.join(" · ");
});

function difficultyOf(problem) {
  return String(problem?.difficulty || "UNKNOWN").toUpperCase();
}

function difficultyLabel(difficulty) {
  return {
    EASY: "简单",
    MEDIUM: "中等",
    HARD: "困难",
  }[String(difficulty || "").toUpperCase()] || "动态";
}

function rankOf(problem) {
  const value = Number(problem?.frontendId);
  return Number.isFinite(value)
    ? String(value).padStart(3, "0")
    : String(problem?.frontendId || "—");
}

function isLocked(problem) {
  return Boolean(props.lockedSlug && problem?.slug !== props.lockedSlug);
}

function problemAriaLabel(problem) {
  const locked = isLocked(problem) ? "，面试期间不可选择" : "";
  return `${problem.frontendId || ""} ${problem.title}，${difficultyLabel(problem.difficulty)}${locked}`;
}

function selectProblem(problem) {
  if (!isLocked(problem) && problem?.slug !== props.selectedSlug) {
    emit("select", problem);
  }
}

function resetFilters() {
  keyword.value = "";
  difficultyFilter.value = "";
  sourceFilter.value = "";
}
</script>

<style scoped>
.problem-navigator {
  min-height: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 16px 10px 10px;
  color: var(--gemini-text-primary);
  box-sizing: border-box;
}

.navigator-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 0 7px 12px;
}

.navigator-kicker {
  display: block;
  margin-bottom: 4px;
  color: var(--gemini-text-tertiary);
  font-size: 0.61rem;
  font-weight: 750;
  letter-spacing: 0.13em;
}

.navigator-header h2 {
  margin: 0;
  font-family: Georgia, "Times New Roman", "Microsoft YaHei", serif;
  font-size: 1.08rem;
  font-weight: 700;
  letter-spacing: -0.02em;
}

.result-count {
  min-width: 25px;
  height: 23px;
  padding: 0 7px;
  display: inline-grid;
  place-items: center;
  border: 1px solid var(--gemini-border-color);
  border-radius: 999px;
  color: var(--gemini-text-secondary);
  background: var(--gemini-bg-tertiary);
  font: 700 0.7rem/1 ui-monospace, SFMono-Regular, Consolas, monospace;
  box-sizing: border-box;
}

.problem-search {
  height: 38px;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 4px 9px;
  padding: 0 9px;
  border: 1px solid var(--gemini-border-color);
  border-radius: 9px;
  color: var(--gemini-text-tertiary);
  background: color-mix(in srgb, var(--gemini-bg-primary) 80%, transparent);
  transition: border-color 140ms ease-out, box-shadow 140ms ease-out;
}

.problem-search:focus-within {
  border-color: var(--gemini-accent-blue);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--gemini-accent-blue) 12%, transparent);
}

.problem-search input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  color: var(--gemini-text-primary);
  background: transparent;
  font: inherit;
  font-size: 0.79rem;
}

.problem-search input::placeholder {
  color: var(--gemini-text-tertiary);
}

.problem-search button {
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 6px;
  color: var(--gemini-text-tertiary);
  background: transparent;
  cursor: pointer;
}

.problem-search button:hover {
  color: var(--gemini-text-primary);
  background: var(--gemini-bg-hover);
}

.problem-search button:focus-visible,
.navigator-rule button:focus-visible,
.empty-state button:focus-visible {
  outline: 2px solid var(--gemini-accent-blue);
  outline-offset: 1px;
}

.filter-row {
  display: grid;
  grid-template-columns: 0.84fr 1.16fr;
  gap: 6px;
  margin: 0 4px 9px;
}

.filter-row label {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.filter-row label > span {
  padding-left: 2px;
  color: var(--gemini-text-tertiary);
  font-size: 0.62rem;
  font-weight: 650;
}

.filter-row select {
  width: 100%;
  height: 31px;
  padding: 0 23px 0 7px;
  border: 1px solid var(--gemini-border-color);
  border-radius: 8px;
  outline: 0;
  color: var(--gemini-text-secondary);
  background: var(--gemini-bg-tertiary);
  font: inherit;
  font-size: 0.69rem;
  cursor: pointer;
}

.filter-row select:focus-visible {
  border-color: var(--gemini-accent-blue);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--gemini-accent-blue) 12%, transparent);
}

.navigator-rule {
  min-height: 27px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  margin: 0 7px 4px;
  border-bottom: 1px solid var(--gemini-border-color);
  color: var(--gemini-text-tertiary);
  font-size: 0.65rem;
}

.navigator-rule span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.navigator-rule button,
.empty-state button {
  border: 0;
  color: var(--gemini-accent-blue);
  background: transparent;
  font: inherit;
  cursor: pointer;
}

.problem-list {
  min-height: 0;
  flex: 1;
  overflow-y: auto;
  padding: 3px 2px 12px;
}

.problem-row {
  position: relative;
  width: 100%;
  min-height: 55px;
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 2px 0;
  padding: 7px 8px;
  border: 1px solid transparent;
  border-radius: 10px;
  color: var(--gemini-text-primary);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background 130ms ease-out, border-color 130ms ease-out, transform 130ms ease-out;
}

.problem-row:not(:disabled):hover {
  border-color: var(--gemini-border-color);
  background: var(--gemini-bg-tertiary);
  transform: translateX(1px);
}

.problem-row.active {
  border-color: color-mix(in srgb, var(--gemini-accent-blue) 43%, var(--gemini-border-color));
  background: color-mix(in srgb, var(--gemini-accent-blue) 10%, transparent);
}

.problem-row.active::before {
  content: "";
  position: absolute;
  left: -3px;
  top: 12px;
  bottom: 12px;
  width: 3px;
  border-radius: 3px;
  background: var(--gemini-accent-blue);
}

.problem-row:focus-visible {
  outline: 2px solid var(--gemini-accent-blue);
  outline-offset: -2px;
}

.problem-row:disabled {
  opacity: 0.38;
  cursor: not-allowed;
}

.problem-rank {
  width: 30px;
  flex: 0 0 30px;
  color: var(--gemini-text-tertiary);
  font: 650 0.66rem/1 ui-monospace, SFMono-Regular, Consolas, monospace;
  letter-spacing: -0.02em;
}

.problem-copy {
  min-width: 0;
  flex: 1;
  display: grid;
  gap: 5px;
}

.problem-copy strong {
  overflow: hidden;
  color: var(--gemini-text-primary);
  font-size: 0.77rem;
  font-weight: 640;
  line-height: 1.15;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.problem-copy small {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 5px;
}

.problem-copy i,
.problem-copy em {
  padding: 2px 5px;
  border-radius: 5px;
  font-size: 0.56rem;
  font-style: normal;
  font-weight: 700;
  line-height: 1.2;
}

.problem-copy em {
  color: var(--gemini-text-secondary);
  background: var(--gemini-bg-tertiary);
}

.problem-copy .locked-mark {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: var(--gemini-accent-blue);
  background: color-mix(in srgb, var(--gemini-accent-blue) 10%, transparent);
}

.difficulty-easy {
  color: #168569;
  background: rgba(22, 133, 105, 0.1);
}

.difficulty-medium {
  color: #b06b0d;
  background: rgba(176, 107, 13, 0.1);
}

.difficulty-hard {
  color: #c34b58;
  background: rgba(195, 75, 88, 0.1);
}

.row-arrow {
  color: var(--gemini-text-tertiary);
  font-size: 1rem;
  opacity: 0;
  transform: translateX(-2px);
  transition: opacity 130ms ease-out, transform 130ms ease-out;
}

.problem-row:hover .row-arrow,
.problem-row.active .row-arrow {
  opacity: 1;
  transform: translateX(0);
}

.problem-skeleton {
  height: 53px;
  display: flex;
  align-items: center;
  gap: 9px;
  margin: 3px 0;
  padding: 0 8px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--gemini-bg-tertiary) 55%, transparent);
  animation: navigator-pulse 1.4s ease-in-out infinite;
}

.problem-skeleton i {
  width: 29px;
  height: 9px;
  border-radius: 4px;
  background: var(--gemini-border-color);
}

.problem-skeleton span {
  width: 62%;
  height: 11px;
  border-radius: 5px;
  background: var(--gemini-border-color);
}

@keyframes navigator-pulse {
  50% { opacity: 0.5; }
}

.empty-state {
  min-height: 180px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 7px;
  padding: 22px 10px;
  color: var(--gemini-text-tertiary);
  text-align: center;
}

.empty-state > span {
  font: 500 1.8rem/1 Georgia, serif;
}

.empty-state strong {
  color: var(--gemini-text-secondary);
  font-size: 0.78rem;
}

.empty-state p {
  margin: 0;
  font-size: 0.68rem;
}

.lock-notice {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 4px 3px 0;
  padding: 8px;
  border: 1px solid color-mix(in srgb, var(--gemini-accent-blue) 30%, var(--gemini-border-color));
  border-radius: 8px;
  color: var(--gemini-text-secondary);
  background: color-mix(in srgb, var(--gemini-accent-blue) 7%, transparent);
  font-size: 0.64rem;
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

@media (max-width: 768px) {
  .problem-navigator {
    padding-top: 13px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .problem-row,
  .row-arrow,
  .problem-skeleton {
    transition: none;
    animation: none;
  }
}
</style>
