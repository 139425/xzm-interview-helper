import { beforeEach, describe, expect, it, vi } from "vitest";
import { streamInterviewAgent } from "@/api/interview";
import { normalizeInterviewSession } from "@/composables/useInterviewAgent";

const streamFromChunks = (chunks) =>
  new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) =>
        controller.enqueue(new TextEncoder().encode(chunk)),
      );
      controller.close();
    },
  });

describe("Interview Agent SSE transport", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    globalThis.fetch = vi.fn();
  });

  it("keeps POST bodies out of the URL and reconstructs split SSE JSON events", async () => {
    localStorage.setItem("token", "test-token");
    globalThis.fetch.mockResolvedValue(
      new Response(
        streamFromChunks([
          'data: {"type":"stage","stage":"resume"}\n\n',
          'data: {"type":"question","question":{"id":"q-1",',
          '"text":"请介绍这个项目"}}\n\n',
        ]),
        { status: 200 },
      ),
    );

    const events = [];
    await streamInterviewAgent({
      path: "/interview-agent/sessions/s-1/turns/stream",
      body: { answer: "候选人的回答" },
      onEvent: (event) => events.push(event),
    });

    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringMatching(/\/interview-agent\/sessions\/s-1\/turns\/stream$/),
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ answer: "候选人的回答" }),
        headers: expect.objectContaining({
          Authorization: "Bearer test-token",
        }),
      }),
    );
    expect(events).toEqual([
      { type: "stage", stage: "resume" },
      { type: "question", question: { id: "q-1", text: "请介绍这个项目" } },
    ]);
  });

  it("rejects malformed stream data instead of silently rendering it", async () => {
    const cancel = vi.fn();
    globalThis.fetch.mockResolvedValue(
      new Response(
        new ReadableStream({
          start(controller) {
            controller.enqueue(new TextEncoder().encode("data: not-json\n\n"));
          },
          cancel,
        }),
        { status: 200 },
      ),
    );

    await expect(
      streamInterviewAgent({
        path: "/interview-agent/sessions/s-1/start/stream",
        onEvent: vi.fn(),
      }),
    ).rejects.toMatchObject({ code: "INVALID_SSE_EVENT" });
    expect(cancel).toHaveBeenCalledOnce();
  });

  it("stops the network reader as soon as a terminal question arrives", async () => {
    const cancel = vi.fn();
    globalThis.fetch.mockResolvedValue(
      new Response(
        new ReadableStream({
          start(controller) {
            controller.enqueue(
              new TextEncoder().encode(
                'data: {"type":"question","question":{"id":"q-1","text":"Question"}}\n\n',
              ),
            );
          },
          cancel,
        }),
        { status: 200 },
      ),
    );

    const events = [];
    await streamInterviewAgent({
      path: "/interview-agent/sessions/s-1/start/stream",
      onEvent: (event) => events.push(event),
    });

    expect(events).toHaveLength(1);
    expect(events[0]).toMatchObject({ type: "question" });
    expect(cancel).toHaveBeenCalledOnce();
  });

  it("treats an algorithm assignment as a terminal interview boundary", async () => {
    const cancel = vi.fn();
    globalThis.fetch.mockResolvedValue(
      new Response(
        new ReadableStream({
          start(controller) {
            controller.enqueue(
              new TextEncoder().encode(
                'data: {"type":"algorithm","session":{"status":"AWAITING_ALGORITHM","algorithmChallenge":{"problemSlug":"two-sum"}}}\n\n',
              ),
            );
          },
          cancel,
        }),
        { status: 200 },
      ),
    );

    const events = [];
    await streamInterviewAgent({
      path: "/interview-agent/sessions/s-1/turns/stream",
      body: { answer: "candidate answer" },
      onEvent: (event) => events.push(event),
    });

    expect(events).toHaveLength(1);
    expect(events[0]).toMatchObject({ type: "algorithm" });
    expect(cancel).toHaveBeenCalledOnce();
  });

  it("does not mistake a transport-only [DONE] marker for interview completion", async () => {
    globalThis.fetch.mockResolvedValue(
      new Response(streamFromChunks(["data: [DONE]\n\n"]), { status: 200 }),
    );
    const onEvent = vi.fn();

    await expect(
      streamInterviewAgent({
        path: "/interview-agent/sessions/s-1/start/stream",
        onEvent,
      }),
    ).rejects.toMatchObject({ code: "INCOMPLETE_INTERVIEW_STREAM" });
    expect(onEvent).not.toHaveBeenCalled();
  });

  it("reports HTTP 403 without clearing the authenticated session", async () => {
    localStorage.setItem("token", "still-valid");
    localStorage.setItem("userInfo", '{"userId":1}');
    globalThis.fetch.mockResolvedValue(
      new Response(
        JSON.stringify({ code: 403, message: "当前账号无权继续该面试" }),
        {
          status: 403,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );

    await expect(
      streamInterviewAgent({
        path: "/interview-agent/sessions/s-1/start/stream",
        onEvent: vi.fn(),
      }),
    ).rejects.toMatchObject({
      message: "当前账号无权继续该面试",
      status: 403,
    });

    expect(localStorage.getItem("token")).toBe("still-valid");
    expect(localStorage.getItem("userInfo")).toBe('{"userId":1}');
    expect(sessionStorage.getItem("authExpired")).toBeNull();
  });
});

describe("Interview Agent session normalization", () => {
  it("treats the server session as authoritative and supports primaryQuestionCount", () => {
    const session = normalizeInterviewSession({
      sessionId: "session-42",
      status: "WAITING_FOR_ANSWER",
      targetRole: "后端工程师",
      resumeFileName: "candidate.pdf",
      primaryQuestionCount: 2,
      totalQuestionCount: 4,
      currentQuestion: {
        turnId: "turn-4",
        question: "如何处理高并发下的缓存击穿？",
        questionKind: "FOLLOW_UP",
      },
      turns: [
        {
          turnId: "turn-1",
          question: "介绍项目",
          answer: "我负责核心服务。",
          knowledgeTags: "缓存, 高并发",
        },
      ],
    });

    expect(session.id).toBe("session-42");
    expect(session.mainQuestionCount).toBe(2);
    expect(session.totalQuestionCount).toBe(4);
    expect(session.resumeName).toBe("candidate.pdf");
    expect(session.turns[0].knowledge).toBe("缓存, 高并发");
    expect(session.currentQuestion).toMatchObject({
      id: "turn-4",
      isFollowUp: true,
    });
    expect(session.currentQuestion.text).toContain("缓存击穿");
  });
});
