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
| `npm run preview`                    | `out/` を basePath 配下(`http://localhost:3000/youtube-handle-id-converter/`)で静的配信して確認 |

## 静的エクスポートの制約

`next.config.ts` で `output: "export"` / `basePath: "/youtube-handle-id-converter"` / `trailingSlash: true` / `images.unoptimized: true` を設定している。SSR・API Route・Server Actions・画像最適化は使えない。backend への通信はすべてブラウザから `NEXT_PUBLIC_API_BASE_URL` に対して行う。

## 環境変数

| 変数                       | 用途                                                                                   |
| -------------------------- | -------------------------------------------------------------------------------------- |
| `NEXT_PUBLIC_API_BASE_URL` | backend の URL(ローカル: `http://localhost:8180`、本番: Cloud Run の URL を CI で注入) |
| `NEXT_PUBLIC_BASE_PATH`    | basePath の上書き(通常は未設定)                                                        |

`src/lib/config.ts` 経由で参照する。
