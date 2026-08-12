import { flushPromises, mount } from "@vue/test-utils";
import { reactive } from "vue";
import { beforeEach, describe, expect, it, vi } from "vitest";
import GeminiSidebar from "@/components/GeminiSidebar.vue";

const mocks = vi.hoisted(() => ({
  chatHistory: vi.fn(),
  anonymousHistory: vi.fn(),
  interviewSessions: vi.fn(),
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
    mocks.uiStore = reactive({
      sidebarExpanded: true,
      isMobile: false,
      currentMode: "algorithm",
      sidebarRefreshTrigger: 0,
      toggleSidebar: vi.fn(),
      collapseSidebar: vi.fn(),
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
});
