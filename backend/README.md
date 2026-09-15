# backend

Kotlin + Spring Boot によるバックエンド。YouTube Data API v3 の呼び出しと API Key の秘匿を担い、Cloud Run にデプロイする。

## 技術スタック

| 項目 | バージョン |
|---|---|
| JDK | 25(Gradle toolchain が自動取得するため事前インストール不要) |
| Kotlin | 2.3.21 |
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

## 設定

| 環境変数 | 用途 |
|---|---|
| `PORT` | アプリケーションのリッスンポート(デフォルト 8180、Cloud Run が注入) |
| `MANAGEMENT_PORT` | Actuator のリッスンポート(デフォルト 8181) |
| `SPRING_PROFILES_ACTIVE=gcp` | Cloud Logging 形式の JSON ログを標準出力に出す |

### ポート分離(セキュリティ)

- アプリケーションと Actuator は別ポートで提供する。Cloud Run が外部公開するのはアプリケーションポート(`PORT`)だけなので、Actuator はインターネットから到達できない
- Cloud Run の startup / liveness probe は `MANAGEMENT_PORT` を直接指定する(probe の `port` はコンテナポートと異なる値を指定可能)
- デフォルトポートは推測されやすい 8080 を避けている

`gcp` プロファイルでは [`GoogleCloudLogFormatter`](src/main/kotlin/io/github/otajisan/youtubehandleidconverter/logging/GoogleCloudLogFormatter.kt) が `severity` / `message` / `timestamp` を持つ 1 行 JSON を出力し、Cloud Logging が自動でパースする。
