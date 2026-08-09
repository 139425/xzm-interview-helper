import request, { baseURL } from "@/utils/request";

const AGENT_ROOT = "/interview-agent";

/**
 * The application still has a few endpoints that return an envelope
 * ({ code, message, data }) and a few that return the resource directly.
 * Keep that transport detail out of the interview state machine.
 */
const unwrapResponse = (response) => {
  const payload = response?.data;
  if (
    payload &&
    typeof payload === "object" &&
    Object.prototype.hasOwnProperty.call(payload, "data") &&
    (Object.prototype.hasOwnProperty.call(payload, "code") ||
      Object.prototype.hasOwnProperty.call(payload, "success"))
  ) {
    return payload.data;
  }
  return payload;
};

const endpointUrl = (path) =>
  `${String(baseURL || "").replace(/\/+$/, "")}${path}`;

const createStreamError = async (response) => {
  let message = `请求失败（${response.status}）`;
  try {
    const payload = await response.json();
    message = payload?.message || payload?.error || payload?.detail || message;
  } catch {
    // Some reverse proxies answer with HTML. The status is still useful.
  }
  const error = new Error(message);
  error.status = response.status;
  return error;
};

const handleUnauthorized = () => {
  localStorage.removeItem("token");
  localStorage.removeItem("userInfo");
  sessionStorage.setItem("authExpired", "true");

  if (typeof window !== "undefined" && window.location.pathname !== "/login") {
    window.location.assign("/login");
  }
};

const parseSseBlock = (block, onEvent) => {
  const dataLines = block
    .split(/\r?\n/)
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trimStart());

  if (!dataLines.length) return false;

  const data = dataLines.join("\n").trim();
  if (!data) return false;

  if (data === "[DONE]") {
    const error = new Error(
      "面试流已结束，但未返回下一题或完成状态，请重试恢复会话。",
    );
    error.code = "INCOMPLETE_INTERVIEW_STREAM";
    throw error;
  }

  let parsed;
  try {
    parsed = JSON.parse(data);
  } catch {
    const error = new Error("面试服务返回了无法识别的流式数据。");
    error.code = "INVALID_SSE_EVENT";
    throw error;
  }

  const event = parsed?.data && !parsed?.type ? parsed.data : parsed;
  onEvent(event);
  const type = String(event?.type || "").trim().toLowerCase();
  return ["question", "algorithm", "completed", "done", "error"].includes(type);
};

/**
 * Read the interview Agent's POST/SSE stream. EventSource cannot send POST
 * bodies or custom authorization headers, so all streaming operations share
 * this fetch implementation instead.
 */
export const streamInterviewAgent = async ({ path, body, signal, onEvent }) => {
  const token = localStorage.getItem("token");
  const response = await fetch(endpointUrl(path), {
    method: "POST",
    signal,
    headers: {
      Accept: "text/event-stream",
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 401) {
    handleUnauthorized();
    const error = new Error("登录已过期，请重新登录。");
    error.status = response.status;
    throw error;
  }
  if (response.status === 403) {
    throw await createStreamError(response);
  }

  if (!response.ok) {
    throw await createStreamError(response);
  }

  if (!response.body) {
    throw new Error("面试服务未返回可读取的数据流。");
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let readerEnded = false;
  let readerCancelled = false;
  let terminalReceived = false;

  const cancelReader = async (reason) => {
    if (readerEnded || readerCancelled) return;
    try {
      await reader.cancel(reason);
    } catch {
      // Abort may already have closed the browser-managed stream.
    } finally {
      readerCancelled = true;
    }
  };

  try {
    while (!terminalReceived) {
      const { value, done } = await reader.read();
      if (done) {
        readerEnded = true;
        break;
      }

      buffer += decoder.decode(value, { stream: true });
      let separator = buffer.search(/\r?\n\r?\n/);

      while (separator >= 0 && !terminalReceived) {
        const block = buffer.slice(0, separator);
        buffer = buffer.slice(separator).replace(/^\r?\n\r?\n/, "");
        terminalReceived = parseSseBlock(block, onEvent);
        separator = buffer.search(/\r?\n\r?\n/);
      }
    }

    if (!terminalReceived) {
      buffer += decoder.decode();
      if (buffer.trim()) terminalReceived = parseSseBlock(buffer, onEvent);
    }
  } catch (error) {
    await cancelReader(error);
    throw error;
  } finally {
    if (!readerEnded) await cancelReader();
    try {
      reader.releaseLock();
    } catch {
      // A cancelled reader can already have released its lock.
    }
  }
};

export const interviewApi = {
  async createSession({ resumeText, targetRole, modelProvider, modelName, enableThinking }) {
    const response = await request.post(`${AGENT_ROOT}/sessions`, {
      resumeText,
      targetRole,
      modelProvider,
      modelName,
      enableThinking,
    });
    return unwrapResponse(response);
  },

  async uploadResume({ file, targetRole, modelProvider, modelName, enableThinking }) {
    const formData = new FormData();
    formData.append("file", file);
    if (targetRole?.trim()) formData.append("targetRole", targetRole.trim());
    formData.append("modelProvider", modelProvider);
    formData.append("modelName", modelName);
    formData.append("enableThinking", String(Boolean(enableThinking)));

    const response = await request.post(
      `${AGENT_ROOT}/sessions/upload`,
      formData,
      {
        headers: { "Content-Type": "multipart/form-data" },
      },
    );
    return unwrapResponse(response);
  },

  async getSession(sessionId) {
    const response = await request.get(
      `${AGENT_ROOT}/sessions/${encodeURIComponent(sessionId)}`,
    );
    return unwrapResponse(response);
  },

  async listSessions() {
    const response = await request.get(`${AGENT_ROOT}/sessions`);
    return unwrapResponse(response);
  },

  startSessionStream(sessionId, options = {}) {
    return streamInterviewAgent({
      ...options,
      path: `${AGENT_ROOT}/sessions/${encodeURIComponent(sessionId)}/start/stream`,
    });
  },

  submitTurnStream(sessionId, answer, options = {}) {
    return streamInterviewAgent({
      ...options,
      path: `${AGENT_ROOT}/sessions/${encodeURIComponent(sessionId)}/turns/stream`,
      body: { answer },
    });
  },

  retryStream(sessionId, options = {}) {
    return streamInterviewAgent({
      ...options,
      path: `${AGENT_ROOT}/sessions/${encodeURIComponent(sessionId)}/retry/stream`,
    });
  },
};

export default interviewApi;
