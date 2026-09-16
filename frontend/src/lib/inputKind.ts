/** 入力種別のプレビュー判定。判定ルールは backend の InputParser と同じ(最終判定は backend が行う) */
export type InputKind = "handle" | "channelId" | "url" | "invalid";

const CHANNEL_ID = /^UC[A-Za-z0-9_-]{22}$/;
const HANDLE = /^[A-Za-z0-9_.-]{3,30}$/;
const YOUTUBE_HOSTS = new Set(["youtube.com", "www.youtube.com", "m.youtube.com"]);
export const MAX_INPUT_LENGTH = 200;

export function classifyInput(raw: string): InputKind {
  const value = raw.trim();
  if (value.length === 0 || value.length > MAX_INPUT_LENGTH) return "invalid";
  if (/youtube\.com\//i.test(value)) {
    // backend と同様に URL として解釈し、ホストを youtube.com 系に限定する
    let url: URL;
    try {
      url = new URL(/^https?:\/\//i.test(value) ? value : `https://${value}`);
    } catch {
      return "invalid";
    }
    if (!YOUTUBE_HOSTS.has(url.hostname.toLowerCase())) return "invalid";
    const [first = "", second = ""] = url.pathname.split("/").filter(Boolean);
    if (first.startsWith("@") && HANDLE.test(first.slice(1))) return "url";
    if (first === "channel" && CHANNEL_ID.test(second)) return "url";
    return "invalid";
  }
  if (CHANNEL_ID.test(value)) return "channelId";
  if (HANDLE.test(value.replace(/^@/, ""))) return "handle";
  return "invalid";
}

export const INPUT_KIND_LABEL: Record<InputKind, string> = {
  handle: "ハンドル",
  channelId: "Channel ID",
  url: "URL",
  invalid: "不正",
};

/** テキストエリアの内容を 1 行 1 件に分割する(空行は無視) */
export function splitInputs(text: string): string[] {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.length > 0);
}
