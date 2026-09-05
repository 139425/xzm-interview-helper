import { shallowMount } from "@vue/test-utils";
import { describe, expect, it } from "vitest";
import StreamingMarkdown from "../src/components/streaming/StreamingMarkdown.vue";

describe("StreamingMarkdown component routing", () => {
  it("renders pending Markdown structure through the sanitized renderer", () => {
    const wrapper = shallowMount(StreamingMarkdown, {
      props: {
        stream: {
          blocks: [
            { id: "b0", kind: "heading", raw: "## 一个标题", done: false },
            {
              id: "b1",
              kind: "paragraph",
              raw: "**重点** <img src=x onerror=alert(1)>",
              done: false,
            },
          ],
          isFinalized: { value: false },
        },
      },
    });
    expect(wrapper.get("h2").text()).toBe("一个标题");
    expect(wrapper.get("strong").text()).toBe("重点");
    expect(wrapper.find("[onerror]").exists()).toBe(false);
    expect(wrapper.text()).toContain("<img");
  });
  it("将已完成的 mermaid 代码块交给图表渲染器", () => {
    const wrapper = shallowMount(StreamingMarkdown, {
      props: {
        stream: {
          blocks: [
            {
              id: "diagram-1",
              kind: "code",
              lang: "mermaid",
              raw: "flowchart LR\nA --> B",
              done: true,
            },
          ],
          isFinalized: { value: true },
        },
      },
    });

    const diagram = wrapper.findComponent({ name: "MermaidDiagram" });
    expect(diagram.exists()).toBe(true);
    expect(diagram.props("code")).toContain("flowchart LR");
  });

  it("将进行中的文字交给短语淡入组件而不改变 Markdown 完成态", () => {
    const wrapper = shallowMount(StreamingMarkdown, {
      props: {
        stream: {
          blocks: [
            {
              id: "p1",
              kind: "paragraph",
              raw: "正在逐字生成一段足够长的回答",
              done: false,
            },
          ],
          isFinalized: { value: false },
        },
      },
    });

    const reveal = wrapper.findComponent({ name: "StreamingTextReveal" });
    expect(reveal.exists()).toBe(true);
    expect(reveal.props("text")).toBe("正在逐字生成一段足够长的回答");
    expect(reveal.props("mode")).toBe("text");
  });

  it("在生成中使用表格布局，完成时保留同一个表格组件", async () => {
    const wrapper = shallowMount(StreamingMarkdown, {
      props: {
        stream: {
          blocks: [
            {
              id: "table-1",
              kind: "table",
              raw: "| 公司 | 状态 |\n| --- | --- |\n| A | 申请中 |",
              done: false,
            },
          ],
          isFinalized: { value: false },
        },
      },
    });

    const table = wrapper.findComponent({ name: "StreamingTable" });
    expect(table.props("raw")).toContain("| A | 申请中 |");
    const instance = table.vm;
    await wrapper.setProps({
      stream: {
        blocks: [
          { id: "table-1", kind: "table", raw: table.props("raw"), done: true },
        ],
        isFinalized: { value: true },
      },
    });
    expect(wrapper.findComponent({ name: "StreamingTable" }).vm).toBe(instance);
  });
});
