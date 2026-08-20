<template>
  <div class="algorithm-page">
    <GeminiSidebar mode="algorithm" @mode-change="handleModeChange">
      <template #context>
        <AlgorithmProblemNavigator
          :problems="problems"
          :selected-slug="currentProblem?.slug"
          :locked-slug="interviewChallenge?.problemSlug || ''"
          :loading="problemListLoading"
          @select="handleProblemSelect"
        />
      </template>
    </GeminiSidebar>
    <main class="algorithm-main" :style="{ marginLeft: `${uiStore.sidebarWidth}px` }">
      <header class="algorithm-topbar">
        <div class="problem-identity">
          <span class="identity-mark">ALG</span>
          <div>
            <span class="eyebrow">INTERVIEW LAB / ALGORITHM</span>
            <strong>{{ currentProblem?.frontendId }}. {{ currentProblem?.title || "算法练习" }}</strong>
          </div>
        </div>
        <div class="topbar-actions">
          <button
            v-if="!uiStore.sidebarExpanded"
            type="button"
            class="catalog-open-button"
            @click="uiStore.expandSidebar()"
          >
            <el-icon><Collection /></el-icon>打开题库
          </button>
          <span v-if="interviewSessionId" class="interview-lock">
            面试终局题 · 题目已锁定
          </span>
          <span class="timer" :class="{ warning: remainingSeconds < 300 }">
            <el-icon><Timer /></el-icon>{{ formattedTime }}
          </span>
          <a v-if="problemDetail?.officialUrl" :href="problemDetail.officialUrl"
             target="_blank" rel="noopener" class="official-link">
            官方题目 <el-icon><TopRight /></el-icon>
          </a>
        </div>
      </header>

      <section
        ref="workbench"
        class="workbench"
        :class="{ resizing: Boolean(activeResize) }"
        :style="workspaceStyle"
      >
        <section class="challenge-panel">
          <div class="challenge-tabs">
            <button :class="{ active: leftTab === 'description' }" @click="leftTab = 'description'">题目描述</button>
            <button :class="{ active: leftTab === 'submissions' }" @click="openSubmissions">提交记录</button>
          </div>
          <div v-if="detailLoading" class="panel-state">
            <el-icon class="is-loading"><Loading /></el-icon> 正在同步题面…
          </div>
          <article v-else-if="leftTab === 'description'" class="problem-description">
            <div class="problem-meta">
              <span :class="`difficulty-pill difficulty-${(currentProblem?.difficulty || 'easy').toLowerCase()}`">
                {{ difficultyLabel(currentProblem?.difficulty) }}
              </span>
              <span v-for="source in currentProblem?.sources || []" :key="source" class="source-pill">
                {{ source === "HOT100" ? "LeetCode Hot 100" : "CodeTop Top 100" }}
              </span>
              <span class="limit-pill">{{ currentProblem?.timeLimitMinutes }} 分钟</span>
            </div>
            <div class="official-content" v-html="safeProblemHtml"></div>
            <section v-if="problemDetail?.sampleTestCase" class="sample-box">
              <span>示例输入</span><pre>{{ problemDetail.sampleTestCase }}</pre>
            </section>
            <div v-if="problemDetail?.tags?.length" class="tag-row">
              <span v-for="tag in problemDetail.tags" :key="tag"># {{ tag }}</span>
            </div>
          </article>
          <div v-else class="submission-list">
            <div v-if="!submissions.length" class="panel-state">还没有提交记录</div>
            <button
              v-for="item in submissions"
              :key="item.id ?? item.submissionId ?? item.submission_id"
              type="button"
              class="submission-row"
              @click="openSubmissionReview(item)"
            >
              <span :class="statusClass(item.status)">{{ statusLabel(item.status) }}</span>
              <span>
                {{ submissionValue(item, "passed_cases", "passedCases", 0) }}/{{
                  submissionValue(item, "total_cases", "totalCases", 0)
                }} 用例
              </span>
              <span>{{ submissionValue(item, "runtime_ms", "runtimeMs", "—") }} ms</span>
              <span>
                {{ submissionValue(item, "ai_score", "aiScore", "—") }}
                <small
                  v-if="submissionValue(item, 'ai_status', 'aiStatus') === 'COMPLETED'"
                >AI</small>
              </span>
              <time>{{ formatDate(submissionValue(item, "create_time", "createTime")) }}</time>
            </button>
          </div>
        </section>

        <div
          class="pane-resizer pane-resizer-vertical"
          role="separator"
          aria-label="调整题目与代码区域宽度"
          aria-orientation="vertical"
          tabindex="0"
          @pointerdown="startResize('horizontal', $event)"
          @keydown="resizeByKeyboard('horizontal', $event)"
          @dblclick="resetHorizontalSplit"
        ><span></span></div>

        <section ref="editorPanel" class="editor-panel">
          <div class="editor-toolbar">
            <div class="language-select"><span class="runtime-dot"></span>Java 17 · 接口模式</div>
            <button
              type="button"
              :disabled="detailLoading || !problemDetail"
              @click="resetTemplate"
            ><el-icon><RefreshLeft /></el-icon>重置</button>
          </div>
          <div class="editor-surface">
            <MonacoCodeEditor v-model="code" language="java"
              :theme="uiStore.currentTheme === 'dark' ? 'xzm-dark' : 'xzm-light'"
              :code-template="problemDetail?.codeTemplates?.java || ''"
              :errors="editorErrors"
              @run="runCode"
              @submit="submitCode"
            />
          </div>
          <div
            class="pane-resizer pane-resizer-horizontal"
            role="separator"
            aria-label="调整代码与结果区域高度"
            aria-orientation="horizontal"
            tabindex="0"
            @pointerdown="startResize('vertical', $event)"
            @keydown="resizeByKeyboard('vertical', $event)"
            @dblclick="resetVerticalSplit"
          ><span></span></div>
          <div class="console-panel">
            <div class="console-heading">
              <div class="console-tabs">
                <button :class="{ active: consoleTab === 'cases' }" @click="consoleTab = 'cases'">测试用例</button>
                <button :class="{ active: consoleTab === 'result' }" @click="consoleTab = 'result'">执行结果</button>
                <button :class="{ active: consoleTab === 'review' }" @click="consoleTab = 'review'">AI 评审</button>
              </div>
              <span v-if="executionResult" :class="['result-badge', statusClass(executionResult.status)]">
                {{ statusLabel(executionResult.status) }}
              </span>
            </div>
            <div v-if="consoleTab === 'cases'" class="custom-case-panel">
              <div class="custom-case-copy">
                <strong>自定义驱动代码</strong>
                <span>写入 Java main 方法体，可直接构造输入并调用 Solution。</span>
              </div>
              <textarea
                v-model="customDriver"
                class="custom-driver"
                spellcheck="false"
                maxlength="10000"
                aria-label="自定义 Java 测试驱动"
                placeholder="Solution solution = new Solution();&#10;System.out.println(solution.yourMethod(...));"
              ></textarea>
              <label class="expected-output">
                <span>预期输出 <small>可留空，仅查看实际输出</small></span>
                <textarea
                  v-model="customExpectedOutput"
                  maxlength="12000"
                  rows="2"
                  spellcheck="false"
                  placeholder="例如：&#10;[0, 1]&#10;true"
                ></textarea>
              </label>
              <div class="custom-case-actions">
                <span>公开示例：{{ problemDetail?.sampleTestCase || "暂无" }}</span>
                <button
                  type="button"
                  :disabled="executing || !customDriver.trim()"
                  @click="runCustomCode"
                >
                  运行自定义用例
                </button>
              </div>
            </div>
            <div v-else-if="consoleTab === 'result'" class="execution-output">
              <template v-if="executionResult">
                <p v-if="resultBasedOnOlderCode" class="stale-result-note">
                  此结果基于较早的代码版本；再次运行可验证当前代码。
                </p>
                <div class="result-metrics">
                  <template v-if="executionResult.status === 'COMPILED'">
                    <strong>✓</strong><span>编译通过 · 暂无自动判题用例</span>
                  </template>
                  <template v-else>
                    <strong>{{ executionResult.passedCases }}/{{ executionResult.totalCases }}</strong><span>通过用例</span>
                  </template>
                  <strong>{{ executionResult.runtimeMs ?? "—" }}</strong><span>ms</span>
                </div>
                <p v-if="executionResult.error" class="error-copy">{{ executionResult.error }}</p>
                <pre v-if="executionResult.output">{{ executionResult.output }}</pre>
                <ul v-if="executionResult.caseResults?.length">
                  <li v-for="item in executionResult.caseResults" :key="item">{{ item }}</li>
                </ul>
              </template>
              <span v-else>运行代码后，这里会显示编译、用例和性能结果。</span>
            </div>
            <div v-else class="ai-review-panel">
              <div v-if="aiReviewing || aiReview?.aiStatus === 'PROCESSING'" class="review-loading">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span>AI 正在从正确性、复杂度、边界条件与代码质量进行评审…</span>
              </div>
              <div
                v-else-if="['FAILED', 'TIMEOUT'].includes(aiReview?.aiStatus)"
                class="review-empty review-failed"
                role="status"
              >
                <strong>
                  {{ aiReview.aiStatus === "TIMEOUT" ? "AI 评审仍在后台处理" : "AI 评审暂时不可用" }}
                </strong>
                <span>
                  {{
                    aiReview.aiEvaluation ||
                    "判题结果已安全保存，AI 评审失败不会改变判题结论。"
                  }}
                </span>
                <button
                  v-if="lastSubmissionId"
                  type="button"
                  :disabled="aiReviewing"
                  @click="requestAiReview(lastSubmissionId)"
                >
                  重新获取评审
                </button>
              </div>
              <template v-else-if="aiReview">
                <div class="review-score">
                  <span>AI CODE REVIEW</span>
                  <strong>{{ aiReview.aiScore ?? aiReview.score ?? "—" }}<small>/ 100</small></strong>
                </div>
                <div class="review-content" v-html="safeAiReviewHtml"></div>
              </template>
              <div v-else class="review-empty">
                <strong>提交后生成 AI 代码评审</strong>
                <span>判题结果始终由沙箱测试决定，AI 仅提供解释与改进建议。</span>
                <button
                  v-if="lastSubmissionId"
                  type="button"
                  @click="requestAiReview(lastSubmissionId)"
                >
                  重新获取评审
                </button>
              </div>
            </div>
          </div>
          <footer class="editor-actions">
            <span class="judge-note">
              {{ currentProblem?.judgeable ? "代码草稿已自动保存 · 运行公开用例，提交隐藏用例" : "代码草稿已自动保存 · 当前支持编译检查与提交记录" }}
            </span>
            <button
              v-if="interviewSessionId"
              class="abandon-button"
              :disabled="executing || finishingInterview"
              @click="abandonInterview"
            >
              主动放弃
            </button>
            <button class="run-button" :disabled="executing || finishingInterview || detailLoading || !problemDetail" @click="runCode">
              <el-icon v-if="executing" class="is-loading"><Loading /></el-icon>
              <el-icon v-else><VideoPlay /></el-icon>运行
            </button>
            <button class="submit-button" :disabled="executing || finishingInterview || detailLoading || !problemDetail" @click="submitCode">
              {{ finishingInterview ? "生成报告中…" : "提交" }}
            </button>
          </footer>
        </section>
      </section>
    </main>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { Collection, Loading, RefreshLeft, Timer, TopRight, VideoPlay } from "@element-plus/icons-vue";
import DOMPurify from "dompurify";
import GeminiSidebar from "@/components/GeminiSidebar.vue";
import AlgorithmProblemNavigator from "@/components/algorithm/AlgorithmProblemNavigator.vue";
import MonacoCodeEditor from "@/components/algorithm/MonacoCodeEditor.vue";
import { algorithmApi } from "@/api/algorithm";
import { useUIStore } from "@/stores/ui";
import {
  readAlgorithmWorkspaceValue,
  resolveAlgorithmWorkspaceOwner,
  saveAlgorithmWorkspaceValue,
} from "@/utils/algorithmWorkspaceStorage";
import { renderMarkdown } from "@/utils/markdownFormatter";

const router = useRouter();
const route = useRoute();
const uiStore = useUIStore();
const workspaceOwnerId = resolveAlgorithmWorkspaceOwner();
const problems = ref([]);
const problemListLoading = ref(true);
const currentProblem = ref(null);
const problemDetail = ref(null);
const detailLoading = ref(false);
const leftTab = ref("description");
const consoleTab = ref("cases");
const code = ref("");
const customDriver = ref("");
const customExpectedOutput = ref("");
const executionResult = ref(null);
const aiReview = ref(null);
const aiReviewing = ref(false);
const lastSubmissionId = ref(null);
const executing = ref(false);
const submissions = ref([]);
const workbench = ref(null);
const editorPanel = ref(null);
const descriptionWidth = ref(
  readStoredNumber("algorithm:descriptionWidth", 390, 240, 720),
);
const consoleHeight = ref(
  readStoredNumber("algorithm:consoleHeight", 230, 120, 480),
);
const activeResize = ref("");
const remainingSeconds = ref(0);
const interviewChallenge = ref(null);
const finishingInterview = ref(false);
const interviewSessionId = computed(() => {
  const value = route.query.interviewSessionId;
  return typeof value === "string" ? value.trim() : "";
});
let countdownTimer = null;
let draftTimer = null;
let customCaseTimer = null;
let reviewPollTimer = null;
let activeInterviewReviewController = null;
let layoutClampTimer = null;
let layoutResizeObserver = null;
let problemVersion = 0;
let codeRevision = 0;
let executionRequestId = 0;
let executionResultRevision = 0;
let reviewRequestId = 0;
let submissionsRequestId = 0;
let activeReviewSubmissionId = null;
let finishStarted = false;
let isUnmounted = false;

const workspaceStyle = computed(() => ({
  "--description-width": `${descriptionWidth.value}px`,
  "--console-height": `${consoleHeight.value}px`,
}));

const safeProblemHtml = computed(() => DOMPurify.sanitize(problemDetail.value?.contentHtml || "", {
  USE_PROFILES: { html: true },
  FORBID_TAGS: ["script", "iframe", "object", "embed", "style"],
  FORBID_ATTR: ["style", "srcset"],
}));
const editorErrors = computed(() =>
  executionResult.value?.status === "COMPILE_ERROR"
    ? executionResult.value?.error || []
    : [],
);
const safeAiReviewHtml = computed(() => renderMarkdown(
  aiReview.value?.aiEvaluation ||
  aiReview.value?.evaluation ||
  aiReview.value?.review ||
  aiReview.value?.content ||
  "暂无评审内容。",
));
const resultBasedOnOlderCode = computed(() =>
  Boolean(executionResult.value) && executionResultRevision !== codeRevision,
);
const formattedTime = computed(() => {
  const seconds = Math.max(0, remainingSeconds.value);
  return `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
});

onMounted(async () => {
  uiStore.initialize();
  uiStore.switchMode("algorithm");
  try {
    const problemRecords = await algorithmApi.listProblems();
    if (isUnmounted) return;
    problems.value = Array.isArray(problemRecords) ? problemRecords : [];
    problemListLoading.value = false;
    if (interviewSessionId.value) {
      interviewChallenge.value = await algorithmApi.interviewChallenge(
        interviewSessionId.value,
      );
      const assigned = problems.value.find(
        (problem) => problem.slug === interviewChallenge.value?.problemSlug,
      );
      if (!assigned) throw new Error("面试算法题不在当前题库中");
      await selectProblem(assigned, {
        deadlineAt: interviewChallenge.value.deadlineAt,
      });
      if (interviewChallenge.value.status === "ACCEPTED") {
        lastSubmissionId.value =
          interviewChallenge.value.latestSubmissionId || null;
        if (lastSubmissionId.value) {
          await awaitInterviewAiReview(lastSubmissionId.value);
        }
        await finishInterview();
      } else if (interviewChallenge.value.status === "TIME_EXPIRED") {
        await finishInterview();
      }
    } else {
      const initial = problems.value.find((p) => p.slug === "two-sum") ||
        problems.value.find((p) => p.judgeable) || problems.value[0];
      if (initial) await selectProblem(initial);
    }
    await nextTick();
    clampWorkspaceSplits();
    observeWorkspaceLayout();
  } catch (error) {
    if (isUnmounted) return;
    ElMessage.error(
      error?.response?.data?.message || error?.message || "题库加载失败",
    );
  } finally {
    if (!isUnmounted) problemListLoading.value = false;
  }
});
onBeforeUnmount(() => {
  persistCurrentWorkspace();
  isUnmounted = true;
  problemVersion += 1;
  executionRequestId += 1;
  reviewRequestId += 1;
  submissionsRequestId += 1;
  clearInterval(countdownTimer);
  clearTimeout(draftTimer);
  clearTimeout(customCaseTimer);
  clearTimeout(reviewPollTimer);
  activeInterviewReviewController?.abort();
  activeInterviewReviewController = null;
  clearTimeout(layoutClampTimer);
  layoutResizeObserver?.disconnect();
  stopResize();
  uiStore.cleanup();
});
watch(code, (value) => {
  codeRevision += 1;
  clearTimeout(draftTimer);
  if (detailLoading.value || isUnmounted) return;
  const slug = currentProblem.value?.slug;
  if (!slug) return;
  draftTimer = setTimeout(() => {
    saveWorkspaceValue(
      "draft",
      slug,
      value,
      "浏览器存储空间不足，本次代码草稿未能自动保存",
    );
  }, 500);
});
watch([customDriver, customExpectedOutput], ([driver, expected]) => {
  clearTimeout(customCaseTimer);
  if (detailLoading.value || isUnmounted) return;
  const slug = currentProblem.value?.slug;
  if (!slug) return;
  customCaseTimer = setTimeout(() => {
    saveWorkspaceValue(
      "customCase",
      slug,
      JSON.stringify({ driver, expected }),
      "自定义测试用例未能保存到本地",
    );
  }, 500);
});

async function selectProblem(problem, options = {}) {
  if (!problem?.slug) return;
  if (
    interviewSessionId.value &&
    interviewChallenge.value?.problemSlug &&
    problem.slug !== interviewChallenge.value.problemSlug
  ) {
    ElMessage.info("面试终局题已锁定，完成后可继续自由练习");
    return;
  }
  persistCurrentWorkspace();
  const version = ++problemVersion;
  executionRequestId += 1;
  submissionsRequestId += 1;
  executing.value = false;
  detailLoading.value = true;
  currentProblem.value = problem;
  problemDetail.value = null;
  code.value = "";
  customDriver.value = "";
  customExpectedOutput.value = "";
  executionResult.value = null;
  reviewRequestId += 1;
  clearTimeout(reviewPollTimer);
  activeInterviewReviewController?.abort();
  activeInterviewReviewController = null;
  activeReviewSubmissionId = null;
  aiReviewing.value = false;
  aiReview.value = null;
  lastSubmissionId.value = null;
  consoleTab.value = "cases";
  leftTab.value = "description";
  submissions.value = [];
  startTimer(problem.timeLimitMinutes, options.deadlineAt);
  try {
    const detail = await algorithmApi.getProblem(problem.slug);
    if (isUnmounted || version !== problemVersion) return;
    problemDetail.value = detail;
    code.value = readWorkspaceValue("draft", problem.slug) ||
      detail.codeTemplates?.java || "class Solution {\n    \n}";
    loadCustomCase(problem, detail);
  } catch {
    if (!isUnmounted && version === problemVersion) {
      ElMessage.error("题面同步失败，请稍后重试");
    }
  } finally {
    if (!isUnmounted && version === problemVersion) detailLoading.value = false;
  }
}
function handleProblemSelect(problem) {
  void selectProblem(problem);
  if (uiStore.isMobile) uiStore.collapseSidebar();
}
function startTimer(minutes, deadlineAt = null) {
  clearInterval(countdownTimer);
  const deadline = deadlineAt ? new Date(deadlineAt).getTime() : 0;
  const updateRemaining = () => {
    remainingSeconds.value = deadline
      ? Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
      : Math.max(0, remainingSeconds.value - 1);
    if (
      interviewSessionId.value &&
      remainingSeconds.value === 0 &&
      !finishStarted
    ) {
      void finishInterview();
    }
    if (remainingSeconds.value === 0) clearInterval(countdownTimer);
  };
  remainingSeconds.value = deadline
    ? Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
    : Math.max(1, Number(minutes) || 20) * 60;
  countdownTimer = setInterval(updateRemaining, 1000);
  if (interviewSessionId.value && remainingSeconds.value === 0) {
    void finishInterview();
  }
}
function resetTemplate() {
  code.value = problemDetail.value?.codeTemplates?.java || "class Solution {\n    \n}";
  executionResult.value = null;
}
function defaultCustomDriver(problem, detail) {
  if (problem?.slug === "two-sum") {
    return [
      "Solution solution = new Solution();",
      "System.out.println(java.util.Arrays.toString(",
      "    solution.twoSum(new int[]{2, 7, 11, 15}, 9)",
      "));",
    ].join("\n");
  }
  const template = detail?.codeTemplates?.java || "";
  const method = template.match(/\b(?:public\s+)?[\w<>\[\], ?]+\s+(\w+)\s*\([^)]*\)\s*\{/)?.[1];
  return [
    "Solution solution = new Solution();",
    method
      ? `// TODO：构造参数并调用 solution.${method}(...)`
      : "// TODO：构造参数并调用 Solution 中的方法",
    "// System.out.println(...);",
  ].join("\n");
}
function loadCustomCase(problem, detail) {
  try {
    const saved = JSON.parse(
      readWorkspaceValue("customCase", problem.slug) || "null",
    );
    customDriver.value = typeof saved?.driver === "string"
      ? saved.driver.slice(0, 10_000)
      : defaultCustomDriver(problem, detail);
    customExpectedOutput.value = typeof saved?.expected === "string"
      ? saved.expected.slice(0, 12_000)
      : "";
  } catch {
    customDriver.value = defaultCustomDriver(problem, detail);
    customExpectedOutput.value = "";
  }
}
function isExecutionCurrent(requestId, snapshot) {
  return (
    !isUnmounted &&
    requestId === executionRequestId &&
    snapshot.problemVersion === problemVersion &&
    currentProblem.value?.slug === snapshot.slug
  );
}
function validateExecutionInput(includeCustomCase = false) {
  if (code.value.length > 30_000) {
    ElMessage.warning("代码不能超过 30000 个字符");
    return false;
  }
  if (includeCustomCase && customDriver.value.length > 10_000) {
    ElMessage.warning("自定义测试驱动不能超过 10000 个字符");
    return false;
  }
  if (includeCustomCase && customExpectedOutput.value.length > 12_000) {
    ElMessage.warning("预期输出不能超过 12000 个字符");
    return false;
  }
  return true;
}
async function execute(mode) {
  if (!currentProblem.value || !problemDetail.value || detailLoading.value || executing.value) return;
  if (!validateExecutionInput()) return;
  const requestId = ++executionRequestId;
  const snapshot = {
    slug: currentProblem.value.slug,
    problemVersion,
    code: code.value,
    codeRevision,
  };
  executing.value = true;
  consoleTab.value = "result";
  try {
    const payload = {
      problemSlug: snapshot.slug,
      language: "java",
      code: snapshot.code,
      ...(mode === "submit" && interviewSessionId.value
        ? { interviewSessionId: interviewSessionId.value }
        : {}),
    };
    const result = mode === "submit"
      ? await algorithmApi.submit(payload)
      : await algorithmApi.run(payload);
    if (!isExecutionCurrent(requestId, snapshot)) return;
    executionResult.value = result;
    executionResultRevision = snapshot.codeRevision;
    if (executionResult.value.status === "ACCEPTED") ElMessage.success(mode === "submit" ? "提交通过" : "公开用例通过");
    if (executionResult.value.status === "COMPILED") ElMessage.success(mode === "submit" ? "编译通过，提交记录已保存" : "编译通过");
    if (mode === "submit") {
      lastSubmissionId.value = result.submissionId || null;
      void openSubmissions(false, snapshot.slug);
      const shouldCompleteInterview = Boolean(
        interviewSessionId.value &&
        executionResult.value.interviewReadyToComplete
      );
      if (lastSubmissionId.value) {
        if (shouldCompleteInterview) {
          // The final interview report reads the persisted review. Wait for a
          // bounded terminal review before finishing so the report can include it.
          // Review failure never changes the deterministic judge result.
          await awaitInterviewAiReview(lastSubmissionId.value);
        } else {
          void requestAiReview(lastSubmissionId.value);
        }
      }
      if (shouldCompleteInterview && isExecutionCurrent(requestId, snapshot)) {
        await finishInterview();
      }
    }
  } catch (error) {
    if (isExecutionCurrent(requestId, snapshot)) {
      ElMessage.error(error?.response?.data?.message || "判题请求失败");
    }
  } finally {
    if (requestId === executionRequestId) executing.value = false;
  }
}
const runCode = () => execute("run");
const submitCode = () => execute("submit");
async function runCustomCode() {
  if (
    !currentProblem.value ||
    !problemDetail.value ||
    detailLoading.value ||
    executing.value ||
    !customDriver.value.trim()
  ) return;
  if (!validateExecutionInput(true)) return;
  const requestId = ++executionRequestId;
  const snapshot = {
    slug: currentProblem.value.slug,
    problemVersion,
    code: code.value,
    codeRevision,
    driver: customDriver.value,
    expectedOutput: customExpectedOutput.value,
  };
  executing.value = true;
  consoleTab.value = "result";
  try {
    const result = await algorithmApi.runCustom({
      problemSlug: snapshot.slug,
      language: "java",
      code: snapshot.code,
      driverCode: snapshot.driver,
      expectedOutput: snapshot.expectedOutput,
    });
    if (!isExecutionCurrent(requestId, snapshot)) return;
    executionResult.value = result;
    executionResultRevision = snapshot.codeRevision;
    if (result.status === "CUSTOM_PASSED") ElMessage.success("自定义用例通过");
  } catch (error) {
    if (isExecutionCurrent(requestId, snapshot)) {
      ElMessage.error(error?.response?.data?.message || "自定义测试运行失败");
    }
  } finally {
    if (requestId === executionRequestId) executing.value = false;
  }
}
async function requestAiReview(submissionId, pollAttempt = 0) {
  if (!submissionId || isUnmounted) return;
  if (aiReviewing.value && activeReviewSubmissionId === submissionId) return;
  const requestId = ++reviewRequestId;
  clearTimeout(reviewPollTimer);
  activeReviewSubmissionId = submissionId;
  const snapshot = {
    slug: currentProblem.value?.slug,
    problemVersion,
  };
  aiReviewing.value = true;
  consoleTab.value = "review";
  try {
    const loadReview = pollAttempt === 0
      ? algorithmApi.reviewSubmission
      : algorithmApi.reviewSubmissionStatus;
    const review = normalizeAiReview(
      await loadReview(submissionId),
      submissionId,
    );
    if (
      requestId !== reviewRequestId ||
      snapshot.problemVersion !== problemVersion ||
      snapshot.slug !== currentProblem.value?.slug
    ) return;
    aiReview.value = review;
    if (review.aiStatus === "PROCESSING" && pollAttempt < 45) {
      clearTimeout(reviewPollTimer);
      reviewPollTimer = setTimeout(() => {
        if (
          requestId === reviewRequestId &&
          snapshot.problemVersion === problemVersion &&
          snapshot.slug === currentProblem.value?.slug
        ) {
          void requestAiReview(submissionId, pollAttempt + 1);
        }
      }, 2_000);
    } else if (review.aiStatus === "PROCESSING") {
      aiReview.value = {
        ...review,
        aiStatus: "TIMEOUT",
        aiEvaluation: "AI 评审仍在后台处理中。你可以稍后从提交记录重新打开并获取结果。",
      };
      ElMessage.info("AI 评审仍在后台处理中，可稍后重新获取");
    }
    if (review.aiStatus === "FAILED") {
      ElMessage.warning("判题结果已保存，AI 评审暂时不可用，可稍后重试");
    }
  } catch (error) {
    if (isUnmounted || requestId !== reviewRequestId) return;
    aiReview.value = null;
    ElMessage.warning(
      error?.response?.data?.message ||
      "判题结果已保存，AI 评审暂时不可用，可稍后重试",
    );
  } finally {
    if (requestId === reviewRequestId) {
      aiReviewing.value = false;
      activeReviewSubmissionId = null;
    }
  }
}
const INTERVIEW_REVIEW_MAX_WAIT_MS = 20_000;
const INTERVIEW_REVIEW_POLL_MS = 1_000;

function reviewTimeoutError() {
  const error = new Error("AI 评审等待超时");
  error.name = "ReviewTimeoutError";
  return error;
}
function awaitWithReviewDeadline(promise, milliseconds, controller) {
  return new Promise((resolve, reject) => {
    const timeoutId = setTimeout(() => {
      controller?.abort();
      reject(reviewTimeoutError());
    }, Math.max(1, milliseconds));
    Promise.resolve(promise).then(
      (value) => {
        clearTimeout(timeoutId);
        resolve(value);
      },
      (error) => {
        clearTimeout(timeoutId);
        reject(error);
      },
    );
  });
}
function waitForReviewPoll(milliseconds, signal) {
  return new Promise((resolve) => {
    const timeoutId = setTimeout(resolve, Math.max(0, milliseconds));
    signal?.addEventListener("abort", () => {
      clearTimeout(timeoutId);
      resolve();
    }, { once: true });
  });
}
async function awaitInterviewAiReview(submissionId) {
  if (!submissionId || isUnmounted) return null;
  const requestId = ++reviewRequestId;
  clearTimeout(reviewPollTimer);
  activeInterviewReviewController?.abort();
  const controller = new AbortController();
  activeInterviewReviewController = controller;
  activeReviewSubmissionId = submissionId;
  aiReviewing.value = true;
  aiReview.value = normalizeAiReview({
    aiStatus: "PROCESSING",
    aiEvaluation: "AI 正在完成算法评审，完成后将生成面试报告。",
  }, submissionId);
  consoleTab.value = "review";
  const deadline = Date.now() + INTERVIEW_REVIEW_MAX_WAIT_MS;
  let reviewTriggered = false;

  try {
    while (!isUnmounted && requestId === reviewRequestId) {
      const remaining = deadline - Date.now();
      if (remaining <= 0) throw reviewTimeoutError();
      const loadReview = reviewTriggered
        ? algorithmApi.reviewSubmissionStatus
        : algorithmApi.reviewSubmission;
      reviewTriggered = true;
      const review = normalizeAiReview(
        await awaitWithReviewDeadline(
          loadReview(submissionId, {
            signal: controller.signal,
            timeout: remaining,
          }),
          remaining,
          controller,
        ),
        submissionId,
      );
      if (isUnmounted || requestId !== reviewRequestId) return null;
      aiReview.value = review;
      if (review.aiStatus === "COMPLETED") return review;
      if (review.aiStatus === "FAILED") {
        ElMessage.warning("算法判题已完成，AI 评审失败，本次将降级生成报告");
        return review;
      }
      await waitForReviewPoll(
        Math.min(INTERVIEW_REVIEW_POLL_MS, Math.max(0, deadline - Date.now())),
        controller.signal,
      );
    }
    return null;
  } catch (error) {
    if (isUnmounted || requestId !== reviewRequestId) return null;
    const timedOut =
      error?.name === "ReviewTimeoutError" ||
      error?.name === "AbortError" ||
      error?.code === "ERR_CANCELED";
    aiReview.value = normalizeAiReview({
      aiStatus: timedOut ? "TIMEOUT" : "FAILED",
      aiEvaluation: timedOut
        ? "AI 评审未在限定时间内完成，面试报告将先按判题结果生成。"
        : error?.response?.data?.message ||
          "AI 评审暂时不可用，面试报告将先按判题结果生成。",
    }, submissionId);
    if (timedOut) {
      ElMessage.info("AI 评审等待超时，将按判题结果继续生成报告");
    } else {
      ElMessage.warning("AI 评审暂时不可用，将按判题结果继续生成报告");
    }
    return aiReview.value;
  } finally {
    if (activeInterviewReviewController === controller) {
      activeInterviewReviewController = null;
    }
    if (requestId === reviewRequestId) {
      aiReviewing.value = false;
      activeReviewSubmissionId = null;
    }
  }
}
async function finishInterview() {
  return completeInterview(
    (options) =>
      algorithmApi.finishInterviewChallenge(interviewSessionId.value, options),
    "算法终局题已记录，面试报告生成完成",
  );
}
async function abandonInterview() {
  if (!interviewSessionId.value || finishStarted) return;
  try {
    await ElMessageBox.confirm(
      "放弃后本题将记为 0 分，并立即生成最终面试报告。该操作不可撤销。",
      "确认放弃算法题？",
      {
        confirmButtonText: "确认放弃",
        cancelButtonText: "继续作答",
        type: "warning",
        distinguishCancelAndClose: true,
      },
    );
  } catch {
    return;
  }
  return completeInterview(
    (options) =>
      algorithmApi.abandonInterviewChallenge(interviewSessionId.value, options),
    "已记录主动放弃，面试报告生成完成",
  );
}
async function completeInterview(streamRequest, successMessage) {
  if (!interviewSessionId.value || finishStarted || isUnmounted) return;
  finishStarted = true;
  finishingInterview.value = true;
  clearInterval(countdownTimer);
  let completed = false;
  try {
    await streamRequest({
      onEvent(event) {
        const type = String(event?.type || "").toLowerCase();
        if (type === "completed" || type === "done") completed = true;
        if (type === "error") {
          throw new Error(event?.message || event?.detail || "生成面试报告失败");
        }
      },
    });
    if (!completed) throw new Error("报告生成流未返回完成状态");
    if (isUnmounted) return;
    ElMessage.success(successMessage);
    await router.replace({
      path: "/aiInterview",
      query: { session: interviewSessionId.value },
    });
  } catch (error) {
    if (isUnmounted) return;
    finishStarted = false;
    ElMessage.error(
      error?.response?.data?.message || error?.message || "生成面试报告失败",
    );
  } finally {
    finishingInterview.value = false;
  }
}
function openSubmissionReview(item) {
  const submissionId = item?.id ?? item?.submissionId ?? item?.submission_id;
  if (!submissionId) return;
  lastSubmissionId.value = submissionId;
  aiReview.value = normalizeAiReview(item, submissionId);
  consoleTab.value = "review";
  if (aiReview.value.aiStatus !== "COMPLETED") {
    void requestAiReview(submissionId);
  }
}
function normalizeAiReview(value, submissionId = null) {
  const review = value && typeof value === "object" ? value : {};
  return {
    ...review,
    submissionId:
      review.submissionId ?? review.submission_id ?? submissionId ?? null,
    judgeStatus: review.judgeStatus ?? review.judge_status ?? review.status ?? null,
    aiStatus: review.aiStatus ?? review.ai_status ?? null,
    aiScore: review.aiScore ?? review.ai_score ?? review.score ?? null,
    aiEvaluation:
      review.aiEvaluation ??
      review.ai_evaluation ??
      review.evaluation ??
      review.review ??
      review.content ??
      "",
    aiEvaluatedAt:
      review.aiEvaluatedAt ?? review.ai_evaluated_at ?? null,
  };
}
async function openSubmissions(switchTab = true, problemSlug = currentProblem.value?.slug) {
  if (switchTab) leftTab.value = "submissions";
  if (!problemSlug) return;
  const requestId = ++submissionsRequestId;
  const version = problemVersion;
  try {
    const records = await algorithmApi.submissions(problemSlug);
    if (
      !isUnmounted &&
      requestId === submissionsRequestId &&
      version === problemVersion &&
      currentProblem.value?.slug === problemSlug
    ) {
      submissions.value = Array.isArray(records) ? records : [];
    }
  }
  catch {
    if (
      !isUnmounted &&
      requestId === submissionsRequestId &&
      version === problemVersion &&
      currentProblem.value?.slug === problemSlug
    ) submissions.value = [];
  }
}
const difficultyLabel = (value) => ({ EASY: "简单", MEDIUM: "中等", HARD: "困难" })[value] || value || "未知";
const statusLabel = (value) => ({
  ACCEPTED: "通过", COMPILED: "编译通过", WRONG_ANSWER: "答案错误", COMPILE_ERROR: "编译错误",
  RUNTIME_ERROR: "运行错误", JUDGE_UNAVAILABLE: "判题服务不可用",
  UNSUPPORTED: "暂不支持判题", UNSUPPORTED_LANGUAGE: "语言不支持",
  EXECUTED: "执行完成", CUSTOM_PASSED: "自定义用例通过", CUSTOM_FAILED: "自定义用例未通过",
})[value] || value;
function statusClass(value) {
  const normalized = String(value || "unknown")
    .toLowerCase()
    .replace(/[^a-z0-9_-]/g, "");
  return `status-${normalized || "unknown"}`;
}
function submissionValue(item, snakeCaseKey, camelCaseKey, fallback = null) {
  return item?.[snakeCaseKey] ?? item?.[camelCaseKey] ?? fallback;
}
function saveLocalValue(key, value, warningMessage = "") {
  try {
    localStorage.setItem(key, value);
    return true;
  } catch {
    if (warningMessage && !isUnmounted) ElMessage.warning(warningMessage);
    return false;
  }
}
function readLocalValue(key, fallback = null) {
  try {
    return localStorage.getItem(key) ?? fallback;
  } catch {
    return fallback;
  }
}
function saveWorkspaceValue(kind, slug, value, warningMessage = "") {
  const saved = saveAlgorithmWorkspaceValue(
    { ownerId: workspaceOwnerId, kind, slug, language: "java" },
    value,
  );
  if (!saved && workspaceOwnerId && warningMessage && !isUnmounted) {
    ElMessage.warning(warningMessage);
  }
  return saved;
}
function readWorkspaceValue(kind, slug, fallback = null) {
  return readAlgorithmWorkspaceValue({
    ownerId: workspaceOwnerId,
    kind,
    slug,
    language: "java",
  }) ?? fallback;
}
function persistCurrentWorkspace() {
  const slug = currentProblem.value?.slug;
  if (!slug || detailLoading.value) return;
  clearTimeout(draftTimer);
  clearTimeout(customCaseTimer);
  saveWorkspaceValue(
    "draft",
    slug,
    code.value,
    "浏览器存储空间不足，本次代码草稿未能自动保存",
  );
  saveWorkspaceValue(
    "customCase",
    slug,
    JSON.stringify({
      driver: customDriver.value,
      expected: customExpectedOutput.value,
    }),
    "自定义测试用例未能保存到本地",
  );
}
function readStoredNumber(key, fallback, minimum = 1, maximum = 10_000) {
  try {
    const storedValue = localStorage.getItem(key);
    if (storedValue === null || storedValue.trim() === "") return fallback;
    const value = Number(storedValue);
    return Number.isFinite(value)
      ? Math.min(Math.max(value, minimum), maximum)
      : fallback;
  } catch {
    return fallback;
  }
}
function clamp(value, minimum, maximum) {
  return Math.min(Math.max(value, minimum), Math.max(minimum, maximum));
}
function horizontalSplitBounds() {
  const rect = workbench.value?.getBoundingClientRect();
  if (!rect || rect.width <= 0 || window.innerWidth <= 900) return null;
  const minimum = rect.width < 1000 ? 240 : 300;
  const minimumEditor = rect.width < 1000 ? 330 : 420;
  return {
    minimum,
    maximum: Math.max(minimum, rect.width - 10 - minimumEditor),
  };
}
function verticalSplitBounds() {
  const rect = editorPanel.value?.getBoundingClientRect();
  if (!rect || rect.height <= 0) return null;
  const minimum = 120;
  return {
    minimum,
    maximum: Math.max(minimum, rect.height - 46 - 180 - 8 - 58),
  };
}
function clampWorkspaceSplits() {
  if (isUnmounted) return;
  const horizontal = horizontalSplitBounds();
  if (horizontal) {
    const nextWidth = Math.round(clamp(
      descriptionWidth.value,
      horizontal.minimum,
      horizontal.maximum,
    ));
    if (nextWidth !== descriptionWidth.value) {
      descriptionWidth.value = nextWidth;
      saveLocalValue("algorithm:descriptionWidth", String(nextWidth));
    }
  }

  const vertical = verticalSplitBounds();
  if (vertical) {
    const nextHeight = Math.round(clamp(
      consoleHeight.value,
      vertical.minimum,
      vertical.maximum,
    ));
    if (nextHeight !== consoleHeight.value) {
      consoleHeight.value = nextHeight;
      saveLocalValue("algorithm:consoleHeight", String(nextHeight));
    }
  }
}
function scheduleWorkspaceClamp() {
  if (isUnmounted) return;
  clearTimeout(layoutClampTimer);
  layoutClampTimer = setTimeout(clampWorkspaceSplits, 80);
}
function observeWorkspaceLayout() {
  if (typeof ResizeObserver === "undefined" || !workbench.value) return;
  layoutResizeObserver?.disconnect();
  layoutResizeObserver = new ResizeObserver(scheduleWorkspaceClamp);
  layoutResizeObserver.observe(workbench.value);
  if (editorPanel.value) layoutResizeObserver.observe(editorPanel.value);
}
function resetHorizontalSplit() {
  descriptionWidth.value = 390;
  const bounds = horizontalSplitBounds();
  if (bounds) {
    descriptionWidth.value = Math.round(clamp(
      descriptionWidth.value,
      bounds.minimum,
      bounds.maximum,
    ));
  }
  saveLocalValue("algorithm:descriptionWidth", String(descriptionWidth.value));
}
function resetVerticalSplit() {
  consoleHeight.value = 230;
  const bounds = verticalSplitBounds();
  if (bounds) {
    consoleHeight.value = Math.round(clamp(
      consoleHeight.value,
      bounds.minimum,
      bounds.maximum,
    ));
  }
  saveLocalValue("algorithm:consoleHeight", String(consoleHeight.value));
}
function startResize(axis, event) {
  if (event.button !== 0) return;
  event.preventDefault();
  activeResize.value = axis;
  window.addEventListener("pointermove", resizeWithPointer);
  window.addEventListener("pointerup", stopResize, { once: true });
}
function resizeWithPointer(event) {
  if (activeResize.value === "horizontal" && workbench.value) {
    const challenge = workbench.value.querySelector(".challenge-panel")?.getBoundingClientRect();
    const bounds = horizontalSplitBounds();
    if (!challenge || !bounds) return;
    descriptionWidth.value = Math.round(clamp(
      event.clientX - challenge.left,
      bounds.minimum,
      bounds.maximum,
    ));
  }
  if (activeResize.value === "vertical" && editorPanel.value) {
    const rect = editorPanel.value.getBoundingClientRect();
    const bounds = verticalSplitBounds();
    if (!bounds) return;
    consoleHeight.value = Math.round(clamp(
      rect.bottom - 58 - event.clientY,
      bounds.minimum,
      bounds.maximum,
    ));
  }
}
function stopResize() {
  if (activeResize.value === "horizontal") {
    saveLocalValue("algorithm:descriptionWidth", String(descriptionWidth.value));
  }
  if (activeResize.value === "vertical") {
    saveLocalValue("algorithm:consoleHeight", String(consoleHeight.value));
  }
  activeResize.value = "";
  window.removeEventListener("pointermove", resizeWithPointer);
  window.removeEventListener("pointerup", stopResize);
}
function resizeByKeyboard(axis, event) {
  const horizontalDelta = event.key === "ArrowLeft" ? -24 : event.key === "ArrowRight" ? 24 : 0;
  const verticalDelta = event.key === "ArrowUp" ? 24 : event.key === "ArrowDown" ? -24 : 0;
  if (axis === "horizontal" && horizontalDelta) {
    event.preventDefault();
    const bounds = horizontalSplitBounds();
    if (!bounds) return;
    descriptionWidth.value = clamp(
      descriptionWidth.value + horizontalDelta,
      bounds.minimum,
      bounds.maximum,
    );
    saveLocalValue("algorithm:descriptionWidth", String(descriptionWidth.value));
  }
  if (axis === "vertical" && verticalDelta) {
    event.preventDefault();
    const bounds = verticalSplitBounds();
    if (!bounds) return;
    consoleHeight.value = clamp(
      consoleHeight.value + verticalDelta,
      bounds.minimum,
      bounds.maximum,
    );
    saveLocalValue("algorithm:consoleHeight", String(consoleHeight.value));
  }
}
function formatDate(value) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}
function handleModeChange(mode) {
  if (mode === "chat") router.push("/chat");
  if (mode === "interview") router.push("/aiInterview");
}
</script>

<style scoped>
.algorithm-page{
  --a:#0d8068;--on-a:#fff;--a-soft:rgba(13,128,104,.09);--a-border:rgba(13,128,104,.28);
  --bg:#e8eeea;--p:#f2f6f2;--p2:#e2eae5;--catalog:#edf2ee;--challenge:#f5f8f4;
  --editor:#edf3ef;--console:#e4ebe7;--code:#e2eae6;--topbar:rgba(248,250,247,.94);
  --bd:#ced9d2;--tx:#182823;--soft:#3f534b;--mut:#6b7b74;
  --hover:rgba(13,128,104,.065);--danger:#c2414a;
  min-height:100vh;background:var(--bg);color:var(--tx);
  font-family:Inter,"Microsoft YaHei",system-ui,sans-serif
}
:global([data-theme="dark"] .algorithm-page){
  --a:#65dfbd;--on-a:#08120f;--a-soft:rgba(101,223,189,.09);--a-border:rgba(101,223,189,.28);
  --bg:#0b1017;--p:#141b24;--p2:#202a36;--catalog:#111822;--challenge:#17202b;
  --editor:#101821;--console:#0d141d;--code:#0b121a;--topbar:rgba(15,22,31,.94);
  --bd:#2a3543;--tx:#edf2f7;--soft:#c6d0dc;--mut:#8f9cac;
  --hover:rgba(101,223,189,.07);--danger:#ff7b86
}
.algorithm-main{min-height:100vh;transition:margin-left 260ms cubic-bezier(.2,0,0,1)}
.algorithm-topbar{height:68px;display:flex;align-items:center;justify-content:space-between;padding:0 20px;border-bottom:1px solid var(--bd);background:var(--topbar);backdrop-filter:blur(18px);box-shadow:0 1px 12px rgba(25,35,48,.05)}
.problem-identity,.topbar-actions,.problem-meta,.editor-toolbar,.editor-actions,.result-metrics{display:flex;align-items:center}.problem-identity{gap:12px;min-width:0}.problem-identity>div{display:grid;gap:3px;min-width:0}.problem-identity strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.identity-mark{display:grid;place-items:center;width:38px;height:38px;border:1px solid var(--a-border);border-radius:10px;background:var(--a-soft);color:var(--a);font:700 10px/1 "JetBrains Mono",monospace;letter-spacing:.12em}.eyebrow{color:var(--mut);font:650 10px/1.2 "JetBrains Mono",monospace;letter-spacing:.14em}
.topbar-actions{gap:12px}.timer,.official-link,.catalog-open-button{display:inline-flex;align-items:center;gap:7px;height:36px;padding:0 12px;border:1px solid var(--bd);border-radius:9px;background:transparent;color:var(--mut);font-size:13px;text-decoration:none}.catalog-open-button{cursor:pointer}.catalog-open-button:hover,.catalog-open-button:focus-visible{border-color:var(--a-border);outline:none;background:var(--a-soft);color:var(--a)}.timer{font-family:"JetBrains Mono",monospace;color:var(--tx)}.timer.warning{color:#ffbc66;border-color:#ffbc6666}.official-link:hover{color:var(--a)}
.interview-lock{display:inline-flex;align-items:center;height:30px;padding:0 10px;border:1px solid var(--a-border);border-radius:999px;background:var(--a-soft);color:var(--a);font:650 10px/1 "JetBrains Mono",monospace;letter-spacing:.06em}
.workbench{display:grid;grid-template-columns:minmax(300px,var(--description-width)) 10px minmax(420px,1fr);height:calc(100vh - 68px);min-height:0;overflow:hidden;background:var(--bd)}.challenge-panel,.editor-panel{min-width:0;min-height:0;background:var(--p)}
.challenge-panel{grid-column:1;background:var(--challenge)}.editor-panel{grid-column:3;background:var(--editor)}
.challenge-tabs button,.console-tabs button,.editor-toolbar button{border:0;background:transparent;color:var(--mut);cursor:pointer}
.difficulty-easy{color:#58d6a6!important}.difficulty-medium{color:#ffbd66!important}.difficulty-hard{color:#ff6b78!important}
.challenge-panel{overflow:hidden;display:flex;flex-direction:column}.challenge-tabs,.console-heading{display:flex;align-items:center;height:46px;border-bottom:1px solid var(--bd)}.challenge-tabs{padding:0 16px;gap:18px}.challenge-tabs button,.console-tabs button{align-self:stretch;border-bottom:2px solid transparent;font-size:12px}.challenge-tabs button.active,.console-tabs button.active{color:var(--tx);border-bottom-color:var(--a)}
.problem-description,.submission-list{min-height:0;overflow-y:auto;padding:22px 24px 48px}.problem-meta{flex-wrap:wrap;gap:7px;margin-bottom:18px}.difficulty-pill,.source-pill,.limit-pill{padding:5px 8px;border-radius:6px;background:var(--p2);font-size:10px}.source-pill{color:#a9b2c1}.limit-pill{color:var(--a)}
.official-content{color:var(--soft);font-size:14px;line-height:1.78}.official-content :deep(pre),.sample-box pre,.case-preview pre,.execution-output pre{overflow:auto;padding:12px;border:1px solid var(--bd);border-radius:8px;background:var(--code);color:var(--tx);font:12px/1.6 "JetBrains Mono",monospace;white-space:pre-wrap}.sample-box{margin-top:22px}.sample-box>span{color:var(--mut);font-size:11px}.tag-row{display:flex;flex-wrap:wrap;gap:7px;margin-top:20px}.tag-row span{color:var(--mut);font-size:11px}.panel-state{display:grid;place-items:center;gap:10px;height:100%;color:var(--mut)}
.submission-row{display:grid;grid-template-columns:minmax(84px,1fr) 80px 70px 62px minmax(96px,auto);align-items:center;gap:10px;width:100%;padding:12px 8px;border:0;border-bottom:1px solid var(--bd);background:transparent;color:var(--mut);font:inherit;font-size:11px;text-align:left;cursor:pointer}
.submission-row:hover{background:var(--hover);color:var(--tx)}.submission-row:focus-visible{outline:2px solid var(--a);outline-offset:-2px}.submission-row small{margin-left:3px;color:var(--a);font-size:9px}
.editor-panel{display:grid;grid-template-rows:46px minmax(180px,1fr) 8px minmax(120px,var(--console-height)) 58px;overflow:hidden}.editor-toolbar{justify-content:space-between;padding:0 14px;border-bottom:1px solid var(--bd)}.language-select{display:flex;align-items:center;gap:8px;font-size:12px}.runtime-dot{width:7px;height:7px;border-radius:50%;background:var(--a);box-shadow:0 0 12px #e2ff6588}.editor-surface{min-height:0;overflow:hidden}
.pane-resizer{position:relative;z-index:3;display:grid;place-items:center;background:var(--bd);touch-action:none;user-select:none}.pane-resizer::after{content:"";position:absolute}.pane-resizer span{display:block;border-radius:999px;background:var(--mut);opacity:.35;transition:opacity .15s,background .15s}.pane-resizer:hover span,.pane-resizer:focus-visible span,.workbench.resizing .pane-resizer span{background:var(--a);opacity:1}.pane-resizer:focus-visible{outline:2px solid var(--a);outline-offset:-2px}.pane-resizer-vertical{grid-column:2;cursor:col-resize}.pane-resizer-vertical::after{inset:0 -4px}.pane-resizer-vertical span{width:2px;height:42px}.pane-resizer-horizontal{cursor:row-resize}.pane-resizer-horizontal::after{inset:-4px 0}.pane-resizer-horizontal span{width:42px;height:2px}
.console-panel{min-height:0;background:var(--console);overflow:hidden}.console-heading{justify-content:space-between;padding:0 14px}.console-tabs{display:flex;gap:18px;height:100%}.result-badge{font-size:10px}.case-preview,.execution-output{height:calc(100% - 46px);overflow:auto;padding:14px;color:var(--mut);font-size:12px}.result-metrics{flex-wrap:wrap;gap:8px;margin-bottom:10px}.result-metrics strong{color:var(--tx);font:650 18px/1 "JetBrains Mono",monospace}.error-copy{color:var(--danger);white-space:pre-wrap}.execution-output ul{display:grid;grid-template-columns:1fr 1fr;gap:7px;padding:0;list-style:none}.execution-output li{padding:7px;border-radius:6px;background:var(--p2)}
.status-accepted,.status-compiled{color:#159873!important}.status-wrong_answer,.status-compile_error,.status-runtime_error{color:var(--danger)!important}.status-judge_unavailable{color:#b57513!important}.editor-actions{justify-content:flex-end;gap:9px;padding:0 14px;border-top:1px solid var(--bd);background:var(--editor)}.judge-note{margin-right:auto;color:var(--mut);font-size:10px}.editor-actions button{height:36px;padding:0 16px;border-radius:8px;font-weight:650;cursor:pointer}.editor-actions button:disabled{opacity:.4;cursor:not-allowed}.run-button{display:inline-flex;align-items:center;gap:6px;border:1px solid var(--bd);background:var(--p2);color:var(--tx)}.abandon-button{border:1px solid color-mix(in srgb,var(--danger) 38%,var(--bd));background:transparent;color:var(--danger)}.abandon-button:hover:not(:disabled){background:color-mix(in srgb,var(--danger) 8%,transparent)}.submit-button{border:1px solid var(--a);background:var(--a);color:var(--on-a)}
.status-custom_passed{color:#159873!important}.status-custom_failed{color:var(--danger)!important}.status-executed{color:var(--a)!important}
.custom-case-panel,.ai-review-panel{height:calc(100% - 46px);overflow:auto;padding:12px 14px;color:var(--mut);font-size:11px}
.custom-case-panel{display:grid;grid-template-rows:auto minmax(72px,1fr) auto auto;gap:9px}
.custom-case-copy{display:flex;align-items:baseline;justify-content:space-between;gap:12px}.custom-case-copy strong{color:var(--tx);font-size:12px}.custom-driver{width:100%;min-height:72px;resize:none;padding:10px;border:1px solid var(--bd);border-radius:8px;outline:none;background:var(--code);color:var(--tx);font:11px/1.55 "JetBrains Mono",monospace}.custom-driver:focus{border-color:var(--a-border);box-shadow:0 0 0 3px var(--a-soft)}
.expected-output{display:grid;grid-template-columns:auto minmax(140px,1fr);align-items:start;gap:10px}.expected-output span{padding-top:8px;color:var(--tx);font-weight:650}.expected-output small{color:var(--mut);font-weight:400}.expected-output textarea{width:100%;min-height:52px;max-height:120px;resize:vertical;padding:8px 9px;border:1px solid var(--bd);border-radius:7px;outline:none;background:var(--p);color:var(--tx);font:11px/1.45 "JetBrains Mono",monospace}.expected-output textarea:focus{border-color:var(--a-border);box-shadow:0 0 0 3px var(--a-soft)}
.custom-case-actions{display:flex;align-items:center;justify-content:space-between;gap:12px;min-width:0}.custom-case-actions>span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.custom-case-actions button,.review-empty button{flex:none;height:32px;padding:0 12px;border:1px solid var(--a-border);border-radius:7px;background:var(--a-soft);color:var(--a);font-weight:650;cursor:pointer}.custom-case-actions button:disabled{opacity:.45;cursor:not-allowed}
.stale-result-note{margin:0 0 10px;padding:8px 10px;border-left:3px solid #d08a18;background:color-mix(in srgb,#d08a18 8%,transparent);color:#b46d06}
.review-loading,.review-empty{display:grid;place-items:center;align-content:center;gap:8px;height:100%;text-align:center}.review-empty strong{color:var(--tx);font-size:13px}.review-score{display:flex;align-items:center;justify-content:space-between;padding-bottom:10px;border-bottom:1px solid var(--bd);color:var(--a);font:650 10px/1 "JetBrains Mono",monospace;letter-spacing:.1em}.review-score strong{color:var(--tx);font-size:24px;letter-spacing:0}.review-score small{color:var(--mut);font-size:10px}.review-content{padding:10px 2px;color:var(--soft);font-size:12px;line-height:1.65}.review-content :deep(h1),.review-content :deep(h2),.review-content :deep(h3){margin:12px 0 6px;color:var(--tx);font-size:13px}.review-content :deep(pre){overflow:auto;padding:10px;border:1px solid var(--bd);border-radius:8px;background:var(--code)}
.review-failed strong{color:var(--danger)}.review-failed button:disabled{opacity:.45;cursor:not-allowed}
@media(max-width:1180px){.workbench{grid-template-columns:minmax(240px,var(--description-width)) 10px minmax(330px,1fr)}}@media(max-width:900px){.workbench{height:auto;overflow:visible;grid-template-columns:1fr}.challenge-panel,.editor-panel{grid-column:1}.challenge-panel{min-height:520px}.editor-panel{min-height:720px}.pane-resizer-vertical{display:none}}@media(max-width:768px){.algorithm-main{margin-left:0!important}}
@media(max-width:640px){.algorithm-topbar{min-height:64px;height:auto;padding:10px 12px;gap:8px}.identity-mark{display:none}.problem-identity .eyebrow,.official-link,.interview-lock{display:none}.problem-identity strong{max-width:42vw;font-size:12px}.topbar-actions{gap:6px}.timer,.catalog-open-button{height:34px;padding:0 9px;font-size:11px}.challenge-panel{min-height:480px}.editor-panel{min-height:760px}.custom-case-copy{align-items:flex-start;flex-direction:column;gap:3px}.expected-output{grid-template-columns:1fr}.expected-output span{padding-top:0}.editor-actions{padding:0 8px}.judge-note{display:none}.editor-actions button{padding:0 12px}}
</style>
