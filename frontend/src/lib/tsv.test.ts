import { describe, expect, it } from "vitest";
import { toTsv } from "./tsv";

describe("toTsv", () => {
  it("ヘッダ付きで、タブと改行はスペースに置き換える", () => {
    const tsv = toTsv([
      {
        input: "@a",
        status: "ok",
        handle: "@a",
        channelId: "UCa",
        title: "A\tB\nC",
        thumbnailUrl: null,
        reason: null,
      },
    ]);

    expect(tsv.split("\n")).toEqual([
      "input\tstatus\thandle\tchannelId\ttitle\tthumbnailUrl",
      "@a\tok\t@a\tUCa\tA B C\t",
    ]);
  });
});
