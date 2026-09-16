import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, convert } from "./api";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("convert", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("入力を POST し、Basic 認証があれば Authorization ヘッダを付ける", async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { results: [] }));
    vi.stubGlobal("fetch", fetchMock);

    await convert(["@a"], { username: "op", password: "pw" });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toMatch(/\/api\/v1\/convert$/);
    expect(init.method).toBe("POST");
    expect(init.body).toBe(JSON.stringify({ inputs: ["@a"] }));
    expect((init.headers as Record<string, string>)["Authorization"]).toBe(
      `Basic ${btoa("op:pw")}`,
    );
  });

  it.each([
    [401, {}, "unauthorized"],
    [400, { max: 10 }, "too_many_inputs"],
    [400, {}, "bad_request"],
    [429, { resetAt: "2026-09-17T00:00-07:00" }, "quota_exceeded"],
    [429, {}, "rate_limited"],
    [502, {}, "upstream"],
    [503, {}, "maintenance"],
    [500, {}, "unknown"],
  ] as const)("HTTP %s %o → %s", async (status, body, kind) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(status, body)));

    await expect(convert(["@a"])).rejects.toMatchObject({ name: "ApiError", kind, status });
  });

  it("接続できなければ network", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));

    await expect(convert(["@a"])).rejects.toMatchObject({ kind: "network", status: null });
  });

  it("本文が JSON でなくても種別は判定できる", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("oops", { status: 503 })));

    const error = (await convert(["@a"]).catch((e: unknown) => e)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.kind).toBe("maintenance");
    expect(error.problem).toBeNull();
  });
});
