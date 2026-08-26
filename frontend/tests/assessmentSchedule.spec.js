import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import AssessmentSchedule from "@/views/AssessmentSchedule.vue";

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  create: vi.fn(),
  setCompleted: vi.fn(),
  remove: vi.fn(),
  restore: vi.fn(),
  permanentDelete: vi.fn(),
  parseImage: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
  confirm: vi.fn(),
}));

vi.mock("@/api/career", () => ({
  scheduleApi: {
    list: mocks.list,
    create: mocks.create,
    setCompleted: mocks.setCompleted,
    remove: mocks.remove,
    restore: mocks.restore,
    permanentDelete: mocks.permanentDelete,
    parseImage: mocks.parseImage,
  },
}));

vi.mock("element-plus", () => ({
  ElMessage: {
    success: mocks.success,
    error: mocks.error,
    warning: mocks.warning,
  },
  ElMessageBox: {
    confirm: mocks.confirm,
  },
}));

const WorkspaceFrameStub = {
  template:
    '<div><slot name="status"></slot><slot name="actions"></slot><slot></slot></div>',
};
const RouterLinkStub = { template: "<a><slot></slot></a>" };

const schedules = [
  {
    id: 1,
    company: "临近科技",
    roleName: "后端开发",
    eventType: "INTERVIEW",
    startAt: "2026-08-24T13:00:00",
    endAt: null,
    notes: "准备项目深挖",
    completedAt: null,
  },
  {
    id: 2,
    company: "昨日智能",
    roleName: "",
    eventType: "WRITTEN_TEST",
    startAt: "2026-08-24T10:00:00",
    endAt: null,
    notes: "",
    completedAt: null,
  },
  {
    id: 3,
    company: "完成网络",
    roleName: "产品经理",
    eventType: "ASSESSMENT",
    startAt: "2026-08-23T09:00:00",
    endAt: "2026-08-23T10:00:00",
    notes: "",
    completedAt: "2026-08-23T10:05:00",
  },
];

function mountPage() {
  return mount(AssessmentSchedule, {
    global: {
      stubs: {
        WorkspaceFrame: WorkspaceFrameStub,
        RouterLink: RouterLinkStub,
      },
    },
  });
}

describe("AssessmentSchedule workspace", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-24T12:00:00"));
    vi.clearAllMocks();
    mocks.list.mockResolvedValue({
      items: schedules.map((item) => ({ ...item })),
      trash: [],
    });
    mocks.confirm.mockResolvedValue("confirm");
    mocks.create.mockImplementation(async (payload) => ({
      id: 9,
      ...payload,
      completedAt: null,
    }));
    mocks.setCompleted.mockImplementation(async (id, completed) => ({
      ...schedules.find((item) => item.id === id),
      completedAt: completed ? "2026-08-24T12:01:00" : null,
    }));
    mocks.remove.mockResolvedValue({ deleted: true });
    mocks.restore.mockResolvedValue({ ...schedules[0], deletedAt: null });
    mocks.permanentDelete.mockResolvedValue({ deleted: true });
    mocks.parseImage.mockResolvedValue({
      company: "用友",
      roleName: "全栈开发工程师【高潜】-27届",
      eventType: "WRITTEN_TEST",
      startAt: "2026-08-27T14:00:00",
      endAt: "2026-08-31T12:00:00",
      eventUrl: "https://exam.nowcoder.com/cts/example",
      notes: "在线笔试，时长 120 分钟",
      confidence: 0.97,
      warnings: [],
      ocrText: "笔试时间：2026-08-27 14:00至2026-08-31 12:00",
    });
    Object.defineProperty(URL, "createObjectURL", {
      configurable: true,
      value: vi.fn(() => "blob:preview"),
    });
    Object.defineProperty(URL, "revokeObjectURL", {
      configurable: true,
      value: vi.fn(),
    });
  });

  afterEach(() => vi.useRealTimers());

  it("shows overdue and nearest schedules before the completed history", async () => {
    const wrapper = mountPage();
    await flushPromises();

    expect(wrapper.findAll(".schedule-list > li")).toHaveLength(2);
    expect(wrapper.get(".schedule-list > li .schedule-card").text()).toContain(
      "昨日智能",
    );
    expect(wrapper.get(".schedule-list > li .proximity-tag").text()).toBe(
      "已逾期",
    );
    expect(wrapper.text()).toContain("临近科技");
    expect(wrapper.get(".completed-section").text()).toContain("完成网络");
    expect(
      wrapper.findAll(".schedule-stats dt").map((node) => node.text()),
    ).toEqual(["2", "2", "1", "1"]);
  });

  it("creates a time-range assessment with company and optional role", async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get('input[placeholder="例如：字节跳动"]').setValue("星河云");
    await wrapper
      .get('input[placeholder="例如：后端开发工程师"]')
      .setValue("Java 后端");
    await wrapper.get('input[type="radio"][value="ASSESSMENT"]').setValue(true);
    await wrapper.get('input[type="radio"][value="range"]').setValue(true);
    const dateInputs = wrapper.findAll('input[type="datetime-local"]');
    await dateInputs[0].setValue("2026-08-26T14:00");
    await dateInputs[1].setValue("2026-08-26T15:30");
    await wrapper.get(".schedule-form").trigger("submit");
    await flushPromises();

    expect(mocks.create).toHaveBeenCalledWith({
      company: "星河云",
      roleName: "Java 后端",
      eventType: "ASSESSMENT",
      startAt: "2026-08-26T14:00",
      endAt: "2026-08-26T15:30",
      eventUrl: "",
      notes: "",
    });
    expect(wrapper.text()).toContain("星河云");
  });

  it("completes and restores completed schedules through direct actions", async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get(".complete-button").trigger("click");
    await flushPromises();
    expect(mocks.setCompleted).toHaveBeenCalledWith(2, true);

    const completedRecord = wrapper
      .findAll(".completed-section li")
      .find((row) => row.text().includes("完成网络"));
    const restore = completedRecord.get("button");
    await restore.trigger("click");
    await flushPromises();
    expect(mocks.setCompleted).toHaveBeenCalledWith(3, false);
  });

  it("moves deletions to the 14-day trash and allows restoration", async () => {
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get(".delete-button").trigger("click");
    await flushPromises();

    expect(mocks.confirm).not.toHaveBeenCalled();
    expect(mocks.remove).toHaveBeenCalledWith(2);
    await wrapper.get(".trash-button").trigger("click");
    expect(wrapper.get(".trash-list").text()).toContain("昨日智能");
    expect(wrapper.get(".trash-list").text()).toContain("14 天后自动清除");

    await wrapper.get(".restore-button").trigger("click");
    await flushPromises();
    expect(mocks.restore).toHaveBeenCalledWith(2);
    expect(wrapper.find(".trash-list").exists()).toBe(false);
  });

  it("parses a screenshot into an editable DeepSeek Flash preview before saving", async () => {
    const wrapper = mountPage();
    await flushPromises();

    const file = new File(["image"], "notice.png", { type: "image/png" });
    const input = wrapper.get('input[type="file"]');
    Object.defineProperty(input.element, "files", {
      configurable: true,
      value: [file],
    });
    await input.trigger("change");
    await flushPromises();

    expect(mocks.parseImage).toHaveBeenCalledWith(file);
    expect(wrapper.get(".import-grid input[required]").element.value).toBe(
      "用友",
    );
    expect(wrapper.get(".import-review").text()).toContain("DeepSeek V4 Flash");
    await wrapper.get(".import-review").trigger("submit");
    await flushPromises();

    expect(mocks.create).toHaveBeenLastCalledWith({
      company: "用友",
      roleName: "全栈开发工程师【高潜】-27届",
      eventType: "WRITTEN_TEST",
      startAt: "2026-08-27T14:00",
      endAt: "2026-08-31T12:00",
      eventUrl: "https://exam.nowcoder.com/cts/example",
      notes: "在线笔试，时长 120 分钟",
    });
  });

  it("permanently deletes only after an explicit trash confirmation", async () => {
    mocks.list.mockResolvedValueOnce({
      items: schedules.map((item) => ({ ...item })),
      trash: [
        {
          ...schedules[0],
          deletedAt: "2026-08-24T11:00:00",
          purgeAt: "2026-09-07T11:00:00",
        },
      ],
    });
    const wrapper = mountPage();
    await flushPromises();

    await wrapper.get(".trash-button").trigger("click");
    await wrapper.get(".purge-button").trigger("click");
    await flushPromises();

    expect(mocks.confirm).toHaveBeenCalledWith(
      expect.stringContaining("无法恢复"),
      "永久删除日程",
      expect.any(Object),
    );
    expect(mocks.permanentDelete).toHaveBeenCalledWith(1);
  });
});
