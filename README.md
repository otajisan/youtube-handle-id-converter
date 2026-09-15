# youtube-handle-id-converter

YouTube のハンドル(`@handle`)と Channel ID(`UC...`)を相互変換する Web ツール。

## 構成

| ディレクトリ | 内容 |
|---|---|
| [`backend/`](backend/) | Kotlin + Spring Boot。YouTube Data API v3 の呼び出しと API Key の秘匿を担う。Cloud Run にデプロイ |
| [`frontend/`](frontend/) | Next.js + TypeScript。静的エクスポートして GitHub Pages で公開 |
| [`infra/`](infra/) | Terraform による GCP リソース定義 |

## 開発状況

開発計画は [Epic #1](https://github.com/otajisan/youtube-handle-id-converter/issues/1) を参照。

## ライセンス

[MIT](LICENSE)
