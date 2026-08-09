import request from "@/utils/request";
import { streamInterviewAgent } from "@/api/interview";

export const algorithmApi = {
  async listProblems(params = {}) {
    const response = await request.get("/algorithm/problems", { params });
    return response.data;
  },
  async getProblem(slug) {
    const response = await request.get(`/algorithm/problems/${encodeURIComponent(slug)}`);
    return response.data;
  },
  async run(payload) {
    const response = await request.post("/algorithm/run", payload);
    return response.data;
  },
  async runCustom(payload) {
    const response = await request.post("/algorithm/run/custom", payload);
    return response.data;
  },
  async submit(payload) {
    const response = await request.post("/algorithm/submit", payload);
    return response.data;
  },
  async submissions(problemSlug = "", limit = 20) {
    const response = await request.get("/algorithm/submissions", {
      params: { problemSlug, limit },
    });
    return response.data;
  },
  async reviewSubmission(submissionId, options = {}) {
    const path =
      `/algorithm/submissions/${encodeURIComponent(submissionId)}/review`;
    const config = {
      ...(options.signal ? { signal: options.signal } : {}),
      ...(Number.isFinite(options.timeout) && options.timeout > 0
        ? { timeout: options.timeout }
        : {}),
    };
    const response = Object.keys(config).length
      ? await request.post(path, undefined, config)
      : await request.post(path);
    return response.data;
  },
  async reviewSubmissionStatus(submissionId, options = {}) {
    const path =
      `/algorithm/submissions/${encodeURIComponent(submissionId)}/review`;
    const config = {
      ...(options.signal ? { signal: options.signal } : {}),
      ...(Number.isFinite(options.timeout) && options.timeout > 0
        ? { timeout: options.timeout }
        : {}),
    };
    const response = Object.keys(config).length
      ? await request.get(path, config)
      : await request.get(path);
    return response.data;
  },
  async interviewChallenge(sessionId) {
    const response = await request.get(
      `/algorithm/interview/${encodeURIComponent(sessionId)}`,
    );
    return response.data;
  },
  finishInterviewChallenge(sessionId, options = {}) {
    return streamInterviewAgent({
      ...options,
      path: `/algorithm/interview/${encodeURIComponent(sessionId)}/finish`,
    });
  },
  abandonInterviewChallenge(sessionId, options = {}) {
    return streamInterviewAgent({
      ...options,
      path: `/algorithm/interview/${encodeURIComponent(sessionId)}/abandon`,
    });
  },
};
