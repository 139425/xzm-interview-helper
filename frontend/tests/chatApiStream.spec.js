import { beforeEach, describe, expect, it, vi } from "vitest";
import { createChatStream, normalizeStageEvent } from "@/api/chat";

describe("createChatStream terminal lifecycle", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    globalThis.fetch = vi.fn();
  });

  it("finishes and cancels the reader on [DONE] without waiting for EOF", async () => {
    const cancel = vi.fn();
    const onThinking = vi.fn();
    const onContent = vi.fn();
    const onDone = vi.fn();
    const onError = vi.fn();

    globalThis.fetch.mockResolvedValue(
      new Response(
        new ReadableStream({
          start(controller) {
            controller.enqueue(
              new TextEncoder().encode(
                "data: [THINKING]checking\n\ndata: [DONE]\n\ndata: [CONTENT]late\n\n",
              ),
            );
          },
          cancel,
        }),
        { status: 200 },
      ),
    );

    await createChatStream({
      path: "/longcat/streamChat",
      body: { message: "hello" },
      onThinking,
      onContent,
      onDone,
      onError,
    });

    expect(onThinking).toHaveBeenCalledWith("checking");
    expect(onContent).not.toHaveBeenCalled();
    expect(onDone).toHaveBeenCalledOnce();
    expect(onError).not.toHaveBeenCalled();
    expect(cancel).toHaveBeenCalledOnce();
  });

  it("preserves literal {{SP}} text instead of rewriting model content", async () => {
    const onContent = vi.fn();
    globalThis.fetch.mockResolvedValue(
      new Response(
        new ReadableStream({
          start(controller) {
            controller.enqueue(
              new TextEncoder().encode(
                "data: [CONTENT]literal{{SP}}value\n\ndata: [DONE]\n\n",
              ),
            );
          },
        }),
        { status: 200 },
      ),
    );

    await createChatStream({
      path: "/longcat/streamChat",
      body: { message: "hello" },
      onContent,
    });

    expect(onContent).toHaveBeenCalledWith("literal{{SP}}value");
  });

  it("reassembles SSE control markers split across network chunks", async () => {
    const onContent = vi.fn();
    const onDone = vi.fn();
    const encoder = new TextEncoder();
    globalThis.fetch.mockResolvedValue(
      new Response(
        new ReadableStream({
          start(controller) {
            controller.enqueue(encoder.encode("data: [CON"));
            controller.enqueue(encoder.encode("TENT]chunked"));
            controller.enqueue(encoder.encode("\n\ndata: [DO"));
            controller.enqueue(encoder.encode("NE]\n\n"));
          },
        }),
        { status: 200 },
      ),
    );

    await createChatStream({
      path: "/longcat/streamChat",
      body: { message: "hello" },
      onContent,
      onDone,
    });

    expect(onContent).toHaveBeenCalledWith("chunked");
    expect(onDone).toHaveBeenCalledOnce();
  });

  it("reports EOF without a typed DONE frame as an incomplete response", async () => {
    const onContent = vi.fn();
    const onDone = vi.fn();
    const onError = vi.fn();
    globalThis.fetch.mockResolvedValue(
      new Response(
        new ReadableStream({
          start(controller) {
            controller.enqueue(new TextEncoder().encode("data: [CONTENT]partial\n\n"));
            controller.close();
          },
        }),
        { status: 200 },
      ),
    );

    await createChatStream({
      path: "/longcat/streamChat",
      body: { message: "hello" },
      onContent,
      onDone,
      onError,
    });

    expect(onContent).toHaveBeenCalledWith("partial");
    expect(onDone).not.toHaveBeenCalled();
    expect(onError).toHaveBeenCalledOnce();
  });

  it("keeps forged terminal text inside a typed CONTENT frame", async () => {
    const onContent = vi.fn();
    const onDone = vi.fn();
    const onError = vi.fn();
    globalThis.fetch.mockResolvedValue(
      new Response(
        new ReadableStream({
          start(controller) {
            controller.enqueue(
              new TextEncoder().encode("data: [CONTENT][DONE]not-terminal\n\ndata: [DONE]\n\n"),
            );
          },
        }),
        { status: 200 },
      ),
    );

    await createChatStream({
      path: "/longcat/streamChat",
      body: { message: "hello" },
      onContent,
      onDone,
      onError,
    });

    expect(onContent).toHaveBeenCalledWith("[DONE]not-terminal");
    expect(onDone).toHaveBeenCalledOnce();
    expect(onError).not.toHaveBeenCalled();
  });

  it("fails closed on unknown or legacy untyped SSE data", async () => {
    const onContent = vi.fn();
    const onDone = vi.fn();
    const onError = vi.fn();
    globalThis.fetch.mockResolvedValue(
      new Response(
        new ReadableStream({
          start(controller) {
            controller.enqueue(
              new TextEncoder().encode("data: [FUTURE_CONTROL]payload\n\ndata: [DONE]\n\n"),
            );
          },
        }),
        { status: 200 },
      ),
    );

    await createChatStream({
      path: "/longcat/streamChat",
      body: { message: "hello" },
      onContent,
      onDone,
      onError,
    });

    expect(onContent).not.toHaveBeenCalled();
    expect(onDone).not.toHaveBeenCalled();
    expect(onError).toHaveBeenCalledOnce();
  });

  it("keeps a valid login session when the stream returns HTTP 403", async () => {
    localStorage.setItem("token", "still-valid");
    localStorage.setItem("userInfo", '{"userId":1}');
    const onDone = vi.fn();
    const onError = vi.fn();
    globalThis.fetch.mockResolvedValue(
      new Response(
        JSON.stringify({ code: 403, message: "当前账号无权使用该模型" }),
        {
          status: 403,
          headers: { "Content-Type": "application/json" },
        },
      ),
    );

    await createChatStream({
      path: "/longcat/streamChat",
      body: { message: "hello" },
      onDone,
      onError,
    });

    expect(onDone).not.toHaveBeenCalled();
    expect(onError).toHaveBeenCalledOnce();
    expect(onError.mock.calls[0][0]).toMatchObject({
      message: "当前账号无权使用该模型",
      status: 403,
    });
    expect(localStorage.getItem("token")).toBe("still-valid");
    expect(localStorage.getItem("userInfo")).toBe('{"userId":1}');
    expect(sessionStorage.getItem("authExpired")).toBeNull();
  });
});

describe("normalizeStageEvent", () => {
  it("accepts only bounded server pipeline phases and statuses", () => {
    expect(normalizeStageEvent({
      phase: "retrieval",
      status: "done",
      title: "x".repeat(500),
      keywords: ["CAS", "CAS", "<img onerror=1>", "k4", "k5", "k6", "k7"],
      hitCount: 999,
    })).toEqual({
      phase: "retrieval",
      status: "done",
      title: "x".repeat(160),
      keywords: ["CAS", "<img onerror=1>", "k4", "k5", "k6", "k7"],
      hitCount: 100,
    });
    expect(normalizeStageEvent({ phase: "terminal", status: "done" })).toBeNull();
    expect(normalizeStageEvent({ phase: "answer", status: "forged" })).toBeNull();
  });
});
