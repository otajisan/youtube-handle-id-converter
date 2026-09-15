# アプリケーションが使う API。bootstrap で有効化した基盤 API(iam / sts / storage 等)は含めない
resource "google_project_service" "this" {
  for_each = toset([
    "run.googleapis.com",
    "artifactregistry.googleapis.com",
    "secretmanager.googleapis.com",
  ])

  service            = each.value
  disable_on_destroy = false
}
