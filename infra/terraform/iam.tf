# ---- Cloud Run 実行 SA ---------------------------------------------------------
# Secret の読み取り以外の権限は持たせない(secrets.tf で secret 単位に付与)
resource "google_service_account" "runtime" {
  account_id   = "${var.service_name}-runtime"
  display_name = "Cloud Run runtime (${var.service_name})"
}

# ---- CD 用 SA(GitHub Actions、main ブランチのみ) --------------------------------
# イメージの push と Cloud Run のリビジョン更新だけができる
resource "google_service_account" "deploy" {
  account_id   = "github-deploy"
  display_name = "GitHub Actions deploy (main only)"
}

resource "google_artifact_registry_repository_iam_member" "deploy_writer" {
  location   = google_artifact_registry_repository.backend.location
  repository = google_artifact_registry_repository.backend.name
  role       = "roles/artifactregistry.writer"
  member     = google_service_account.deploy.member
}

# 新しいリビジョンを実行 SA で起動するために必要
resource "google_service_account_iam_member" "deploy_acts_as_runtime" {
  service_account_id = google_service_account.runtime.name
  role               = "roles/iam.serviceAccountUser"
  member             = google_service_account.deploy.member
}

# WIF: bootstrap が作成した pool "github" を参照し、main ブランチのトークンにのみ借用を許可する
locals {
  wif_pool_principal = "principalSet://iam.googleapis.com/projects/${data.google_project.this.number}/locations/global/workloadIdentityPools/github"
}

resource "google_service_account_iam_member" "deploy_wif" {
  service_account_id = google_service_account.deploy.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "${local.wif_pool_principal}/attribute.repo_ref/${var.github_repository}@refs/heads/main"
}
