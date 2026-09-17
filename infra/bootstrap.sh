#!/usr/bin/env bash
#
# Terraform 管理に入る前の「鶏と卵」部分だけを作る bootstrap。冪等(何度実行しても同じ結果)。
#   - GCP プロジェクト作成と課金紐付け
#   - tfstate 用 GCS バケット
#   - Terraform 実行用 Service Account(apply 用 / plan 用)と最小権限のロール
#   - GitHub Actions 用 Workload Identity Federation(JSON キーは作らない)
#   - GitHub Repository variables の登録
#
# 使い方: BILLING_ACCOUNT=XXXXXX-XXXXXX-XXXXXX ./infra/bootstrap.sh
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-yt-handle-id-converter}"
PROJECT_NAME="${PROJECT_NAME:-youtube-handle-id-converter}"
REGION="${REGION:-asia-northeast1}"
BILLING_ACCOUNT="${BILLING_ACCOUNT:?BILLING_ACCOUNT (XXXXXX-XXXXXX-XXXXXX) を指定してください}"
GITHUB_REPO="${GITHUB_REPO:-otajisan/youtube-handle-id-converter}"

STATE_BUCKET="${PROJECT_ID}-tfstate"
TF_SA_NAME="terraform"
TF_SA="${TF_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
TF_PLAN_SA_NAME="terraform-plan"
TF_PLAN_SA="${TF_PLAN_SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"
WIF_POOL="github"
WIF_PROVIDER="github-oidc"

log() { printf '\n==> %s\n' "$*"; }

# ---- 1. プロジェクト ----------------------------------------------------------
log "プロジェクト ${PROJECT_ID}"
if ! gcloud projects describe "${PROJECT_ID}" >/dev/null 2>&1; then
  gcloud projects create "${PROJECT_ID}" --name="${PROJECT_NAME}"
fi
# 既に紐付いていれば link を呼ばない(再 link は課金アカウントのプロジェクト数上限チェックに引っかかる)
if [ "$(gcloud billing projects describe "${PROJECT_ID}" --format='value(billingEnabled)')" != "True" ]; then
  gcloud billing projects link "${PROJECT_ID}" --billing-account="${BILLING_ACCOUNT}" >/dev/null
fi
PROJECT_NUMBER="$(gcloud projects describe "${PROJECT_ID}" --format='value(projectNumber)')"

# ---- 2. bootstrap に必要な API ------------------------------------------------
# アプリ用の API(run / artifactregistry / secretmanager)は Terraform 側で有効化する
log "API 有効化"
gcloud services enable --project="${PROJECT_ID}" \
  cloudresourcemanager.googleapis.com \
  serviceusage.googleapis.com \
  iam.googleapis.com \
  iamcredentials.googleapis.com \
  sts.googleapis.com \
  storage.googleapis.com

# ---- 3. tfstate バケット --------------------------------------------------------
log "state バケット gs://${STATE_BUCKET}"
if ! gcloud storage buckets describe "gs://${STATE_BUCKET}" --project="${PROJECT_ID}" >/dev/null 2>&1; then
  gcloud storage buckets create "gs://${STATE_BUCKET}" \
    --project="${PROJECT_ID}" \
    --location="${REGION}" \
    --uniform-bucket-level-access \
    --public-access-prevention
fi
gcloud storage buckets update "gs://${STATE_BUCKET}" --versioning >/dev/null

# ---- 4. Terraform 実行 SA -------------------------------------------------------
# apply 用(書き込み権限、main ブランチからのみ)と plan 用(読み取りのみ、PR からも可)を分ける。
# PR 上の任意の HCL が管理権限で実行されるのを防ぐため
log "Service Account ${TF_SA} (apply)"
if ! gcloud iam service-accounts describe "${TF_SA}" --project="${PROJECT_ID}" >/dev/null 2>&1; then
  gcloud iam service-accounts create "${TF_SA_NAME}" \
    --project="${PROJECT_ID}" \
    --display-name="Terraform apply (GitHub Actions, main only)"
fi
log "Service Account ${TF_PLAN_SA} (plan)"
if ! gcloud iam service-accounts describe "${TF_PLAN_SA}" --project="${PROJECT_ID}" >/dev/null 2>&1; then
  gcloud iam service-accounts create "${TF_PLAN_SA_NAME}" \
    --project="${PROJECT_ID}" \
    --display-name="Terraform plan (GitHub Actions, read-only)"
fi

# Terraform が管理するリソース種別に絞ったロール(owner / editor は付けない)
for role in \
  roles/serviceusage.serviceUsageAdmin \
  roles/run.admin \
  roles/artifactregistry.admin \
  roles/secretmanager.admin \
  roles/iam.serviceAccountAdmin \
  roles/iam.serviceAccountUser \
  roles/iam.workloadIdentityPoolAdmin \
  roles/resourcemanager.projectIamAdmin \
  roles/monitoring.editor
do
  gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
    --member="serviceAccount:${TF_SA}" --role="${role}" --condition=None >/dev/null
done
# state バケットへの読み書きはバケット単位で付与
gcloud storage buckets add-iam-policy-binding "gs://${STATE_BUCKET}" \
  --member="serviceAccount:${TF_SA}" --role="roles/storage.objectAdmin" >/dev/null

# plan 用は読み取りのみ(state は objectViewer なので plan は -lock=false で実行する)
gcloud projects add-iam-policy-binding "${PROJECT_ID}" \
  --member="serviceAccount:${TF_PLAN_SA}" --role="roles/viewer" --condition=None >/dev/null
gcloud storage buckets add-iam-policy-binding "gs://${STATE_BUCKET}" \
  --member="serviceAccount:${TF_PLAN_SA}" --role="roles/storage.objectViewer" >/dev/null

# ---- 5. Workload Identity Federation ------------------------------------------
log "WIF pool=${WIF_POOL} provider=${WIF_PROVIDER}"
if ! gcloud iam workload-identity-pools describe "${WIF_POOL}" \
      --project="${PROJECT_ID}" --location=global >/dev/null 2>&1; then
  gcloud iam workload-identity-pools create "${WIF_POOL}" \
    --project="${PROJECT_ID}" --location=global \
    --display-name="GitHub Actions"
fi

# このリポジトリの main と PR からのトークンだけを受け付ける
ATTRIBUTE_CONDITION="assertion.repository == '${GITHUB_REPO}' && (assertion.ref == 'refs/heads/main' || assertion.ref.startsWith('refs/pull/'))"
# repo_ref = "owner/name@refs/heads/main" の形にして、SA ごとにブランチ単位で借用を絞れるようにする
ATTRIBUTE_MAPPING="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref,attribute.repo_ref=assertion.repository+'@'+assertion.ref"
if ! gcloud iam workload-identity-pools providers describe "${WIF_PROVIDER}" \
      --project="${PROJECT_ID}" --location=global --workload-identity-pool="${WIF_POOL}" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools providers create-oidc "${WIF_PROVIDER}" \
    --project="${PROJECT_ID}" --location=global \
    --workload-identity-pool="${WIF_POOL}" \
    --display-name="GitHub OIDC" \
    --issuer-uri="https://token.actions.githubusercontent.com" \
    --attribute-mapping="${ATTRIBUTE_MAPPING}" \
    --attribute-condition="${ATTRIBUTE_CONDITION}"
else
  gcloud iam workload-identity-pools providers update-oidc "${WIF_PROVIDER}" \
    --project="${PROJECT_ID}" --location=global \
    --workload-identity-pool="${WIF_POOL}" \
    --attribute-mapping="${ATTRIBUTE_MAPPING}" \
    --attribute-condition="${ATTRIBUTE_CONDITION}" >/dev/null
fi

WIF_PROVIDER_NAME="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${WIF_POOL}/providers/${WIF_PROVIDER}"
POOL_PRINCIPAL="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${WIF_POOL}"

# apply 用 SA は main ブランチのワークフローからのみ借用できる
gcloud iam service-accounts add-iam-policy-binding "${TF_SA}" \
  --project="${PROJECT_ID}" \
  --role="roles/iam.workloadIdentityUser" \
  --member="${POOL_PRINCIPAL}/attribute.repo_ref/${GITHUB_REPO}@refs/heads/main" >/dev/null
# 以前の(リポジトリ全体に許可する)バインディングが残っていれば外す
gcloud iam service-accounts remove-iam-policy-binding "${TF_SA}" \
  --project="${PROJECT_ID}" \
  --role="roles/iam.workloadIdentityUser" \
  --member="${POOL_PRINCIPAL}/attribute.repository/${GITHUB_REPO}" >/dev/null 2>&1 || true

# plan 用 SA はリポジトリの main / PR から借用できる(provider の条件で絞られる)
gcloud iam service-accounts add-iam-policy-binding "${TF_PLAN_SA}" \
  --project="${PROJECT_ID}" \
  --role="roles/iam.workloadIdentityUser" \
  --member="${POOL_PRINCIPAL}/attribute.repository/${GITHUB_REPO}" >/dev/null

# ---- 6. GitHub Repository variables -------------------------------------------
log "GitHub Repository variables (${GITHUB_REPO})"
gh variable set GCP_PROJECT_ID        --repo "${GITHUB_REPO}" --body "${PROJECT_ID}"
gh variable set GCP_PROJECT_NUMBER    --repo "${GITHUB_REPO}" --body "${PROJECT_NUMBER}"
gh variable set GCP_REGION            --repo "${GITHUB_REPO}" --body "${REGION}"
gh variable set GCP_WIF_PROVIDER      --repo "${GITHUB_REPO}" --body "${WIF_PROVIDER_NAME}"
gh variable set GCP_TERRAFORM_SA_EMAIL --repo "${GITHUB_REPO}" --body "${TF_SA}"
gh variable set GCP_TERRAFORM_PLAN_SA_EMAIL --repo "${GITHUB_REPO}" --body "${TF_PLAN_SA}"
gh variable set TF_STATE_BUCKET       --repo "${GITHUB_REPO}" --body "${STATE_BUCKET}"

log "完了"
cat <<SUMMARY
  PROJECT_ID          = ${PROJECT_ID}
  PROJECT_NUMBER      = ${PROJECT_NUMBER}
  REGION              = ${REGION}
  STATE_BUCKET        = gs://${STATE_BUCKET}
  TERRAFORM_SA        = ${TF_SA} (apply, main only)
  TERRAFORM_PLAN_SA   = ${TF_PLAN_SA} (plan, read-only)
  WIF_PROVIDER        = ${WIF_PROVIDER_NAME}
SUMMARY
