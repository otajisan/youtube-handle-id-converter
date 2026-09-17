# backend

Kotlin + Spring Boot によるバックエンド。YouTube Data API v3 の呼び出しと API Key の秘匿を担い、Cloud Run にデプロイする。

## 技術スタック

| 項目 | バージョン |
|---|---|
| JDK | 25(Gradle toolchain が自動取得するため事前インストール不要) |
| Kotlin | 2.4.20 |
| Spring Boot | 4.1.1 |
| Gradle | 9.7.1(wrapper) |
| Lint | ktlint 1.8.0 |
| テスト | JUnit 5 / MockK / Jacoco(line カバレッジ 80% 未満でビルド失敗) |

## 開発

```sh
./gradlew check     # ktlint → test → Jacoco 閾値検証
./gradlew bootRun   # app: http://localhost:8180 / health: http://localhost:8181/actuator/health
./gradlew ktlintFormat  # フォーマット自動修正
```

Jacoco のレポートは `build/reports/jacoco/test/html/index.html`。

## CI

[`.github/workflows/backend-ci.yml`](../.github/workflows/backend-ci.yml) が PR と `main` への push で実行される。

| ジョブ | 内容 |
|---|---|
| `backend-check` | ktlint → test → Jacoco(line 80% 未満で失敗)。カバレッジ要約を PR にコメント |
| `backend-dependency-review` | 依存グラフを GitHub に送信し、PR で severity high 以上の脆弱な依存が追加されていれば失敗 |
| `backend-docker-build` | hadolint → Docker build(push なし)→ コンテナ起動して healthy になることを確認 |
| `backend-deploy` | `main` への push のみ。Artifact Registry に push → Cloud Run の image を更新 → スモークテスト(詳細は [`docs/infra.md`](../docs/infra.md)) |

`backend/**` に変更が無い PR ではジョブがスキップされる(required check は成功扱い)。fork からの PR では PR コメントと依存グラフ送信を行わない。

## Docker

```sh
# リポジトリルートで
cp .env.example .env          # 初回のみ
docker compose up --build     # http://localhost:8180(Actuator の 8181 はホストに公開しない)
```

[`Dockerfile`](Dockerfile) はマルチステージ構成:

| ステージ | ベース | 内容 |
|---|---|---|
| build | `eclipse-temurin:25-jdk-alpine` | Gradle で bootJar を作成(テストは CI 側で実行) |
| extract | 同上 | `-Djarmode=tools extract --layers` で依存 / ローダー / アプリをレイヤー分割 |
| runtime | `eclipse-temurin:25-jre-alpine` | 非 root(uid 10001)、`HEALTHCHECK` は `MANAGEMENT_PORT` の readiness を参照 |

- ベースイメージはダイジェスト固定(Dependabot の `docker` エコシステムが更新)
- `compose.yaml` では `read_only` / `cap_drop: ALL` / `no-new-privileges` を有効化
- Cloud Run は `HEALTHCHECK` を無視するため、probe は Terraform(#8)で `MANAGEMENT_PORT` を指定する

## API

OpenAPI 定義: `GET /v3/api-docs`(Cloud Run では公開 URL 配下)

### `POST /api/v1/convert`

```json
{ "inputs": ["@youtube", "UC-9-kyTW8ZkZNDHQJ6FgpwQ", "https://www.youtube.com/@google"] }
```

- ハンドル / Channel ID / YouTube URL(`youtube.com/@handle`、`youtube.com/channel/UC...`)を混在可。上限は `APP_MAX_INPUTS`(既定 100)、超過は 400
- 重複は 1 回だけ問い合わせる(ハンドルは大文字小文字を区別しない)
- Quota 消費 = ハンドル件数 + ⌈Channel ID 件数 / 50⌉。Channel ID は 50 件ずつ `channels.list?id=` にまとめる(100 件なら最大 2 unit)。ハンドルは仮想スレッドで並列(同時 20 件)に引く

レスポンス(200、入力順):

```json
{ "results": [ { "input": "@youtube", "status": "ok", "handle": "@youtube", "channelId": "UC-9-kyTW8ZkZNDHQJ6FgpwQ", "title": "YouTube", "thumbnailUrl": "https://...", "reason": null } ] }
```

| `status` | 意味 |
|---|---|
| `ok` | 変換成功 |
| `not_found` | 該当チャンネルなし |
| `invalid` | 解釈できない入力(`reason` に理由) |

全体エラーは RFC 9457 `ProblemDetail`: 400(件数超過・不正 JSON)/ 429(Quota 枯渇。`resetAt` = 太平洋時間 0 時)/ 502(YouTube API 障害)。

CORS は `APP_CORS_ALLOWED_ORIGINS`(カンマ区切り)のオリジンからのみ `/api/**` を許可する。

## 保護機構

[`protection/`](src/main/kotlin/io/github/otajisan/youtubehandleidconverter/protection/) パッケージ。運用手順は [`docs/operations.md`](../docs/operations.md)。

| 機構 | 実装 | 応答 |
|---|---|---|
| メンテナンスモード | Spring Security 直後の Filter(CORS 処理後、preflight は通す) | 503 ProblemDetail + `Retry-After` |
| Basic 認証 | Spring Security(`/api/**` のみ。preflight と `/v3/api-docs` は公開、CSRF / セッションなし) | 401 ProblemDetail + `WWW-Authenticate` |
| レートリミット | Bucket4j + Caffeine、`X-Forwarded-For` **末尾**(Cloud Run が付与する実 IP)単位、インスタンス内メモリ | 429 ProblemDetail + `Retry-After` |

## YouTube Data API クライアント

[`youtube/`](src/main/kotlin/io/github/otajisan/youtubehandleidconverter/youtube/) パッケージ。`channels.list` の薄いラッパー。

| メソッド | API 呼び出し | Quota | 未存在時 |
|---|---|---|---|
| `byHandle(handle)` | `?part=id,snippet&forHandle=` | 1 unit / 件 | `YouTubeApiException.NotFound` |
| `byIds(ids)`(最大 50 件) | `?part=id,snippet&id=a,b,c` | 1 unit / リクエスト | 結果に含まれない |

- 403 + `reason=quotaExceeded` → `QuotaExceeded`、それ以外の HTTP エラー / 通信エラー → `Upstream`(メッセージに URL や鍵を含めない)
- `snippet.customUrl` が無いチャンネルは `handle = null`
- タイムアウトは `spring.http.clients.connect-timeout` / `read-timeout`(3s / 5s)
- テストは `@RestClientTest` + `MockRestServiceServer`(実 API Key は使わない)

## 設定

| 環境変数 | 用途 |
|---|---|
| `YOUTUBE_API_KEY` | YouTube Data API v3 の API Key(**必須**。未設定なら起動時に失敗する)。`x-goog-api-key` ヘッダで送り URL には含めない |
| `PORT` | アプリケーションのリッスンポート(デフォルト 8180、Cloud Run が注入) |
| `MANAGEMENT_PORT` | Actuator のリッスンポート(デフォルト 8181) |
| `SPRING_PROFILES_ACTIVE=gcp` | Cloud Logging 形式の JSON ログを標準出力に出す |
| `APP_MAX_INPUTS` | 1 リクエストの入力件数上限(既定 100、1〜200) |
| `APP_CORS_ALLOWED_ORIGINS` | CORS 許可オリジン(カンマ区切り。既定 `http://localhost:3000`) |
| `APP_MAINTENANCE_MODE` | `true` で `/api/**` が 503(既定 `false`) |
| `APP_AUTH_ENABLED` / `APP_AUTH_USERNAME` / `APP_AUTH_PASSWORD` | Basic 認証(既定無効。有効化時は資格情報必須) |
| `APP_RATE_LIMIT_PER_MINUTE` | IP ごとの 1 分あたり上限(既定 30、0 で無効) |

### ポート分離(セキュリティ)

- アプリケーションと Actuator は別ポートで提供する。Cloud Run が外部公開するのはアプリケーションポート(`PORT`)だけなので、Actuator はインターネットから到達できない
- Cloud Run の startup / liveness probe は `MANAGEMENT_PORT` を直接指定する(probe の `port` はコンテナポートと異なる値を指定可能)
- デフォルトポートは推測されやすい 8080 を避けている

`gcp` プロファイルでは [`GoogleCloudLogFormatter`](src/main/kotlin/io/github/otajisan/youtubehandleidconverter/logging/GoogleCloudLogFormatter.kt) が `severity` / `message` / `timestamp` を持つ 1 行 JSON を出力し、Cloud Logging が自動でパースする。
