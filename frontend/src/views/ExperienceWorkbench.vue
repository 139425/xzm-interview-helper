<template>
  <WorkspaceFrame
    :mode="preparing ? 'preparation' : 'experience'"
    :title="preparing ? '备战工作台' : '面经研究'"
    eyebrow="INTERVIEW LAB"
    mark="研"
  >
    <template #actions
      ><button
        class="button quiet"
        :disabled="syncing || !status.automatic"
        @click="synchronize"
      >
        {{ syncing ? "正在同步…" : "同步语雀秋招" }}
      </button></template
    >
    <main class="experience-page">
      <header class="hero">
        <div>
          <p class="eyebrow">从真实问题，到下一次进步</p>
          <h1>
            {{
              preparing
                ? "把准备落到每一次作答。"
                : "读懂面试，也看见自己的下一步。"
            }}
          </h1>
          <p>真实问法、知识依据与训练记录，在这里连起来。</p>
        </div>
        <div class="view-switch">
          <RouterLink to="/experience" :class="{ active: !preparing }"
            >面经研究</RouterLink
          ><RouterLink to="/preparation" :class="{ active: preparing }"
            >备战工作台</RouterLink
          >
        </div>
      </header>
      <div v-if="loading" class="empty" role="status">正在加载面经库…</div>
      <div v-else-if="error" class="empty" role="alert">
        {{ error }}<button class="button" @click="load">重新加载</button>
      </div>
      <div v-else-if="!status.connected" class="empty">
        当前账号尚未接入面经库。语雀资料只对所属账号开放。
      </div>
      <template v-else>
        <section
          class="sync-note"
          :class="{ failed: status.status === 'FAILED' }"
          aria-live="polite"
        >
          <span>{{
            status.message || "已导入历史面经，后续仅同步秋招目录。"
          }}</span
          ><small
            >版本 {{ library.revision }} · 最近成功同步
            {{ formatDate(status.syncedAt) }}</small
          >
        </section>
        <details class="revision-history" @toggle="loadRevisions">
          <summary>数据版本与恢复</summary>
          <p class="boundary">
            恢复只调整题库资料，个人答案和作答历史保留。下一次语雀同步仍会检查最新秋招内容。
          </p>
          <button
            v-for="v in revisions"
            :key="v.revision"
            class="button quiet"
            :disabled="v.revision === library.revision || saving"
            @click="restoreVersion(v.revision)"
          >
            恢复版本 {{ v.revision }} · {{ formatDate(v.createdAt) }}
          </button>
          <p v-if="!revisions.length" class="boundary">
            尚无变更历史，首次同步或核对后会保存版本。
          </p>
        </details>
        <section class="metrics" aria-label="面经概览">
          <div>
            <b>{{ activeDocs.length }}</b
            ><span>源面经</span>
          </div>
          <div>
            <b>{{ library.questions.length }}</b
            ><span>归并条目</span>
          </div>
          <div>
            <b>{{ dueCount }}</b
            ><span>到期复测</span>
          </div>
          <div>
            <b>{{ pendingCount }}</b
            ><span>待核对新问法</span>
          </div>
        </section>
        <section v-if="preparing" class="preparation-card">
          <div>
            <p class="eyebrow">今天，从这里开始</p>
            <h2>先诊断，再补缺口。</h2>
            <p>
              排序考虑计划、复测日期、你的自评状态、岗位方向和题频。未练过代表未验证。
            </p>
          </div>
          <label
            >目标方向<input
              v-model="role"
              placeholder="例如 Java 后端、SRE、AI 应用"
              maxlength="100"
          /></label>
          <label v-if="schedules.length"
            >关联下一场面试<select
              v-model="scheduleId"
              @change="selectSchedule"
            >
              <option value="">选择面试安排</option>
              <option v-for="s in schedules" :key="s.id" :value="String(s.id)">
                {{ s.company }} · {{ s.roleName || s.role_name || "面试" }} ·
                {{ formatDate(s.startAt || s.start_at) }}
              </option>
            </select></label
          >
          <div class="daily-list">
            <button
              v-for="q in ranked.slice(0, 3)"
              :key="q.key"
              class="daily-task"
              @click="openQuestion(q)"
            >
              <span>{{ q.question }}</span
              ><small>{{ rank(q).reasons.join(" · ") }}</small
              ><b>开始作答 →</b>
            </button>
            <p v-if="!ranked.length">当前筛选下没有任务。</p>
          </div>
        </section>
        <nav class="tabs" aria-label="研究视图">
          <button
            v-for="t in tabs"
            :key="t.id"
            :class="{ active: tab === t.id }"
            @click="tab = t.id"
          >
            {{ t.label }}
          </button>
        </nav>
        <section class="filters">
          <input
            v-model="search"
            aria-label="搜索问题或原文"
            placeholder="搜索考点、公司或真实问法…"
          /><select v-model="category" aria-label="分类">
            <option value="">全部分类</option>
            <option v-for="c in categories" :key="c">{{ c }}</option></select
          ><select v-model="stage" aria-label="招聘阶段">
            <option value="">全部阶段</option>
            <option>秋招</option>
            <option>暑期</option>
            <option>日常</option></select
          ><select v-model="sort" aria-label="排序">
            <option value="frequency">出现篇数优先</option>
            <option value="priority">准备优先级</option>
            <option value="mentions">关联记录数优先</option></select
          ><label class="check"
            ><input v-model="pending" type="checkbox" />待核对</label
          >
        </section>
        <p class="boundary">
          频次按不同面经篇数计算，同篇多次追问只计一篇；反问单独归类。它表示历史记录，不是下次被问概率。
        </p>
        <section v-if="tab === 'questions'" class="question-list">
          <div class="list-heading">
            <span>{{ filtered.length }} 个条目</span
            ><span>出现篇数 / 原文记录</span>
          </div>
          <button
            v-for="q in visible"
            :key="q.key"
            class="question-row"
            @click="openQuestion(q)"
          >
            <div>
              <div class="tags">
                <span>{{ q.category }}</span
                ><span v-if="q.reviewed === false" class="warning">待核对</span
                ><span v-if="progress[q.key]?.pinned">已加入计划</span>
              </div>
              <h3>{{ q.question }}</h3>
              <p>
                {{ masteryLabels[progress[q.key]?.state || "UNKNOWN"]
                }}<template v-if="preparing || sort === 'priority'">
                  · {{ rank(q).reasons.join(" · ") }}</template
                >
              </p>
            </div>
            <div class="frequency">
              <b>{{ q.frequency }}</b
              ><span>{{ q.mentions }} 条原文</span>
            </div>
          </button>
          <p v-if="!filtered.length" class="empty">
            没有匹配的题目，试试其他筛选。
          </p>
          <button
            v-if="visible.length < filtered.length"
            class="button more"
            @click="limit += 40"
          >
            再显示 40 条
          </button>
        </section>
        <section v-else-if="tab === 'categories'" class="category-grid">
          <article
            v-for="c in categorySummary"
            :key="c.name"
            class="category-card"
          >
            <small>{{ c.docs }} 篇面经 · {{ c.questions.length }} 个条目</small>
            <h2>{{ c.name }}</h2>
            <p>
              {{
                categoryGuidance[c.name] ||
                "围绕真实问法，补齐概念、前提与一个具体例子。"
              }}
            </p>
            <button
              v-for="q in c.questions.slice(0, 3)"
              :key="q.key"
              @click="openQuestion(q)"
            >
              {{ q.question }} <span>{{ q.frequency }} 篇</span></button
            ><button
              class="button quiet"
              @click="
                category = c.name;
                tab = 'questions';
              "
            >
              查看本类全部问题 →
            </button>
          </article>
          <p v-if="!categorySummary.length" class="empty">
            当前筛选没有分类结果。
          </p>
        </section>
        <section v-else-if="tab === 'documents'" class="document-grid">
          <button
            v-for="d in filteredDocs"
            :key="d.id"
            class="document-card"
            @click="openDocument(d.id)"
          >
            <small>{{ d.stage }} · {{ d.rowCount }} 条正文记录</small>
            <h3>{{ d.title }}</h3>
            <span>查看整场面经 →</span>
          </button>
          <p v-if="!filteredDocs.length" class="empty">没有匹配的面经。</p>
        </section>
        <section v-else class="category-grid">
          <article
            v-for="c in filteredChains"
            :key="c.id"
            class="category-card"
          >
            <small>{{ c.kind }} · {{ c.length }} 条关联记录</small>
            <h2>{{ c.title }}</h2>
            <p>{{ c.insight }}</p>
            <p class="prep">
              准备任务：{{ c.prep || "按原文顺序回答，并检查每一步的前提。" }}
            </p>
            <button class="button" @click="openChain(c)">
              按原始记录练习 →
            </button>
          </article>
          <p class="boundary">
            专题来自已有人工整理。源文档变化后，缺失的旧记录会提示失效；连续记录数不等于实际追问层数。
          </p>
        </section>
      </template>
    </main>
    <el-drawer
      v-model="drawer"
      :title="document ? '整场面经' : '作答 · 证据 · 复测'"
      size="min(800px, 100vw)"
      destroy-on-close
      @closed="closeDetail"
    >
      <div v-if="detailLoading" class="empty">正在读取…</div>
      <p v-else-if="detailError" role="alert">{{ detailError }}</p>
      <div v-else-if="document" class="detail">
        <h2>{{ document.title }}</h2>
        <a
          v-if="safeSource(document.url)"
          :href="safeSource(document.url)"
          target="_blank"
          rel="noopener noreferrer"
          >打开语雀原文 ↗</a
        >
        <pre class="source-body">{{ document.body }}</pre>
      </div>
      <div v-else-if="selected" class="detail">
        <div class="tags">
          <span>{{ selected.category }}</span
          ><span>{{ selected.frequency }} 篇面经</span>
        </div>
        <h2>{{ selected.question }}</h2>
        <p v-if="chain" class="chain-progress">
          专题：{{ chain.title }} · 第 {{ chainIndex + 1 }} /
          {{ chainQuestions.length }} 条
          <button
            v-if="chainIndex + 1 < chainQuestions.length"
            class="button quiet"
            :disabled="evaluating"
            @click="nextChain"
          >
            下一条 →
          </button>
        </p>
        <nav class="tabs">
          <button
            v-for="t in detailTabs"
            :key="t.id"
            :class="{ active: detailTab === t.id }"
            @click="detailTab = t.id"
          >
            {{ t.label }}
          </button>
        </nav>
        <section v-if="detailTab === 'practice'" class="practice">
          <label v-if="!parentAttemptId"
            >练习问法<select
              v-model="rowId"
              :disabled="evaluating"
              @change="resetAttempt"
            >
              <option value="">标准题目</option>
              <option
                v-for="e in selected.evidence"
                :key="`${e.docId}-${e.row.id}`"
                :value="e.row.id"
              >
                {{ e.title }}：{{ e.row.text }}
              </option>
            </select></label
          >
          <div class="asked">
            <small>{{
              parentAttemptId
                ? "AI 生成的训练变式"
                : rowId
                  ? "来自面经原文"
                  : "标准题目"
            }}</small>
            <p>{{ asked }}</p>
          </div>
          <label
            >先独立回答<textarea
              v-model="answer"
              rows="8"
              maxlength="12000"
              placeholder="讲清结论、前提、理由与边界。涉及个人经历时，只写实际做过的事。"
              :disabled="evaluating"
              @input="attemptId = ''; feedback = null"
            />
          </label>
          <button
            class="button"
            :disabled="evaluating || !answer.trim()"
            @click="submitAnswer"
          >
            {{ evaluating ? "回答已提交，正在生成反馈…" : "提交并获取反馈" }}
          </button>
          <p class="boundary">
            反馈用于辅助复盘，掌握状态由你结合实际表现记录；AI
            无法确定面试淘汰原因。
          </p>
          <section v-if="feedback" class="feedback">
            <h3>本次反馈</h3>
            <StaticMarkdown :content="feedback.feedback" />
            <details>
              <summary>查看回答提纲与参考资料</summary>
              <StaticMarkdown :content="feedback.reference" />
              <p v-if="feedback.knowledgeSources?.length">
                参考资料：{{ feedback.knowledgeSources.join("、") }}
              </p>
            </details>
            <button
              v-if="feedback.followUp"
              :disabled="evaluating"
              class="button quiet"
              @click="followUp"
            >
              继续追问 →
            </button>
          </section>
          <details v-if="attempts.length" class="history">
            <summary>历史作答（{{ attempts.length }}）</summary>
            <article v-for="a in attempts" :key="a.id">
              <small
                >{{ formatDate(a.createdAt) }} · 题库版本
                {{ a.revision }}</small
              >
              <h4>{{ a.question }}</h4>
              <pre>{{ a.answer }}</pre>
              <StaticMarkdown
                :content="
                  parseFeedback(a.feedback).feedback ||
                  '反馈尚未完成，回答已保留。'
                "
              />
            </article>
          </details>
        </section>
        <section v-else-if="detailTab === 'sources'" class="sources">
          <article
            v-for="e in selected.evidence"
            :key="`${e.docId}-${e.row.id}`"
          >
            <small>{{ e.stage }} · {{ e.title }} · L{{ e.row.line }}</small>
            <p>{{ e.row.text }}</p>
            <button class="button quiet" @click="openDocument(e.docId)">
              查看整场上下文</button
            ><a
              v-if="safeSource(e.url)"
              :href="safeSource(e.url)"
              target="_blank"
              rel="noopener noreferrer"
              >语雀原文 ↗</a
            >
          </article>
        </section>
        <form
          v-else-if="detailTab === 'personal'"
          class="personal"
          @submit.prevent="saveProgress"
        >
          <details v-if="relatedNotes.length">
            <summary>合并前的个人答案与证据</summary>
            <article v-for="n in relatedNotes" :key="n.questionKey">
              <pre class="source-body"
                >{{ n.personalAnswer + '\n' + n.evidence }}</pre
              >
            </article>
          </details>
          <label
            >我的掌握状态<select v-model="draft.state">
              <option
                v-for="(label, value) in masteryLabels"
                :key="value"
                :value="value"
              >
                {{ label }}
              </option>
            </select></label
          ><label
            >我的答案提纲<textarea
              v-model="draft.personalAnswer"
              rows="7"
              maxlength="30000"
              placeholder="可按 30 秒结论、两分钟展开、深入追问组织。"
            /></label
          ><label
            >项目依据 / 待核验事实<textarea
              v-model="draft.evidence"
              rows="5"
              maxlength="10000"
              placeholder="背景、我的贡献、方案取舍、验证结果；缺少数据时标记待验证。"
            /></label
          ><label
            >目标岗位<input v-model="draft.targetRole" maxlength="300" /></label
          ><label>下次复测<input v-model="draft.dueAt" type="date" /></label
          ><label class="check"
            ><input v-model="draft.pinned" type="checkbox" />加入备战计划</label
          ><button class="button" :disabled="saving">
            {{ saving ? "保存中…" : "保存答案与计划" }}
          </button>
        </form>
        <form v-else class="personal" @submit.prevent="saveReview">
          <p>
            人工核对会保留到后续同步。合并仅调整来源归属，旧作答和个人笔记仍保存在原题下。
          </p>
          <label
            >标准题干<textarea
              v-model="review.question"
              maxlength="3000"
              rows="4"
            /></label
          ><label
            >分类<input
              v-model="review.category"
              maxlength="100"
              list="experience-categories" /><datalist
              id="experience-categories"
            >
              <option
                v-for="c in categories"
                :key="c"
                :value="c"
              /></datalist></label
          ><label
            >或归并到已有题目<select v-model="review.mergeInto">
              <option value="">保持独立题目</option>
              <option
                v-for="q in library.questions.filter(
                  (x) => x.key !== selected.key,
                )"
                :key="q.key"
                :value="q.key"
              >
                {{ q.question }}
              </option>
            </select></label
          ><button class="button" :disabled="saving">确认归类</button
          ><button
            type="button"
            class="button quiet"
            :disabled="saving"
            @click="excludeQuestion"
          >
            这是过程说明，不计题频
          </button>
        </form>
      </div>
    </el-drawer>
  </WorkspaceFrame>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import WorkspaceFrame from "@/components/WorkspaceFrame.vue";
import StaticMarkdown from "@/components/StaticMarkdown.vue";
import { experienceApi } from "@/api/experience";
import { scheduleApi } from "@/api/career";
import {
  filterQuestions,
  localDate,
  masteryLabels,
  parseFeedback,
  priority,
  safeSource,
} from "@/utils/experience";

const route = useRoute(),
  preparing = computed(() => route.path === "/preparation");
const library = ref({ questions: [], docs: [], chains: [], progress: [] }),
  status = ref({}),
  loading = ref(true),
  error = ref("");
const search = ref(""),
  category = ref(""),
  stage = ref(""),
  pending = ref(false),
  sort = ref(preparing.value ? "priority" : "frequency"),
  tab = ref("questions"),
  limit = ref(40),
  role = ref(""),
  schedules = ref([]),
  scheduleId = ref("");
const drawer = ref(false),
  detailLoading = ref(false),
  detailError = ref(""),
  selected = ref(null),
  document = ref(null),
  detailTab = ref("practice");
const answer = ref(""),
  rowId = ref(""),
  parentAttemptId = ref(""),
  askedOverride = ref(""),
  attemptId = ref(""),
  feedback = ref(null),
  attempts = ref([]),
  evaluating = ref(false),
  saving = ref(false);
const revisions = ref([]),
  relatedNotes = ref([]);
const draft = ref({}),
  review = ref({}),
  chain = ref(null),
  chainQuestions = ref([]),
  chainIndex = ref(0);
let timer,
  detailRequest = 0,
  disposed = false;
const tabs = [
  { id: "questions", label: "问题与频次" },
  { id: "categories", label: "分类总结" },
  { id: "chains", label: "追问专题" },
  { id: "documents", label: "整场面经" },
];
const detailTabs = [
  { id: "practice", label: "练习与反馈" },
  { id: "sources", label: "真实问法" },
  { id: "personal", label: "我的答案与计划" },
  { id: "review", label: "核对归类" },
];
const categoryGuidance = {
  数据库与中间件:
    "先证明结果正确，再解释性能与并发边界。准备查询、时序或一个反例。",
  "AI / RAG": "讲清输入到结果的链路，用真实失败案例解释效果与取舍。",
  项目与业务: "区分已有系统和个人贡献，补齐业务目标、实现与验证依据。",
  "工程 / SRE / 容器":
    "从现象收集证据，再定位、恢复与复盘。说明每一步判断依据。",
  "HR 与行为面": "用具体事件回答：情境、个人行动、结果与反思。",
  算法与数据结构: "独立实现后解释复杂度、边界和替代方案，可接着进入算法训练。",
  反问与交流: "明确要了解的信息：业务、职责、培养方式与岗位匹配。",
};
const progress = computed(() =>
  Object.fromEntries(library.value.progress.map((p) => [p.questionKey, p])),
);
const activeDocs = computed(() =>
  library.value.docs.filter((d) => d.active !== false),
);
const categories = computed(() =>
  [...new Set(library.value.questions.map((q) => q.group))].sort(),
);
const pendingCount = computed(
  () => library.value.questions.filter((q) => q.reviewed === false).length,
);
const dueCount = computed(
  () =>
    library.value.progress.filter(
      (p) => p.dueAt && String(p.dueAt).slice(0, 10) <= localDate(),
    ).length,
);
const syncing = computed(() => status.value.status === "RUNNING");
const rank = (q) => priority(q, progress.value[q.key], role.value);
const filtered = computed(() =>
  filterQuestions(library.value.questions, {
    search: search.value,
    category: category.value,
    stage: stage.value,
    pending: pending.value,
  }).sort((a, b) =>
    sort.value === "priority"
      ? rank(b).score - rank(a).score
      : sort.value === "mentions"
        ? b.mentions - a.mentions
        : b.frequency - a.frequency || b.mentions - a.mentions,
  ),
);
const ranked = computed(() =>
  [...filtered.value].sort((a, b) => rank(b).score - rank(a).score),
);
const visible = computed(() => filtered.value.slice(0, limit.value));
const categorySummary = computed(() =>
  [...new Set(filtered.value.map((q) => q.group))].map((name) => {
    const questions = filtered.value.filter((q) => q.group === name);
    return {
      name,
      questions,
      docs: new Set(questions.flatMap((q) => q.evidence.map((e) => e.docId)))
        .size,
    };
  }),
);
const filteredDocs = computed(() =>
  activeDocs.value.filter(
    (d) =>
      (!stage.value || d.stage === stage.value) &&
      (!search.value ||
        d.title.toLowerCase().includes(search.value.toLowerCase())),
  ),
);
const filteredChains = computed(() =>
  library.value.chains.filter((c) => {
    const doc = activeDocs.value.find((d) => d.id === c.doc_id);
    return (
      doc &&
      (!stage.value || doc.stage === stage.value) &&
      (!search.value ||
        `${c.title} ${c.insight}`
          .toLowerCase()
          .includes(search.value.toLowerCase()))
    );
  }),
);
const asked = computed(() =>
  parentAttemptId.value
    ? askedOverride.value
    : selected.value?.evidence.find((e) => e.row.id === rowId.value)?.row
        .text ||
      selected.value?.question ||
      "",
);
const formatDate = (value) =>
  value
    ? new Date(value).toLocaleString("zh-CN", {
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
      })
    : "尚未同步";
const message = (e, fallback) =>
  e?.response?.data?.message || e?.response?.data?.detail || fallback;
async function load() {
  loading.value = true;
  error.value = "";
  try {
    status.value = await experienceApi.status();
    if (status.value.connected) library.value = await experienceApi.library();
  } catch (e) {
    error.value = message(e, "加载失败，请稍后重试");
  } finally {
    loading.value = false;
  }
}
async function synchronize() {
  try {
    await experienceApi.sync();
    status.value = {
      ...status.value,
      status: "RUNNING",
      message: "同步已提交，正在检查秋招目录…",
    };
    ElMessage.success("已开始同步");
  } catch (e) {
    ElMessage.error(message(e, "无法开始同步"));
  }
}
async function poll() {
  if (disposed || !status.value.connected) return;
  try {
    const next = await experienceApi.status();
    const previous = status.value.status;
    status.value = next;
    if (
      (previous === "RUNNING" && next.status !== "RUNNING") ||
      next.revision !== library.value.revision
    )
      library.value = await experienceApi.library();
  } catch {
    /* Keep last successful snapshot visible. */
  }
}
function resetAttempt() {
  feedback.value = null;
  attemptId.value = "";
  parentAttemptId.value = "";
  askedOverride.value = "";
}
async function openQuestion(q, retainChain = false, initialRow = "") {
  const request = ++detailRequest;
  if (!retainChain) chain.value = null;
  drawer.value = true;
  document.value = null;
  selected.value = q;
  detailLoading.value = true;
  detailError.value = "";
  detailTab.value = "practice";
  answer.value = "";
  rowId.value = initialRow;
  resetAttempt();
  try {
    const data = await experienceApi.question(q.key);
    if (request !== detailRequest) return;
    selected.value = data.question;
    attempts.value = data.attempts;
    relatedNotes.value = data.relatedNotes || [];
    const p = progress.value[q.key] || {};
    draft.value = {
      state: p.state || "UNKNOWN",
      personalAnswer: p.personalAnswer || "",
      evidence: p.evidence || "",
      pinned: !!p.pinned,
      dueAt: p.dueAt ? String(p.dueAt).slice(0, 10) : "",
      targetRole: p.targetRole || role.value,
    };
    review.value = {
      question: selected.value.question,
      category: selected.value.group,
      mergeInto: "",
    };
  } catch (e) {
    if (request === detailRequest)
      detailError.value = message(e, "读取题目失败");
  } finally {
    if (request === detailRequest) detailLoading.value = false;
  }
}
async function openDocument(id) {
  const request = ++detailRequest;
  drawer.value = true;
  detailLoading.value = true;
  detailError.value = "";
  document.value = null;
  try {
    const data = await experienceApi.document(id);
    if (request === detailRequest) document.value = data;
  } catch (e) {
    if (request === detailRequest)
      detailError.value = message(e, "读取面经失败");
  } finally {
    if (request === detailRequest) detailLoading.value = false;
  }
}
function closeDetail() {
  detailRequest++;
  document.value = null;
  selected.value = null;
  chain.value = null;
}
async function saveProgress() {
  saving.value = true;
  try {
    await experienceApi.progress(selected.value.key, draft.value);
    library.value = await experienceApi.library();
    ElMessage.success("答案与复测计划已保存");
  } catch (e) {
    ElMessage.error(message(e, "保存失败"));
  } finally {
    saving.value = false;
  }
}
async function saveReview() {
  saving.value = true;
  try {
    await experienceApi.review(selected.value.key, {
      ...review.value,
      revision: library.value.revision,
    });
    library.value = await experienceApi.library();
    drawer.value = false;
    ElMessage.success("归类已更新，频次已重新计算");
  } catch (e) {
    ElMessage.error(message(e, "保存失败"));
  } finally {
    saving.value = false;
  }
}
async function submitAnswer() {
  if (evaluating.value) return;
  const key = selected.value.key,
    request = detailRequest;
  evaluating.value = true;
  if (!attemptId.value) attemptId.value = crypto.randomUUID();
  try {
    const data = await experienceApi.attempt(key, {
      id: attemptId.value,
      answer: answer.value,
      rowId: rowId.value,
      parentAttemptId: parentAttemptId.value,
    });
    if (request === detailRequest) {
      feedback.value = data.result;
      attempts.value = (await experienceApi.question(key)).attempts;
    }
  } catch (e) {
    ElMessage.error(message(e, "反馈未完成，回答已保留，可重试"));
  } finally {
    evaluating.value = false;
  }
}
function followUp() {
  askedOverride.value = feedback.value.followUp;
  parentAttemptId.value = attemptId.value;
  rowId.value = "";
  attemptId.value = "";
  feedback.value = null;
  answer.value = "";
}
function openChain(c) {
  const items = [];
  for (const id of c.row_ids) {
    const q = library.value.questions.find((q) =>
      q.evidence.some((e) => e.docId === c.doc_id && e.row.id === id),
    );
    if (q) items.push({ q, rowId: id });
  }
  if (!items.length) {
    ElMessage.info("该专题原始记录已变化，请从整场面经重新选择");
    return;
  }
  if (items.length < c.row_ids.length)
    ElMessage.info("部分旧记录已变化，仅练习仍可追溯的原文");
  chain.value = c;
  chainQuestions.value = items;
  chainIndex.value = 0;
  openQuestion(items[0].q, true, items[0].rowId);
}
function nextChain() {
  chainIndex.value++;
  const item = chainQuestions.value[chainIndex.value];
  openQuestion(item.q, true, item.rowId);
}
async function loadRevisions(event) {
  if (!event.target.open) return;
  try {
    revisions.value = await experienceApi.revisions();
  } catch {
    ElMessage.error("版本记录加载失败");
  }
}
async function restoreVersion(version) {
  try {
    await ElMessageBox.confirm(
      `恢复题库版本 ${version}？个人作答记录会保留。`,
      "恢复资料版本",
    );
    saving.value = true;
    await experienceApi.restore(version, library.value.revision);
    await load();
    revisions.value = await experienceApi.revisions();
    ElMessage.success("已恢复为新版本");
  } catch (e) {
    if (e !== "cancel" && e !== "close")
      ElMessage.error(message(e, "恢复失败"));
  } finally {
    saving.value = false;
  }
}
async function excludeQuestion() {
  saving.value = true;
  try {
    await experienceApi.review(selected.value.key, {
      revision: library.value.revision,
      exclude: true,
    });
    library.value = await experienceApi.library();
    drawer.value = false;
    ElMessage.success("已从当前题频中排除，原文保留");
  } catch (e) {
    ElMessage.error(message(e, "保存失败"));
  } finally {
    saving.value = false;
  }
}
function selectSchedule() {
  const s = schedules.value.find((s) => String(s.id) === scheduleId.value);
  if (s) role.value = s.roleName || s.role_name || "";
}
watch([search, category, stage, pending], () => {
  limit.value = 40;
});
watch(preparing, (v) => {
  sort.value = v ? "priority" : "frequency";
});
onMounted(async () => {
  await load();
  try {
    const s = await scheduleApi.list();
    schedules.value = (s.items || []).filter(
      (x) => !x.completedAt && !x.completed_at,
    );
  } catch {
    /* Corpus remains usable without schedule access. */
  }
  timer = setInterval(poll, 5000);
});
onUnmounted(() => {
  disposed = true;
  detailRequest++;
  clearInterval(timer);
});
</script>

<style scoped>
.revision-history {
  margin-top: 12px;
  font-size: 12px;
  color: var(--xzm-text-secondary);
}
.revision-history summary {
  cursor: pointer;
}
.revision-history button {
  margin: 4px;
}
.experience-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 28px 28px 90px;
  color: var(--xzm-text-primary);
}
.hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}
.eyebrow {
  color: var(--xzm-brand);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}
.hero h1 {
  font-size: clamp(24px, 3vw, 34px);
  font-weight: 550;
  letter-spacing: -0.03em;
  margin: 10px 0;
}
.hero p,
.preparation-card p {
  font-size: 13px;
  color: var(--xzm-text-secondary);
  line-height: 1.8;
}
.view-switch {
  display: flex;
  white-space: nowrap;
  padding: 4px;
  background: var(--xzm-surface-control);
  border: 1px solid var(--xzm-border-color);
  border-radius: 10px;
}
.view-switch a {
  padding: 9px 12px;
  text-decoration: none;
  font-size: 12px;
  color: var(--xzm-text-secondary);
  border-radius: 7px;
}
.view-switch .active {
  background: var(--xzm-surface-elevated);
  color: var(--xzm-brand);
}
.button {
  border: 1px solid var(--xzm-brand);
  border-radius: 8px;
  padding: 9px 14px;
  background: var(--xzm-brand);
  color: var(--xzm-text-on-brand);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.button.quiet {
  background: transparent;
  color: var(--xzm-brand);
  border-color: var(--xzm-border-color);
}
button:disabled {
  opacity: 0.5;
  cursor: wait;
}
.sync-note {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 15px;
  border: 1px solid var(--xzm-border-color);
  border-radius: 10px;
  color: var(--xzm-text-secondary);
  font-size: 12px;
  line-height: 1.7;
}
.sync-note.failed {
  border-color: var(--xzm-danger);
  color: var(--xzm-danger);
}
small {
  color: var(--xzm-text-secondary);
  font-size: 11px;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin: 18px 0 24px;
}
.metrics > div {
  display: grid;
  gap: 5px;
  padding: 19px;
  border: 1px solid var(--xzm-border-color);
  border-radius: 12px;
  background: var(--xzm-surface-elevated);
}
.metrics b {
  font-size: 27px;
  font-weight: 550;
  font-variant-numeric: tabular-nums;
}
.metrics span {
  font-size: 12px;
  color: var(--xzm-text-secondary);
}
.tabs {
  display: flex;
  gap: 18px;
  border-bottom: 1px solid var(--xzm-border-color);
  margin: 20px 0 16px;
  overflow: auto;
}
.tabs button {
  white-space: nowrap;
  border: 0;
  border-bottom: 2px solid transparent;
  padding: 10px 0;
  color: var(--xzm-text-secondary);
  background: none;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.tabs .active {
  border-color: var(--xzm-brand);
  color: var(--xzm-brand);
}
.filters {
  display: flex;
  gap: 9px;
  flex-wrap: wrap;
}
.filters > input {
  flex: 1;
  min-width: 190px;
}
input,
select,
textarea {
  border: 1px solid var(--xzm-border-color-strong);
  border-radius: 8px;
  background: var(--xzm-surface-control);
  color: var(--xzm-text-primary);
  font: inherit;
  font-size: 13px;
  padding: 10px;
  min-width: 0;
  max-width: 100%;
}
textarea {
  resize: vertical;
  line-height: 1.8;
}
.check {
  display: flex !important;
  align-items: center;
  gap: 6px;
  font-size: 12px !important;
}
.check input {
  width: auto;
}
.boundary {
  font-size: 11px;
  color: var(--xzm-text-secondary);
  line-height: 1.8;
  margin: 12px 0 18px;
}
.question-list {
  border: 1px solid var(--xzm-border-color);
  border-radius: 12px;
  overflow: hidden;
  background: var(--xzm-surface-elevated);
}
.list-heading {
  display: flex;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--xzm-border-color);
  font-size: 11px;
  color: var(--xzm-text-secondary);
}
.question-row {
  display: flex;
  width: 100%;
  text-align: left;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  padding: 19px 20px;
  border: 0;
  border-bottom: 1px solid var(--xzm-border-color);
  background: transparent;
  color: inherit;
  cursor: pointer;
}
.question-row:hover,
.daily-task:hover {
  background: var(--xzm-brand-soft);
}
.question-row > div:first-child {
  min-width: 0;
}
.question-row h3 {
  font-size: 14px;
  font-weight: 550;
  line-height: 1.7;
  margin: 7px 0;
  overflow-wrap: anywhere;
}
.question-row p {
  font-size: 11px;
  color: var(--xzm-text-secondary);
  margin: 0;
}
.tags {
  display: flex;
  gap: 7px;
  flex-wrap: wrap;
}
.tags span {
  background: var(--xzm-surface-control);
  color: var(--xzm-text-secondary);
  border-radius: 4px;
  padding: 3px 7px;
  font-size: 10px;
}
.tags .warning {
  color: var(--xzm-brand);
}
.frequency {
  display: grid;
  flex-shrink: 0;
  gap: 7px;
  text-align: right;
  min-width: 55px;
}
.frequency b {
  font-size: 24px;
  font-weight: 550;
}
.frequency span {
  font-size: 10px;
  color: var(--xzm-text-secondary);
}
.more {
  display: block;
  margin: 18px auto;
}
.category-grid,
.document-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 15px;
}
.category-card,
.document-card {
  padding: 22px;
  border: 1px solid var(--xzm-border-color);
  border-radius: 12px;
  background: var(--xzm-surface-elevated);
  color: inherit;
  text-align: left;
  min-width: 0;
}
.category-card h2 {
  font-size: 18px;
  font-weight: 550;
  margin: 12px 0;
}
.category-card p {
  font-size: 12px;
  line-height: 1.8;
  color: var(--xzm-text-secondary);
}
.category-card > button:not(.button) {
  display: block;
  width: 100%;
  text-align: left;
  padding: 12px 0;
  border: 0;
  border-bottom: 1px solid var(--xzm-border-color);
  background: none;
  color: inherit;
  font: inherit;
  font-size: 12px;
  line-height: 1.8;
  cursor: pointer;
}
.category-card > button span {
  color: var(--xzm-brand);
  white-space: nowrap;
}
.category-card > .button {
  margin-top: 16px;
}
.document-card {
  cursor: pointer;
}
.document-card h3 {
  font-size: 15px;
  line-height: 1.7;
  font-weight: 550;
}
.document-card > span {
  font-size: 12px;
  color: var(--xzm-brand);
}
.empty {
  padding: 50px 18px;
  text-align: center;
  color: var(--xzm-text-secondary);
  font-size: 13px;
}
.empty button {
  display: block;
  margin: 16px auto;
}
.preparation-card {
  padding: 23px;
  border: 1px solid var(--xzm-border-color);
  border-radius: 12px;
  background: var(--xzm-surface-elevated);
}
.preparation-card h2 {
  font-size: 21px;
  font-weight: 550;
}
.preparation-card > label {
  display: inline-grid;
  gap: 8px;
  font-size: 12px;
  margin: 8px 15px 14px 0;
  max-width: 100%;
}
.daily-list {
  display: grid;
  gap: 9px;
}
.daily-task {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  text-align: left;
  border: 1px solid var(--xzm-border-color);
  border-radius: 8px;
  padding: 14px;
  background: transparent;
  color: inherit;
  font: inherit;
  cursor: pointer;
}
.daily-task > span {
  font-size: 13px;
  line-height: 1.6;
}
.daily-task > small {
  grid-column: 1;
}
.daily-task > b {
  grid-column: 2;
  grid-row: 1/3;
  align-self: center;
  font-size: 11px;
  color: var(--xzm-brand);
}
.detail {
  color: var(--xzm-text-primary);
  padding: 0 5px 35px;
}
.detail h2 {
  font-size: 21px;
  line-height: 1.7;
  font-weight: 550;
}
.detail a {
  font-size: 12px;
  color: var(--xzm-brand);
  margin-left: 12px;
}
.detail label {
  display: grid;
  gap: 9px;
  margin: 16px 0;
  font-size: 12px;
  color: var(--xzm-text-secondary);
}
.detail label input,
.detail label select,
.detail label textarea {
  width: 100%;
}
.asked {
  padding: 16px;
  background: var(--xzm-surface-control);
  border-radius: 10px;
  margin: 16px 0;
}
.asked p {
  line-height: 1.8;
  font-size: 14px;
  margin-bottom: 0;
}
.feedback,
.history,
.sources article {
  margin-top: 22px;
  padding: 18px;
  border: 1px solid var(--xzm-border-color);
  border-radius: 10px;
}
.feedback h3 {
  font-size: 15px;
}
.feedback details {
  margin: 16px 0;
}
.feedback summary,
.history summary {
  font-size: 12px;
  cursor: pointer;
}
.history article {
  border-bottom: 1px solid var(--xzm-border-color);
  padding: 16px 0;
}
.history pre,
.source-body {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font: inherit;
  font-size: 13px;
  line-height: 1.9;
}
.sources article p {
  line-height: 1.8;
  font-size: 13px;
  overflow-wrap: anywhere;
}
.chain-progress {
  font-size: 12px;
  line-height: 1.8;
  color: var(--xzm-brand);
}
.personal > p {
  font-size: 12px;
  line-height: 1.8;
  color: var(--xzm-text-secondary);
}
@media (max-width: 760px) {
  .experience-page {
    padding: 20px 14px 95px;
  }
  .hero {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }
  .hero h1 {
    font-size: 25px;
  }
  .metrics {
    gap: 8px;
    grid-template-columns: repeat(2, 1fr);
  }
  .metrics > div {
    padding: 14px;
  }
  .category-grid,
  .document-grid {
    grid-template-columns: 1fr;
  }
  .filters > input {
    flex-basis: 100%;
  }
  .filters select {
    flex: 1;
  }
  .question-row {
    padding: 16px 12px;
    gap: 10px;
  }
  .question-row h3 {
    font-size: 13px;
  }
  .preparation-card {
    padding: 16px;
  }
  .daily-task {
    grid-template-columns: 1fr;
  }
  .daily-task > b {
    grid-column: 1;
    grid-row: auto;
  }
  .tabs {
    gap: 15px;
  }
  .detail .tabs button {
    font-size: 12px;
  }
}
</style>
