# インフラ(GCP / Terraform)

## 全体像

| 層 | 管理方法 | 内容 |
|---|---|---|
| bootstrap | [`infra/bootstrap.sh`](../infra/bootstrap.sh)(手動実行、冪等) | GCP プロジェクト、課金紐付け、tfstate バケット、Terraform 実行 SA、WIF、GitHub Repository variables |
| それ以外 | [`infra/terraform/`](../infra/terraform/)(CI で plan / apply) | API 有効化、Artifact Registry、Secret Manager、Cloud Run、IAM |

bootstrap 以外の GCP リソースを手動で変更しない。Secret の**値**は Terraform 管理外(`gcloud secrets versions add` で投入し、tfstate に残さない)。

## 構成値

| 項目 | 値 |
|---|---|
| プロジェクト ID / 番号 | `yt-handle-id-converter` / `578990507544` |
| リージョン | `asia-northeast1` |
| state バケット | `gs://yt-handle-id-converter-tfstate`(バージョニング有効、公開防止 enforced、uniform access) |
| Terraform apply SA | `terraform@yt-handle-id-converter.iam.gserviceaccount.com`(`main` ブランチからのみ借用可) |
| Terraform plan SA | `terraform-plan@yt-handle-id-converter.iam.gserviceaccount.com`(読み取り専用、PR からも借用可) |
| WIF プール / プロバイダ | `github` / `github-oidc` |

## bootstrap

```sh
BILLING_ACCOUNT=XXXXXX-XXXXXX-XXXXXX ./infra/bootstrap.sh
```

- 何度実行しても同じ結果になる(既存リソースはスキップ)
- **JSON キーは作成しない。** GitHub Actions は Workload Identity Federation で SA を借用する
- Terraform の SA は **apply 用と plan 用を分ける**。PR 上の任意の HCL(例: `data "external"`)が管理権限で実行されないようにするため
  - `terraform`(apply): owner / editor は付けず、管理するリソース種別のロールのみ — `serviceusage.serviceUsageAdmin` / `run.admin` / `artifactregistry.admin` / `secretmanager.admin` / `iam.serviceAccountAdmin` / `iam.serviceAccountUser` / `iam.workloadIdentityPoolAdmin` / `resourcemanager.projectIamAdmin` / `monitoring.editor`、state バケットへの `storage.objectAdmin`。WIF バインディングは `attribute.repo_ref = otajisan/youtube-handle-id-converter@refs/heads/main` に限定
  - `terraform-plan`(plan): `roles/viewer` と state バケットへの `storage.objectViewer` のみ。ロックを取れないため plan は `-lock=false` で実行する。WIF バインディングは `attribute.repository` 単位(provider の条件で `main` / PR に絞られる)
- WIF provider の attribute condition は `otajisan/youtube-handle-id-converter` の `refs/heads/main` と `refs/pull/*` に限定

### 課金アカウントのプロジェクト数上限

新規の課金アカウントは紐付けられるプロジェクト数が 5 に制限されている。`gcloud.billing.projects.link` が `FAILED_PRECONDITION` / `QuotaFailure` で失敗した場合は、不要なプロジェクトの課金を外す(`gcloud billing projects unlink <project>`)か、Cloud Console から上限緩和を申請する。

## Terraform

```sh
cd infra/terraform
export GOOGLE_OAUTH_ACCESS_TOKEN=$(gcloud auth print-access-token)   # ローカルは gcloud のトークンで認証(ADC 不要)
export TF_VAR_alert_email=<通知先メールアドレス>                         # リポジトリに置かない値
terraform init
terraform plan
```

- Terraform のバージョンは [`.terraform-version`](../infra/terraform/.terraform-version)(tfenv)で固定
- Lint はコンテナで実行できる:
  ```sh
  docker run --rm -v "$(pwd)":/data --entrypoint sh ghcr.io/terraform-linters/tflint -c 'tflint --init && tflint'
  docker run --rm -v "$(pwd)":/src aquasec/trivy config --severity HIGH,CRITICAL /src
  ```
- `apply` はローカルから実行しない。`main` へのマージで CI が実行する

### CI/CD([`.github/workflows/infra-ci.yml`](../.github/workflows/infra-ci.yml))

| ジョブ | トリガー | 内容 |
|---|---|---|
| `infra-lint` | PR / push | `fmt -check` → `validate`(backend 接続なし)→ tflint → trivy config(HIGH 以上で失敗)。GCP 認証不要 |
| `infra-plan` | PR(同一リポジトリ) | plan 用 SA で `plan -lock=false`。結果を PR コメントに投稿(同一コメントを更新) |
| `infra-apply` | `main` への push | apply 用 SA で `plan -out` → `apply`。Environment `production` |

**Environment `production` の判断**: デプロイ可能ブランチを保護ブランチ(`main`)に限定し、必須レビュアーは設定しない。ソロ開発では承認者が PR 作成者と同一人物になり形骸化するため。apply の内容は PR 時点の plan コメントでレビューし、WIF 側でも apply 用 SA は `main` のトークンしか受け付けない(二重の制約)。共同開発者が増えた時点で必須レビュアーを追加する。

## Terraform が管理するリソース

| ファイル | リソース |
|---|---|
| `apis.tf` | `run` / `artifactregistry` / `secretmanager` / `youtube`(Data API v3)/ `monitoring` API |
| `artifact_registry.tf` | Docker リポジトリ `backend`(直近 10 バージョン保持、30 日超は削除) |
| `secrets.tf` | Secret の器 `YOUTUBE_API_KEY` / `APP_AUTH_USERNAME` / `APP_AUTH_PASSWORD`。実行 SA にのみ `secretAccessor` |
| `iam.tf` | 実行 SA `backend-runtime`、CD 用 SA `github-deploy`(AR writer + 実行 SA の `serviceAccountUser`、WIF は `main` のみ) |
| `cloud_run.tf` | Cloud Run サービス `backend`(min 0 / max 2、512Mi、probe は 8181)、`allUsers` invoker、CD 用 SA の `run.developer` |
| `monitoring.tf` | Quota ダッシュボード、メール通知チャネル、アラート(直近 24 時間の消費が上限の 80% 超 / 枯渇)。宛先は `TF_VAR_alert_email` |

### Secret の値の投入(Terraform 管理外)

API Key は [Cloud Console → 認証情報](https://console.cloud.google.com/apis/credentials?project=yt-handle-id-converter) で発行し、**API の制限を YouTube Data API v3 のみ**にする。値は端末に表示せず、ファイルからパイプで投入する:

```sh
# .env(YOUTUBE_API_KEY=...)から値だけを取り出して投入する
grep '^YOUTUBE_API_KEY=' .env | cut -d= -f2- | tr -d '\n' \
  | gcloud secrets versions add YOUTUBE_API_KEY --project=yt-handle-id-converter --data-file=-
```

Cloud Run は `latest` バージョンを参照するため、サービス作成前に各 Secret に少なくとも 1 つのバージョンが必要(未設定の項目はダミー値でよい)。**環境変数として注入される Secret は新しいインスタンスの起動時に解決される**ので、値を変えたら新しいリビジョンをデプロイして確実に反映させる(`gcloud run services update backend --region=asia-northeast1 --update-env-vars=APP_MAINTENANCE_MODE=false` のように現在値と同じ値で update すれば設定を変えずにリビジョンだけ作れる)。

### Cloud Run の運用フラグ

`infra/terraform/variables.tf` の変数を変更して PR → apply で反映する(plan コメントで差分を確認できる)。緊急時は `gcloud run services update backend --region=asia-northeast1 --update-env-vars=APP_MAINTENANCE_MODE=true` で即時反映し、後から Terraform 側を追従させる。

| 変数 | 環境変数 | 用途 |
|---|---|---|
| `maintenance_mode` | `APP_MAINTENANCE_MODE` | `/api/**` を 503 にする緊急停止 |
| `auth_enabled` | `APP_AUTH_ENABLED` | Basic 認証の要求(資格情報は Secret) |
| `max_inputs` | `APP_MAX_INPUTS` | 1 リクエストの変換件数上限 |
| `cors_allowed_origins` | `APP_CORS_ALLOWED_ORIGINS` | 許可オリジン |

### CD(`main` マージ → Cloud Run)

[`.github/workflows/backend-ci.yml`](../.github/workflows/backend-ci.yml) の `backend-deploy` ジョブ(`backend/**` 変更を含む `main` への push で実行、Environment `production`):

1. WIF で `github-deploy` SA を借用(`main` のトークンのみ)
2. `linux/amd64` でビルドし Artifact Registry に `<sha>` と `latest` タグで push
3. `deploy-cloudrun` で **image だけ**を差し替え(`skip_default_labels: true`。env / secret / probe / scaling は Terraform 管理)
4. スモークテスト: 最新リビジョンが Ready かつトラフィック 100%、image が一致、URL が 5xx でない

Terraform は `image` を `ignore_changes` にしているため、CD 後も `terraform plan` は空差分になる。

backend と infra の両方を変更する PR をマージすると CD(image 更新)と `infra-apply`(env 等の更新)が同時に走るため、両ジョブはジョブ単位の `concurrency: cloud-run-backend` で直列化している(同時更新すると片方が `startup probe` 失敗として報告されることがある。#47 の記録参照)。

### 初期イメージ(1 回限りのシード)

Cloud Run サービスの作成にはイメージが必要なため、初回のみ手動で push した(`backend:initial`)。以後は CD(#9)が SHA タグで push し image を更新する。Terraform は `image` を `ignore_changes` にしている。

```sh
gcloud auth configure-docker asia-northeast1-docker.pkg.dev
cd backend && docker buildx build --platform linux/amd64 -t asia-northeast1-docker.pkg.dev/yt-handle-id-converter/backend/backend:initial --push .
```

## GitHub Repository variables

bootstrap が登録する。ワークフローから `vars.*` で参照する。

| 変数 | 用途 |
|---|---|
| `GCP_PROJECT_ID` / `GCP_PROJECT_NUMBER` / `GCP_REGION` | プロジェクト識別・リージョン |
| `GCP_WIF_PROVIDER` | `google-github-actions/auth` の `workload_identity_provider` |
| `GCP_TERRAFORM_SA_EMAIL` | 同 `service_account`(apply、`main` のみ) |
| `GCP_TERRAFORM_PLAN_SA_EMAIL` | 同 `service_account`(plan、読み取り専用) |
| `TF_STATE_BUCKET` | state バケット名 |

Terraform の output から登録する(CD 用):

| 変数 | 値の元 |
|---|---|
| `GCP_DEPLOY_SA_EMAIL` | `terraform output deploy_service_account` |
| `GCP_ARTIFACT_REGISTRY` | `terraform output artifact_registry_repository` |
| `CLOUD_RUN_SERVICE` | `backend`(`var.service_name`) |
| `NEXT_PUBLIC_API_BASE_URL` | `terraform output backend_url`(frontend のビルド時に埋め込む) |
| `ALERT_EMAIL` | Quota アラートの通知先。`TF_VAR_alert_email` として infra ワークフローに渡す(public リポジトリに置かないため) |
