import { beforeEach, describe, expect, it, vi } from "vitest";

const http = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}));

vi.mock("@/utils/request", () => ({
  default: http,
}));

vi.mock("@/api/interview", () => ({
  streamInterviewAgent: vi.fn(),
}));

import { algorithmApi } from "@/api/algorithm";

describe("algorithm REST API boundaries", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    http.get.mockResolvedValue({ data: { aiStatus: "COMPLETED" } });
    http.post.mockResolvedValue({ data: { status: "EXECUTED" } });
  });

  it("keeps custom execution separate from official run and submit", async () => {
    const payload = {
      problemSlug: "two-sum",
      language: "java",
      code: "class Solution {}",
      driverCode: "System.out.println(1);",
      expectedOutput: "1",
    };

    await algorithmApi.runCustom(payload);

    expect(http.post).toHaveBeenCalledWith("/algorithm/run/custom", payload);
  });

  it("reviews only a persisted submission id", async () => {
    await algorithmApi.reviewSubmission("42 / unsafe");

    expect(http.post).toHaveBeenCalledWith(
      "/algorithm/submissions/42%20%2F%20unsafe/review",
    );
  });

  it("forwards a bounded cancellation contract for interview-final reviews", async () => {
    const controller = new AbortController();

    await algorithmApi.reviewSubmission("42", {
      signal: controller.signal,
      timeout: 8_000,
    });

    expect(http.post).toHaveBeenCalledWith(
      "/algorithm/submissions/42/review",
      undefined,
      { signal: controller.signal, timeout: 8_000 },
    );
  });

  it("polls persisted review status with GET instead of retriggering the model", async () => {
    const controller = new AbortController();

    await algorithmApi.reviewSubmissionStatus("42 / unsafe", {
      signal: controller.signal,
      timeout: 3_000,
    });

    expect(http.get).toHaveBeenCalledWith(
      "/algorithm/submissions/42%20%2F%20unsafe/review",
      { signal: controller.signal, timeout: 3_000 },
    );
    expect(http.post).not.toHaveBeenCalled();
  });
});
