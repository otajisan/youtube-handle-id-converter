output "project_number" {
  description = "GCP プロジェクト番号"
  value       = data.google_project.this.number
}

output "artifact_registry_repository" {
  description = "backend イメージの push 先(docker tag に使うパス)"
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.backend.repository_id}"
}

output "runtime_service_account" {
  description = "Cloud Run 実行 SA"
  value       = google_service_account.runtime.email
}

output "deploy_service_account" {
  description = "CD(GitHub Actions)用 SA。GCP_DEPLOY_SA_EMAIL に登録する"
  value       = google_service_account.deploy.email
}

output "backend_url" {
  description = "Cloud Run サービスの URL。フロントの NEXT_PUBLIC_API_BASE_URL に設定する"
  value       = google_cloud_run_v2_service.backend.uri
}

output "quota_dashboard_url" {
  description = "Quota ダッシュボードの URL"
  value       = "https://console.cloud.google.com/monitoring/dashboards/builder/${element(split("/", google_monitoring_dashboard.quota.id), length(split("/", google_monitoring_dashboard.quota.id)) - 1)}?project=${var.project_id}"
}
