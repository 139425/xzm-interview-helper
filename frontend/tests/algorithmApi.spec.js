import { beforeEach, describe, expect, it, vi } from "vitest";
import { algorithmApi } from "@/api/algorithm";

describe("algorithm interview transport", () => {
  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem("token", "test-token");
    globalThis.fetch = vi.fn();
  });

  it("posts an abandon request and consumes the completed report stream", async () => {
    globalThis.fetch.mockResolvedValue(
      new Response(
        new ReadableStream({
          start(controller) {
            controller.enqueue(
              new TextEncoder().encode(
                'data: {"type":"completed","summary":"done"}\n\n',
              ),
            );
            controller.close();
          },
        }),
        { status: 200 },
      ),
    );
    const onEvent = vi.fn();

    await algorithmApi.abandonInterviewChallenge("session / 42", { onEvent });

    expect(globalThis.fetch).toHaveBeenCalledWith(
      expect.stringMatching(
        /\/algorithm\/interview\/session%20%2F%2042\/abandon$/,
      ),
      expect.objectContaining({
        method: "POST",
        headers: expect.objectContaining({
          Authorization: "Bearer test-token",
        }),
      }),
    );
    expect(onEvent).toHaveBeenCalledWith(
      expect.objectContaining({ type: "completed" }),
    );
  });
});
