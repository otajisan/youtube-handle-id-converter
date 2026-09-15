import { describe, expect, it } from "vitest";
import { config } from "./config";

describe("config", () => {
  it("apiBaseUrl は末尾スラッシュを持たない", () => {
    expect(config.apiBaseUrl).not.toMatch(/\/$/);
  });
});
