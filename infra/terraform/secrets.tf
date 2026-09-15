# Secret の「器」だけを作る。値(version)は Terraform 管理外で、tfstate に残さない:
#   gcloud secrets versions add YOUTUBE_API_KEY --data-file=-
locals {
  secret_ids = toset([
    "YOUTUBE_API_KEY",
    "APP_AUTH_USERNAME",
    "APP_AUTH_PASSWORD",
  ])
}

resource "google_secret_manager_secret" "this" {
  for_each = local.secret_ids

  secret_id = each.value

  replication {
    auto {}
  }

  depends_on = [google_project_service.this["secretmanager.googleapis.com"]]
}

# Cloud Run 実行 SA だけが値を読める(secret 単位の IAM)
resource "google_secret_manager_secret_iam_member" "runtime_accessor" {
  for_each = google_secret_manager_secret.this

  secret_id = each.value.id
  role      = "roles/secretmanager.secretAccessor"
  member    = google_service_account.runtime.member
}
