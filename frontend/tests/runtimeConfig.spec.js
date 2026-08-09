import { describe, expect, it, vi } from "vitest";
import { resolveApiBase } from "@/config/runtime";

describe("runtime API configuration", () => {
  it("uses the same-origin gateway when no deployment override exists", () => {
    expect(resolveApiBase("", { protocol: "http:" })).toBe("/xzm");
  });

  it("normalizes an explicit integration endpoint", () => {
    expect(
      resolveApiBase("https://api.example.test/xzm///", {
        protocol: "https:",
      }),
    ).toBe("https://api.example.test/xzm");
  });

  it("prevents mixed-content deployments from breaking every API request", () => {
    const warning = vi.spyOn(console, "warn").mockImplementation(() => {});
    expect(
      resolveApiBase("http://203.0.113.10:8104/xzm", {
        protocol: "https:",
      }),
    ).toBe("/xzm");
    expect(warning).toHaveBeenCalledOnce();
    warning.mockRestore();
  });
});
