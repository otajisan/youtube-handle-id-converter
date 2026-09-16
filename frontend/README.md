# frontend

Next.js (App Router) + TypeScript。静的エクスポートして GitHub Pages(project site)で公開する。

## 技術スタック

| 項目            | 採用                                                              |
| --------------- | ----------------------------------------------------------------- |
| Node.js         | 24(`.node-version`)                                               |
| Next.js / React | 16 / 19(`output: "export"`)                                       |
| Lint / Format   | ESLint(`eslint-config-next` + `eslint-config-prettier`)、Prettier |
| テスト          | Vitest + Testing Library(jsdom)、カバレッジは v8                  |

## 開発

```sh
npm ci
cp .env.example .env.local   # 初回のみ。NEXT_PUBLIC_API_BASE_URL を設定
npm run dev                  # http://localhost:3000/youtube-handle-id-converter/
```

| スクリプト                           | 内容                                                                                            |
| ------------------------------------ | ----------------------------------------------------------------------------------------------- |
| `npm run lint`                       | ESLint + Prettier チェック                                                                      |
| `npm run format`                     | Prettier で整形                                                                                 |
| `npm run typecheck`                  | `tsc --noEmit`                                                                                  |
| `npm test` / `npm run test:coverage` | Vitest(`src/**/*.test.{ts,tsx}`)                                                                |
| `npm run build`                      | `out/` に静的エクスポート                                                                       |
| `npm run lock:linux`                 | Linux コンテナで `package-lock.json` を再生成(下記参照)                                         |
| `npm run preview`                    | `out/` を basePath 配下(`http://localhost:3000/youtube-handle-id-converter/`)で静的配信して確認 |

### 依存関係を変更したとき

macOS で `npm install` すると Linux 向けの optional 依存(`@emnapi/*` 等)が `package-lock.json` から落ち、CI(Linux)の `npm ci` が失敗する([npm/cli#4828](https://github.com/npm/cli/issues/4828))。依存を追加・更新したら **`npm run lock:linux`** で lockfile を作り直してからコミットする。Dependabot の PR は Linux で生成されるため影響しない。

## CI / デプロイ

[`.github/workflows/frontend-ci.yml`](../.github/workflows/frontend-ci.yml) が PR と `main` への push で実行される。

| ジョブ                       | 内容                                                                                                                            |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `frontend-check`             | `npm ci` → lint → typecheck → test(coverage)→ build。`main` では `out/` を Pages artifact として保存                            |
| `frontend-dependency-review` | PR で severity high 以上の脆弱な依存が追加されていれば失敗(npm は依存グラフが自動解析される)                                    |
| `frontend-deploy`            | `main` への push のみ。`deploy-pages` で https://otajisan.github.io/youtube-handle-id-converter/ に公開し、表示をスモークテスト |

本番の `NEXT_PUBLIC_API_BASE_URL` は Repository variable からビルド時に注入される。

## 画面構成

| ファイル                       | 内容                                                                                                                                                                                                        |
| ------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `src/components/Converter.tsx` | 変換フォーム。1 行 1 件のテキストエリア、種別プレビュー、上限超過の警告、結果テーブル、行 / 全件 TSV コピー、エラー出し分け(429 Quota / 429 レートリミット / 503 / 401 → 資格情報入力 / 502 / ネットワーク) |
| `src/lib/api.ts`               | `POST /api/v1/convert` の `fetch` ラッパー。ProblemDetail を種別付き `ApiError` に変換。Basic 認証は `Authorization` ヘッダで送る(cross-origin `fetch` ではブラウザのダイアログが出ないため自前の入力欄)    |
| `src/lib/inputKind.ts`         | 入力種別のプレビュー判定(backend の `InputParser` と同じ規則。最終判定は backend)                                                                                                                           |
| `src/lib/tsv.ts`               | 結果の TSV 化                                                                                                                                                                                               |

上限件数は既定 10 で、backend が 400(`max`)を返した場合はその値に追従する。

## 静的エクスポートの制約

`next.config.ts` で `output: "export"` / `basePath: "/youtube-handle-id-converter"` / `trailingSlash: true` / `images.unoptimized: true` を設定している。SSR・API Route・Server Actions・画像最適化は使えない。backend への通信はすべてブラウザから `NEXT_PUBLIC_API_BASE_URL` に対して行う。

## 環境変数

| 変数                       | 用途                                                                                   |
| -------------------------- | -------------------------------------------------------------------------------------- |
| `NEXT_PUBLIC_API_BASE_URL` | backend の URL(ローカル: `http://localhost:8180`、本番: Cloud Run の URL を CI で注入) |
| `NEXT_PUBLIC_BASE_PATH`    | basePath の上書き(通常は未設定)                                                        |

`src/lib/config.ts` 経由で参照する。
