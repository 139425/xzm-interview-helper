<template>
  <div class="interview-agent-page">
    <GeminiSidebar
      ref="sidebarRef"
      mode="interview"
      @new-chat="resetInterview"
      @mode-change="handleModeChange"
      @interview-select="handleSessionSelect"
    />

    <main
      class="agent-main"
      :style="{ marginLeft: `${uiStore.sidebarWidth}px` }"
    >
      <header class="agent-topbar">
        <div class="topbar-start">
          <button
            v-if="uiStore.isMobile"
            type="button"
            class="icon-button"
            :aria-label="uiStore.sidebarExpanded ? '收起侧边栏' : '展开侧边栏'"
            @click="uiStore.toggleSidebar"
          >
            <el-icon :size="20"
              ><Fold v-if="uiStore.sidebarExpanded" /><Expand v-else
            /></el-icon>
          </button>
          <div class="agent-brand" aria-label="模拟面试 Agent">
            <span class="brand-mark">IA</span>
            <div>
              <span class="eyebrow">INTERVIEW AGENT</span>
              <strong>模拟面试工作台</strong>
            </div>
          </div>
        </div>

        <div class="topbar-actions">
          <span class="secure-chip"><i></i> 会话实时保存</span>
          <button
            v-if="agentView.isStreaming"
            type="button"
            class="stop-request-button"
            @click="stopRequest"
          >
            <el-icon><Close /></el-icon>
            停止请求
          </button>
          <UserAvatar />
        </div>
      </header>

      <section
        v-if="!agentView.session"
        class="setup-shell"
        aria-labelledby="setup-title"
      >
        <div class="setup-copy">
          <div class="setup-kicker"><span></span> 以简历为起点</div>
          <h1 id="setup-title">一次更像真实交流的<br /><em>模拟面试。</em></h1>
          <p>
            Agent 会从你的经历中抽取线索、补充岗位知识，并在 3–8
            个主问题之间根据证据决定追问或换题，最后进入算法实战。
          </p>

          <div class="process-preview" aria-label="面试流程">
            <div class="process-step active">
              <b>01</b><span>理解简历</span>
            </div>
            <div class="process-line"></div>
            <div class="process-step"><b>02</b><span>动态追问</span></div>
            <div class="process-line"></div>
            <div class="process-step"><b>03</b><span>算法终局题</span></div>
          </div>

          <div class="guardrail-list">
            <span><el-icon><CircleCheck /></el-icon> 主问题 3–8 个</span>
            <span><el-icon><CircleCheck /></el-icon> 完整运行轨迹</span>
            <span
              ><el-icon><CircleCheck /></el-icon> 全程可恢复</span
            >
          </div>
        </div>

        <section class="intake-card" aria-labelledby="intake-title">
          <div class="card-heading">
            <div class="heading-icon">
              <el-icon><Document /></el-icon>
            </div>
            <div>
              <p class="eyebrow">SESSION BRIEF</p>
              <h2 id="intake-title">准备你的面试材料</h2>
            </div>
          </div>

          <label class="field-label" for="target-role"
            >目标岗位 <span>可选</span></label
          >
          <input
            id="target-role"
            v-model="targetRole"
            class="role-input"
            type="text"
            maxlength="80"
            autocomplete="organization-title"
            placeholder="例如：后端开发工程师 / 产品经理"
          />

          <div class="engine-config" aria-labelledby="engine-config-title">
            <div class="engine-config-head">
              <div>
                <span id="engine-config-title" class="field-label">面试引擎</span>
                <small>本场面试创建后锁定，保证追问与总结口径一致</small>
              </div>
              <span class="engine-status">{{ interviewThinking ? "THINKING ON" : "DIRECT" }}</span>
            </div>

            <div class="model-choice-grid" role="radiogroup" aria-label="选择模拟面试模型">
              <button
                v-for="model in INTERVIEW_MODELS"
                :key="model.id"
                type="button"
                class="model-choice"
                :class="{ active: selectedInterviewModelId === model.id }"
                role="radio"
                :aria-checked="selectedInterviewModelId === model.id"
                @click="selectedInterviewModelId = model.id"
              >
                <span class="model-choice-mark">DS</span>
                <span>
                  <strong>{{ model.label }}</strong>
                  <small>{{ model.description }}</small>
                </span>
                <i aria-hidden="true"></i>
              </button>
            </div>

            <button
              type="button"
              class="interview-thinking-toggle"
              :class="{ active: interviewThinking }"
              role="switch"
              :aria-checked="interviewThinking"
              @click="interviewThinking = !interviewThinking"
            >
              <span class="thinking-toggle-icon"><el-icon><Cpu /></el-icon></span>
              <span class="thinking-toggle-copy">
                <strong>深度思考</strong>
                <small>{{ interviewThinking ? "先推理再决定追问方向" : "更低延迟，直接生成判断" }}</small>
              </span>
              <span class="switch-track" aria-hidden="true"><i></i></span>
            </button>
          </div>

          <div class="source-tabs" role="tablist" aria-label="选择简历提交方式">
            <button
              id="paste-tab"
              type="button"
              role="tab"
              :aria-selected="inputMode === 'paste'"
              :class="{ active: inputMode === 'paste' }"
              @click="inputMode = 'paste'"
            >
              <el-icon><Document /></el-icon> 粘贴简历
            </button>
            <button
              id="file-tab"
              type="button"
              role="tab"
              :aria-selected="inputMode === 'file'"
              :class="{ active: inputMode === 'file' }"
              @click="inputMode = 'file'"
            >
              <el-icon><UploadFilled /></el-icon> 上传文件
            </button>
          </div>

          <div
            v-if="inputMode === 'paste'"
            class="resume-panel"
            role="tabpanel"
            aria-labelledby="paste-tab"
          >
            <textarea
              v-model="resumeText"
              class="resume-textarea"
              maxlength="30000"
              placeholder="粘贴简历内容。建议包含项目经历、职责、技术栈和成果，让问题更贴近真实面试。"
              aria-label="粘贴简历内容"
            ></textarea>
            <div class="input-hint">
              <span>仅用于本次面试上下文</span
              ><span>{{ resumeText.length.toLocaleString() }}/30,000</span>
            </div>
          </div>

          <div
            v-else
            class="upload-panel"
            :class="{ dragging: isDraggingFile, chosen: selectedFile }"
            role="tabpanel"
            aria-labelledby="file-tab"
            tabindex="0"
            @click="openFilePicker"
            @keydown.enter.prevent="openFilePicker"
            @keydown.space.prevent="openFilePicker"
            @dragenter.prevent="isDraggingFile = true"
            @dragover.prevent="isDraggingFile = true"
            @dragleave.prevent="isDraggingFile = false"
            @drop.prevent="handleFileDrop"
          >
            <input
              ref="fileInput"
              class="visually-hidden"
              type="file"
              accept=".pdf,.md,.doc,.docx,.docs"
              @change="handleFileInput"
            />
            <template v-if="!selectedFile">
              <div class="upload-symbol">
                <el-icon><UploadFilled /></el-icon>
              </div>
              <strong>拖放简历到这里，或点击选择</strong>
              <span>支持 PDF、Markdown、DOC、DOCX、DOCS，单个文件最大 10 MB</span>
            </template>
            <template v-else>
              <div class="selected-file-icon">
                <el-icon><Document /></el-icon>
              </div>
              <div class="selected-file-copy">
                <strong>{{ selectedFile.name }}</strong>
                <span>{{ formatFileSize(selectedFile.size) }} · 等待上传</span>
              </div>
              <button
                type="button"
                class="remove-file"
                aria-label="移除文件"
                @click.stop="clearSelectedFile"
              >
                <el-icon><Close /></el-icon>
              </button>
            </template>
          </div>

          <p class="privacy-note">
            <el-icon><InfoFilled /></el-icon> 不会把简历内容拼接到
            URL；会话建立后由服务端安全保存。
          </p>

          <button
            type="button"
            class="begin-button"
            :disabled="!canBegin || agentView.isBusy"
            @click="beginInterview"
          >
            <el-icon v-if="agentView.isBusy" class="is-loading"
              ><Loading
            /></el-icon>
            <el-icon v-else><MagicStick /></el-icon>
            {{ agentView.isBusy ? "正在建立面试…" : "开始模拟面试" }}
            <span aria-hidden="true">↗</span>
          </button>
        </section>
      </section>

      <section
        v-else
        class="interview-shell"
        :class="{ compact: uiStore.isTablet }"
      >
        <aside class="agent-rail" aria-label="面试状态">
          <div class="session-card">
            <div class="session-topline">
              <span>LIVE SESSION</span
              ><i :class="{ done: agentView.isCompleted }"></i>
            </div>
            <h2>{{ agentView.session.targetRole || "通用能力面试" }}</h2>
            <p>{{ resumeDescriptor }}</p>
            <div class="session-id">
              #{{ String(agentView.session.id).slice(-8) }}
            </div>
          </div>

          <section class="progress-card" aria-labelledby="progress-title">
            <div class="rail-heading">
              <span id="progress-title">面试进度</span>
              <el-icon><DataAnalysis /></el-icon>
            </div>
            <div class="main-progress">
              <span>主问题</span>
              <strong>{{ agentView.progress.main }} <small>/ 8</small></strong>
            </div>
            <div class="progress-track">
              <i :style="{ width: `${mainProgressWidth}%` }"></i>
            </div>
            <div class="total-progress">
              <span>已提问</span>
              <strong
                >{{ agentView.progress.total }}
                <small>/ 15（含算法）</small></strong
              >
            </div>
            <p>
              至少完成 3 个主问题；追问 {{ agentView.progress.followUp }} 次，
              全流程不超过 15 题。
            </p>
          </section>

          <section class="trajectory-card" aria-labelledby="trajectory-title">
            <div class="rail-heading">
              <span id="trajectory-title">Agent 工作轨迹</span>
              <el-icon><Clock /></el-icon>
            </div>
            <p class="trajectory-caption">展示任务阶段，不展示模型内部推理。</p>
            <ol
              v-if="agentView.stages.length"
              ref="trajectoryList"
              class="stage-list"
              tabindex="0"
              aria-label="Agent 运行轨迹，可上下滚动查看全部阶段"
              @scroll="handleTrajectoryScroll"
            >
              <li
                v-for="stage in agentView.stages"
                :key="stage.id"
                :class="stage.status"
              >
                <span class="stage-dot"
                  ><el-icon v-if="stage.status === 'done'"
                    ><CircleCheck /></el-icon
                  ><i v-else></i
                ></span>
                <div>
                  <strong>{{ stage.title }}</strong
                  ><small>{{ stage.detail }}</small>
                </div>
              </li>
            </ol>
            <div v-else class="empty-stages">
              <el-icon><MagicStick /></el-icon
              ><span>等待 Agent 开始本轮工作</span>
            </div>
          </section>
        </aside>

        <section class="conversation-column" aria-label="模拟面试对话">
          <div class="conversation-head">
            <div>
              <p class="eyebrow">CANDIDATE ROOM</p>
              <h1>{{ conversationTitle }}</h1>
            </div>
            <div class="phase-chip" :class="agentView.phase">
              <span></span>{{ phaseLabel }}
            </div>
          </div>

          <div
            ref="conversationLog"
            class="conversation-log"
            role="log"
            aria-live="polite"
            aria-relevant="additions text"
            :aria-busy="agentView.isBusy ? 'true' : 'false'"
            aria-label="面试问答记录"
          >
            <div
              v-if="
                !answeredTurns.length &&
                !agentView.session.currentQuestion &&
                agentView.isBusy
              "
              class="thinking-welcome"
            >
              <div class="pulse-orb">
                <el-icon class="is-loading"><Loading /></el-icon>
              </div>
              <div>
                <strong>面试 Agent 正在就位</strong
                ><span>它将先理解你的简历，再发出第一道问题。</span>
              </div>
            </div>

            <article
              v-for="(turn, index) in answeredTurns"
              :key="turn.id || index"
              class="turn-pair"
            >
              <div class="message assistant-message">
                <div class="message-meta">
                  <span class="message-avatar agent-avatar">IA</span
                  ><strong>面试 Agent</strong
                  ><em>{{ turn.isFollowUp ? "追问" : `问题 ${index + 1}` }}</em>
                </div>
                <p>{{ turn.question }}</p>
              </div>
              <div class="message candidate-message">
                <div class="message-meta">
                  <span class="message-avatar candidate-avatar"
                    ><el-icon><UserFilled /></el-icon></span
                  ><strong>你的回答</strong>
                </div>
                <p>{{ turn.answer }}</p>
              </div>
            </article>

            <article
              v-if="agentView.session.currentQuestion"
              class="message assistant-message current-question"
            >
              <div class="message-meta">
                <span class="message-avatar agent-avatar">IA</span>
                <strong>面试 Agent</strong>
                <em>{{
                  agentView.session.currentQuestion.isFollowUp
                    ? "针对性追问"
                    : `第 ${agentView.progress.total} 题`
                }}</em>
              </div>
              <p>{{ agentView.session.currentQuestion.text }}</p>
            </article>

            <div v-if="agentView.isBusy" class="working-row" role="status">
              <span class="working-pulse"><i></i><i></i><i></i></span>
              <span>{{ activeStageText }}</span>
              <button type="button" @click="stopRequest">停止</button>
            </div>

            <div
              v-if="agentView.phase === 'ready'"
              class="recovery-card ready-card"
            >
              <span class="recovery-icon"
                ><el-icon><VideoPlay /></el-icon
              ></span>
              <div>
                <strong>会话已准备好</strong>
                <p>继续后，Agent 会开始或恢复这一场面试。</p>
              </div>
              <button type="button" @click="continueInterview">继续面试</button>
            </div>

            <div
              v-if="
                agentView.phase === 'interrupted' || agentView.phase === 'error'
              "
              class="recovery-card"
              role="alert"
            >
              <span class="recovery-icon warning"
                ><el-icon><WarningFilled /></el-icon
              ></span>
              <div>
                <strong>{{
                  agentView.phase === "interrupted"
                    ? "本次请求已停止"
                    : "连接需要恢复"
                }}</strong>
                <p>
                  {{
                    agentView.errorMessage || "已保留现有面试记录，可安全重试。"
                  }}
                </p>
              </div>
              <button type="button" @click="retryRequest">
                <el-icon><RefreshRight /></el-icon> 重试
              </button>
            </div>

            <section
              v-if="agentView.isAwaitingAlgorithm"
              class="algorithm-handoff"
              aria-labelledby="algorithm-handoff-title"
            >
              <div class="algorithm-handoff-mark">
                <el-icon><Cpu /></el-icon>
              </div>
              <div class="algorithm-handoff-copy">
                <p class="eyebrow">FINAL ALGORITHM ROUND</p>
                <h2 id="algorithm-handoff-title">
                  {{ agentView.session.algorithmChallenge.title || "算法终局题" }}
                </h2>
                <p>
                  难度
                  {{ difficultyLabel(agentView.session.algorithmChallenge.difficulty) }}
                  · 限时
                  {{ agentView.session.algorithmChallenge.timeLimitMinutes }} 分钟。
                  通过隐藏用例或限时结束后，Agent 将生成最终报告。
                </p>
              </div>
              <button type="button" @click="openAlgorithmChallenge">
                进入代码工作台 <el-icon><Promotion /></el-icon>
              </button>
            </section>

            <section
              v-if="agentView.isCompleted"
              class="completion-card"
              aria-labelledby="completion-title"
            >
              <div class="completion-mark">
                <el-icon><Finished /></el-icon>
              </div>
              <div>
                <p class="eyebrow">INTERVIEW COMPLETE</p>
                <h2 id="completion-title">本次面试已完成</h2>
                <p>所有问答与 Agent 总结已经写入会话记录。</p>
              </div>
              <button type="button" @click="openReport">
                <el-icon><View /></el-icon> 查看完整报告
              </button>
              <div v-if="agentView.session.summary" class="summary-preview">
                <StaticMarkdown :content="agentView.session.summary" />
              </div>
            </section>
          </div>

          <form
            v-if="agentView.isAwaitingAnswer"
            class="answer-composer"
            @submit.prevent="sendAnswer"
          >
            <div class="composer-label">
              <span>你的回答</span>
              <span class="answer-timer" aria-label="本题思考时间">
                <el-icon><Clock /></el-icon>{{ answerElapsedLabel }}
              </span>
              <small><kbd>Ctrl</kbd> + <kbd>Enter</kbd> 提交</small>
            </div>
            <div class="composer-tools">
              <button
                type="button"
                class="guide-toggle"
                :aria-expanded="showAnswerGuide"
                aria-controls="answer-guide"
                @click="showAnswerGuide = !showAnswerGuide"
              >
                {{ showAnswerGuide ? "收起作答框架" : "查看作答框架" }}
              </button>
              <span><i></i> 草稿已在本机自动保存</span>
            </div>
            <div v-if="showAnswerGuide" id="answer-guide" class="answer-guide">
              <strong>先给结论，再补证据</strong>
              <ol>
                <li><b>观点</b><span>直接回答问题</span></li>
                <li><b>原理</b><span>说明为什么</span></li>
                <li><b>证据</b><span>职责、数据或案例</span></li>
                <li><b>权衡</b><span>边界与替代方案</span></li>
              </ol>
            </div>
            <textarea
              ref="answerInput"
              v-model="answerText"
              maxlength="6000"
              placeholder="用事实、过程和结果来回答。你可以像真实面试一样思考后再作答。"
              aria-label="输入你的面试回答"
              @keydown.ctrl.enter.prevent="sendAnswer"
              @keydown.meta.enter.prevent="sendAnswer"
            ></textarea>
            <div class="composer-footer">
              <span>{{ answerText.length.toLocaleString() }}/6,000</span>
              <div>
                <button
                  type="button"
                  class="ghost-button"
                  @click="answerText = ''"
                >
                  清空
                </button>
                <button
                  type="button"
                  class="ghost-button skip-answer"
                  @click="sendNoAnswer"
                >
                  暂时不会，继续
                </button>
                <button
                  type="submit"
                  class="answer-send"
                  :disabled="!answerText.trim()"
                >
                  提交回答 <el-icon><Promotion /></el-icon>
                </button>
              </div>
            </div>
          </form>
        </section>
      </section>
    </main>
  </div>
</template>

<script setup>
import {
  computed,
  nextTick,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  CircleCheck,
  Clock,
  Close,
  Cpu,
  DataAnalysis,
  Document,
  Expand,
  Finished,
  Fold,
  InfoFilled,
  Loading,
  MagicStick,
  Promotion,
  RefreshRight,
  UploadFilled,
  UserFilled,
  VideoPlay,
  View,
  WarningFilled,
} from "@element-plus/icons-vue";
import GeminiSidebar from "@/components/GeminiSidebar.vue";
import StaticMarkdown from "@/components/StaticMarkdown.vue";
import UserAvatar from "@/components/UserAvatar.vue";
import { useInterviewAgent } from "@/composables/useInterviewAgent";
import { useUIStore } from "@/stores/ui";
import { useUserStore } from "@/stores/user";

const router = useRouter();
const route = useRoute();
const uiStore = useUIStore();
const userStore = useUserStore();
const agent = useInterviewAgent();
// `useInterviewAgent` intentionally returns refs so script code can make state
// transitions explicit. This shallow read-only view keeps Vue templates from
// accidentally rendering Ref objects from a nested plain object.
const agentView = {
  get session() {
    return agent.session.value;
  },
  get phase() {
    return agent.phase.value;
  },
  get stages() {
    return agent.stages.value;
  },
  get errorMessage() {
    return agent.errorMessage.value;
  },
  get isBusy() {
    return agent.isBusy.value;
  },
  get isStreaming() {
    return agent.isStreaming.value;
  },
  get isAwaitingAnswer() {
    return agent.isAwaitingAnswer.value;
  },
  get isAwaitingAlgorithm() {
    return agent.isAwaitingAlgorithm.value;
  },
  get isCompleted() {
    return agent.isCompleted.value;
  },
  get progress() {
    return agent.progress.value;
  },
};

const sidebarRef = ref(null);
const fileInput = ref(null);
const conversationLog = ref(null);
const trajectoryList = ref(null);
const followLatestTrajectory = ref(true);
const answerInput = ref(null);
const inputMode = ref("paste");
const targetRole = ref("");
const resumeText = ref("");
const INTERVIEW_MODELS = Object.freeze([
  {
    id: "deepseek-v4-flash",
    label: "DeepSeek V4 Flash",
    description: "响应更快，适合高节奏面试",
    provider: "deepseek",
    modelName: "deepseek-v4-flash",
  },
  {
    id: "deepseek-v4-pro",
    label: "DeepSeek V4 Pro",
    description: "复杂判断与追问质量优先",
    provider: "deepseek",
    modelName: "deepseek-v4-pro",
  },
]);
const selectedInterviewModelId = ref("deepseek-v4-pro");
const interviewThinking = ref(false);
const selectedInterviewModel = computed(
  () =>
    INTERVIEW_MODELS.find(
      (model) => model.id === selectedInterviewModelId.value,
    ) || INTERVIEW_MODELS[1],
);
const selectedFile = ref(null);
const isDraggingFile = ref(false);
const answerText = ref("");
const showAnswerGuide = ref(false);
const answerElapsedSeconds = ref(0);
let answerClock = null;
let timedQuestionKey = "";
// The answer is not discarded merely because the browser lost the stream before the
// server acknowledged it.  Keep it tied to the exact persisted session/question so
// a retry snapshot can safely put it back, without ever leaking it into a new question.
const pendingAnswerDraft = ref(null);

const ALLOWED_RESUME_EXTENSIONS = new Set(["pdf", "md", "doc", "docx", "docs"]);
const MAX_FILE_SIZE = 10 * 1024 * 1024;

const canBegin = computed(() =>
  inputMode.value === "paste"
    ? Boolean(resumeText.value.trim())
    : Boolean(selectedFile.value),
);

const answeredTurns = computed(() =>
  (agent.session.value?.turns || []).filter((turn) =>
    Boolean(String(turn?.answer || "").trim()),
  ),
);

const resumeDescriptor = computed(() => {
  if (agent.session.value?.resumeName)
    return `已上传 · ${agent.session.value.resumeName}`;
  return agent.session.value?.resumeSource === "file"
    ? "已上传简历文件"
    : "已粘贴简历内容";
});

const mainProgressWidth = computed(() =>
  Math.min(100, (agent.progress.value.main / 8) * 100),
);

const answerElapsedLabel = computed(() => {
  const minutes = Math.floor(answerElapsedSeconds.value / 60);
  const seconds = answerElapsedSeconds.value % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
});

const phaseLabel = computed(
  () =>
    ({
      draft: "准备中",
      creating: "建立会话",
      starting: "准备问题",
      streaming: "处理中",
      answering: "分析回答",
      retrying: "恢复中",
      "awaiting-answer": "等待回答",
      algorithm: "算法终局题",
      ready: "可继续",
      interrupted: "已暂停",
      completed: "已完成",
      error: "需要恢复",
    })[agent.phase.value] || "进行中",
);

const conversationTitle = computed(() => {
  if (agent.isCompleted.value) return "面试回顾";
  if (agent.isAwaitingAlgorithm.value) return "完成最后一道算法题";
  if (agent.isAwaitingAnswer.value) return "请像真实面试一样作答";
  if (agent.isBusy.value) return "Agent 正在推进面试";
  return "你的模拟面试";
});

const activeStageText = computed(() => {
  const stage = agent.stages.value[agent.stages.value.length - 1];
  return stage?.detail || "Agent 正在安全地推进本轮面试。";
});

const formatFileSize = (bytes) => {
  if (!Number.isFinite(bytes)) return "";
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const ensureLoggedIn = () => {
  if (userStore.isLoggedIn) return true;
  ElMessage.warning("请先登录后再开始模拟面试。");
  router.push("/login");
  return false;
};

const validateFile = (file) => {
  if (!file) return false;
  const extension = file.name.split(".").pop()?.toLowerCase();
  if (!extension || !ALLOWED_RESUME_EXTENSIONS.has(extension)) {
    ElMessage.error("仅支持 PDF、Markdown、DOC、DOCX 和 DOCS 格式的简历。");
    return false;
  }
  if (file.size > MAX_FILE_SIZE) {
    ElMessage.error("简历文件不能超过 10 MB。");
    return false;
  }
  return true;
};

const selectFile = (file) => {
  if (!validateFile(file)) return;
  selectedFile.value = file;
  isDraggingFile.value = false;
};

const openFilePicker = () => fileInput.value?.click();

const handleFileInput = (event) => {
  selectFile(event.target.files?.[0]);
  event.target.value = "";
};

const handleFileDrop = (event) => {
  isDraggingFile.value = false;
  selectFile(event.dataTransfer?.files?.[0]);
};

const clearSelectedFile = () => {
  selectedFile.value = null;
};

const refreshSidebar = async () => {
  if (typeof sidebarRef.value?.loadHistoryList === "function") {
    await sidebarRef.value.loadHistoryList(true);
  }
};

const scrollConversationToBottom = async () => {
  await nextTick();
  const log = conversationLog.value;
  if (log) log.scrollTo({ top: log.scrollHeight, behavior: "smooth" });
};

const questionDraftKey = (question) => {
  if (!question) return "";
  return String(question.id || question.text || "");
};

const draftStorageKey = (sessionId, questionKey) =>
  `interview-answer-draft:v1:${String(sessionId)}:${encodeURIComponent(questionKey).slice(0, 320)}`;

const saveDraftToSession = (sessionId, questionKey, answer) => {
  if (!sessionId || !questionKey) return;
  try {
    const key = draftStorageKey(sessionId, questionKey);
    if (!answer) sessionStorage.removeItem(key);
    else sessionStorage.setItem(key, JSON.stringify({ answer, savedAt: Date.now() }));
  } catch {
    // Storage can be unavailable in strict privacy modes; the in-memory draft
    // below remains the graceful fallback.
  }
};

const readDraftFromSession = (sessionId, questionKey) => {
  try {
    const raw = sessionStorage.getItem(draftStorageKey(sessionId, questionKey));
    const parsed = raw ? JSON.parse(raw) : null;
    return typeof parsed?.answer === "string" ? parsed.answer.slice(0, 6000) : "";
  } catch {
    return "";
  }
};

const rememberPendingAnswerDraft = (answer) => {
  const activeSession = agent.session.value;
  const key = questionDraftKey(activeSession?.currentQuestion);
  if (!activeSession?.id || !key) return;
  pendingAnswerDraft.value = {
    sessionId: String(activeSession.id),
    questionKey: key,
    answer,
  };
  saveDraftToSession(activeSession.id, key, answer);
};

const clearPendingAnswerDraft = () => {
  const draft = pendingAnswerDraft.value;
  if (draft) saveDraftToSession(draft.sessionId, draft.questionKey, "");
  else {
    const activeSession = agent.session.value;
    const activeQuestionKey = questionDraftKey(activeSession?.currentQuestion);
    if (activeSession?.id && activeQuestionKey) {
      saveDraftToSession(activeSession.id, activeQuestionKey, "");
    }
  }
  pendingAnswerDraft.value = null;
  answerText.value = "";
};

const restorePendingAnswerDraftForCurrentQuestion = () => {
  const activeSession = agent.session.value;
  const key = questionDraftKey(activeSession?.currentQuestion);
  // During an in-flight optimistic submit there is intentionally no current question.
  // Leave the local text alone until the server provides an authoritative boundary.
  if (!activeSession?.id || !key) return;

  const draft = pendingAnswerDraft.value;
  if (
    draft &&
    draft.sessionId === String(activeSession.id) &&
    draft.questionKey === key
  ) {
    answerText.value = draft.answer;
    return;
  }

  const storedDraft = readDraftFromSession(activeSession.id, key);
  if (storedDraft) {
    answerText.value = storedDraft;
    return;
  }

  // A different authoritative question means the previous answer was accepted, or
  // the user intentionally switched sessions. It must never prefill this question.
  pendingAnswerDraft.value = null;
  answerText.value = "";
};

const beginInterview = async () => {
  if (!ensureLoggedIn() || !canBegin.value) return;

  const success =
    inputMode.value === "paste"
      ? await agent.createFromText({
          resumeText: resumeText.value,
          targetRole: targetRole.value,
          modelProvider: selectedInterviewModel.value.provider,
          modelName: selectedInterviewModel.value.modelName,
          enableThinking: interviewThinking.value,
        })
      : await agent.createFromFile({
          file: selectedFile.value,
          targetRole: targetRole.value,
          modelProvider: selectedInterviewModel.value.provider,
          modelName: selectedInterviewModel.value.modelName,
          enableThinking: interviewThinking.value,
        });

  if (success) {
    await refreshSidebar();
    await scrollConversationToBottom();
    return;
  }

  ElMessage.error(agent.errorMessage.value || "暂时无法开始面试，请重试。");
};

const sendAnswer = async () => {
  const answer = answerText.value.trim();
  if (!answer) return;

  rememberPendingAnswerDraft(answer);
  const success = await agent.submitAnswer(answer);
  if (success) clearPendingAnswerDraft();
  await scrollConversationToBottom();
  if (!success && agent.phase.value === "error") {
    ElMessage.error(
      agent.errorMessage.value || "本轮回答处理失败，可点击重试。",
    );
  }
};

const sendNoAnswer = async () => {
  answerText.value = "这道题我目前无法给出可靠答案，希望继续下一题。";
  await sendAnswer();
};

const stopRequest = () => {
  if (agent.stop()) {
    ElMessage.info("已停止当前请求，已有面试记录会保留。");
  }
};

const retryRequest = async () => {
  const success = await agent.retry();
  await scrollConversationToBottom();
  if (!success && agent.phase.value === "error") {
    ElMessage.error(agent.errorMessage.value || "恢复失败，请稍后再试。");
  }
};

const continueInterview = async () => {
  const success = await agent.start();
  await scrollConversationToBottom();
  if (!success && agent.phase.value === "error") {
    ElMessage.error(agent.errorMessage.value || "无法继续该面试。");
  }
};

const resetInterview = () => {
  clearPendingAnswerDraft();
  agent.reset();
  targetRole.value = "";
  resumeText.value = "";
  selectedInterviewModelId.value = "deepseek-v4-pro";
  interviewThinking.value = false;
  selectedFile.value = null;
  inputMode.value = "paste";
  router.replace({ query: {} });
};

const handleModeChange = (mode) => {
  if (mode === "chat") router.push("/chat");
  if (mode === "algorithm") router.push("/algorithms");
};

const difficultyLabel = (difficulty) =>
  ({ EASY: "简单", MEDIUM: "中等", HARD: "困难" })[
    String(difficulty || "").toUpperCase()
  ] || "动态";

const openAlgorithmChallenge = () => {
  const sessionId = agent.session.value?.id;
  if (!sessionId) return;
  router.push({
    path: "/algorithms",
    query: { interviewSessionId: String(sessionId) },
  });
};

const handleSessionSelect = async (item) => {
  const sessionId =
    item?.sessionId || item?.interviewId || item?.memoryId || item?.id;
  if (!sessionId) {
    ElMessage.warning("未找到可恢复的面试会话。");
    return;
  }

  if (!ensureLoggedIn()) return;
  const restored = await agent.restore(sessionId);
  if (!restored) {
    ElMessage.error(agent.errorMessage.value || "恢复面试会话失败。");
    return;
  }
  await scrollConversationToBottom();
  await nextTick();
  if (trajectoryList.value) {
    trajectoryList.value.scrollTop = trajectoryList.value.scrollHeight;
  }
  if (agent.isAwaitingAnswer.value)
    await nextTick(() => answerInput.value?.focus());
};

const openReport = () => {
  const report = agent.toReport();
  if (!report) {
    ElMessage.warning("报告仍在准备中。");
    return;
  }
  sessionStorage.setItem("interviewReport", JSON.stringify(report));
  router.push("/interview-report");
};

const handleTrajectoryScroll = (event) => {
  const element = event.currentTarget;
  followLatestTrajectory.value =
    element.scrollHeight - element.scrollTop - element.clientHeight < 36;
};

watch(
  () => agent.stages.value.length,
  async () => {
    if (!followLatestTrajectory.value) return;
    await nextTick();
    if (trajectoryList.value) {
      trajectoryList.value.scrollTop = trajectoryList.value.scrollHeight;
    }
  },
  { flush: "post" },
);

watch(answerText, (answer) => {
  const activeSession = agent.session.value;
  const key = questionDraftKey(activeSession?.currentQuestion);
  if (!agent.isAwaitingAnswer.value || !activeSession?.id || !key) return;
  saveDraftToSession(activeSession.id, key, answer);
});

watch(
  () => `${agent.session.value?.id || ""}:${questionDraftKey(agent.session.value?.currentQuestion)}`,
  (key) => {
    if (key !== timedQuestionKey) {
      timedQuestionKey = key;
      answerElapsedSeconds.value = 0;
      showAnswerGuide.value = false;
    }
  },
  { immediate: true },
);

watch(
  trajectoryList,
  (element) => {
    if (element && followLatestTrajectory.value) {
      element.scrollTop = element.scrollHeight;
    }
  },
  { flush: "post" },
);

watch(
  () => [
    agent.session.value?.turns?.length,
    agent.session.value?.id,
    questionDraftKey(agent.session.value?.currentQuestion),
    agent.phase.value,
  ],
  () => {
    restorePendingAnswerDraftForCurrentQuestion();
    scrollConversationToBottom();
  },
);

watch(
  () => agent.isAwaitingAnswer.value,
  async (awaiting) => {
    if (awaiting) {
      await nextTick();
      answerInput.value?.focus();
    }
  },
);

onMounted(async () => {
  uiStore.switchMode("interview");
  answerClock = window.setInterval(() => {
    if (agent.isAwaitingAnswer.value) answerElapsedSeconds.value += 1;
  }, 1000);
  const sessionId = route.query.session;
  if (sessionId && ensureLoggedIn()) await handleSessionSelect({ sessionId });
});

onBeforeUnmount(() => {
  agent.stop();
  if (answerClock) window.clearInterval(answerClock);
});
</script>

<style scoped>
.interview-agent-page {
  min-height: 100vh;
  color: var(--gemini-text-primary);
  background:
    radial-gradient(
      circle at 78% -15%,
      color-mix(in srgb, var(--gemini-accent-blue) 22%, transparent),
      transparent 31rem
    ),
    radial-gradient(
      circle at 25% 102%,
      color-mix(in srgb, var(--gemini-accent-green) 11%, transparent),
      transparent 37rem
    ),
    var(--gemini-bg-primary);
}

.agent-main {
  min-height: 100vh;
  transition: margin-left var(--gemini-transition-normal, 220ms ease);
}

.agent-topbar {
  height: 70px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 clamp(18px, 3vw, 44px);
  border-bottom: 1px solid
    color-mix(in srgb, var(--gemini-border-color) 88%, transparent);
  background: color-mix(in srgb, var(--gemini-bg-primary) 82%, transparent);
  backdrop-filter: blur(18px);
  position: sticky;
  top: 0;
  z-index: 8;
}

.topbar-start,
.topbar-actions,
.agent-brand,
.secure-chip,
.stop-request-button,
.process-preview,
.guardrail-list,
.card-heading,
.source-tabs,
.input-hint,
.privacy-note,
.session-topline,
.rail-heading,
.message-meta,
.working-row,
.composer-label,
.composer-footer,
.completion-card,
.algorithm-handoff,
.recovery-card {
  display: flex;
  align-items: center;
}

.algorithm-handoff {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 18px;
  margin: 18px 0;
  padding: 22px;
  border: 1px solid color-mix(in srgb, var(--gemini-accent-yellow) 42%, transparent);
  border-radius: 18px;
  background:
    linear-gradient(
      120deg,
      color-mix(in srgb, var(--gemini-accent-yellow) 12%, transparent),
      transparent 58%
    ),
    var(--gemini-bg-secondary);
}

.algorithm-handoff-mark {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--gemini-accent-yellow) 16%, transparent);
  color: var(--gemini-accent-yellow);
}

.algorithm-handoff-copy h2 {
  margin: 5px 0 7px;
  font-size: 1.15rem;
}

.algorithm-handoff-copy p:last-child {
  margin: 0;
  color: var(--gemini-text-secondary);
  font-size: 0.86rem;
  line-height: 1.65;
}

.algorithm-handoff > button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 16px;
  border: 1px solid var(--gemini-accent-yellow);
  border-radius: 11px;
  background: var(--gemini-accent-yellow);
  color: #17180f;
  font-weight: 700;
  cursor: pointer;
}

.topbar-start {
  gap: 14px;
}
.topbar-actions {
  gap: 12px;
}

.icon-button,
.ghost-button,
.remove-file,
.working-row button {
  border: 0;
  color: inherit;
  background: transparent;
  cursor: pointer;
}

.icon-button {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: var(--gemini-bg-secondary);
  border: 1px solid var(--gemini-border-color);
}

.agent-brand {
  gap: 10px;
}
.brand-mark {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 10px 10px 10px 3px;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: -0.04em;
  color: #07111f;
  background: linear-gradient(145deg, #e7f9ff, #77d7f6 78%);
  box-shadow: 0 7px 20px rgba(93, 211, 246, 0.24);
}

.agent-brand > div {
  display: grid;
  gap: 2px;
}
.agent-brand strong {
  font-size: 14px;
  letter-spacing: 0.02em;
}
.eyebrow {
  margin: 0;
  color: var(--gemini-text-tertiary);
  font-size: 10px;
  letter-spacing: 0.15em;
  font-weight: 700;
}

.secure-chip {
  gap: 7px;
  color: var(--gemini-text-secondary);
  font-size: 12px;
  white-space: nowrap;
}

.secure-chip i,
.session-topline i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #64dea7;
  box-shadow: 0 0 0 4px color-mix(in srgb, #64dea7 12%, transparent);
}

.stop-request-button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 44px;
  padding: 0 12px;
  border: 1px solid color-mix(in srgb, #f39591 60%, var(--gemini-border-color));
  border-radius: 9px;
  color: #ffb4ad;
  background: color-mix(in srgb, #c03b36 13%, var(--gemini-bg-secondary));
  cursor: pointer;
  font-size: 12px;
  font-weight: 650;
}

.setup-shell {
  max-width: 1240px;
  min-height: calc(100vh - 70px);
  margin: 0 auto;
  padding: clamp(48px, 8vw, 114px) clamp(22px, 5vw, 76px);
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(385px, 0.8fr);
  align-items: center;
  gap: clamp(45px, 8vw, 120px);
}

.setup-copy {
  max-width: 620px;
}
.setup-kicker {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 18px;
  color: #84dff9;
  font-size: 12px;
  letter-spacing: 0.11em;
  font-weight: 700;
}
.setup-kicker span {
  width: 25px;
  height: 1px;
  background: currentColor;
}

.setup-copy h1 {
  margin: 0;
  font-family: Georgia, "Noto Serif SC", "Songti SC", serif;
  max-width: 700px;
  color: var(--gemini-text-primary);
  font-size: clamp(38px, 5vw, 66px);
  line-height: 1.06;
  letter-spacing: -0.052em;
  font-weight: 600;
}

.setup-copy h1 em {
  font-weight: 500;
  color: #85dff6;
  font-style: italic;
}

.setup-copy > p {
  max-width: 505px;
  margin: 28px 0 38px;
  color: var(--gemini-text-secondary);
  font-size: 16px;
  line-height: 1.85;
}

.process-preview {
  max-width: 488px;
  gap: 11px;
}
.process-step {
  display: grid;
  gap: 4px;
  color: var(--gemini-text-tertiary);
  white-space: nowrap;
}
.process-step b {
  font-size: 11px;
  letter-spacing: 0.12em;
}
.process-step span {
  font-size: 13px;
  font-weight: 650;
}
.process-step.active {
  color: #a2e8fb;
}
.process-line {
  min-width: 18px;
  height: 1px;
  flex: 1;
  background: var(--gemini-border-color);
}

.guardrail-list {
  flex-wrap: wrap;
  gap: 10px 18px;
  margin-top: 36px;
  color: var(--gemini-text-tertiary);
  font-size: 12px;
}
.guardrail-list span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.guardrail-list .el-icon {
  color: #69dcad;
}

.intake-card {
  position: relative;
  padding: clamp(24px, 3vw, 34px);
  border: 1px solid
    color-mix(in srgb, var(--gemini-border-color) 92%, #71dcfa 8%);
  border-radius: 24px;
  background: linear-gradient(
    148deg,
    color-mix(in srgb, var(--gemini-bg-secondary) 93%, #163653 7%),
    var(--gemini-bg-secondary)
  );
  box-shadow:
    0 28px 80px rgba(0, 0, 0, 0.2),
    inset 0 1px rgba(255, 255, 255, 0.045);
}

.intake-card::before {
  content: "";
  position: absolute;
  top: 0;
  right: 32px;
  left: 32px;
  height: 2px;
  border-radius: 0 0 4px 4px;
  background: linear-gradient(90deg, transparent, #83e1fa, transparent);
  opacity: 0.7;
}

.card-heading {
  gap: 13px;
  margin-bottom: 26px;
}
.heading-icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 11px;
  color: #8ce5fa;
  background: color-mix(in srgb, #73d7f4 13%, transparent);
}
.card-heading h2 {
  margin: 3px 0 0;
  font-size: 18px;
  letter-spacing: -0.02em;
}

.field-label {
  display: block;
  margin-bottom: 9px;
  color: var(--gemini-text-secondary);
  font-size: 12px;
  font-weight: 650;
}
.field-label span {
  margin-left: 5px;
  color: var(--gemini-text-tertiary);
  font-weight: 400;
}
.role-input,
.resume-textarea,
.answer-composer textarea {
  box-sizing: border-box;
  width: 100%;
  outline: none;
  color: var(--gemini-text-primary);
  border: 1px solid var(--gemini-border-color);
  background: color-mix(in srgb, var(--gemini-bg-primary) 70%, transparent);
  font: inherit;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease;
}
.role-input:focus,
.resume-textarea:focus,
.answer-composer textarea:focus {
  border-color: #72d8f4;
  box-shadow: 0 0 0 3px color-mix(in srgb, #72d8f4 13%, transparent);
}
.role-input {
  height: 44px;
  padding: 0 13px;
  border-radius: 10px;
  font-size: 14px;
}

.engine-config {
  margin-top: 22px;
  padding: 14px;
  border: 1px solid color-mix(in srgb, var(--gemini-border-color) 84%, #78dcf7 16%);
  border-radius: 15px;
  background:
    linear-gradient(135deg, color-mix(in srgb, #61d5f5 5%, transparent), transparent 42%),
    color-mix(in srgb, var(--gemini-bg-primary) 55%, transparent);
}
.engine-config-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 11px;
}
.engine-config-head .field-label {
  margin: 0 0 3px;
}
.engine-config-head small,
.model-choice small,
.thinking-toggle-copy small {
  display: block;
  color: var(--gemini-text-tertiary);
  font-size: 10px;
  line-height: 1.45;
}
.engine-status {
  flex: 0 0 auto;
  padding: 4px 7px;
  border: 1px solid color-mix(in srgb, #73d9f5 30%, transparent);
  border-radius: 999px;
  color: #8de6fb;
  font-family: "JetBrains Mono", "Cascadia Code", monospace;
  font-size: 9px;
  font-weight: 750;
  letter-spacing: 0.08em;
}
.model-choice-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}
.model-choice {
  min-width: 0;
  min-height: 58px;
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) 9px;
  align-items: center;
  gap: 9px;
  padding: 9px;
  border: 1px solid var(--gemini-border-color);
  border-radius: 11px;
  background: color-mix(in srgb, var(--gemini-bg-secondary) 72%, transparent);
  color: var(--gemini-text-secondary);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 160ms ease,
    background-color 160ms ease,
    color 160ms ease;
}
.model-choice:hover,
.model-choice:focus-visible {
  border-color: color-mix(in srgb, #76dff8 55%, var(--gemini-border-color));
  color: var(--gemini-text-primary);
}
.model-choice:focus-visible,
.interview-thinking-toggle:focus-visible {
  outline: 2px solid color-mix(in srgb, #79def8 72%, transparent);
  outline-offset: 2px;
}
.model-choice.active {
  border-color: #72d9f5;
  color: var(--gemini-text-primary);
  background: color-mix(in srgb, #65d6f6 11%, var(--gemini-bg-secondary));
}
.model-choice-mark {
  width: 30px;
  height: 26px;
  display: grid;
  place-items: center;
  border-radius: 7px;
  color: #89e3fa;
  background: color-mix(in srgb, #64d3f4 12%, var(--gemini-bg-primary));
  font-family: "JetBrains Mono", "Cascadia Code", monospace;
  font-size: 9px;
  font-weight: 800;
}
.model-choice strong {
  display: block;
  overflow: hidden;
  margin-bottom: 2px;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.model-choice > i {
  width: 7px;
  height: 7px;
  border: 1px solid var(--gemini-border-hover);
  border-radius: 50%;
}
.model-choice.active > i {
  border-color: #75ddf7;
  background: #75ddf7;
  box-shadow: 0 0 0 3px color-mix(in srgb, #75ddf7 14%, transparent);
}
.interview-thinking-toggle {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
  padding: 9px 10px;
  border: 1px solid var(--gemini-border-color);
  border-radius: 11px;
  background: color-mix(in srgb, var(--gemini-bg-secondary) 66%, transparent);
  color: var(--gemini-text-secondary);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 160ms ease,
    background-color 160ms ease;
}
.interview-thinking-toggle.active {
  border-color: color-mix(in srgb, #f5cf67 52%, var(--gemini-border-color));
  background: color-mix(in srgb, #f5cf67 8%, var(--gemini-bg-secondary));
}
.thinking-toggle-icon {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  flex: 0 0 auto;
  border-radius: 8px;
  color: #f1cb63;
  background: color-mix(in srgb, #f1cb63 10%, transparent);
}
.thinking-toggle-copy {
  flex: 1;
  min-width: 0;
}
.thinking-toggle-copy strong {
  display: block;
  margin-bottom: 2px;
  color: var(--gemini-text-primary);
  font-size: 12px;
}
.switch-track {
  width: 34px;
  height: 19px;
  flex: 0 0 auto;
  padding: 2px;
  border-radius: 999px;
  background: var(--gemini-border-hover);
  transition: background-color 160ms ease;
}
.switch-track i {
  display: block;
  width: 15px;
  height: 15px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.24);
  transition: transform 160ms ease;
}
.interview-thinking-toggle.active .switch-track {
  background: #d9ae38;
}
.interview-thinking-toggle.active .switch-track i {
  transform: translateX(15px);
}

.source-tabs {
  gap: 5px;
  margin: 16px 0 13px;
  padding: 4px;
  border-radius: 11px;
  background: color-mix(in srgb, var(--gemini-bg-primary) 62%, transparent);
}
.source-tabs button {
  flex: 1;
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--gemini-text-tertiary);
  cursor: pointer;
  font-size: 12px;
  font-weight: 650;
  transition: all 160ms ease;
}
.source-tabs button.active {
  color: #b8effd;
  background: color-mix(in srgb, #65d6f6 15%, var(--gemini-bg-secondary));
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
}

.resume-textarea {
  min-height: 198px;
  resize: vertical;
  padding: 13px;
  border-radius: 12px;
  line-height: 1.65;
  font-size: 13px;
}
.input-hint {
  justify-content: space-between;
  margin-top: 7px;
  color: var(--gemini-text-tertiary);
  font-size: 11px;
}

.upload-panel {
  min-height: 210px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 9px;
  padding: 20px;
  box-sizing: border-box;
  border: 1px dashed
    color-mix(in srgb, var(--gemini-border-color) 75%, #73d8f5 25%);
  border-radius: 13px;
  color: var(--gemini-text-tertiary);
  background: color-mix(in srgb, var(--gemini-bg-primary) 47%, transparent);
  cursor: pointer;
  outline: none;
  transition:
    border-color 160ms ease,
    background 160ms ease,
    transform 160ms ease;
}
.upload-panel:hover,
.upload-panel:focus-visible,
.upload-panel.dragging {
  border-color: #80dcf8;
  background: color-mix(in srgb, #6fd7f5 9%, var(--gemini-bg-primary));
}
.upload-panel.dragging {
  transform: scale(1.01);
}
.upload-panel strong {
  color: var(--gemini-text-secondary);
  font-size: 13px;
}
.upload-panel > span {
  max-width: 275px;
  text-align: center;
  line-height: 1.55;
  font-size: 11px;
}
.upload-symbol,
.selected-file-icon {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 13px;
  color: #92e4fa;
  background: color-mix(in srgb, #79d9f7 12%, transparent);
  font-size: 21px;
}
.upload-panel.chosen {
  flex-direction: row;
  align-items: center;
  min-height: 120px;
  padding: 17px;
  text-align: left;
  border-style: solid;
}
.selected-file-copy {
  display: grid;
  gap: 4px;
  flex: 1;
  min-width: 0;
}
.selected-file-copy strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.selected-file-copy span {
  font-size: 11px;
}
.remove-file {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  color: var(--gemini-text-tertiary);
}
.remove-file:hover {
  color: #ffb3ae;
  background: color-mix(in srgb, #e85b52 12%, transparent);
}

.privacy-note {
  gap: 5px;
  margin: 13px 0 18px;
  color: var(--gemini-text-tertiary);
  font-size: 11px;
  line-height: 1.4;
}
.privacy-note .el-icon {
  color: #76d9f3;
  flex: none;
}

.begin-button {
  width: 100%;
  min-height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 0;
  border-radius: 12px;
  color: #06121d;
  background: linear-gradient(110deg, #a6edff, #77d9f3 55%, #8ae8cb);
  box-shadow: 0 12px 30px rgba(94, 214, 241, 0.17);
  cursor: pointer;
  font-size: 14px;
  font-weight: 750;
  transition:
    transform 160ms ease,
    filter 160ms ease;
}
.begin-button span {
  margin-left: 3px;
  font-size: 17px;
}
.begin-button:hover:not(:disabled) {
  transform: translateY(-1px);
  filter: brightness(1.04);
}
.begin-button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.interview-shell {
  max-width: 1440px;
  min-height: calc(100vh - 70px);
  margin: 0 auto;
  padding: 28px clamp(18px, 3vw, 42px) 38px;
  display: grid;
  grid-template-columns: 270px minmax(0, 1fr);
  gap: clamp(22px, 3vw, 42px);
}

.agent-rail {
  display: flex;
  flex-direction: column;
  gap: 15px;
}
.session-card,
.progress-card,
.trajectory-card {
  border: 1px solid
    color-mix(in srgb, var(--gemini-border-color) 88%, transparent);
  border-radius: 16px;
  background: color-mix(in srgb, var(--gemini-bg-secondary) 84%, transparent);
  box-shadow: inset 0 1px rgba(255, 255, 255, 0.025);
}
.session-card {
  padding: 17px;
  overflow: hidden;
  position: relative;
}
.session-card::after {
  content: "";
  width: 115px;
  height: 115px;
  position: absolute;
  right: -45px;
  bottom: -58px;
  border: 1px solid color-mix(in srgb, #81dffc 38%, transparent);
  border-radius: 50%;
}
.session-topline {
  justify-content: space-between;
  color: var(--gemini-text-tertiary);
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.13em;
}
.session-topline i.done {
  background: #75dcaa;
}
.session-card h2 {
  margin: 14px 0 7px;
  font-family: Georgia, "Noto Serif SC", serif;
  font-size: 18px;
  line-height: 1.3;
}
.session-card p {
  position: relative;
  z-index: 1;
  overflow: hidden;
  margin: 0;
  color: var(--gemini-text-tertiary);
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
}
.session-id {
  margin-top: 15px;
  color: #80dff6;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 10px;
  letter-spacing: 0.06em;
}

.progress-card,
.trajectory-card {
  padding: 16px;
}
.trajectory-card {
  height: 360px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.rail-heading {
  justify-content: space-between;
  color: var(--gemini-text-secondary);
  font-size: 12px;
  font-weight: 700;
}
.rail-heading .el-icon {
  color: #76dcf7;
}
.main-progress,
.total-progress {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: 18px;
  color: var(--gemini-text-tertiary);
  font-size: 11px;
}
.total-progress {
  margin-top: 17px;
}
.main-progress strong,
.total-progress strong {
  color: var(--gemini-text-primary);
  font-size: 19px;
  letter-spacing: -0.04em;
}
.main-progress small,
.total-progress small {
  color: var(--gemini-text-tertiary);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0;
}
.progress-track {
  height: 5px;
  margin-top: 8px;
  overflow: hidden;
  border-radius: 9px;
  background: color-mix(in srgb, var(--gemini-border-color) 68%, transparent);
}
.progress-track i {
  display: block;
  height: 100%;
  min-width: 2px;
  border-radius: inherit;
  background: linear-gradient(90deg, #75d9f4, #84e5bc);
  transition: width 300ms ease;
}
.progress-card > p {
  margin: 9px 0 0;
  color: var(--gemini-text-tertiary);
  font-size: 10px;
}

.trajectory-caption {
  margin: 8px 0 13px;
  color: var(--gemini-text-tertiary);
  font-size: 10px;
  line-height: 1.45;
}
.stage-list {
  min-height: 0;
  flex: 1;
  display: grid;
  align-content: start;
  gap: 13px;
  margin: 0;
  padding: 2px 7px 8px 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-width: thin;
  scrollbar-color:
    color-mix(in srgb, var(--gemini-text-tertiary) 42%, transparent)
    transparent;
  list-style: none;
}
.stage-list:focus-visible {
  outline: 2px solid color-mix(in srgb, #76dcf7 62%, transparent);
  outline-offset: 4px;
  border-radius: 8px;
}
.stage-list::-webkit-scrollbar {
  width: 5px;
}
.stage-list::-webkit-scrollbar-thumb {
  border-radius: 999px;
  background: color-mix(
    in srgb,
    var(--gemini-text-tertiary) 42%,
    transparent
  );
}
.stage-list li {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  color: var(--gemini-text-tertiary);
}
.stage-list li + li::before {
  content: "";
  position: absolute;
  width: 1px;
  height: 12px;
  margin: -13px 0 0 8px;
  background: var(--gemini-border-color);
}
.stage-list li {
  position: relative;
}
.stage-dot {
  width: 17px;
  height: 17px;
  display: grid;
  place-items: center;
  z-index: 1;
  border-radius: 50%;
  color: #73dcaf;
  background: var(--gemini-bg-secondary);
  font-size: 15px;
}
.stage-dot i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--gemini-text-tertiary);
}
.stage-list li.active .stage-dot i {
  background: #7bdef7;
  box-shadow: 0 0 0 5px color-mix(in srgb, #7bdef7 12%, transparent);
  animation: glow 1.5s infinite;
}
.stage-list strong {
  display: block;
  color: var(--gemini-text-secondary);
  font-size: 11px;
}
.stage-list small {
  display: block;
  margin-top: 3px;
  font-size: 10px;
  line-height: 1.4;
}
.empty-stages {
  min-height: 0;
  flex: 1;
  display: grid;
  place-items: center;
  gap: 7px;
  padding: 19px 0 8px;
  color: var(--gemini-text-tertiary);
  font-size: 10px;
  text-align: center;
}
.empty-stages .el-icon {
  color: #76dcf7;
  font-size: 18px;
}

.conversation-column {
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.conversation-head {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 14px;
  padding: 5px 2px 18px;
}
.conversation-head h1 {
  margin: 4px 0 0;
  font-family: Georgia, "Noto Serif SC", serif;
  font-size: clamp(22px, 2.5vw, 31px);
  letter-spacing: -0.04em;
}
.phase-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 7px 10px;
  border-radius: 20px;
  color: var(--gemini-text-tertiary);
  background: var(--gemini-bg-secondary);
  border: 1px solid var(--gemini-border-color);
  font-size: 11px;
  white-space: nowrap;
}
.phase-chip span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.phase-chip.awaiting-answer {
  color: #8de4f9;
}
.phase-chip.completed {
  color: #72ddaa;
}
.phase-chip.error,
.phase-chip.interrupted {
  color: #ffc178;
}
.phase-chip.starting,
.phase-chip.answering,
.phase-chip.retrying {
  color: #9ceaff;
}

.conversation-log {
  flex: 1;
  min-height: 390px;
  max-height: calc(100vh - 260px);
  overflow-y: auto;
  padding: 8px clamp(2px, 1.4vw, 15px) 20px 2px;
  scroll-behavior: smooth;
}
.turn-pair {
  display: grid;
  gap: 14px;
  margin-bottom: 22px;
}
.message {
  max-width: min(760px, 90%);
  animation: message-in 260ms ease both;
}
.assistant-message {
  margin-right: auto;
}
.candidate-message {
  margin-left: auto;
}
.message-meta {
  gap: 8px;
  min-height: 27px;
  margin-bottom: 7px;
  color: var(--gemini-text-secondary);
  font-size: 11px;
}
.message-meta strong {
  font-size: 11px;
}
.message-meta em {
  margin-left: 1px;
  color: #6fdaf6;
  font-style: normal;
  font-size: 10px;
}
.message-avatar {
  width: 25px;
  height: 25px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  font-weight: 800;
  font-size: 9px;
}
.agent-avatar {
  color: #05121b;
  background: linear-gradient(145deg, #b3eeff, #78d5ef);
}
.candidate-avatar {
  color: #8de4f8;
  background: color-mix(in srgb, #6dcef0 15%, var(--gemini-bg-secondary));
  border: 1px solid color-mix(in srgb, #72d7f4 30%, var(--gemini-border-color));
}
.message p {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  border-radius: 4px 15px 15px 15px;
  border: 1px solid
    color-mix(in srgb, var(--gemini-border-color) 86%, transparent);
  background: var(--gemini-bg-secondary);
  padding: 16px 18px;
  color: var(--gemini-text-primary);
  line-height: 1.78;
  font-size: 14px;
}
.candidate-message p {
  border-radius: 15px 4px 15px 15px;
  background: color-mix(in srgb, #3a94b0 14%, var(--gemini-bg-secondary));
  border-color: color-mix(in srgb, #6dd8f4 32%, var(--gemini-border-color));
}
.current-question {
  margin: 5px 0 22px;
}
.current-question p {
  border-color: color-mix(in srgb, #77d9f4 56%, var(--gemini-border-color));
  box-shadow: 0 8px 28px color-mix(in srgb, #6dd8f4 8%, transparent);
}

.thinking-welcome {
  min-height: 292px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: var(--gemini-text-tertiary);
  text-align: left;
}
.thinking-welcome > div:last-child {
  display: grid;
  gap: 5px;
}
.thinking-welcome strong {
  color: var(--gemini-text-secondary);
  font-size: 13px;
}
.thinking-welcome span {
  font-size: 11px;
}
.pulse-orb {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #8de8fd;
  background: color-mix(in srgb, #70d7f5 14%, transparent);
  box-shadow: 0 0 0 11px color-mix(in srgb, #70d7f5 5%, transparent);
}

.working-row {
  gap: 9px;
  min-height: 37px;
  margin: 5px 0 18px;
  padding: 0 11px;
  border-radius: 11px;
  color: var(--gemini-text-tertiary);
  background: color-mix(in srgb, var(--gemini-bg-secondary) 76%, transparent);
  font-size: 11px;
}
.working-row button {
  margin-left: auto;
  color: #ffb4ae;
  font-size: 11px;
}
.working-pulse {
  display: inline-flex;
  gap: 3px;
}
.working-pulse i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #7cdef8;
  animation: dot 1s infinite alternate;
}
.working-pulse i:nth-child(2) {
  animation-delay: 0.18s;
}
.working-pulse i:nth-child(3) {
  animation-delay: 0.36s;
}

.recovery-card {
  gap: 12px;
  margin: 8px 0 20px;
  padding: 15px;
  border: 1px solid color-mix(in srgb, #ffc277 34%, var(--gemini-border-color));
  border-radius: 14px;
  background: color-mix(in srgb, #bb7c29 8%, var(--gemini-bg-secondary));
}
.recovery-card.ready-card {
  border-color: color-mix(in srgb, #79d9f5 35%, var(--gemini-border-color));
  background: color-mix(in srgb, #4aaec9 8%, var(--gemini-bg-secondary));
}
.recovery-icon {
  width: 31px;
  height: 31px;
  display: grid;
  place-items: center;
  flex: none;
  border-radius: 10px;
  color: #8fe3f9;
  background: color-mix(in srgb, #71d7f4 13%, transparent);
}
.recovery-icon.warning {
  color: #ffc576;
  background: color-mix(in srgb, #ffb24f 13%, transparent);
}
.recovery-card > div:nth-child(2) {
  flex: 1;
}
.recovery-card strong {
  color: var(--gemini-text-secondary);
  font-size: 12px;
}
.recovery-card p {
  margin: 4px 0 0;
  color: var(--gemini-text-tertiary);
  font-size: 11px;
  line-height: 1.45;
}
.recovery-card > button {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex: none;
  border: 1px solid color-mix(in srgb, #7edff8 38%, var(--gemini-border-color));
  border-radius: 8px;
  padding: 8px 10px;
  color: #a7ecfc;
  background: transparent;
  cursor: pointer;
  font-size: 11px;
}

.completion-card {
  position: relative;
  flex-wrap: wrap;
  gap: 13px;
  margin: 10px 0 18px;
  padding: 20px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, #76dda9 43%, var(--gemini-border-color));
  border-radius: 17px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, #48c984 12%, var(--gemini-bg-secondary)),
    var(--gemini-bg-secondary)
  );
}
.completion-mark {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  color: #69dda3;
  background: color-mix(in srgb, #65d59b 14%, transparent);
  font-size: 21px;
}
.completion-card > div:nth-child(2) {
  flex: 1;
}
.completion-card h2 {
  margin: 3px 0;
  font-family: Georgia, "Noto Serif SC", serif;
  font-size: 21px;
}
.completion-card > div:nth-child(2) > p:last-child {
  margin: 0;
  color: var(--gemini-text-tertiary);
  font-size: 11px;
}
.completion-card > button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border: 0;
  border-radius: 9px;
  color: #06130e;
  background: #82e1ad;
  cursor: pointer;
  font-size: 12px;
  font-weight: 750;
}
.summary-preview {
  width: 100%;
  padding-top: 7px;
  border-top: 1px solid
    color-mix(in srgb, var(--gemini-border-color) 76%, transparent);
  color: var(--gemini-text-secondary);
  font-size: 12px;
}

.answer-composer {
  margin-top: auto;
  padding: 15px;
  border: 1px solid color-mix(in srgb, #75d9f5 35%, var(--gemini-border-color));
  border-radius: 17px;
  background: color-mix(in srgb, var(--gemini-bg-secondary) 93%, transparent);
  box-shadow: 0 14px 38px rgba(0, 0, 0, 0.13);
}
.composer-label {
  justify-content: space-between;
  margin-bottom: 10px;
  color: var(--gemini-text-secondary);
  font-size: 12px;
  font-weight: 700;
}
.answer-timer {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-left: auto;
  color: var(--gemini-text-tertiary);
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.composer-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: -2px 0 10px;
  font-size: 10px;
}
.composer-tools > span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--gemini-text-tertiary);
}
.composer-tools > span i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #65d59b;
}
.guide-toggle {
  min-height: 40px;
  padding: 0 8px;
  border: 1px solid var(--gemini-border-color);
  border-radius: 8px;
  color: var(--gemini-text-secondary);
  background: transparent;
  cursor: pointer;
  font: inherit;
}
.guide-toggle:hover {
  border-color: color-mix(in srgb, var(--gemini-accent-blue) 55%, var(--gemini-border-color));
  color: var(--gemini-text-primary);
}
.answer-guide {
  margin-bottom: 10px;
  padding: 11px 12px;
  border: 1px solid color-mix(in srgb, var(--gemini-accent-blue) 22%, var(--gemini-border-color));
  border-radius: 10px;
  background: color-mix(in srgb, var(--gemini-accent-blue) 6%, transparent);
}
.answer-guide > strong {
  color: var(--gemini-text-primary);
  font-size: 11px;
}
.answer-guide ol {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 7px;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
}
.answer-guide li {
  display: grid;
  gap: 2px;
  min-width: 0;
  padding-left: 8px;
  border-left: 2px solid color-mix(in srgb, var(--gemini-accent-blue) 50%, transparent);
}
.answer-guide b {
  color: var(--gemini-text-primary);
  font-size: 10px;
}
.answer-guide li span {
  color: var(--gemini-text-tertiary);
  font-size: 9px;
}
.composer-label small {
  color: var(--gemini-text-tertiary);
  font-size: 10px;
  font-weight: 400;
}
.composer-label kbd {
  padding: 1px 4px;
  border: 1px solid var(--gemini-border-color);
  border-radius: 3px;
  font-family: inherit;
  font-size: 9px;
}
.answer-composer textarea {
  min-height: 104px;
  max-height: 245px;
  resize: vertical;
  padding: 12px;
  border-radius: 10px;
  line-height: 1.65;
  font-size: 13px;
}
.composer-footer {
  justify-content: space-between;
  margin-top: 10px;
  color: var(--gemini-text-tertiary);
  font-size: 10px;
}
.composer-footer > div {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}
.ghost-button {
  min-height: 44px;
  padding: 0 10px;
  color: var(--gemini-text-tertiary);
  font-size: 11px;
}
.skip-answer {
  border-color: color-mix(in srgb, #f0b860 28%, var(--gemini-border-color));
}
.answer-send {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 0;
  border-radius: 8px;
  min-height: 44px;
  padding: 0 13px;
  color: #06121b;
  background: #8be4f9;
  cursor: pointer;
  font-size: 11px;
  font-weight: 750;
}
.answer-send:disabled {
  cursor: not-allowed;
  opacity: 0.42;
}

.guide-toggle:focus-visible,
.ghost-button:focus-visible,
.answer-send:focus-visible {
  outline: 2px solid var(--gemini-accent-blue);
  outline-offset: 2px;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  clip-path: inset(50%);
  white-space: nowrap;
}

@keyframes glow {
  from {
    box-shadow: 0 0 0 3px color-mix(in srgb, #7bdef7 8%, transparent);
  }
  to {
    box-shadow: 0 0 0 7px color-mix(in srgb, #7bdef7 2%, transparent);
  }
}
@keyframes dot {
  to {
    opacity: 0.25;
    transform: translateY(-2px);
  }
}
@keyframes message-in {
  from {
    opacity: 0;
    transform: translateY(7px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

:global([data-theme="light"]) .interview-agent-page {
  background:
    radial-gradient(
      circle at 78% -15%,
      rgba(72, 193, 226, 0.16),
      transparent 31rem
    ),
    radial-gradient(
      circle at 25% 102%,
      rgba(55, 182, 121, 0.1),
      transparent 37rem
    ),
    #f7fafc;
}
:global([data-theme="light"]) .intake-card {
  background: linear-gradient(148deg, #fff, #f5fbfd);
  box-shadow: 0 24px 60px rgba(22, 53, 70, 0.09);
}
:global([data-theme="light"]) .session-card,
:global([data-theme="light"]) .progress-card,
:global([data-theme="light"]) .trajectory-card,
:global([data-theme="light"]) .message p,
:global([data-theme="light"]) .answer-composer {
  background: #fff;
}
:global([data-theme="light"]) .role-input,
:global([data-theme="light"]) .resume-textarea,
:global([data-theme="light"]) .answer-composer textarea,
:global([data-theme="light"]) .upload-panel {
  background: #f8fbfc;
}

@media (max-width: 1080px) {
  .interview-shell {
    grid-template-columns: 220px minmax(0, 1fr);
    gap: 20px;
  }
  .secure-chip {
    display: none;
  }
}

@media (max-width: 860px) {
  .setup-shell {
    grid-template-columns: 1fr;
    max-width: 650px;
    padding-top: 50px;
  }
  .setup-copy {
    max-width: 100%;
  }
  .interview-shell {
    grid-template-columns: 1fr;
  }
  .agent-rail {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    align-items: start;
  }
  .session-card {
    grid-column: 1 / -1;
  }
  .trajectory-card {
    min-height: 0;
  }
  .conversation-log {
    max-height: none;
    min-height: 350px;
  }
}

@media (max-width: 768px) {
  .agent-main {
    margin-left: 0 !important;
  }
  .agent-topbar {
    height: 62px;
    padding: 0 15px;
  }
  .agent-brand strong {
    font-size: 13px;
  }
  .topbar-actions :deep(.user-avatar-container) {
    display: none;
  }
  .stop-request-button {
    padding: 0 9px;
    font-size: 11px;
  }
  .setup-shell {
    min-height: calc(100vh - 62px);
    padding: 36px 18px 48px;
  }
  .setup-copy h1 {
    font-size: 39px;
  }
  .setup-copy > p {
    margin: 21px 0 28px;
    font-size: 14px;
  }
  .process-preview {
    gap: 7px;
  }
  .process-step span {
    font-size: 11px;
  }
  .process-line {
    min-width: 8px;
  }
  .guardrail-list {
    margin-top: 25px;
  }
  .intake-card {
    padding: 21px;
    border-radius: 19px;
  }
  .interview-shell {
    padding: 19px 14px 28px;
  }
  .agent-rail {
    display: flex;
  }
  .trajectory-card {
    order: 3;
  }
  .conversation-head {
    padding-bottom: 13px;
  }
  .conversation-head h1 {
    font-size: 22px;
  }
  .phase-chip {
    padding: 6px 8px;
  }
  .conversation-log {
    min-height: 320px;
    padding-right: 0;
  }
  .message {
    max-width: 97%;
  }
  .message p {
    padding: 13px 14px;
    font-size: 13px;
  }
  .completion-card > button {
    width: 100%;
    justify-content: center;
  }
  .composer-label small {
    display: none;
  }
}

@media (max-width: 430px) {
  .agent-brand .eyebrow {
    display: none;
  }
  .agent-brand strong {
    font-size: 12px;
  }
  .stop-request-button {
    font-size: 0;
    gap: 0;
    width: 33px;
    justify-content: center;
    padding: 0;
  }
  .stop-request-button .el-icon {
    font-size: 14px;
  }
  .source-tabs button {
    font-size: 11px;
  }
  .model-choice-grid {
    grid-template-columns: 1fr;
  }
  .engine-config-head small {
    max-width: 220px;
  }
  .upload-panel {
    min-height: 185px;
  }
  .upload-panel.chosen {
    min-height: 108px;
  }
  .recovery-card {
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .recovery-card > button {
    margin-left: 43px;
  }
  .composer-tools > span {
    display: none;
  }
  .answer-guide ol {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .composer-footer {
    align-items: flex-start;
    gap: 8px;
  }
  .composer-footer > div {
    flex-wrap: wrap;
    justify-content: flex-end;
  }
}

@media (prefers-reduced-motion: reduce) {
  .agent-main,
  .message,
  .working-pulse i,
  .pulse-orb,
  .is-loading {
    animation: none !important;
    transition-duration: 1ms !important;
    scroll-behavior: auto !important;
  }
}
</style>
