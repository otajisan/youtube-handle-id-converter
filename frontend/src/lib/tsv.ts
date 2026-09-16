import type { ConvertResult } from "./api";

const HEADER = ["input", "status", "handle", "channelId", "title", "thumbnailUrl"] as const;

function cell(value: string | null): string {
  // TSV では区切りと改行を潰す
  const text = (value ?? "").replace(/[\t\r\n]+/g, " ");
  // 表計算ソフトに貼り付けたとき数式として解釈されないようにする(CSV インジェクション対策)
  return /^[=+\-@]/.test(text) ? `'${text}` : text;
}

export function toTsvRow(r: ConvertResult): string {
  return [r.input, r.status, r.handle, r.channelId, r.title, r.thumbnailUrl].map(cell).join("\t");
}

export function toTsv(results: ConvertResult[]): string {
  return [HEADER.join("\t"), ...results.map(toTsvRow)].join("\n");
}
