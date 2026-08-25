import { flushPromises, mount } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import AssessmentSchedule from "@/views/AssessmentSchedule.vue";

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  create: vi.fn(),
  setCompleted: vi.fn(),
  remove: vi.fn(),
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
      notes: "",
    });
    expect(wrapper.text()).toContain("星河云");
  });

  it("completes, restores, and deletes schedules through direct actions", async () => {
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

    await wrapper.get(".delete-button").trigger("click");
    await flushPromises();
    expect(mocks.confirm).toHaveBeenCalledOnce();
    expect(mocks.remove).toHaveBeenCalled();
  });
});
