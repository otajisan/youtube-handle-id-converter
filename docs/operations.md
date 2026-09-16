# 運用手順

bot 等で YouTube Data API の Quota が急速に枯渇する事態に、運用者が**環境変数の変更だけ**で対処するための手順。

## 保護機構の一覧

| 環境変数 | Terraform 変数 | 既定 | 効果 |
|---|---|---|---|
| `APP_MAINTENANCE_MODE` | `maintenance_mode` | `false` | `true` で `/api/**` が 503(`Retry-After: 3600`)。Actuator(management ポート)は影響なし |
| `APP_AUTH_ENABLED` | `auth_enabled` | `false` | `true` で `/api/**` に Basic 認証を要求。資格情報は Secret `APP_AUTH_USERNAME` / `APP_AUTH_PASSWORD` |
| `APP_RATE_LIMIT_PER_MINUTE` | `rate_limit_per_minute` | `30` | IP ごとの 1 分あたり上限(Cloud Run インスタンス単位)。超過は 429。`0` で無効 |
| `APP_MAX_INPUTS` | `max_inputs` | `10` | 1 リクエストの入力件数上限 |
| `APP_CORS_ALLOWED_ORIGINS` | `cors_allowed_origins` | GitHub Pages | 許可オリジン(カンマ区切り) |

いずれも新しいリビジョンのデプロイで反映される(Cloud Run の環境変数はリビジョン単位)。

## 対処の流れ

### 1. Quota 消費を確認する

[Cloud Console → API とサービス → YouTube Data API v3 → 割り当て](https://console.cloud.google.com/apis/api/youtube.googleapis.com/quotas?project=yt-handle-id-converter)、または Cloud Logging で `/api/v1/convert` のリクエスト元を確認する。

### 2. 即時対応(gcloud、数十秒で反映)

```sh
# 緊急停止
gcloud run services update backend --project=yt-handle-id-converter --region=asia-northeast1 \
  --update-env-vars=APP_MAINTENANCE_MODE=true

# Basic 認証を要求する(事前に Secret に本番の資格情報を入れておくこと)
gcloud run services update backend --project=yt-handle-id-converter --region=asia-northeast1 \
  --update-env-vars=APP_AUTH_ENABLED=true

# レートリミットを厳しくする
gcloud run services update backend --project=yt-handle-id-converter --region=asia-northeast1 \
  --update-env-vars=APP_RATE_LIMIT_PER_MINUTE=5
```

**gcloud で変更した値は Terraform と食い違う(ドリフト)。** 落ち着いたら必ず 3 に進み、Terraform 側を同じ値にするか、gcloud で元に戻す。次の `terraform apply` はドリフトを Terraform の値に戻すため、戻し忘れると保護が意図せず解除される。

### 3. 恒久対応(Terraform、PR → apply)

`infra/terraform/variables.tf` の `default` を変更する PR を作る。`infra-plan` が差分を PR コメントに出すので確認してマージする。

```hcl
variable "maintenance_mode" {
  default = true
}
```

### 4. 解除

gcloud で `APP_MAINTENANCE_MODE=false` に戻す(Terraform の値と一致すればドリフトは消える)か、Terraform の変数を戻して apply する。

## Basic 認証の資格情報

```sh
printf '%s' 'operator' | gcloud secrets versions add APP_AUTH_USERNAME --project=yt-handle-id-converter --data-file=-
printf '%s' "$(openssl rand -base64 24)" | gcloud secrets versions add APP_AUTH_PASSWORD --project=yt-handle-id-converter --data-file=-
```

Cloud Run は `latest` バージョンを参照するが、**環境変数として注入されるのはリビジョン起動時**なので、値の変更後は新しいリビジョンのデプロイ(上記の `gcloud run services update` など)が必要。`APP_AUTH_ENABLED=true` で資格情報が空だと起動に失敗する(fail-fast)。

## 上限件数の変更

`APP_MAX_INPUTS`(Terraform `max_inputs`)を変更する。フロントの入力欄の上限表示は backend の 400 応答(`max` プロパティ)に従う。

## Dependabot アラートの対応方針

- Security タブのアラートと、PR の `*-dependency-review` ジョブ(severity high 以上で失敗)で検知する
- Dependabot の PR は CI が緑ならマージする。グループ化された PR(Kotlin / Spring Boot / react / dev-dependencies)は 1 つの PR に複数の更新が入る
- Spring Boot の BOM が管理する依存(Tomcat 等)に脆弱性がある場合は、`build.gradle.kts` の `constraints` で上書きし、BOM が追随したら外す(例: #20 の Tomcat 11.0.25)
