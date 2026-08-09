import { mount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import AlgorithmProblemNavigator from "@/components/algorithm/AlgorithmProblemNavigator.vue";

const problems = [
  {
    frontendId: "1",
    slug: "two-sum",
    title: "两数之和",
    difficulty: "EASY",
    sources: ["HOT100"],
    tags: ["数组", "哈希表"],
    judgeable: true,
  },
  {
    frontendId: "3",
    slug: "longest-substring",
    title: "无重复字符的最长子串",
    difficulty: "MEDIUM",
    sources: ["CODETOP"],
    tags: ["字符串", "滑动窗口"],
    judgeable: true,
  },
];

describe("AlgorithmProblemNavigator", () => {
  it("filters by keyword and emits the selected problem", async () => {
    const wrapper = mount(AlgorithmProblemNavigator, {
      props: { problems },
      global: { stubs: { "el-icon": true } },
    });

    await wrapper.get('input[type="search"]').setValue("滑动窗口");
    const rows = wrapper.findAll(".problem-row");

    expect(rows).toHaveLength(1);
    expect(rows[0].text()).toContain("无重复字符的最长子串");

    await rows[0].trigger("click");
    expect(wrapper.emitted("select")?.[0]?.[0]).toEqual(problems[1]);
  });

  it("prevents selecting a problem other than the locked interview problem", async () => {
    const wrapper = mount(AlgorithmProblemNavigator, {
      props: {
        problems,
        selectedSlug: "two-sum",
        lockedSlug: "two-sum",
      },
      global: { stubs: { "el-icon": true } },
    });

    const lockedOutRow = wrapper
      .findAll(".problem-row")
      .find((row) => row.text().includes("无重复字符"));

    expect(lockedOutRow.attributes("disabled")).toBeDefined();
    await lockedOutRow.trigger("click");
    expect(wrapper.emitted("select")).toBeUndefined();
  });
});
