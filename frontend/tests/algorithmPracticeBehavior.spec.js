import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  api: {
    listProblems: vi.fn(),
    getProblem: vi.fn(),
    run: vi.fn(),
    runCustom: vi.fn(),
    submit: vi.fn(),
    submissions: vi.fn(),
    reviewSubmission: vi.fn(),
    reviewSubmissionStatus: vi.fn(),
    interviewChallenge: vi.fn(),
    finishInterviewChallenge: vi.fn(),
    abandonInterviewChallenge: vi.fn(),
  },
  message: {
    error: vi.fn(),
    warning: vi.fn(),
    success: vi.fn(),
    info: vi.fn(),
  },
  routerPush: vi.fn(),
  routerReplace: vi.fn(),
  routeQuery: {},
}));

vi.mock("@/api/algorithm", () => ({
  algorithmApi: mocks.api,
}));

vi.mock("@/stores/ui", () => ({
  useUIStore: () => ({
    sidebarWidth: 280,
    sidebarExpanded: true,
    isMobile: false,
    currentTheme: "light",
    initialize: vi.fn(),
    cleanup: vi.fn(),
    switchMode: vi.fn(),
    expandSidebar: vi.fn(),
    collapseSidebar: vi.fn(),
  }),
}));

vi.mock("vue-router", () => ({
  useRoute: () => ({ query: mocks.routeQuery }),
  useRouter: () => ({
    push: mocks.routerPush,
    replace: mocks.routerReplace,
  }),
}));

vi.mock("element-plus", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    ElMessage: mocks.message,
    ElMessageBox: { confirm: vi.fn() },
  };
});

vi.mock("@/utils/markdownFormatter", () => ({
  renderMarkdown: (value) => `<p>${String(value)}</p>`,
}));

vi.mock("@/components/GeminiSidebar.vue", () => ({
  default: {
    name: "GeminiSidebar",
    template: "<aside><slot name='context' /></aside>",
  },
}));

vi.mock("@/components/algorithm/AlgorithmProblemNavigator.vue", () => ({
  default: {
    name: "AlgorithmProblemNavigator",
    props: ["problems", "selectedSlug", "lockedSlug", "loading"],
    emits: ["select"],
    template: `
      <nav>
        <button
          v-for="problem in problems"
          :key="problem.slug"
          :data-problem="problem.slug"
          @click="$emit('select', problem)"
        >{{ problem.title }}</button>
      </nav>
    `,
  },
}));

vi.mock("@/components/algorithm/MonacoCodeEditor.vue", () => ({
  default: {
    name: "MonacoCodeEditor",
    props: ["modelValue"],
    emits: ["update:modelValue", "run", "submit"],
    template: `
      <textarea
        class="editor-stub"
        :value="modelValue"
        @input="$emit('update:modelValue', $event.target.value)"
      />
    `,
  },
}));

import AlgorithmPractice from "@/views/AlgorithmPractice.vue";
import algorithmPracticeSource from "@/views/AlgorithmPractice.vue?raw";

let resizeObserverCallbacks = [];

const problems = [
  {
    frontendId: "1",
    slug: "two-sum",
    title: "两数之和",
    difficulty: "EASY",
    sources: ["HOT100"],
    timeLimitMinutes: 20,
    judgeable: true,
  },
  {
    frontendId: "2",
    slug: "second-problem",
    title: "第二题",
    difficulty: "MEDIUM",
    sources: ["CODETOP"],
    timeLimitMinutes: 20,
    judgeable: true,
  },
];

const detail = (slug) => ({
  slug,
  title: slug,
  contentHtml: `<p>${slug}</p>`,
  codeTemplates: { java: `class Solution { // ${slug}\n}` },
});

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

async function mountPractice() {
  const wrapper = mount(AlgorithmPractice, {
    global: {
      stubs: {
        "el-icon": { template: "<span><slot /></span>" },
      },
    },
  });
  await flushPromises();
  return wrapper;
}

describe("AlgorithmPractice request generations", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    localStorage.setItem("token", "test-token");
    localStorage.setItem("userInfo", JSON.stringify({ userId: 7 }));
    Object.keys(mocks.routeQuery).forEach((key) => {
      delete mocks.routeQuery[key];
    });
    resizeObserverCallbacks = [];
    vi.stubGlobal(
      "ResizeObserver",
      class ResizeObserver {
        constructor(callback) {
          resizeObserverCallbacks.push(callback);
        }
        observe() {}
        disconnect() {}
      },
    );
    mocks.api.listProblems.mockResolvedValue(problems);
    mocks.api.getProblem.mockImplementation((slug) =>
      Promise.resolve(detail(slug)),
    );
    mocks.api.submissions.mockResolvedValue([]);
    mocks.api.reviewSubmission.mockResolvedValue({
      submissionId: 1,
      aiStatus: "COMPLETED",
      aiScore: 90,
      aiEvaluation: "稳定",
    });
    mocks.api.reviewSubmissionStatus.mockResolvedValue({
      submissionId: 1,
      aiStatus: "COMPLETED",
      aiScore: 90,
      aiEvaluation: "稳定",
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it("uses the intended split defaults when no stored preference exists", async () => {
    const wrapper = await mountPractice();

    expect(wrapper.get(".workbench").attributes("style")).toContain(
      "--description-width: 390px",
    );
    expect(wrapper.get(".workbench").attributes("style")).toContain(
      "--console-height: 230px",
    );
    wrapper.unmount();
  });

  it("keeps the desktop sidebar offset until the shared 768px mobile breakpoint", () => {
    expect(algorithmPracticeSource).toContain(
      "@media(max-width:768px){.algorithm-main{margin-left:0!important}}",
    );
    expect(algorithmPracticeSource).not.toContain(
      "@media(max-width:900px){.algorithm-main{margin-left:0!important}",
    );
  });

  it("re-clamps persisted splits when the available workbench becomes smaller", async () => {
    vi.useFakeTimers();
    localStorage.setItem("algorithm:descriptionWidth", "720");
    localStorage.setItem("algorithm:consoleHeight", "480");

    const wrapper = await mountPractice();
    const workbench = wrapper.get(".workbench").element;
    const editorPanel = wrapper.get(".editor-panel").element;
    workbench.getBoundingClientRect = () => ({
      width: 800,
      height: 700,
      left: 0,
      right: 800,
      top: 0,
      bottom: 700,
    });
    editorPanel.getBoundingClientRect = () => ({
      width: 460,
      height: 600,
      left: 340,
      right: 800,
      top: 0,
      bottom: 600,
    });

    resizeObserverCallbacks.forEach((callback) => callback([]));
    await vi.advanceTimersByTimeAsync(100);
    await flushPromises();

    expect(wrapper.get(".workbench").attributes("style")).toContain(
      "--description-width: 460px",
    );
    expect(wrapper.get(".workbench").attributes("style")).toContain(
      "--console-height: 308px",
    );
    expect(localStorage.getItem("algorithm:descriptionWidth")).toBe("460");
    expect(localStorage.getItem("algorithm:consoleHeight")).toBe("308");
    wrapper.unmount();
  });

  it("preserves multiline expected output up to the backend contract limit", async () => {
    mocks.api.runCustom.mockResolvedValue({
      status: "CUSTOM_PASSED",
      output: "first line\nsecond line",
      passedCases: 1,
      totalCases: 1,
      runtimeMs: 1,
    });
    const wrapper = await mountPractice();
    const expectedOutput = wrapper.get(".expected-output textarea");
    expect(expectedOutput.attributes("maxlength")).toBe("12000");

    await expectedOutput.setValue("first line\nsecond line");
    await wrapper.get(".custom-case-actions button").trigger("click");
    await flushPromises();

    expect(mocks.api.runCustom).toHaveBeenCalledWith(
      expect.objectContaining({
        expectedOutput: "first line\nsecond line",
      }),
    );
    wrapper.unmount();
  });

  it("invalidates the old execution and disables submission while the next problem loads", async () => {
    const oldRun = deferred();
    const secondDetail = deferred();
    mocks.api.run.mockReturnValue(oldRun.promise);
    mocks.api.getProblem.mockImplementation((slug) =>
      slug === "second-problem"
        ? secondDetail.promise
        : Promise.resolve(detail(slug)),
    );

    const wrapper = await mountPractice();
    await wrapper.get(".run-button").trigger("click");
    expect(mocks.api.run).toHaveBeenCalledTimes(1);

    await wrapper.get('[data-problem="second-problem"]').trigger("click");
    expect(wrapper.get(".run-button").attributes("disabled")).toBeDefined();
    expect(wrapper.get(".submit-button").attributes("disabled")).toBeDefined();

    oldRun.reject(new Error("late failure"));
    await flushPromises();
    expect(mocks.message.error).not.toHaveBeenCalled();

    secondDetail.resolve(detail("second-problem"));
    await flushPromises();
    expect(wrapper.get(".run-button").attributes("disabled")).toBeUndefined();
    expect(wrapper.get(".editor-stub").element.value).toContain("second-problem");
    wrapper.unmount();
  });

  it("renders both camelCase and snake_case submission review fields", async () => {
    mocks.api.submissions.mockResolvedValue([
      {
        submissionId: 11,
        status: "ACCEPTED",
        passedCases: 2,
        totalCases: 2,
        runtimeMs: 0,
        aiStatus: "COMPLETED",
        aiScore: 88,
        aiEvaluation: "复杂度合理",
        createTime: "2026-07-29T12:00:00Z",
      },
    ]);

    const wrapper = await mountPractice();
    await wrapper.findAll(".challenge-tabs button")[1].trigger("click");
    await flushPromises();

    const row = wrapper.get(".submission-row");
    expect(row.text()).toContain("2/2 用例");
    expect(row.text()).toContain("0 ms");
    expect(row.text()).toContain("88");

    await row.trigger("click");
    expect(wrapper.text()).toContain("复杂度合理");
    expect(mocks.api.reviewSubmission).not.toHaveBeenCalled();
    wrapper.unmount();
  });

  it("cancels AI-review polling when the page unmounts", async () => {
    vi.useFakeTimers();
    mocks.api.submit.mockResolvedValue({
      status: "ACCEPTED",
      submissionId: 9,
      passedCases: 2,
      totalCases: 2,
      runtimeMs: 1,
    });
    mocks.api.reviewSubmission.mockResolvedValue({
      submissionId: 9,
      aiStatus: "PROCESSING",
    });

    const wrapper = await mountPractice();
    await wrapper.get(".submit-button").trigger("click");
    await flushPromises();
    expect(mocks.api.reviewSubmission).toHaveBeenCalledTimes(1);

    wrapper.unmount();
    await vi.advanceTimersByTimeAsync(3_000);
    expect(mocks.api.reviewSubmission).toHaveBeenCalledTimes(1);
  });

  it("keeps a failed AI review retryable without changing the judge result", async () => {
    mocks.api.submit.mockResolvedValue({
      status: "ACCEPTED",
      submissionId: 12,
      passedCases: 2,
      totalCases: 2,
      runtimeMs: 1,
    });
    mocks.api.reviewSubmission.mockResolvedValue({
      submissionId: 12,
      aiStatus: "FAILED",
      aiEvaluation: "模型服务暂时不可用",
    });

    const wrapper = await mountPractice();
    await wrapper.get(".submit-button").trigger("click");
    await flushPromises();

    expect(wrapper.get(".result-badge").text()).toBe("通过");
    expect(wrapper.get(".review-failed").text()).toContain("AI 评审暂时不可用");
    expect(wrapper.get(".review-failed").text()).toContain("重新获取评审");
    wrapper.unmount();
  });

  it("waits for the final algorithm AI review before completing an interview", async () => {
    const submissionId = "9223372036854775807";
    mocks.routeQuery.interviewSessionId = "session-1";
    mocks.api.interviewChallenge.mockResolvedValue({
      problemSlug: "two-sum",
      status: "ACTIVE",
    });
    mocks.api.submit.mockResolvedValue({
      status: "ACCEPTED",
      submissionId,
      passedCases: 2,
      totalCases: 2,
      runtimeMs: 1,
      interviewReadyToComplete: true,
    });
    mocks.api.reviewSubmission.mockResolvedValue({
      submissionId,
      aiStatus: "COMPLETED",
      aiScore: 93,
      aiEvaluation: "边界处理完整",
    });
    mocks.api.finishInterviewChallenge.mockImplementation(
      async (_sessionId, options) => {
        options.onEvent({ type: "completed" });
      },
    );

    const wrapper = await mountPractice();
    await wrapper.get(".submit-button").trigger("click");
    await flushPromises();

    expect(mocks.api.reviewSubmission).toHaveBeenCalledWith(
      submissionId,
      expect.objectContaining({
        signal: expect.any(Object),
        timeout: expect.any(Number),
      }),
    );
    expect(
      mocks.api.reviewSubmission.mock.invocationCallOrder[0],
    ).toBeLessThan(
      mocks.api.finishInterviewChallenge.mock.invocationCallOrder[0],
    );
    expect(mocks.api.finishInterviewChallenge).toHaveBeenCalledWith(
      "session-1",
      expect.any(Object),
    );
    expect(mocks.routerReplace).toHaveBeenCalledWith({
      path: "/aiInterview",
      query: { session: "session-1" },
    });
    wrapper.unmount();
  });

  it("polls a processing interview review through the read-only status endpoint", async () => {
    mocks.routeQuery.interviewSessionId = "session-poll";
    mocks.api.interviewChallenge.mockResolvedValue({
      problemSlug: "two-sum",
      status: "ACTIVE",
    });
    mocks.api.submit.mockResolvedValue({
      status: "ACCEPTED",
      submissionId: "41",
      passedCases: 2,
      totalCases: 2,
      runtimeMs: 1,
      interviewReadyToComplete: true,
    });
    mocks.api.reviewSubmission.mockResolvedValue({
      submissionId: "41",
      aiStatus: "PROCESSING",
    });
    mocks.api.reviewSubmissionStatus.mockResolvedValue({
      submissionId: "41",
      aiStatus: "COMPLETED",
      aiScore: 91,
      aiEvaluation: "完成",
    });
    mocks.api.finishInterviewChallenge.mockImplementation(
      async (_sessionId, options) => {
        options.onEvent({ type: "completed" });
      },
    );

    const wrapper = await mountPractice();
    await wrapper.get(".submit-button").trigger("click");
    await new Promise((resolve) => setTimeout(resolve, 1_050));
    await flushPromises();

    expect(mocks.api.reviewSubmission).toHaveBeenCalledOnce();
    expect(mocks.api.reviewSubmissionStatus).toHaveBeenCalledOnce();
    expect(mocks.api.finishInterviewChallenge).toHaveBeenCalledOnce();
    wrapper.unmount();
  });

  it("resumes the review-before-report barrier after reloading an accepted challenge", async () => {
    const submissionId = "9223372036854775806";
    mocks.routeQuery.interviewSessionId = "session-reload";
    mocks.api.interviewChallenge.mockResolvedValue({
      problemSlug: "two-sum",
      status: "ACCEPTED",
      latestSubmissionId: submissionId,
    });
    mocks.api.reviewSubmission.mockResolvedValue({
      submissionId,
      aiStatus: "COMPLETED",
      aiScore: 90,
    });
    mocks.api.finishInterviewChallenge.mockImplementation(
      async (_sessionId, options) => {
        options.onEvent({ type: "completed" });
      },
    );

    const wrapper = await mountPractice();

    expect(mocks.api.submit).not.toHaveBeenCalled();
    expect(mocks.api.reviewSubmission).toHaveBeenCalledWith(
      submissionId,
      expect.any(Object),
    );
    expect(
      mocks.api.reviewSubmission.mock.invocationCallOrder[0],
    ).toBeLessThan(
      mocks.api.finishInterviewChallenge.mock.invocationCallOrder[0],
    );
    wrapper.unmount();
  });

  it("degrades a rate-limited interview review and still completes the report", async () => {
    mocks.routeQuery.interviewSessionId = "session-rate-limit";
    mocks.api.interviewChallenge.mockResolvedValue({
      problemSlug: "two-sum",
      status: "ACTIVE",
    });
    mocks.api.submit.mockResolvedValue({
      status: "ACCEPTED",
      submissionId: "31",
      passedCases: 2,
      totalCases: 2,
      runtimeMs: 1,
      interviewReadyToComplete: true,
    });
    mocks.api.reviewSubmission.mockRejectedValue({
      response: { status: 429, data: { message: "评审请求过于频繁" } },
    });
    mocks.api.finishInterviewChallenge.mockImplementation(
      async (_sessionId, options) => {
        options.onEvent({ type: "completed" });
      },
    );

    const wrapper = await mountPractice();
    await wrapper.get(".submit-button").trigger("click");
    await flushPromises();

    expect(mocks.api.finishInterviewChallenge).toHaveBeenCalledOnce();
    expect(mocks.message.warning).toHaveBeenCalledWith(
      "AI 评审暂时不可用，将按判题结果继续生成报告",
    );
    wrapper.unmount();
  });

  it("bounds a stalled interview review, keeps the review stage visible, and continues", async () => {
    vi.useFakeTimers();
    mocks.routeQuery.interviewSessionId = "session-timeout";
    mocks.api.interviewChallenge.mockResolvedValue({
      problemSlug: "two-sum",
      status: "ACTIVE",
    });
    mocks.api.submit.mockResolvedValue({
      status: "ACCEPTED",
      submissionId: "32",
      passedCases: 2,
      totalCases: 2,
      runtimeMs: 1,
      interviewReadyToComplete: true,
    });
    mocks.api.reviewSubmission.mockImplementation(() => new Promise(() => {}));
    mocks.api.finishInterviewChallenge.mockImplementation(
      async (_sessionId, options) => {
        options.onEvent({ type: "completed" });
      },
    );

    const wrapper = await mountPractice();
    await wrapper.get(".submit-button").trigger("click");
    await flushPromises();
    expect(wrapper.get(".review-loading").text()).toContain("AI 正在");
    expect(mocks.api.finishInterviewChallenge).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(20_001);
    await flushPromises();

    expect(mocks.message.info).toHaveBeenCalledWith(
      "AI 评审等待超时，将按判题结果继续生成报告",
    );
    expect(mocks.api.finishInterviewChallenge).toHaveBeenCalledOnce();
    wrapper.unmount();
  });
});
