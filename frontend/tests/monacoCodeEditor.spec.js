import { describe, expect, it } from "vitest";
import {
  buildJavaCompletionItems,
  extractJavaContextIdentifiers,
} from "@/components/algorithm/MonacoCodeEditor.vue";

describe("MonacoCodeEditor Java completions", () => {
  it("extracts identifiers from the problem template without comments or literals", () => {
    const identifiers = extractJavaContextIdentifiers(`
      class Solution {
        // ignoredComment and FakeType should not leak into suggestions
        public int[] twoSum(int[] nums, int target) {
          String text = "ignoredString";
          return nums;
        }
      }
    `);

    expect(identifiers).toEqual(
      expect.arrayContaining([
        "Solution",
        "twoSum",
        "nums",
        "target",
        "String",
      ]),
    );
    expect(identifiers).not.toContain("ignoredComment");
    expect(identifiers).not.toContain("ignoredString");
  });

  it("includes practical collection, algorithm, node and context completions", () => {
    const completions = buildJavaCompletionItems(`
      class Solution {
        public TreeNode lowestCommonAncestor(TreeNode root) { return root; }
      }
    `);
    const byLabel = new Map(completions.map((item) => [item.label, item]));

    expect(byLabel.get("HashMap")).toMatchObject({ kind: "Class" });
    expect(byLabel.get("computeIfAbsent")).toMatchObject({
      kind: "Method",
      snippet: true,
    });
    expect(byLabel.get("fori")).toMatchObject({
      kind: "Snippet",
      snippet: true,
    });
    expect(byLabel.get("ListNode")).toMatchObject({ kind: "Snippet" });
    expect(byLabel.get("TreeNode")).toMatchObject({ kind: "Snippet" });
    expect(byLabel.get("lowestCommonAncestor")).toMatchObject({
      kind: "Variable",
      detail: "来自当前题目的代码模板",
    });
    expect(byLabel.get("root")).toMatchObject({ kind: "Variable" });
  });

  it("does not mutate or duplicate its static completion definitions", () => {
    const first = buildJavaCompletionItems("class FirstProblem {}");
    const hashMap = first.find((item) => item.label === "HashMap");
    hashMap.detail = "mutated by a caller";

    const second = buildJavaCompletionItems("class SecondProblem {}");
    expect(second.find((item) => item.label === "HashMap").detail).toBe(
      "Java 常用类型",
    );
    expect(
      second.filter((item) => item.label === "SecondProblem"),
    ).toHaveLength(1);
    expect(second.some((item) => item.label === "FirstProblem")).toBe(false);
  });
});
