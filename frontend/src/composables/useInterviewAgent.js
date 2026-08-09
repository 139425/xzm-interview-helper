import { computed, ref } from "vue";
import { interviewApi } from "@/api/interview";

const toText = (value) => (typeof value === "string" ? value.trim() : "");
const firstValue = (...values) =>
  values.find((value) => value !== undefined && value !== null && value !== "");

const toPublicErrorMessage = (
  value,
  fallback = "面试 Agent 暂时无法继续。",
) => {
  const message = toText(value).replace(/\s+/g, " ");
  if (
    !message ||
    /chain\s*of\s*thought|\bcot\b|reasoning|思维链|内部推理/i.test(message)
  )
    return fallback;
  return message.slice(0, 240);
};

const toArray = (value) => {
  if (Array.isArray(value)) return value;
  if (Array.isArray(value?.records)) return value.records;
  if (Array.isArray(value?.items)) return value.items;
  if (Array.isArray(value?.content)) return value.content;
  if (Array.isArray(value?.sessions)) return value.sessions;
  return [];
};

const numberOrZero = (value) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : 0;
};

const isFollowUpKind = (value) =>
  /follow[\s_-]*up|追问/i.test(String(value || ""));

const isAlgorithmKind = (value) =>
  /algorithm|coding|算法/i.test(String(value || ""));

const normalizeQuestion = (value, fallback = {}) => {
  if (typeof value === "string") {
    return {
      id: fallback.id || null,
      text: value.trim(),
      isFollowUp: Boolean(fallback.isFollowUp),
    };
  }

  if (!value || typeof value !== "object") return null;

  const text = toText(
    firstValue(
      value.question,
      value.questionText,
      value.question_text,
      value.content,
      value.text,
      value.prompt,
      value.title,
    ),
  );
  if (!text) return null;

  const rawMainQuestionIndex = firstValue(
    value.mainQuestionIndex,
    value.main_question_index,
    fallback.mainQuestionIndex,
    null,
  );
  const rawTotalQuestionIndex = firstValue(
    value.totalQuestionIndex,
    value.total_question_index,
    fallback.totalQuestionIndex,
    null,
  );

  return {
    id: firstValue(
      value.id,
      value.turnId,
      value.turn_id,
      value.questionId,
      value.question_id,
      fallback.id,
      null,
    ),
    text,
    isFollowUp:
      Boolean(
        firstValue(
          value.isFollowUp,
          value.is_follow_up,
          value.followUp,
          value.follow_up,
          fallback.isFollowUp,
          false,
        ),
      ) ||
      isFollowUpKind(
        firstValue(
          value.questionKind,
          value.question_kind,
          fallback.questionKind,
          fallback.question_kind,
          "",
        ),
      ),
    mainQuestionIndex:
      rawMainQuestionIndex === null ? null : numberOrZero(rawMainQuestionIndex),
    totalQuestionIndex:
      rawTotalQuestionIndex === null
        ? null
        : numberOrZero(rawTotalQuestionIndex),
  };
};

const normalizeTurn = (turn, index) => {
  const question = normalizeQuestion(
    firstValue(
      turn?.question,
      turn?.questionText,
      turn?.question_text,
      turn?.prompt,
    ),
    turn,
  );
  if (!question) return null;

  return {
    id: firstValue(
      turn?.id,
      turn?.turnId,
      turn?.turn_id,
      question.id,
      `turn-${index}`,
    ),
    question: question.text,
    answer: toText(
      firstValue(
        turn?.answer,
        turn?.candidateAnswer,
        turn?.candidate_answer,
        turn?.userAnswer,
        turn?.user_answer,
        turn?.reply,
      ),
    ),
    isFollowUp: question.isFollowUp,
    isAlgorithm: isAlgorithmKind(
      firstValue(
        turn?.questionKind,
        turn?.question_kind,
        turn?.kind,
        turn?.type,
        "",
      ),
    ),
    evaluation: toText(firstValue(turn?.evaluation, turn?.feedback)),
    reference: toText(
      firstValue(
        turn?.reference,
        turn?.referenceAnswer,
        turn?.reference_answer,
      ),
    ),
    score: firstValue(turn?.score, null),
    knowledge: toText(
      firstValue(
        turn?.knowledge,
        turn?.knowledgeTags,
        turn?.knowledge_tags,
        turn?.topic,
        turn?.dimension,
      ),
    ),
    createdAt: firstValue(turn?.createdAt, turn?.created_at, null),
  };
};

const isCompletedStatus = (status) =>
  /complete|finish|report|closed|done/i.test(String(status || ""));

/**
 * Be deliberately permissive about server field names. It lets an agent model
 * change its response schema without breaking the interview UI, while the
 * normalized state used by the view remains small and predictable.
 */
export const normalizeInterviewSession = (raw = {}) => {
  const source =
    raw?.session && typeof raw.session === "object" ? raw.session : raw;
  const turns = toArray(
    firstValue(source.turns, source.questions, source.history, source.records),
  )
    .map(normalizeTurn)
    .filter(Boolean);

  const explicitCurrentQuestion = normalizeQuestion(
    firstValue(
      source.currentQuestion,
      source.current_question,
      source.activeQuestion,
      source.active_question,
    ),
    source,
  );
  const unansweredTurn = turns
    .slice()
    .reverse()
    .find((turn) => !turn.answer && !turn.isAlgorithm);
  const currentQuestion =
    explicitCurrentQuestion ||
    (unansweredTurn
      ? {
          id: unansweredTurn.id,
          text: unansweredTurn.question,
          isFollowUp: unansweredTurn.isFollowUp,
        }
      : null);

  const status = String(
    firstValue(
      source.status,
      source.state,
      source.sessionStatus,
      source.session_status,
      "",
    ),
  ).toLowerCase();
  const mainCountFromTurns = turns.filter(
    (turn) => !turn.isFollowUp && turn.answer,
  ).length;
  const totalCountFromTurns =
    turns.filter((turn) => turn.answer).length +
    (currentQuestion?.text ? 1 : 0);
  const challengeSource = firstValue(
    source.algorithmChallenge,
    source.algorithm_challenge,
    source.challenge,
    null,
  );
  const algorithmChallenge =
    challengeSource && typeof challengeSource === "object"
      ? {
          id: firstValue(challengeSource.id, null),
          turnId: firstValue(
            challengeSource.turnId,
            challengeSource.turn_id,
            null,
          ),
          problemSlug: toText(
            firstValue(
              challengeSource.problemSlug,
              challengeSource.problem_slug,
            ),
          ),
          frontendId: firstValue(
            challengeSource.frontendId,
            challengeSource.frontend_id,
            null,
          ),
          title: toText(challengeSource.title),
          difficulty: toText(challengeSource.difficulty).toUpperCase(),
          timeLimitMinutes: numberOrZero(
            firstValue(
              challengeSource.timeLimitMinutes,
              challengeSource.time_limit_minutes,
            ),
          ),
          status: toText(challengeSource.status).toUpperCase(),
          startedAt: firstValue(
            challengeSource.startedAt,
            challengeSource.started_at,
            null,
          ),
          deadlineAt: firstValue(
            challengeSource.deadlineAt,
            challengeSource.deadline_at,
            null,
          ),
          completedAt: firstValue(
            challengeSource.completedAt,
            challengeSource.completed_at,
            null,
          ),
        }
      : null;

  return {
    id: firstValue(
      source.id,
      source.sessionId,
      source.session_id,
      source.interviewId,
      source.interview_id,
      null,
    ),
    status,
    targetRole: toText(
      firstValue(
        source.targetRole,
        source.target_role,
        source.role,
        source.position,
      ),
    ),
    resumeName: toText(
      firstValue(
        source.resumeName,
        source.resume_name,
        source.resumeFileName,
        source.resume_file_name,
        source.fileName,
        source.file_name,
      ),
    ),
    resumeSource: toText(
      firstValue(source.resumeSource, source.resume_source, source.source),
    ),
    createdAt: firstValue(
      source.createdAt,
      source.created_at,
      source.createTime,
      source.create_time,
      null,
    ),
    updatedAt: firstValue(
      source.updatedAt,
      source.updated_at,
      source.updateTime,
      source.update_time,
      source.completedAt,
      source.completed_at,
      source.startedAt,
      source.started_at,
      source.createTime,
      source.create_time,
      null,
    ),
    events: Array.isArray(source.events) ? source.events : [],
    turns,
    currentQuestion: status === "awaiting_algorithm" ? null : currentQuestion,
    algorithmChallenge,
    mainQuestionCount: numberOrZero(
      firstValue(
        source.mainQuestionCount,
        source.main_question_count,
        source.primaryQuestionCount,
        source.primary_question_count,
        source.mainQuestions,
        mainCountFromTurns,
      ),
    ),
    totalQuestionCount: numberOrZero(
      firstValue(
        source.totalQuestionCount,
        source.total_question_count,
        source.questionCount,
        totalCountFromTurns,
      ),
    ),
    followUpCount: numberOrZero(
      firstValue(
        source.followUpCount,
        source.follow_up_count,
        source.followupCount,
        source.followup_count,
        turns.filter((turn) => turn.isFollowUp).length,
      ),
    ),
    summary: toText(
      firstValue(
        source.summary,
        source.report,
        source.finalSummary,
        source.final_summary,
        source.finalReport,
        source.final_report,
      ),
    ),
    completed:
      Boolean(
        firstValue(
          source.completed,
          source.isCompleted,
          source.is_completed,
          false,
        ),
      ) || isCompletedStatus(status),
    raw: source,
  };
};

const stageCatalog = [
  {
    key: "resume",
    match: /resume|简历|背景|profile/,
    title: "解析简历线索",
    detail: "正在提取可验证的项目、能力与岗位匹配点。",
  },
  {
    key: "retrieve",
    match: /rag|retriev|知识库|knowledge|检索/,
    title: "检索岗位知识",
    detail: "正在补充本轮所需的岗位知识范围。",
  },
  {
    key: "question",
    match: /question|问题|出题|plan/,
    title: "准备下一道问题",
    detail: "正在根据面试进度组织下一步交流。",
  },
  {
    key: "evaluate",
    match: /evaluat|assess|评估|回答|answer/,
    title: "评估本轮回答",
    detail: "正在核对回答中的事实、深度与完整性。",
  },
  {
    key: "follow-up",
    match: /follow|追问|decision|decide/,
    title: "判断是否追问",
    detail: "正在决定继续深挖，还是进入下一个主题。",
  },
  {
    key: "summary",
    match: /summary|report|总结|报告|complete|finish/,
    title: "整理面试总结",
    detail: "正在汇总已验证的能力信号与建议。",
  },
  {
    key: "algorithm",
    match: /algorithm|coding|算法/,
    title: "进入算法终局题",
    detail: "正在根据问答表现匹配题目难度与限时。",
  },
];

const safeStageText = (value, fallback) => {
  const text = toText(value);
  if (
    !text ||
    /chain\s*of\s*thought|\bcot\b|private\s+(?:model\s+)?trace|hidden\s+(?:reasoning|analysis)|internal\s+(?:reasoning|deliberation)|思维链|内部推理|隐藏推理/i.test(
      text,
    )
  )
    return fallback;
  return text.slice(0, 360);
};

/**
 * The server may carry internal model metadata in a stage event. The UI must
 * never surface raw reasoning: it only renders one of these public, bounded
 * progress labels.
 */
const toPublicStage = (event = {}) => {
  const descriptor = [
    event.stage,
    event.name,
    event.code,
    event.toolName,
    event.tool_name,
    event.title,
    event.status,
  ]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();
  const fallback =
    stageCatalog.find((stage) => stage.match.test(descriptor)) || {
      key: "progress",
      title: "推进本轮面试",
      detail: "正在安全地处理本轮面试信息。",
    };
  return {
    ...fallback,
    key: toText(firstValue(event.toolName, event.tool_name, fallback.key)),
    title: safeStageText(event.title, fallback.title),
    detail: safeStageText(
      firstValue(event.detail, event.message),
      fallback.detail,
    ),
    type: String(event?.type || event?.event || event?.kind || "stage").toLowerCase(),
  };
};

const normalizeEventType = (event) =>
  String(event?.type || event?.event || event?.kind || "").toLowerCase();

const stagesFromPersistedEvents = (events) => {
  if (!Array.isArray(events)) return [];
  const restored = [];
  for (const event of events) {
    const type = normalizeEventType(event);
    const stage = toPublicStage(event);
    restored.push({
      ...stage,
      turnId: firstValue(event.turnId, event.turn_id, null),
      id: `persisted-${firstValue(
        event.sequenceNo,
        event.sequence_no,
        event.id,
        restored.length,
      )}`,
      status: "done",
      createdAt: firstValue(event.createTime, event.create_time, 0),
    });
  }
  return restored;
};

const eventSession = (event) =>
  event?.session || event?.snapshot || event?.data?.session || null;

export function useInterviewAgent() {
  const session = ref(null);
  const phase = ref("draft");
  const stages = ref([]);
  const errorMessage = ref("");
  const activeController = ref(null);
  const lastAction = ref(null);
  let streamVersion = 0;

  const isBusy = computed(() =>
    ["creating", "starting", "streaming", "answering", "retrying"].includes(
      phase.value,
    ),
  );
  const isStreaming = computed(
    () => Boolean(activeController.value) && isBusy.value,
  );
  const isAwaitingAnswer = computed(
    () =>
      phase.value === "awaiting-answer" &&
      Boolean(session.value?.currentQuestion?.text),
  );
  const isAwaitingAlgorithm = computed(
    () =>
      phase.value === "algorithm" &&
      Boolean(session.value?.algorithmChallenge?.problemSlug),
  );
  const isCompleted = computed(
    () => phase.value === "completed" || Boolean(session.value?.completed),
  );
  const canRetry = computed(
    () =>
      Boolean(session.value?.id) &&
      ["interrupted", "error", "ready"].includes(phase.value),
  );
  const progress = computed(() => ({
    main: session.value?.mainQuestionCount || 0,
    total: session.value?.totalQuestionCount || 0,
    // total includes the final algorithm challenge; use the persisted follow-up counter
    // instead of deriving it, otherwise the algorithm turn is incorrectly shown as a follow-up.
    followUp: session.value?.followUpCount || 0,
  }));

  const setSession = (raw, { preserveCurrentQuestion = false } = {}) => {
    if (!raw || typeof raw !== "object") return null;
    const normalized = normalizeInterviewSession(raw);
    const source =
      raw?.session && typeof raw.session === "object" ? raw.session : raw;
    const carriesTurns = ["turns", "questions", "history", "records"].some(
      (key) => Array.isArray(source?.[key]),
    );
    const previous = session.value;
    session.value = {
      ...previous,
      ...normalized,
      id: normalized.id || previous?.id || null,
      currentQuestion:
        normalized.currentQuestion ||
        (preserveCurrentQuestion &&
        normalized.status !== "awaiting_algorithm"
          ? previous?.currentQuestion
          : null),
      turns: carriesTurns ? normalized.turns : previous?.turns || [],
    };
    return session.value;
  };

  const addStage = (event) => {
    const stage = toPublicStage(event);
    const previous = stages.value[stages.value.length - 1];
    if (previous?.status === "active") previous.status = "done";
    stages.value = [
      ...stages.value,
      {
        ...stage,
        id: `${Date.now()}-${Math.random()}`,
        status: "active",
        createdAt: Date.now(),
      },
    ];
  };

  const markStagesDone = () => {
    stages.value = stages.value.map((stage) => ({ ...stage, status: "done" }));
  };

  const derivePhaseFromSession = (normalized) => {
    if (!normalized?.id) return "draft";
    if (normalized.completed || normalized.summary) return "completed";
    if (
      normalized.status === "awaiting_algorithm" ||
      normalized.algorithmChallenge?.status === "ASSIGNED"
    )
      return "algorithm";
    // These statuses are durable recovery states, never a fresh session that can be started
    // again. `retry()` is the only operation allowed to reclaim them safely.
    if (
      [
        "evaluation_failed",
        "summary_failed",
        "generating",
        "evaluating",
        "summarizing",
      ].includes(
        normalized.status,
      )
    )
      return "error";
    if (normalized.currentQuestion?.text) return "awaiting-answer";
    return "ready";
  };

  const reset = () => {
    streamVersion += 1;
    if (activeController.value) activeController.value.abort();
    activeController.value = null;
    session.value = null;
    stages.value = [];
    errorMessage.value = "";
    lastAction.value = null;
    phase.value = "draft";
  };

  const applyQuestion = (event) => {
    const question = normalizeQuestion(
      firstValue(event.question, event.currentQuestion, event),
      event,
    );
    if (!question?.text) return false;

    const current = session.value || {};
    const reportedMainQuestionCount = firstValue(
      event.mainQuestionCount,
      event.main_question_count,
      event.primaryQuestionCount,
      event.primary_question_count,
      question.mainQuestionIndex,
      null,
    );
    const reportedTotalQuestionCount = firstValue(
      event.totalQuestionCount,
      event.total_question_count,
      question.totalQuestionIndex,
      null,
    );
    const mainQuestionCount =
      reportedMainQuestionCount === null
        ? numberOrZero(current.mainQuestionCount) +
          (question.isFollowUp ? 0 : 1)
        : numberOrZero(reportedMainQuestionCount);
    const totalQuestionCount =
      reportedTotalQuestionCount === null
        ? numberOrZero(current.totalQuestionCount) + 1
        : numberOrZero(reportedTotalQuestionCount);

    session.value = {
      ...current,
      currentQuestion: question,
      mainQuestionCount,
      totalQuestionCount,
    };
    const questionStageAlreadyPersisted = stages.value.some(
      (stage) =>
        String(stage.turnId || "") === String(question.id || "") &&
        stage.type === "question",
    );
    if (!questionStageAlreadyPersisted) {
      addStage({ stage: question.isFollowUp ? "follow-up" : "question" });
    }
    markStagesDone();
    phase.value = "awaiting-answer";
    return true;
  };

  const applyEvent = (event, context) => {
    const type = normalizeEventType(event);
    const includedSession = eventSession(event);
    if (includedSession) {
      setSession(includedSession, { preserveCurrentQuestion: true });
      const restoredStages = stagesFromPersistedEvents(session.value?.events);
      if (restoredStages.length) stages.value = restoredStages;
    }

    if (type === "stage") {
      addStage(event);
      return false;
    }

    if (type === "snapshot") {
      const restoredStages = stagesFromPersistedEvents(session.value?.events);
      if (restoredStages.length) stages.value = restoredStages;
      if (session.value) phase.value = derivePhaseFromSession(session.value);
      // A snapshot is terminal only when it restores a durable candidate-facing
      // boundary. A processing/empty snapshot followed by EOF is still a broken
      // operation and must remain retryable.
      return Boolean(
        session.value?.currentQuestion?.text ||
          session.value?.algorithmChallenge?.problemSlug ||
          session.value?.completed ||
          session.value?.summary,
      );
    }

    if (type === "question") return applyQuestion(event);

    if (type === "algorithm") {
      if (event.session)
        setSession(event.session, { preserveCurrentQuestion: false });
      if (session.value) session.value.currentQuestion = null;
      const algorithmStageAlreadyPersisted = stages.value.some(
        (stage) =>
          String(stage.turnId || "") === String(event.turnId || "") &&
          stage.type === "algorithm",
      );
      if (!algorithmStageAlreadyPersisted) {
        addStage({
          ...event,
          stage: "algorithm",
          title: event.title || "进入算法终局题",
          detail:
            event.detail ||
            "题目难度已根据本轮表现动态匹配，完成后将生成最终报告。",
        });
      }
      markStagesDone();
      phase.value = "algorithm";
      return true;
    }

    if (type === "completed" || type === "done") {
      if (event.summary && session.value)
        session.value.summary = toText(event.summary);
      if (event.session)
        setSession(event.session, { preserveCurrentQuestion: false });
      if (session.value) {
        session.value.completed = true;
        session.value.currentQuestion = null;
      }
      addStage({ stage: "summary" });
      markStagesDone();
      phase.value = "completed";
      return true;
    }

    if (type === "error") {
      const error = new Error(
        toPublicErrorMessage(
          firstValue(event.message, event.detail, event.error),
        ),
      );
      error.code = event.code || "STREAM_ERROR";
      throw error;
    }

    // A backend that omits `type` but sends a question is still recoverable.
    if (event?.question || event?.currentQuestion) return applyQuestion(event);
    return context?.receivedBoundary || false;
  };

  const runStream = async ({ action, run, busyPhase }) => {
    if (!session.value?.id) throw new Error("尚未创建面试会话。");

    const controller = new AbortController();
    const runVersion = ++streamVersion;
    activeController.value = controller;
    lastAction.value = action;
    errorMessage.value = "";
    phase.value = busyPhase;
    let receivedBoundary = false;

    try {
      await run({
        signal: controller.signal,
        onEvent: (event) => {
          if (runVersion !== streamVersion) return;
          receivedBoundary =
            applyEvent(event, { receivedBoundary }) || receivedBoundary;
        },
      });

      if (controller.signal.aborted || runVersion !== streamVersion)
        return false;

      if (!receivedBoundary) {
        throw new Error(
          "连接已结束，但未收到下一题或完成状态。请重试以恢复面试。",
        );
      }

      return true;
    } catch (error) {
      if (runVersion !== streamVersion) return false;

      if (controller.signal.aborted || error?.name === "AbortError") {
        phase.value = session.value?.algorithmChallenge?.problemSlug
          ? "algorithm"
          : session.value?.currentQuestion?.text
            ? "awaiting-answer"
            : "interrupted";
        return false;
      }

      errorMessage.value = toPublicErrorMessage(error?.message);
      phase.value = "error";
      return false;
    } finally {
      if (activeController.value === controller) activeController.value = null;
    }
  };

  const start = async () => {
    if (!session.value?.id) throw new Error("尚未创建面试会话。");
    return runStream({
      action: "start",
      busyPhase: "starting",
      run: (options) =>
        interviewApi.startSessionStream(session.value.id, options),
    });
  };

  const createFromText = async ({
    resumeText,
    targetRole,
    modelProvider,
    modelName,
    enableThinking,
  }) => {
    const normalizedResume = toText(resumeText);
    if (!normalizedResume) throw new Error("请先粘贴简历内容。");

    reset();
    const creationVersion = ++streamVersion;
    phase.value = "creating";
    try {
      const created = await interviewApi.createSession({
        resumeText: normalizedResume,
        targetRole: toText(targetRole),
        modelProvider,
        modelName,
        enableThinking,
      });
      if (creationVersion !== streamVersion) return false;
      const normalized = setSession(created);
      if (!normalized?.id)
        throw new Error("面试会话创建成功，但未返回会话标识。");
      addStage({ stage: "resume" });
      return await start();
    } catch (error) {
      if (creationVersion !== streamVersion) return false;
      errorMessage.value = toPublicErrorMessage(
        error?.message,
        "创建面试会话失败。",
      );
      phase.value = "error";
      return false;
    }
  };

  const createFromFile = async ({
    file,
    targetRole,
    modelProvider,
    modelName,
    enableThinking,
  }) => {
    if (typeof File === "undefined" || !(file instanceof File)) {
      throw new Error("请选择有效的简历文件。");
    }

    reset();
    const creationVersion = ++streamVersion;
    phase.value = "creating";
    try {
      const created = await interviewApi.uploadResume({
        file,
        targetRole: toText(targetRole),
        modelProvider,
        modelName,
        enableThinking,
      });
      if (creationVersion !== streamVersion) return false;
      const normalized = setSession({
        ...created,
        resumeName: created?.resumeName || file.name,
        resumeSource: "file",
      });
      if (!normalized?.id) throw new Error("简历上传成功，但未返回会话标识。");
      addStage({ stage: "resume" });
      return await start();
    } catch (error) {
      if (creationVersion !== streamVersion) return false;
      errorMessage.value = toPublicErrorMessage(
        error?.message,
        "上传简历或创建面试会话失败。",
      );
      phase.value = "error";
      return false;
    }
  };

  const submitAnswer = async (answer) => {
    const normalizedAnswer = toText(answer);
    if (!normalizedAnswer) throw new Error("请先输入回答。");
    if (!isAwaitingAnswer.value) throw new Error("当前还不能提交回答。");

    const question = session.value.currentQuestion;
    const existing = session.value.turns || [];
    session.value = {
      ...session.value,
      turns: [
        ...existing.filter((turn) => turn.id !== question.id),
        {
          id: question.id || `turn-${Date.now()}`,
          question: question.text,
          answer: normalizedAnswer,
          isFollowUp: question.isFollowUp,
          evaluation: "",
          reference: "",
          score: null,
          knowledge: "",
          createdAt: new Date().toISOString(),
        },
      ],
      currentQuestion: null,
    };
    addStage({ stage: "evaluate" });

    return runStream({
      action: "turn",
      busyPhase: "answering",
      run: (options) =>
        interviewApi.submitTurnStream(
          session.value.id,
          normalizedAnswer,
          options,
        ),
    });
  };

  const retry = async () => {
    if (!session.value?.id) return false;
    return runStream({
      action: "retry",
      busyPhase: "retrying",
      run: (options) => interviewApi.retryStream(session.value.id, options),
    });
  };

  const stop = () => {
    if (!activeController.value) return false;
    activeController.value.abort();
    phase.value = session.value?.algorithmChallenge?.problemSlug
      ? "algorithm"
      : session.value?.currentQuestion?.text
        ? "awaiting-answer"
        : "interrupted";
    return true;
  };

  const restore = async (sessionId) => {
    if (!sessionId) throw new Error("缺少要恢复的面试会话。");
    const restoreVersion = ++streamVersion;
    if (activeController.value) activeController.value.abort();
    activeController.value = null;

    errorMessage.value = "";
    phase.value = "starting";
    try {
      const fetched = await interviewApi.getSession(sessionId);
      if (restoreVersion !== streamVersion) return null;
      const normalized = setSession(fetched);
      if (!normalized?.id) throw new Error("未找到该面试会话。");
      stages.value = stagesFromPersistedEvents(normalized.events);
      if (!stages.value.length) {
        addStage({ stage: normalized.completed ? "summary" : "resume" });
        markStagesDone();
      }
      phase.value = derivePhaseFromSession(normalized);
      return normalized;
    } catch (error) {
      if (restoreVersion !== streamVersion) return null;
      errorMessage.value = toPublicErrorMessage(
        error?.message,
        "恢复面试会话失败。",
      );
      phase.value = "error";
      return null;
    }
  };

  const loadSessions = async () => {
    const result = await interviewApi.listSessions();
    return toArray(result)
      .map(normalizeInterviewSession)
      .filter((item) => item.id);
  };

  const toReport = () => {
    if (!session.value) return null;
    return {
      agentSessionId: session.value.id,
      interviewId: session.value.id,
      generatedAt: session.value.updatedAt || new Date().toISOString(),
      summary: session.value.summary || "面试已结束，暂未返回完整总结。",
      rounds: (session.value.turns || [])
        .filter((turn) => turn.answer)
        .map((turn) => ({
          question: turn.question,
          answer: turn.answer,
          evaluation: turn.evaluation,
          reference: turn.reference,
          score: turn.score,
          knowledge: turn.knowledge,
        })),
    };
  };

  return {
    session,
    phase,
    stages,
    errorMessage,
    lastAction,
    isBusy,
    isStreaming,
    isAwaitingAnswer,
    isAwaitingAlgorithm,
    isCompleted,
    canRetry,
    progress,
    reset,
    start,
    createFromText,
    createFromFile,
    submitAnswer,
    retry,
    stop,
    restore,
    loadSessions,
    toReport,
  };
}
