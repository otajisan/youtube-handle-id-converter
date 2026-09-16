import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ConvertResult } from "@/lib/api";
import { Converter } from "./Converter";

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const ok: ConvertResult = {
  input: "@youtube",
  status: "ok",
  handle: "@youtube",
  channelId: "UC-9-kyTW8ZkZNDHQJ6FgpwQ",
  title: "YouTube",
  thumbnailUrl: "https://yt3.ggpht.com/x=s88",
  reason: null,
};
const notFound: ConvertResult = {
  input: "@nobody",
  status: "not_found",
  handle: null,
  channelId: null,
  title: null,
  thumbnailUrl: null,
  reason: "handle not found",
};

async function typeAndSubmit(text: string) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText(/1 行に 1 件ずつ/), text);
  await user.click(screen.getByRole("button", { name: "変換する" }));
  return user;
}

describe("Converter", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("結果を入力順にテーブル表示し、種別プレビューを出す", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(jsonResponse(200, { results: [ok, notFound] })),
    );

    render(<Converter />);
    await typeAndSubmit("@youtube\n@nobody");

    const preview = screen.getByRole("list", { name: "入力種別のプレビュー" });
    expect(
      within(preview)
        .getAllByRole("listitem")
        .map((li) => li.textContent),
    ).toEqual(["ハンドル: @youtube", "ハンドル: @nobody"]);

    const table = await screen.findByRole("table");
    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent("UC-9-kyTW8ZkZNDHQJ6FgpwQ");
    expect(rows[0]).toHaveTextContent("変換済み");
    expect(rows[1]).toHaveTextContent("見つかりません");
    expect(rows[1]).toHaveTextContent("handle not found");
    expect(screen.getByText("1 / 2 件を変換")).toBeInTheDocument();
  });

  it("上限を超えると警告し送信できない", async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    const user = userEvent.setup();

    render(<Converter maxInputs={2} />);
    await user.type(screen.getByLabelText(/1 行に 1 件ずつ/), "@a\n@b\n@c");

    expect(screen.getByRole("status")).toHaveTextContent("3 / 2 件 — 2 件以下にしてください");
    expect(screen.getByRole("button", { name: "変換する" })).toBeDisabled();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("空入力では送信できない", () => {
    render(<Converter />);
    expect(screen.getByRole("button", { name: "変換する" })).toBeDisabled();
  });

  it.each([
    [429, { resetAt: "2026-09-17T00:00-07:00" }, /1 日の割り当てを使い切りました/],
    [429, {}, /リクエストが多すぎます/],
    [503, {}, /メンテナンス中/],
    [502, {}, /YouTube Data API との通信に失敗/],
  ] as const)("HTTP %s は利用者向けメッセージを出す", async (status, body, message) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(status, body)));

    render(<Converter />);
    await typeAndSubmit("@youtube");

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("ネットワークエラーを表示する", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));

    render(<Converter />);
    await typeAndSubmit("@youtube");

    expect(await screen.findByRole("alert")).toHaveTextContent(/接続できませんでした/);
  });

  it("401 なら資格情報の入力欄を出し、Authorization ヘッダ付きで再送する", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse(401, { title: "Authentication required" }))
      .mockResolvedValueOnce(jsonResponse(200, { results: [ok] }));
    vi.stubGlobal("fetch", fetchMock);

    render(<Converter />);
    const user = await typeAndSubmit("@youtube");

    await user.type(await screen.findByLabelText("ユーザー名"), "op");
    await user.type(screen.getByLabelText("パスワード"), "pw");
    await user.click(screen.getByRole("button", { name: "変換する" }));

    expect(await screen.findByRole("table")).toBeInTheDocument();
    const [, init] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect((init.headers as Record<string, string>)["Authorization"]).toBe(
      `Basic ${btoa("op:pw")}`,
    );
    expect(screen.queryByLabelText("ユーザー名")).not.toBeInTheDocument();
  });

  it("誤った資格情報で再度 401 なら、その旨を表示する", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(401, {})));

    render(<Converter />);
    const user = await typeAndSubmit("@youtube");
    expect(await screen.findByRole("alert")).toHaveTextContent("認証が必要です");

    await user.type(screen.getByLabelText("ユーザー名"), "op");
    await user.type(screen.getByLabelText("パスワード"), "wrong");
    await user.click(screen.getByRole("button", { name: "変換する" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("正しくありません");
    expect(screen.getByLabelText("ユーザー名")).toHaveValue("op");
  });

  it("backend が返す上限(400 の max)に表示を追従させる", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(jsonResponse(400, { max: 1 })));

    render(<Converter maxInputs={5} />);
    await typeAndSubmit("@a\n@b");

    expect(await screen.findByRole("alert")).toHaveTextContent("1 件以下にしてください");
    expect(screen.getByRole("status")).toHaveTextContent("2 / 1 件");
    expect(screen.getByRole("button", { name: "変換する" })).toBeDisabled();
  });

  it("行コピーと全件 TSV コピーができる", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(jsonResponse(200, { results: [ok, notFound] })),
    );

    render(<Converter />);
    // userEvent.setup() が navigator.clipboard をスタブするので、そこから読み戻して検証する
    const user = await typeAndSubmit("@youtube\n@nobody");

    await user.click(await screen.findByRole("button", { name: "@youtube の結果をコピー" }));
    expect(await navigator.clipboard.readText()).toBe(
      "'@youtube\tok\t'@youtube\tUC-9-kyTW8ZkZNDHQJ6FgpwQ\tYouTube\thttps://yt3.ggpht.com/x=s88",
    );

    await user.click(screen.getByRole("button", { name: "全件を TSV でコピー" }));
    const tsv = await navigator.clipboard.readText();
    expect(tsv).toMatch(/^input\tstatus\thandle/);
    expect(tsv.split("\n")).toHaveLength(3);
    expect(screen.getByRole("button", { name: "コピーしました" })).toBeInTheDocument();
  });
});
