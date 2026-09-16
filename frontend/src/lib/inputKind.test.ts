import { describe, expect, it } from "vitest";
import { classifyInput, splitInputs } from "./inputKind";

describe("classifyInput", () => {
  it.each([
    ["@youtube", "handle"],
    ["youtube", "handle"],
    ["UC-9-kyTW8ZkZNDHQJ6FgpwQ", "channelId"],
    ["https://www.youtube.com/@google", "url"],
    ["https://www.youtube.com/@google?si=abc", "url"],
    ["m.youtube.com/@google#x", "url"],
    ["youtube.com/channel/UC-9-kyTW8ZkZNDHQJ6FgpwQ", "url"],
    ["https://notyoutube.com/@google", "invalid"],
    ["https://www.youtube.com/watch?v=abc", "invalid"],
    ["ab", "invalid"],
    ["", "invalid"],
    ["a".repeat(201), "invalid"],
  ] as const)("%s → %s", (input, expected) => {
    expect(classifyInput(input)).toBe(expected);
  });
});

describe("splitInputs", () => {
  it("1 行 1 件、空行と前後の空白を除く", () => {
    expect(splitInputs(" @a \n\n UCx \r\n")).toEqual(["@a", "UCx"]);
  });
});
