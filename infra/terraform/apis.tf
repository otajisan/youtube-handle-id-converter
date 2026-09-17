# アプリケーションが使う API。bootstrap で有効化した基盤 API(iam / sts / storage 等)は含めない
resource "google_project_service" "this" {
  for_each = toset([
    "run.googleapis.com",
    "artifactregistry.googleapis.com",
    "secretmanager.googleapis.com",
    "youtube.googleapis.com",    # YouTube Data API v3(API Key はこのプロジェクトで発行する)
    "monitoring.googleapis.com", # Quota の可視化・アラート
  ])

  service            = each.value
  disable_on_destroy = false
}
