import { flushPromises, mount } from "@vue/test-utils";
import { reactive } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ElMessageBox } from "element-plus";
import GeminiSidebar from "@/components/GeminiSidebar.vue";

const mocks = vi.hoisted(() => ({
  chatHistory: vi.fn(),
  anonymousHistory: vi.fn(),
  interviewSessions: vi.fn(),
  deleteInterviewSession: vi.fn(),
  createNewChat: vi.fn(),
  routerPush: vi.fn(),
  uiStore: null,
}));

vi.mock("@/api/chat", () => ({
  chatApi: {
    getChatHistorySummariesByUserPaged: mocks.chatHistory,
    getAllChatHistorySummaries: mocks.anonymousHistory,
  },
}));

vi.mock("@/api/interview", () => ({
  interviewApi: {
    listSessions: mocks.interviewSessions,
    deleteSession: mocks.deleteInterviewSession,
  },
}));

vi.mock("@/stores/chat", () => ({
  useChatStore: () => ({
    currentMemoryId: null,
    messages: [],
    createNewChat: mocks.createNewChat,
  }),
}));

vi.mock("@/stores/user", () => ({
  useUserStore: () => ({
    isLoggedIn: true,
    userId: 7,
  }),
}));

vi.mock("@/stores/ui", () => ({
  useUIStore: () => mocks.uiStore,
}));

vi.mock("vue-router", () => ({
  useRouter: () => ({
    currentRoute: { value: { path: "/algorithms" } },
    push: mocks.routerPush,
  }),
}));

describe("GeminiSidebar workspaces", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(ElMessageBox, "confirm").mockResolvedValue("confirm");
    mocks.uiStore = reactive({
      sidebarExpanded: true,
      workspaceListExpanded: false,
      isMobile: false,
      currentMode: "algorithm",
      sidebarRefreshTrigger: 0,
      toggleSidebar: vi.fn(),
      collapseSidebar: vi.fn(),
      toggleWorkspaceList: vi.fn(() => {
        mocks.uiStore.workspaceListExpanded = !mocks.uiStore.workspaceListExpanded;
      }),
      switchMode: vi.fn((mode) => {
        mocks.uiStore.currentMode = mode;
      }),
      resetPromptBarToCenter: vi.fn(),
      displayWelcome: vi.fn(),
      hideWelcome: vi.fn(),
      movePromptBarToBottom: vi.fn(),
    });
  });

  it("does not request chat history in the algorithm workspace", async () => {
    const wrapper = mount(GeminiSidebar, {
      props: { mode: "algorithm" },
      slots: { context: "<div data-test='catalog'>题库</div>" },
      global: { stubs: { "el-icon": true } },
    });

    await flushPromises();

    expect(wrapper.get("[data-test='catalog']").text()).toBe("题库");
    expect(mocks.chatHistory).not.toHaveBeenCalled();
    expect(mocks.anonymousHistory).not.toHaveBeenCalled();
    expect(mocks.interviewSessions).not.toHaveBeenCalled();
  });

  it("switches to chat without creating or clearing a conversation", async () => {
    mocks.uiStore.workspaceListExpanded = true;
    const wrapper = mount(GeminiSidebar, {
      props: { mode: "algorithm" },
      global: { stubs: { "el-icon": true } },
    });

    const chatButton = wrapper
      .findAll(".mode-btn")
      .find((button) => button.attributes("aria-label") === "AI 对话");
    await chatButton.trigger("click");

    expect(mocks.uiStore.switchMode).toHaveBeenCalledWith("chat");
    expect(mocks.createNewChat).not.toHaveBeenCalled();
    expect(mocks.uiStore.resetPromptBarToCenter).not.toHaveBeenCalled();
    expect(mocks.uiStore.displayWelcome).not.toHaveBeenCalled();
    expect(mocks.routerPush).toHaveBeenCalledWith("/chat");
  });

  it("renders a compact navigation-only rail when collapsed", async () => {
    mocks.uiStore.sidebarExpanded = false;

    const wrapper = mount(GeminiSidebar, {
      props: { mode: "algorithm" },
      global: { stubs: { "el-icon": true } },
    });
    await flushPromises();

    expect(wrapper.get("aside").classes()).toContain("collapsed");
    expect(wrapper.find(".logo-section").exists()).toBe(false);
    expect(wrapper.find(".workspace-label").exists()).toBe(false);
    expect(wrapper.find(".mode-copy").exists()).toBe(false);
    expect(wrapper.findAll(".mode-btn")).toHaveLength(6);
    expect(wrapper.find(".algorithm-context").exists()).toBe(false);
  });

  it("shows only the active workspace until the list is expanded", async () => {
    const wrapper = mount(GeminiSidebar, {
      props: { mode: "algorithm" },
      global: { stubs: { "el-icon": true } },
    });

    expect(wrapper.findAll(".mode-btn")).toHaveLength(1);
    expect(wrapper.get(".mode-btn").attributes("aria-label")).toBe("算法训练");
    expect(wrapper.findAll(".mode-copy small")).toHaveLength(1);
    expect(wrapper.get(".mode-copy small").text()).toContain("题库");

    await wrapper.get(".workspace-density-toggle").trigger("click");

    expect(mocks.uiStore.toggleWorkspaceList).toHaveBeenCalledOnce();
    expect(wrapper.findAll(".mode-btn")).toHaveLength(6);
    expect(wrapper.findAll(".mode-copy small")).toHaveLength(6);
  });

  it("deletes an owned interview session from its history list", async () => {
    mocks.uiStore.currentMode = "interview";
    mocks.interviewSessions.mockResolvedValue([
      {
        sessionId: "session-owned-1",
        targetRole: "后端开发工程师",
        status: "AWAITING_ANSWER",
        updatedAt: "2026-08-12T20:00:00Z",
      },
    ]);
    mocks.deleteInterviewSession.mockResolvedValue(undefined);
    const wrapper = mount(GeminiSidebar, {
      props: { mode: "interview" },
      global: { stubs: { "el-icon": true } },
    });
    await flushPromises();

    await wrapper.get('[aria-label^="删除会话："]').trigger("click");
    await flushPromises();

    expect(mocks.deleteInterviewSession).toHaveBeenCalledWith("session-owned-1");
    expect(wrapper.emitted("interview-delete")?.[0]).toEqual([
      ["session-owned-1"],
    ]);
  });
});
