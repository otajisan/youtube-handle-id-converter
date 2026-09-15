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
./gradlew bootRun   # http://localhost:8080/actuator/health
./gradlew ktlintFormat  # フォーマット自動修正
```

Jacoco のレポートは `build/reports/jacoco/test/html/index.html`。

## 設定

| 環境変数 | 用途 |
|---|---|
| `PORT` | リッスンポート(デフォルト 8080、Cloud Run が注入) |
| `SPRING_PROFILES_ACTIVE=gcp` | Cloud Logging 形式の JSON ログを標準出力に出す |

`gcp` プロファイルでは [`GoogleCloudLogFormatter`](src/main/kotlin/io/github/otajisan/youtubehandleidconverter/logging/GoogleCloudLogFormatter.kt) が `severity` / `message` / `timestamp` を持つ 1 行 JSON を出力し、Cloud Logging が自動でパースする。
