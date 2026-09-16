"use client";

import Image from "next/image";
import { useCallback, useMemo, useState } from "react";
import { ApiError, convert, type ConvertResult, type Credentials } from "@/lib/api";
import { INPUT_KIND_LABEL, classifyInput, splitInputs } from "@/lib/inputKind";
import { toTsv, toTsvRow } from "@/lib/tsv";
import styles from "./Converter.module.css";

export const DEFAULT_MAX_INPUTS = 10;

const STATUS_LABEL: Record<ConvertResult["status"], string> = {
  ok: "変換済み",
  not_found: "見つかりません",
  invalid: "不正な入力",
};

interface ConverterProps {
  maxInputs?: number;
}

export function Converter({ maxInputs: initialMax = DEFAULT_MAX_INPUTS }: ConverterProps) {
  const [text, setText] = useState("");
  const [maxInputs, setMaxInputs] = useState(initialMax);
  const [loading, setLoading] = useState(false);
  const [results, setResults] = useState<ConvertResult[] | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [credentials, setCredentials] = useState<Credentials | null>(null);
  const [needsAuth, setNeedsAuth] = useState(false);
  const [copied, setCopied] = useState<string | null>(null);

  const inputs = useMemo(() => splitInputs(text), [text]);
  const overLimit = inputs.length > maxInputs;
  const canSubmit = inputs.length > 0 && !overLimit && !loading;

  const submit = useCallback(async () => {
    if (!canSubmit) return;
    setLoading(true);
    setError(null);
    setResults(null);
    try {
      const response = await convert(inputs, credentials ?? undefined);
      setResults(response.results);
      setNeedsAuth(false);
    } catch (e) {
      const apiError = e instanceof ApiError ? e : new ApiError("unknown", null, null);
      setError(apiError);
      if (apiError.kind === "unauthorized") setNeedsAuth(true);
      if (apiError.kind === "too_many_inputs" && apiError.problem?.max)
        setMaxInputs(apiError.problem.max);
    } finally {
      setLoading(false);
    }
  }, [canSubmit, inputs, credentials]);

  const copy = useCallback(async (key: string, value: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(key);
      setTimeout(() => setCopied((current) => (current === key ? null : current)), 1500);
    } catch {
      // クリップボードが使えない環境(権限なし等)では何もしない
    }
  }, []);

  return (
    <div className={styles.form}>
      <label htmlFor="inputs">
        ハンドル / Channel ID / YouTube URL を 1 行に 1 件ずつ入力(最大 {maxInputs} 件)
      </label>
      <textarea
        id="inputs"
        className={styles.textarea}
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder={"@youtube\nUC-9-kyTW8ZkZNDHQJ6FgpwQ\nhttps://www.youtube.com/@google"}
        spellCheck={false}
        disabled={loading}
      />
      {inputs.length > 0 && (
        <ul className={styles.preview} aria-label="入力種別のプレビュー">
          {inputs.map((input, i) => {
            const kind = classifyInput(input);
            return (
              <li
                key={`${i}-${input}`}
                className={`${styles.chip} ${kind === "invalid" ? styles.chipInvalid : ""}`}
              >
                {INPUT_KIND_LABEL[kind]}: {input.length > 40 ? `${input.slice(0, 40)}…` : input}
              </li>
            );
          })}
        </ul>
      )}
      <div className={styles.toolbar}>
        <button type="button" className={styles.button} onClick={submit} disabled={!canSubmit}>
          {loading ? "変換中…" : "変換する"}
        </button>
        <span className={`${styles.count} ${overLimit ? styles.countOver : ""}`} role="status">
          {inputs.length} / {maxInputs} 件{overLimit && ` — ${maxInputs} 件以下にしてください`}
        </span>
      </div>

      {needsAuth && (
        <div className={`${styles.alert} ${styles.alertInfo}`}>
          <p>
            このツールは現在、利用に認証が必要です。ユーザー名とパスワードを入力して再度変換してください。
          </p>
          <div className={styles.credentials}>
            <input
              type="text"
              aria-label="ユーザー名"
              placeholder="ユーザー名"
              autoComplete="username"
              onChange={(e) =>
                setCredentials((c) => ({ username: e.target.value, password: c?.password ?? "" }))
              }
            />
            <input
              type="password"
              aria-label="パスワード"
              placeholder="パスワード"
              autoComplete="current-password"
              onChange={(e) =>
                setCredentials((c) => ({ username: c?.username ?? "", password: e.target.value }))
              }
            />
          </div>
        </div>
      )}

      {error && error.kind !== "unauthorized" && (
        <div className={styles.alert} role="alert">
          <ErrorMessage error={error} />
        </div>
      )}

      {results && (
        <section className={styles.results} aria-label="変換結果">
          <div className={styles.toolbar}>
            <span className={styles.count}>
              {results.filter((r) => r.status === "ok").length} / {results.length} 件を変換
            </span>
            <button
              type="button"
              className={styles.secondary}
              onClick={() => copy("all", toTsv(results))}
            >
              {copied === "all" ? "コピーしました" : "全件を TSV でコピー"}
            </button>
          </div>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th></th>
                  <th>入力</th>
                  <th>ハンドル</th>
                  <th>Channel ID</th>
                  <th>チャンネル名</th>
                  <th>状態</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {results.map((r, i) => (
                  <tr key={`${i}-${r.input}`}>
                    <td>
                      {r.thumbnailUrl?.startsWith("https://") ? (
                        <Image
                          className={styles.thumb}
                          src={r.thumbnailUrl}
                          alt=""
                          width={36}
                          height={36}
                          unoptimized
                        />
                      ) : null}
                    </td>
                    <td className={styles.mono}>{r.input}</td>
                    <td className={styles.mono}>{r.handle ?? "—"}</td>
                    <td className={styles.mono}>{r.channelId ?? "—"}</td>
                    <td>{r.title ?? "—"}</td>
                    <td>
                      <span className={r.status === "ok" ? styles.statusOk : styles.statusNg}>
                        {STATUS_LABEL[r.status]}
                      </span>
                      {r.reason && <div className={styles.reason}>{r.reason}</div>}
                    </td>
                    <td>
                      {r.status === "ok" && (
                        <button
                          type="button"
                          className={styles.secondary}
                          onClick={() => copy(`row-${i}`, toTsvRow(r))}
                          aria-label={`${r.input} の結果をコピー`}
                        >
                          {copied === `row-${i}` ? "コピーしました" : "コピー"}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}

function ErrorMessage({ error }: { error: ApiError }) {
  switch (error.kind) {
    case "quota_exceeded": {
      const resetAt = error.problem?.resetAt ? new Date(error.problem.resetAt) : null;
      return (
        <p>
          YouTube Data API の 1 日の割り当てを使い切りました。
          {resetAt && !Number.isNaN(resetAt.getTime())
            ? ` ${resetAt.toLocaleString("ja-JP")}(太平洋時間 0 時)にリセットされます。`
            : " 太平洋時間 0 時にリセットされます。"}
        </p>
      );
    }
    case "rate_limited":
      return <p>リクエストが多すぎます。しばらく待ってから再度お試しください。</p>;
    case "maintenance":
      return <p>メンテナンス中のため変換を停止しています。しばらくしてから再度お試しください。</p>;
    case "too_many_inputs":
      return <p>入力件数が上限を超えています。{error.problem?.max ?? ""} 件以下にしてください。</p>;
    case "bad_request":
      return <p>リクエストが不正です。入力内容を確認してください。</p>;
    case "upstream":
      return <p>YouTube Data API との通信に失敗しました。しばらくしてから再試行してください。</p>;
    case "network":
      return <p>サーバーに接続できませんでした。ネットワークを確認して再試行してください。</p>;
    default:
      return <p>予期しないエラーが発生しました。しばらくしてから再試行してください。</p>;
  }
}
