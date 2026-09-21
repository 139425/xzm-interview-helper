import { describe, expect, it } from "vitest";
import {
  filterQuestions,
  priority,
  safeSource,
  localDate,
  parseFeedback,
} from "../src/utils/experience";

describe("interview corpus and preparation", () => {
  const questions = [
    {
      key: "q",
      question: "SQL 优化",
      group: "数据库",
      category: "SQL",
      reviewed: true,
      evidence: [
        { docId: "a", stage: "秋招", title: "一面", row: { text: "如何优化" } },
        {
          docId: "a",
          stage: "秋招",
          title: "一面",
          row: { text: "证明正确性" },
        },
        { docId: "b", stage: "暑期", title: "二面", row: { text: "SQL" } },
      ],
    },
  ];
  it("recomputes distinct document frequency inside stage filter", () => {
    const q = filterQuestions(questions, { stage: "秋招" })[0];
    expect(q.frequency).toBe(1);
    expect(q.mentions).toBe(2);
    expect(filterQuestions(questions)[0].frequency).toBe(2);
  });
  it("searches actual wording and company without inventing matches", () => {
    expect(filterQuestions(questions, { search: "正确性" })).toHaveLength(1);
    expect(filterQuestions(questions, { search: "不存在" })).toHaveLength(0);
  });
  it("keeps pending classification separate from verified corpus", () => {
    expect(filterQuestions(questions, { pending: true })).toHaveLength(0);
  });
  it("prioritizes overdue personal weakness over mere frequency", () => {
    const weak = priority(
      { frequency: 1 },
      { dueAt: "2026-09-20", state: "PROMPTED" },
      "",
      "2026-09-21",
    );
    expect(weak.score).toBeGreaterThan(
      priority({ frequency: 15 }, { state: "INDEPENDENT" }).score,
    );
    expect(weak.reasons).toContain("到期复测");
  });
  it("unknown mastery means diagnosis rather than an inferred failure", () => {
    expect(priority({ frequency: 1 }).reasons).toEqual(["先做诊断"]);
  });
  it("rejects untrusted source links", () => {
    expect(safeSource("javascript:alert(1)")).toBe("");
    expect(safeSource("https://www.yuque.com.evil.test/a")).toBe("");
    expect(safeSource("https://www.yuque.com/a/b")).toBe(
      "https://www.yuque.com/a/b",
    );
  });
  it("uses local date boundaries and tolerates unfinished feedback", () => {
    expect(localDate(new Date(2026, 8, 21, 0, 2))).toBe("2026-09-21");
    expect(parseFeedback("broken")).toEqual({});
  });
});
