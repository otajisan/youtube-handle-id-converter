# インフラ(GCP / Terraform)

## 全体像

| 層 | 管理方法 | 内容 |
|---|---|---|
| bootstrap | [`infra/bootstrap.sh`](../infra/bootstrap.sh)(手動実行、冪等) | GCP プロジェクト、課金紐付け、tfstate バケット、Terraform 実行 SA、WIF、GitHub Repository variables |
| それ以外 | [`infra/terraform/`](../infra/terraform/)(CI で plan / apply) | API 有効化、Artifact Registry、Secret Manager、Cloud Run、IAM(#8 で追加) |

bootstrap 以外の GCP リソースを手動で変更しない。Secret の**値**は Terraform 管理外(`gcloud secrets versions add` で投入し、tfstate に残さない)。

## 構成値

| 項目 | 値 |
|---|---|
| プロジェクト ID / 番号 | `yt-handle-id-converter` / `578990507544` |
| リージョン | `asia-northeast1` |
| state バケット | `gs://yt-handle-id-converter-tfstate`(バージョニング有効、公開防止 enforced、uniform access) |
| Terraform 実行 SA | `terraform@yt-handle-id-converter.iam.gserviceaccount.com` |
| WIF プール / プロバイダ | `github` / `github-oidc` |

## bootstrap

```sh
BILLING_ACCOUNT=XXXXXX-XXXXXX-XXXXXX ./infra/bootstrap.sh
```

- 何度実行しても同じ結果になる(既存リソースはスキップ)
- **JSON キーは作成しない。** GitHub Actions は Workload Identity Federation で SA を借用する
- Terraform 実行 SA には owner / editor を付けず、Terraform が管理するリソース種別のロールのみ付与する:
  `serviceusage.serviceUsageAdmin` / `run.admin` / `artifactregistry.admin` / `secretmanager.admin` / `iam.serviceAccountAdmin` / `iam.serviceAccountUser` / `iam.workloadIdentityPoolAdmin` / `resourcemanager.projectIamAdmin`、および state バケットへの `storage.objectAdmin`
- WIF の attribute condition は `otajisan/youtube-handle-id-converter` の `refs/heads/main` と `refs/pull/*` に限定

### 課金アカウントのプロジェクト数上限

新規の課金アカウントは紐付けられるプロジェクト数が 5 に制限されている。`gcloud.billing.projects.link` が `FAILED_PRECONDITION` / `QuotaFailure` で失敗した場合は、不要なプロジェクトの課金を外す(`gcloud billing projects unlink <project>`)か、Cloud Console から上限緩和を申請する。

## Terraform

```sh
cd infra/terraform
export GOOGLE_OAUTH_ACCESS_TOKEN=$(gcloud auth print-access-token)   # ローカルは gcloud のトークンで認証(ADC 不要)
terraform init
terraform plan
```

- Terraform のバージョンは [`.terraform-version`](../infra/terraform/.terraform-version)(tfenv)で固定
- Lint はコンテナで実行できる:
  ```sh
  docker run --rm -v "$(pwd)":/data --entrypoint sh ghcr.io/terraform-linters/tflint -c 'tflint --init && tflint'
  docker run --rm -v "$(pwd)":/src aquasec/trivy config --severity HIGH,CRITICAL /src
  ```
- `apply` はローカルから実行しない。`main` へのマージで CI が実行する(#7)

## GitHub Repository variables

bootstrap が登録する。ワークフローから `vars.*` で参照する。

| 変数 | 用途 |
|---|---|
| `GCP_PROJECT_ID` / `GCP_PROJECT_NUMBER` / `GCP_REGION` | プロジェクト識別・リージョン |
| `GCP_WIF_PROVIDER` | `google-github-actions/auth` の `workload_identity_provider` |
| `GCP_TERRAFORM_SA_EMAIL` | 同 `service_account` |
| `TF_STATE_BUCKET` | state バケット名 |
