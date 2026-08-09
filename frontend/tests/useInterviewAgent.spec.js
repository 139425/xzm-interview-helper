import { beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({
  getSession: vi.fn(),
  startSessionStream: vi.fn(),
  submitTurnStream: vi.fn(),
  retryStream: vi.fn(),
  createSession: vi.fn(),
  uploadResume: vi.fn(),
  listSessions: vi.fn(),
}));

vi.mock("@/api/interview", () => ({ interviewApi: api }));

import { useInterviewAgent } from "@/composables/useInterviewAgent";

const session = (overrides = {}) => ({
  id: "session-1",
  status: "IN_PROGRESS",
  primaryQuestionCount: 0,
  totalQuestionCount: 0,
  turns: [],
  ...overrides,
});

describe("useInterviewAgent terminal-state handling", () => {
  beforeEach(() => {
    Object.values(api).forEach((mock) => mock.mockReset());
  });

  it("treats a snapshot-only disconnect as an incomplete operation", async () => {
    api.getSession.mockResolvedValue(session());
    api.startSessionStream.mockImplementation(async (_id, { onEvent }) => {
      onEvent({ type: "snapshot", session: session() });
    });

    const agent = useInterviewAgent();
    await agent.restore("session-1");
    const result = await agent.start();

    expect(result).toBe(false);
    expect(agent.phase.value).toBe("error");
    expect(agent.errorMessage.value).not.toBe("");
  });

  it("uses the persisted follow-up count instead of counting the algorithm turn", async () => {
    api.getSession.mockResolvedValue(
      session({
        status: "AWAITING_ALGORITHM",
        primaryQuestionCount: 3,
        followUpCount: 1,
        totalQuestionCount: 5,
        algorithmChallenge: {
          problemSlug: "two-sum",
          status: "ASSIGNED",
        },
      }),
    );

    const agent = useInterviewAgent();
    await agent.restore("session-1");

    expect(agent.progress.value).toEqual({
      main: 3,
      followUp: 1,
      total: 5,
    });
  });

  it("accepts a snapshot that restores an actionable persisted question", async () => {
    const restored = session({
      status: "AWAITING_ANSWER",
      currentQuestion: {
        turnId: "turn-1",
        question: "Explain your most relevant project.",
        questionKind: "PRIMARY",
      },
    });
    api.getSession.mockResolvedValue(session());
    api.startSessionStream.mockImplementation(async (_id, { onEvent }) => {
      onEvent({ type: "snapshot", session: restored });
    });

    const agent = useInterviewAgent();
    await agent.restore("session-1");
    const result = await agent.start();

    expect(result).toBe(true);
    expect(agent.phase.value).toBe("awaiting-answer");
    expect(agent.session.value.currentQuestion.text).toContain("relevant project");
  });

  it("keeps a server error distinct from an EOF transport failure", async () => {
    api.getSession.mockResolvedValue(session());
    api.startSessionStream.mockImplementation(async (_id, { onEvent }) => {
      onEvent({ type: "error", message: "当前会话不可继续" });
    });

    const agent = useInterviewAgent();
    await agent.restore("session-1");
    const result = await agent.start();

    expect(result).toBe(false);
    expect(agent.phase.value).toBe("error");
    expect(agent.errorMessage.value).toBe("当前会话不可继续");
    expect(agent.errorMessage.value).not.toContain("连接已结束");
  });

  it("does not let a late create response resurrect a reset session", async () => {
    let resolveCreate;
    api.createSession.mockReturnValue(
      new Promise((resolve) => {
        resolveCreate = resolve;
      }),
    );

    const agent = useInterviewAgent();
    const pending = agent.createFromText({
      resumeText: "候选人简历",
      targetRole: "前端工程师",
    });
    agent.reset();
    resolveCreate(session());

    await expect(pending).resolves.toBe(false);
    expect(agent.session.value).toBeNull();
    expect(agent.phase.value).toBe("draft");
  });

  it("releases an aborted stream instead of leaving the UI in a busy state", async () => {
    api.getSession.mockResolvedValue(session());
    api.startSessionStream.mockImplementation(
      (_id, { signal }) =>
        new Promise((_resolve, reject) => {
          signal.addEventListener("abort", () =>
            reject(Object.assign(new Error("aborted"), { name: "AbortError" })),
          );
        }),
    );

    const agent = useInterviewAgent();
    await agent.restore("session-1");
    const pending = agent.start();
    expect(agent.isStreaming.value).toBe(true);

    agent.stop();
    await expect(pending).resolves.toBe(false);
    expect(agent.isStreaming.value).toBe(false);
    expect(agent.phase.value).toBe("interrupted");
  });

  it("restores a long adaptive interview without a hard question cap", async () => {
    api.getSession.mockResolvedValue(
      session({
        status: "AWAITING_ANSWER",
        totalQuestionCount: 24,
        primaryQuestionCount: 11,
        currentQuestion: {
          turnId: "turn-25",
          question: "Continue with the next evidence-based question.",
          questionKind: "PRIMARY",
        },
      }),
    );

    const agent = useInterviewAgent();
    await expect(agent.restore("session-1")).resolves.toBeTruthy();

    expect(agent.phase.value).toBe("awaiting-answer");
    expect(agent.progress.value.total).toBe(24);
  });

  it("treats an algorithm assignment as a durable terminal boundary", async () => {
    const algorithmSession = session({
      status: "AWAITING_ALGORITHM",
      totalQuestionCount: 8,
      turns: [
        {
          id: "algorithm-turn",
          question: "Two Sum",
          questionKind: "ALGORITHM",
          answer: "",
        },
      ],
      algorithmChallenge: {
        problemSlug: "two-sum",
        title: "两数之和",
        difficulty: "EASY",
        timeLimitMinutes: 20,
        status: "ASSIGNED",
      },
    });
    api.getSession.mockResolvedValue(session());
    api.startSessionStream.mockImplementation(async (_id, { onEvent }) => {
      onEvent({ type: "algorithm", session: algorithmSession });
    });

    const agent = useInterviewAgent();
    await agent.restore("session-1");
    const result = await agent.start();

    expect(result).toBe(true);
    expect(agent.phase.value).toBe("algorithm");
    expect(agent.isAwaitingAlgorithm.value).toBe(true);
    expect(agent.session.value.currentQuestion).toBeNull();
    expect(agent.session.value.algorithmChallenge.problemSlug).toBe("two-sum");
  });

  it("keeps durable evaluation and summary recovery states on the retry path", async () => {
    api.getSession.mockResolvedValue(session({ status: "SUMMARY_FAILED" }));

    const agent = useInterviewAgent();
    await agent.restore("session-1");

    expect(agent.phase.value).toBe("error");
    expect(agent.canRetry.value).toBe(true);
  });

  it("restores candidate-safe tool stages without exposing persisted raw detail", async () => {
    api.getSession.mockResolvedValue(
      session({
        status: "AWAITING_ANSWER",
        currentQuestion: {
          turnId: "turn-1",
          question: "Explain your RAG design.",
          questionKind: "PRIMARY",
        },
        events: [
          {
            id: 1,
            type: "stage",
            toolName: "rag_search",
            title: "internal retrieval",
            detail: "private model trace must never render",
          },
          {
            id: 2,
            type: "question",
            toolName: "question_generation",
            title: "question ready",
          },
        ],
      }),
    );

    const agent = useInterviewAgent();
    await agent.restore("session-1");

    expect(agent.stages.value.map((stage) => stage.key)).toEqual([
      "rag_search",
      "question_generation",
    ]);
    expect(JSON.stringify(agent.stages.value)).not.toContain("private model trace");
  });
});
