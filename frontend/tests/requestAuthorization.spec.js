import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => {
  const state = {
    responseFulfilled: null,
    responseRejected: null,
    notifyAuthExpired: vi.fn(),
    routerReplace: vi.fn(),
  };
  state.client = {
    interceptors: {
      request: { use: vi.fn() },
      response: {
        use: vi.fn((fulfilled, rejected) => {
          state.responseFulfilled = fulfilled;
          state.responseRejected = rejected;
        }),
      },
    },
  };
  return state;
});

vi.mock("axios", () => ({
  default: {
    create: vi.fn(() => mocks.client),
  },
}));

vi.mock("@/router", () => ({
  default: {
    currentRoute: { value: { path: "/algorithms" } },
    replace: mocks.routerReplace,
  },
}));

vi.mock("@/utils/authSession", () => ({
  notifyAuthExpired: mocks.notifyAuthExpired,
}));

import "@/utils/request";

describe("Axios authorization boundary", () => {
  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    vi.clearAllMocks();
    localStorage.setItem("token", "still-valid");
    localStorage.setItem("userInfo", '{"userId":1}');
  });

  it("rejects an application-level 403 without destroying the login session", async () => {
    const response = {
      status: 200,
      data: { code: 403, message: "仅管理员可执行此操作" },
    };

    await expect(mocks.responseFulfilled(response)).rejects.toMatchObject({
      name: "PermissionError",
      code: "FORBIDDEN",
      status: 403,
      message: "仅管理员可执行此操作",
      response,
    });
    expect(localStorage.getItem("token")).toBe("still-valid");
    expect(localStorage.getItem("userInfo")).toBe('{"userId":1}');
    expect(sessionStorage.getItem("authExpired")).toBeNull();
    expect(mocks.notifyAuthExpired).not.toHaveBeenCalled();
    expect(mocks.routerReplace).not.toHaveBeenCalled();
  });

  it("rejects HTTP 403 as a permission error without logging the user out", async () => {
    const response = {
      status: 403,
      data: { message: "无权读取该资源" },
    };

    await expect(
      mocks.responseRejected({ response, message: "Request failed" }),
    ).rejects.toMatchObject({
      name: "PermissionError",
      code: "FORBIDDEN",
      status: 403,
      message: "无权读取该资源",
      response,
    });
    expect(localStorage.getItem("token")).toBe("still-valid");
    expect(localStorage.getItem("userInfo")).toBe('{"userId":1}');
    expect(sessionStorage.getItem("authExpired")).toBeNull();
    expect(mocks.notifyAuthExpired).not.toHaveBeenCalled();
    expect(mocks.routerReplace).not.toHaveBeenCalled();
  });
});
