# youtube-handle-id-converter

[![backend](https://github.com/otajisan/youtube-handle-id-converter/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/otajisan/youtube-handle-id-converter/actions/workflows/backend-ci.yml)

YouTube のハンドル(`@handle`)と Channel ID(`UC...`)を相互変換する Web ツール。

## 構成

| ディレクトリ | 内容 |
|---|---|
| [`backend/`](backend/) | Kotlin 2.3 + Spring Boot 4.1(JDK 25)。YouTube Data API v3 の呼び出しと API Key の秘匿を担う。Cloud Run にデプロイ |
| [`frontend/`](frontend/) | Next.js + TypeScript。静的エクスポートして GitHub Pages で公開 |
| [`infra/`](infra/) | Terraform による GCP リソース定義 |

## ローカル開発

```sh
# Docker で起動
cp .env.example .env              # 初回のみ。API Key 等を記入
docker compose up --build         # http://localhost:8180

# Gradle で直接起動
cd backend && ./gradlew check     # ktlint → test → Jacoco 閾値検証
cd backend && ./gradlew bootRun   # app: http://localhost:8180 / health: http://localhost:8181/actuator/health
```

JDK 25 は Gradle toolchain が自動取得するため事前インストールは不要(Gradle 自体の起動には JDK 17 以上が必要)。詳細は [`backend/README.md`](backend/README.md)。

## 開発状況

開発計画は [Epic #1](https://github.com/otajisan/youtube-handle-id-converter/issues/1) を参照。

## ライセンス

[MIT](LICENSE)
